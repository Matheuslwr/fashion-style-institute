package br.com.fsistudio.store.product;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, unique = true, length = 80)
    private String sku;

    @Column(nullable = false, length = 80)
    private String color;

    @Column(nullable = false, length = 40)
    private String size;

    @Column(nullable = false)
    private int stock;

    protected ProductVariant() { }

    public ProductVariant(String sku, String color, String size, int stock) {
        this.sku = sku;
        this.color = color;
        this.size = size;
        this.stock = stock;
    }

    void attachTo(Product product) { this.product = product; }
    public void removeStock(int quantity) { this.stock -= quantity; }
    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public int getStock() { return stock; }
}