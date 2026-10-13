package br.com.fsistudio.store.product;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.fsistudio.store.category.Category;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    public Product(String name, String slug, String description, long priceCents,
                   Long promotionalPriceCents, boolean featured, boolean active, Category category) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.priceCents = priceCents;
        this.promotionalPriceCents = promotionalPriceCents;
        this.featured = featured;
        this.active = active;
        this.category = category;
    }

    public void update(String name, String slug, String description, long priceCents,
                       Long promotionalPriceCents, boolean featured, boolean active, Category category) {
        this.name = name;
        this.slug = slug;
        this.description = description;
        this.priceCents = priceCents;
        this.promotionalPriceCents = promotionalPriceCents;
        this.featured = featured;
        this.active = active;
        this.category = category;
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
    public long getPriceCents() { return priceCents; }
    public Long getPromotionalPriceCents() { return promotionalPriceCents; }
    public boolean isFeatured() { return featured; }
    public boolean isActive() { return active; }
    public Category getCategory() { return category; }
    public List<ProductImage> getImages() { return images; }
    public List<ProductVariant> getVariants() { return variants; }
}