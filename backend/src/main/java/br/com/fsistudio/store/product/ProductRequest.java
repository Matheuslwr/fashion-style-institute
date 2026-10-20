package br.com.fsistudio.store.product;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ProductRequest(
    @NotBlank @Size(max = 140) String name,
    @NotBlank @Size(max = 4000) String description,
    @Size(max = 4000) String details,
    @PositiveOrZero long priceCents,
    @PositiveOrZero Long promotionalPriceCents,
    @NotNull UUID categoryId,
    boolean featured,
    boolean active,
    @NotEmpty @Size(max = 12) List<@Valid ImageRequest> images,
    @NotEmpty @Size(max = 200) List<@Valid VariantRequest> variants
) {
    public record ImageRequest(
        @NotBlank @Size(max = 500) String imageUrl,
        @NotBlank @Size(max = 180) String altText,
        @Size(max = 80) String color,
        @PositiveOrZero int displayOrder
    ) { }

    public record VariantRequest(
        @NotBlank @Size(max = 80) String sku,
        @NotBlank @Size(max = 80) String color,
        @NotBlank @Size(max = 40) String size,
        @PositiveOrZero int stock
    ) { }
}