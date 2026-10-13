package br.com.fsistudio.store.product;

import java.util.List;
import java.util.UUID;

public record ProductResponse(
    String id,
    UUID databaseId,
    String name,
    String slug,
    String description,
    long priceCents,
    Long promotionalPriceCents,
    boolean featured,
    boolean active,
    CategorySummary category,
    List<ImageSummary> images,
    List<VariantSummary> variants
) {
    static ProductResponse from(Product product) {
        return new ProductResponse(
            product.getSlug(), product.getId(), product.getName(), product.getSlug(), product.getDescription(),
            product.getPriceCents(), product.getPromotionalPriceCents(), product.isFeatured(), product.isActive(),
            new CategorySummary(product.getCategory().getId(), product.getCategory().getName(), product.getCategory().getSlug()),
            product.getImages().stream().map(image -> new ImageSummary(image.getImageUrl(), image.getAltText(), image.getColor(), image.getDisplayOrder())).toList(),
            product.getVariants().stream().map(variant -> new VariantSummary(variant.getSku(), variant.getColor(), variant.getSize(), variant.getStock())).toList()
        );
    }

    public record CategorySummary(UUID id, String name, String slug) { }
    public record ImageSummary(String imageUrl, String altText, String color, int displayOrder) { }
    public record VariantSummary(String sku, String color, String size, int stock) { }
}