package com.owjash.assistant;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
import static com.owjash.assistant.InventoryInputs.*;

@RestController
@RequestMapping("/api")
public class InventoryController {
    private final InventoryService service;
    InventoryController(InventoryService service) { this.service=service; }
    @GetMapping("/products") List<Product> products(@RequestParam(defaultValue="") String search) { return service.products(search); }
    @PostMapping("/products") @ResponseStatus(HttpStatus.CREATED) Product createProduct(@Valid @RequestBody ProductInput in) { return service.saveProduct(null,in); }
    @PutMapping("/products/{id}") Product updateProduct(@PathVariable Long id,@Valid @RequestBody ProductInput in) { return service.saveProduct(id,in); }
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteProduct(@PathVariable Long id) { service.deleteProduct(id); }
    @GetMapping("/suppliers") List<Supplier> suppliers() { return service.suppliers(); }
    @PostMapping("/suppliers") @ResponseStatus(HttpStatus.CREATED) Supplier createSupplier(@Valid @RequestBody SupplierInput in) { return service.saveSupplier(null,in); }
    @PutMapping("/suppliers/{id}") Supplier updateSupplier(@PathVariable Long id,@Valid @RequestBody SupplierInput in) { return service.saveSupplier(id,in); }
    @DeleteMapping("/suppliers/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) void deleteSupplier(@PathVariable Long id) { service.deleteSupplier(id); }
    @GetMapping("/orders") List<StockOrder> orders() { return service.orders(); }
    @PostMapping("/orders") @ResponseStatus(HttpStatus.CREATED) StockOrder placeOrder(@Valid @RequestBody OrderInput in) { return service.placeOrder(in); }
    @GetMapping("/dashboard") Map<String,Object> dashboard() { return service.dashboard(); }
    @GetMapping("/low-stock") List<Product> lowStock() { return service.lowStock(); }
    @GetMapping("/sales") Map<String,Object> sales(@RequestParam(defaultValue="30") int days) { return service.sales(days); }
}
