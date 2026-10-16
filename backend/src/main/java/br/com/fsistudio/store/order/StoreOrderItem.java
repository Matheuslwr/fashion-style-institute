package br.com.fsistudio.store.order;

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
@Table(name = "store_order_items")
public class StoreOrderItem {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;

    @Column(name = "product_slug", nullable = false, length = 160)
    private String productSlug;

    @Column(name = "product_name", nullable = false, length = 140)
    private String productName;

    @Column(nullable = false, length = 80)
    private String color;

    @Column(nullable = false, length = 40)
    private String size;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    protected StoreOrderItem() { }

    StoreOrderItem(String productSlug, String productName, String color, String size, int quantity, long unitPriceCents) {
        this.productSlug = productSlug;
        this.productName = productName;
        this.color = color;
        this.size = size;
        this.quantity = quantity;
        this.unitPriceCents = unitPriceCents;
    }

    void attachTo(StoreOrder order) { this.order = order; }
    public String getProductSlug() { return productSlug; }
    public String getProductName() { return productName; }
    public String getColor() { return color; }
    public String getSize() { return size; }
    public int getQuantity() { return quantity; }
    public long getUnitPriceCents() { return unitPriceCents; }
}
