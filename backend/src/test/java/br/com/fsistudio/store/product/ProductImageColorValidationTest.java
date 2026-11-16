package br.com.fsistudio.store.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.category.CategoryRepository;

class ProductImageColorValidationTest {
    @Test
    void rejectsInvalidImageColorsWhenCreatingAProduct() {
        ProductService service = new ProductService(mock(ProductRepository.class), mock(CategoryRepository.class));
        ProductRequest request = request(
            List.of(image("Preto, Branco, Bege")),
            List.of(variant("Preto"), variant("Branco"), variant("Bege"))
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.create(request));

        assertEquals("A cor de cada imagem deve corresponder a uma única cor cadastrada nas variantes",
            exception.getReason());
    }

    @Test
    void rejectsInvalidImageColorsBeforeUpdatingAProduct() {
        ProductService service = new ProductService(mock(ProductRepository.class), mock(CategoryRepository.class));
        ProductRequest request = request(
            List.of(image("Preto, Branco, Bege")),
            List.of(variant("Preto"), variant("Branco"), variant("Bege"))
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.update(UUID.randomUUID(), request));

        assertEquals("A cor de cada imagem deve corresponder a uma única cor cadastrada nas variantes",
            exception.getReason());
    }

    @Test
    void rejectsAnImageLabelThatCombinesMultipleVariantColors() {
        ProductRequest request = request(
            List.of(image("Preto, Branco, Bege")),
            List.of(variant("Preto"), variant("Branco"), variant("Bege"))
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> ProductService.validateImageColors(request));

        assertEquals("A cor de cada imagem deve corresponder a uma única cor cadastrada nas variantes",
            exception.getReason());
    }

    @Test
    void requiresAnImageForEveryVariantColor() {
        ProductRequest request = request(
            List.of(image("Preto")),
            List.of(variant("Preto"), variant("Branco"))
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> ProductService.validateImageColors(request));

        assertEquals("Adicione ao menos uma imagem para cada cor das variantes: Branco", exception.getReason());
    }

    @Test
    void acceptsMultipleImagesPerColorAndIgnoresCaseAndSurroundingSpaces() {
        ProductRequest request = request(
            List.of(image("preto"), image(" PRETO "), image("Branco")),
            List.of(variant("Preto"), variant("Branco"))
        );

        assertDoesNotThrow(() -> ProductService.validateImageColors(request));
    }

    private static ProductRequest request(
        List<ProductRequest.ImageRequest> images,
        List<ProductRequest.VariantRequest> variants
    ) {
        return new ProductRequest("Relógio", "Descrição", "", ProductType.STANDARD, null, 10000, null,
            UUID.randomUUID(), false, true, images, variants);
    }

    private static ProductRequest.ImageRequest image(String color) {
        return new ProductRequest.ImageRequest("https://example.com/watch.jpg", "Relógio", color, 0);
    }

    private static ProductRequest.VariantRequest variant(String color) {
        return new ProductRequest.VariantRequest("WATCH-" + color, color, "Único", 1);
    }
}
