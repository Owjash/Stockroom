package com.owjash.assistant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.owjash.assistant.InventoryInputs.*;

@Service
@Transactional(readOnly=true)
public class InventoryService {
    private final Products products;
    private final Suppliers suppliers;
    private final StockOrders orders;
    InventoryService(Products products,Suppliers suppliers,StockOrders orders) { this.products=products; this.suppliers=suppliers; this.orders=orders; }
    public List<Product> products(String search) {
        String q=search==null?"":search.trim().toLowerCase(Locale.ROOT);
        return products.findAll().stream().filter(p -> (p.name+" "+p.sku+" "+p.category+" "+p.supplier.name).toLowerCase(Locale.ROOT).contains(q)).sorted(Comparator.comparing(p -> p.name)).toList();
    }
    public List<Supplier> suppliers() { return suppliers.findAll(); }
    public List<Product> lowStock() { return products.lowStock(); }
    public List<StockOrder> orders() { return orders.findTop100ByOrderByCreatedAtDesc(); }
    @Transactional
    public Supplier saveSupplier(Long id,SupplierInput in) {
        Supplier s=id==null?new Supplier():suppliers.findById(id).orElseThrow(() -> missing("Supplier"));
        s.name=in.name().trim(); s.email=in.email(); s.leadTime=in.leadTime(); return suppliers.save(s);
    }
    @Transactional
    public void deleteSupplier(Long id) {
        if (!suppliers.existsById(id)) throw missing("Supplier");
        if (products.existsBySupplierId(id)) throw conflict("Reassign this supplier's products before deleting it.");
        suppliers.deleteById(id);
    }
    @Transactional
    public Product saveProduct(Long id,ProductInput in) {
        Product p=id==null?new Product():products.lockById(id).orElseThrow(() -> missing("Product"));
        String sku=in.sku().toUpperCase(Locale.ROOT);
        products.findBySku(sku).filter(other -> !Objects.equals(other.id,id)).ifPresent(other -> { throw conflict("That SKU already exists."); });
        p.sku=sku; p.name=in.name().trim(); p.category=in.category().trim(); p.quantity=in.quantity();
        p.reorderLevel=in.reorderLevel(); p.price=in.price();
        p.supplier=suppliers.findById(in.supplierId()).orElseThrow(() -> missing("Supplier"));
        return products.save(p);
    }
    @Transactional
    public void deleteProduct(Long id) {
        Product p=products.lockById(id).orElseThrow(() -> missing("Product")); products.delete(p);
    }
    @Transactional
    public StockOrder placeOrder(OrderInput in) {
        var quantities=new TreeMap<Long,Integer>();
        for (LineInput line:in.lines()) quantities.merge(line.productId(),line.quantity(),Math::addExact);
        var order=new StockOrder(); order.type=in.type(); order.createdAt=Instant.now(); order.total=BigDecimal.ZERO;
        // Acquire product locks in a consistent order. The transaction rolls back every line if one fails.
        for (var item:quantities.entrySet()) {
            Product p=products.lockById(item.getKey()).orElseThrow(() -> missing("Product"));
            int change="SALE".equals(in.type())?-item.getValue():item.getValue();
            long next=(long)p.quantity+change;
            if (next<0) throw conflict("Insufficient stock for "+p.name+". Available: "+p.quantity+".");
            if (next>1000000) throw conflict("Stock cannot exceed 1,000,000 units per product.");
            p.quantity=(int)next; products.save(p);
            order.lines.add(new OrderLine(p,item.getValue()));
            order.total=order.total.add(p.price.multiply(BigDecimal.valueOf(item.getValue())));
        }
        return orders.save(order);
    }
    public Map<String,Object> dashboard() {
        var all=products.findAll();
        BigDecimal value=all.stream().map(p -> p.price.multiply(BigDecimal.valueOf(p.quantity))).reduce(BigDecimal.ZERO,BigDecimal::add);
        return Map.of("products",all.size(),"units",all.stream().mapToLong(p -> p.quantity).sum(),"lowStock",all.stream().filter(p -> p.quantity<=p.reorderLevel).count(),"inventoryValue",value,"suppliers",suppliers.count());
    }
    public Map<String,Object> sales(int days) {
        if (days<1 || days>366) throw PdfIngestor.invalid("Days must be between 1 and 366.");
        LocalDate first=LocalDate.now(ZoneOffset.UTC).minusDays(days-1);
        var found=orders.findByTypeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc("SALE",first.atStartOfDay().toInstant(ZoneOffset.UTC));
        var totals=new TreeMap<String,BigDecimal>();
        for (int i=0;i<days;i++) totals.put(first.plusDays(i).toString(),BigDecimal.ZERO);
        var sold=new HashMap<Long,Integer>(); var names=new HashMap<Long,String>(); BigDecimal revenue=BigDecimal.ZERO;
        for (var order:found) {
            String day=order.createdAt.atOffset(ZoneOffset.UTC).toLocalDate().toString();
            totals.merge(day,order.total,BigDecimal::add); revenue=revenue.add(order.total);
            for(var line:order.lines) { sold.merge(line.productId,line.quantity,Integer::sum); names.put(line.productId,line.productName); }
        }
        var top=sold.entrySet().stream().sorted(Map.Entry.<Long,Integer>comparingByValue().reversed()).limit(10)
                .map(e -> Map.of("productId",e.getKey(),"name",names.get(e.getKey()),"units",e.getValue())).toList();
        return Map.of("days",days,"timezone","UTC","revenue",revenue,"orders",found.size(),"topProducts",top,
                "daily",totals.entrySet().stream().map(e -> Map.of("date",e.getKey(),"revenue",e.getValue())).toList());
    }
    static ResponseStatusException missing(String type) { return new ResponseStatusException(HttpStatus.NOT_FOUND,type+" not found."); }
    static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
