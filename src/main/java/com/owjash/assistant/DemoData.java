package com.owjash.assistant;

import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import static com.owjash.assistant.InventoryInputs.*;

@Component
public class DemoData {
    private final InventoryService service;
    private final Suppliers suppliers;
    private final boolean enabled;
    DemoData(InventoryService service,Suppliers suppliers,@Value("${app.seed-demo:true}") boolean enabled) { this.service=service; this.suppliers=suppliers; this.enabled=enabled; }
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (!enabled || suppliers.count()>0) return;
        var a=service.saveSupplier(null,new SupplierInput("Northstar Components","orders@northstar.example","3–5 business days"));
        var b=service.saveSupplier(null,new SupplierInput("Circuit Supply Co.","sales@circuit.example","5–7 business days"));
        var c=service.saveSupplier(null,new SupplierInput("Metro Tech Wholesale","hello@metro.example","2–4 business days"));
        Product cable=product("CBL-001","USB-C Cable, 1 m","Accessories",64,20,"8.50",a.id);
        Product mouse=product("MSE-002","Wireless Mouse","Peripherals",8,12,"24.00",c.id);
        Product board=product("MCU-003","Microcontroller Board","Electronics",18,8,"32.00",b.id);
        product("SSD-004","Portable SSD, 1 TB","Storage",5,8,"79.00",a.id);
        product("KEY-005","Mechanical Keyboard","Peripherals",22,8,"49.00",c.id);
        product("HUB-006","USB-C Hub","Accessories",3,10,"35.00",a.id);
        product("SNS-007","Temperature Sensor Module","Electronics",45,15,"6.50",b.id);
        product("PWR-008","Power Adapter, 20 W","Accessories",28,10,"18.00",c.id);
        service.placeOrder(new OrderInput("SALE",List.of(new LineInput(cable.id,4),new LineInput(board.id,2))));
        service.placeOrder(new OrderInput("RECEIPT",List.of(new LineInput(mouse.id,2))));
    }
    private Product product(String sku,String name,String category,int qty,int min,String price,Long supplier) {
        return service.saveProduct(null,new ProductInput(sku,name,category,qty,min,new BigDecimal(price),supplier));
    }
}
