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
@Table(name = "product_images")
public class ProductImage {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(name = "alt_text", nullable = false, length = 180)
    private String altText;

    @Column(length = 80)
    private String color;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected ProductImage() { }

    public ProductImage(String imageUrl, String altText, String color, int displayOrder) {
        this.imageUrl = imageUrl;
        this.altText = altText;
        this.color = color;
        this.displayOrder = displayOrder;
    }

    void attachTo(Product product) { this.product = product; }
    public UUID getId() { return id; }
    public String getImageUrl() { return imageUrl; }
    public String getAltText() { return altText; }
    public String getColor() { return color; }
    public int getDisplayOrder() { return displayOrder; }
}