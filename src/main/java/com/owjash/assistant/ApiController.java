package com.owjash.assistant;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.owjash.assistant.ApiTypes.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final DocumentService service;
    private final boolean ai;
    private final String engine;
    private final String chatModel;
    private final String embedModel;
    ApiController(DocumentService service,@Value("${app.ai-enabled:false}") boolean ai,
                  @Value("${app.retrieval-engine:memory}") String engine,
                  @Value("${spring.ai.ollama.chat.options.model:qwen2.5:3b}") String chatModel,
                  @Value("${app.embedding-model}") String embedModel) {
        this.service=service; this.ai=ai; this.engine=engine; this.chatModel=chatModel; this.embedModel=embedModel;
    }
    @GetMapping("/status") Map<String,Object> status() {
        return Map.of("name","Smart Inventory AI","aiEnabled",ai,"retrieval",ai?"semantic":"keyword","vectorStore",engine,"chatModel",chatModel,"embeddingModel",embedModel,"chunkSize",1200,"chunkOverlap",180,"topK",3,"version","0.1.0");
    }
    @GetMapping("/documents") List<DocumentRecord> documents() { return service.list(); }
    @PostMapping(value="/documents",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    DocumentRecord upload(@RequestPart("file") MultipartFile file) throws IOException {
        return service.upload(file.getOriginalFilename(),file.getBytes());
    }
    @PostMapping("/demo") @ResponseStatus(HttpStatus.CREATED)
    DocumentRecord demo() throws IOException {
        return service.upload("Northstar Supplier Policy - Demo.pdf",new ClassPathResource("demo/manual.pdf").getContentAsByteArray());
    }
    @GetMapping("/demo/evaluation") Object demoEvaluation() throws IOException {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(new ClassPathResource("demo/evaluation.json").getContentAsByteArray());
    }
    @GetMapping(value="/documents/{id}/pdf",produces=MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> pdf(@PathVariable String id) {
        var doc=service.get(id);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline().filename(doc.name,StandardCharsets.UTF_8).build().toString()).body(doc.pdfBytes);
    }
    @DeleteMapping("/documents/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) { service.delete(id); }
    @GetMapping("/documents/{id}/history") List<Answer> history(@PathVariable String id) { return service.history(id); }
    @PostMapping("/documents/{id}/ask") Answer ask(@PathVariable String id,@Valid @RequestBody AskRequest req) {
        return service.ask(id,req.question());
    }
    @PostMapping("/documents/{id}/evaluate") Evaluation evaluate(@PathVariable String id,@Valid @RequestBody EvalRequest req) {
        return service.evaluate(id,req.cases(),req.endToEnd());
    }
}
