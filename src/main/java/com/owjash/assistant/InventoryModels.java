package com.owjash.assistant;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity
class Supplier {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(nullable=false) public String name;
    public String email;
    public String leadTime;
    protected Supplier() {}
    Supplier(String name,String email,String leadTime) { this.name=name; this.email=email; this.leadTime=leadTime; }
}

@Entity
class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    @Column(unique=true,nullable=false) public String sku;
    @Column(nullable=false) public String name;
    public String category;
    public int quantity;
    public int reorderLevel;
    @Column(precision=12,scale=2) public BigDecimal price;
    @ManyToOne(optional=false) public Supplier supplier;
    @Version @com.fasterxml.jackson.annotation.JsonIgnore public long version;
    protected Product() {}
}
// owjash

@Entity
@Table(name="stock_order")
class StockOrder {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
    public String type;
    public Instant createdAt;
    public BigDecimal total;
    @ElementCollection(fetch=FetchType.EAGER)
    @OrderColumn(name="line_number")
    public List<OrderLine> lines=new ArrayList<>();
    protected StockOrder() {}
}

@Embeddable
class OrderLine {
    public Long productId;
    public String productName;
    public String sku;
    public int quantity;
    @Column(precision=12,scale=2) public BigDecimal unitPrice;
    protected OrderLine() {}
    OrderLine(Product p,int quantity) { productId=p.id; productName=p.name; sku=p.sku; this.quantity=quantity; unitPrice=p.price; }
}

final class InventoryInputs {
    record SupplierInput(@NotBlank @Size(max=100) String name,@Email @Size(max=150) String email,@Size(max=80) String leadTime) {}
    record ProductInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_-]{1,40}") String sku,
                        @NotBlank @Size(max=150) String name,@NotBlank @Size(max=60) String category,
                        @Min(0) @Max(1000000) int quantity,@Min(0) @Max(1000000) int reorderLevel,
                        @NotNull @DecimalMin("0.00") @Digits(integer=8,fraction=2) BigDecimal price,
                        @NotNull @Positive Long supplierId) {}
    record LineInput(@NotNull @Positive Long productId,@Min(1) @Max(100000) int quantity) {}
    record OrderInput(@Pattern(regexp="SALE|RECEIPT") @NotNull String type,
                      @NotEmpty @Size(max=30) List<@NotNull @Valid LineInput> lines) {}
}
