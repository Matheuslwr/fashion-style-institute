package br.com.fsistudio.store.product;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.fsistudio.store.category.Category;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 140)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String slug;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 24)
    private ProductType productType;

    @Column(name = "watch_width_cm", precision = 6, scale = 2)
    private BigDecimal watchWidthCm;

    @Column(name = "watch_length_cm", precision = 6, scale = 2)
    private BigDecimal watchLengthCm;

    @Column(name = "watch_height_cm", precision = 6, scale = 2)
    private BigDecimal watchHeightCm;

    @Column(name = "watch_dial_cm", precision = 6, scale = 2)
    private BigDecimal watchDialCm;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(name = "promotional_price_cents")
    private Long promotionalPriceCents;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private boolean active;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<ProductImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("color ASC, size ASC")
    private List<ProductVariant> variants = new ArrayList<>();

    protected Product() { }

    public Product(String name, String slug, String description, String details, ProductType productType,
                   WatchMeasurements watchMeasurements, long priceCents,
                   Long promotionalPriceCents, boolean featured, boolean active, Category category) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.details = details;
        setProductTypeAndMeasurements(productType, watchMeasurements);
        this.priceCents = priceCents;
        this.promotionalPriceCents = promotionalPriceCents;
        this.featured = featured;
        this.active = active;
        this.category = category;
    }

    public void update(String name, String slug, String description, String details, ProductType productType,
                       WatchMeasurements watchMeasurements, long priceCents,
                       Long promotionalPriceCents, boolean featured, boolean active, Category category) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.details = details;
        setProductTypeAndMeasurements(productType, watchMeasurements);
        this.priceCents = priceCents;
        this.promotionalPriceCents = promotionalPriceCents;
        this.featured = featured;
        this.active = active;
        this.category = category;
    }

    private void setProductTypeAndMeasurements(ProductType productType, WatchMeasurements measurements) {
        this.productType = productType;
        this.watchWidthCm = measurements == null ? null : measurements.widthCm();
        this.watchLengthCm = measurements == null ? null : measurements.lengthCm();
        this.watchHeightCm = measurements == null ? null : measurements.heightCm();
        this.watchDialCm = measurements == null ? null : measurements.dialCm();
    }

    public void replaceImages(List<ProductImage> replacement) {
        images.clear();
        replacement.forEach(image -> {
            image.attachTo(this);
            images.add(image);
        });
    }

    public void replaceVariants(List<ProductVariant> replacement) {
        variants.clear();
        replacement.forEach(variant -> {
            variant.attachTo(this);
            variants.add(variant);
        });
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getDescription() { return description; }
    public String getDetails() { return details; }
    public ProductType getProductType() { return productType; }
    public WatchMeasurements getWatchMeasurements() {
        return productType == ProductType.WATCH
            ? new WatchMeasurements(watchWidthCm, watchLengthCm, watchHeightCm, watchDialCm)
            : null;
    }
    public long getPriceCents() { return priceCents; }
    public Long getPromotionalPriceCents() { return promotionalPriceCents; }
    public boolean isFeatured() { return featured; }
    public boolean isActive() { return active; }
    public Category getCategory() { return category; }
    public List<ProductImage> getImages() { return images; }
    public List<ProductVariant> getVariants() { return variants; }
}