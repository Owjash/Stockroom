package com.owjash.assistant;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.core.io.ClassPathResource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.owjash.assistant.InventoryInputs.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:inventory-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","app.seed-demo=false","spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class InventoryIntegrationTest {
    @Autowired InventoryService service;
    @Autowired Products products;
    @Autowired Suppliers suppliers;
    @Autowired StockOrders orders;
    @Autowired MockMvc mvc;
    @Autowired DocumentService documents;
    private Long supplier;
    @BeforeEach void setup() {
        orders.deleteAll(); products.deleteAll(); suppliers.deleteAll();
        for(var d:documents.list()) documents.delete(d.id);
        supplier=service.saveSupplier(null,new SupplierInput("Test supplier","test@example.com","3 days")).id;
    }
    Product product(String sku,int quantity) {
        return service.saveProduct(null,new ProductInput(sku,"Test "+sku,"Electronics",quantity,3,new BigDecimal("5.50"),supplier));
    }
    @Test void saleAndReceiptChangeStockAndPreserveHistoricalPrices() {
        var p=product("P1",10);
        var sale=service.placeOrder(new OrderInput("SALE",List.of(new LineInput(p.id,3))));
        assertThat(products.findById(p.id).orElseThrow().quantity).isEqualTo(7);
        assertThat(sale.total).isEqualByComparingTo("16.50");
        service.placeOrder(new OrderInput("RECEIPT",List.of(new LineInput(p.id,2))));
        assertThat(products.findById(p.id).orElseThrow().quantity).isEqualTo(9);
        assertThat(service.sales(7).get("orders")).isEqualTo(1);
        assertThat(service.sales(7).get("revenue")).isEqualTo(new BigDecimal("16.50"));
    }
    @Test void failedMultiLineOrderRollsBackAllChanges() {
        var p=product("P1",10); var q=product("P2",1);
        assertThatThrownBy(() -> service.placeOrder(new OrderInput("SALE",List.of(new LineInput(p.id,2),new LineInput(q.id,5)))))
                .hasMessageContaining("Insufficient stock");
        assertThat(products.findById(p.id).orElseThrow().quantity).isEqualTo(10);
        assertThat(orders.count()).isZero();
    }
    @Test void concurrentSalesCannotBothBuyTheLastUnit() throws Exception {
        var p=product("LAST",1); var ready=new CountDownLatch(2); var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> buy=() -> { ready.countDown(); start.await(); try {service.placeOrder(new OrderInput("SALE",List.of(new LineInput(p.id,1)))); return true;} catch(org.springframework.web.server.ResponseStatusException e){return false;} };
            var a=pool.submit(buy); var b=pool.submit(buy); assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue(); start.countDown();
            assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
        }
        assertThat(products.findById(p.id).orElseThrow().quantity).isZero(); assertThat(orders.count()).isEqualTo(1);
    }
    @Test void repeatedProductLinesAreAggregatedBeforeCheckingStock() {
        var p=product("P1",3);
        assertThatThrownBy(() -> service.placeOrder(new OrderInput("SALE",List.of(new LineInput(p.id,2),new LineInput(p.id,2)))))
                .hasMessageContaining("Insufficient stock");
        assertThat(products.findById(p.id).orElseThrow().quantity).isEqualTo(3);
    }
    @Test void supplierCannotBeDeletedWhileProductsReferenceIt() {
        product("P1",2); assertThatThrownBy(() -> service.deleteSupplier(supplier)).hasMessageContaining("Reassign");
    }
    @Test void skuUniquenessIgnoresInputCase() {
        product("part",2); assertThatThrownBy(() -> product("PART",3)).hasMessageContaining("SKU already exists");
    }
    @Test void negativeStockIsRejectedAtApiBoundary() throws Exception {
        mvc.perform(post("/api/products").contentType("application/json").content("""
            {"sku":"NEG","name":"Negative","category":"Test","quantity":-1,"reorderLevel":0,"price":1,"supplierId":%d}
            """.formatted(supplier))).andExpect(status().isBadRequest());
    }
    @Test void oversellReturnsConflictAndLeavesStockUntouched() throws Exception {
        var p=product("P1",1);
        mvc.perform(post("/api/orders").contentType("application/json").content("{\"type\":\"SALE\",\"lines\":[{\"productId\":"+p.id+",\"quantity\":2}]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("message").value(org.hamcrest.Matchers.containsString("Insufficient stock")));
        assertThat(products.findById(p.id).orElseThrow().quantity).isEqualTo(1);
    }
    @Test void pdfUploadRetrievalCitationAndDeletionWork() throws Exception {
        byte[] pdf=new ClassPathResource("demo/manual.pdf").getContentAsByteArray();
        var file=new MockMultipartFile("file","policy.pdf","application/pdf",pdf);
        var response=mvc.perform(multipart("/api/documents").file(file)).andExpect(status().isCreated()).andExpect(jsonPath("pageCount").value(3)).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/documents/"+id+"/ask").contentType("application/json").content("{\"question\":\"What is the minimum order quantity?\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("mode").value("keyword")).andExpect(jsonPath("sources[0].page").value(1));
        mvc.perform(get("/api/documents/"+id+"/pdf")).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"));
        mvc.perform(delete("/api/documents/"+id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/"+id+"/history")).andExpect(status().isNotFound());
    }
    @Test void documentScopePreventsCrossDocumentRetrieval() throws Exception {
        var a=documents.upload("a.pdf",new ClassPathResource("demo/manual.pdf").getContentAsByteArray());
        var b=documents.upload("b.pdf",singlePagePdf("A completely unrelated astronomy document about Saturn and Jupiter."));
        assertThat(documents.ask(a.id,"minimum order quantity").sources()).isNotEmpty();
        assertThat(documents.ask(b.id,"minimum order quantity").sources()).isEmpty();
    }
    @Test void malformedAndScannedPdfsProduceHelpfulErrors() throws Exception {
        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file","bad.pdf","application/pdf","not pdf".getBytes())))
                .andExpect(status().isBadRequest());
        assertThatThrownBy(() -> documents.upload("blank.pdf",singlePagePdf(""))).hasMessageContaining("No readable text");
    }
    @Test void evaluationHasMeasuredTimingsAndRejectsInvalidLabels() throws Exception {
        var d=documents.upload("policy.pdf",new ClassPathResource("demo/manual.pdf").getContentAsByteArray());
        var report=documents.evaluate(d.id,List.of(new ApiTypes.EvalCase("minimum order quantity",1,false),new ApiTypes.EvalCase("warranty duration",null,true)),false);
        assertThat(report.count()).isEqualTo(2); assertThat(report.documents()).isEqualTo(1); assertThat(report.pages()).isEqualTo(3);
        assertThat(report.pageHitRateAt3()).isEqualTo(1.0); assertThat(report.medianRetrievalMs()).isGreaterThanOrEqualTo(0);
        assertThat(report.abstentionBasis()).contains("threshold");
        assertThatThrownBy(() -> documents.evaluate(d.id,List.of(new ApiTypes.EvalCase("x",9,false)),false)).hasMessageContaining("valid expectedPage");
    }
    @Test void assistantDoesNotPretendAiIsEnabled() throws Exception {
        mvc.perform(post("/api/assistant").contentType("application/json").content("{\"question\":\"Which products need restocking?\"}"))
                .andExpect(status().isServiceUnavailable());
    }
    private byte[] singlePagePdf(String text) throws Exception {
        try(var pdf=new org.apache.pdfbox.pdmodel.PDDocument();var bytes=new java.io.ByteArrayOutputStream()) {
            var page=new org.apache.pdfbox.pdmodel.PDPage(); pdf.addPage(page);
            try(var canvas=new org.apache.pdfbox.pdmodel.PDPageContentStream(pdf,page)) {
                canvas.beginText();canvas.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);canvas.newLineAtOffset(50,700);canvas.showText(text);canvas.endText();
            }
            pdf.save(bytes);return bytes.toByteArray();
        }
    }
}
