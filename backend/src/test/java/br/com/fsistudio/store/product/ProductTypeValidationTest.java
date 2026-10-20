package br.com.fsistudio.store.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.category.CategoryRepository;

class ProductTypeValidationTest {
    @Test
    void rejectsWatchProductsWithoutAllMeasurements() {
        ProductService service = new ProductService(mock(ProductRepository.class), mock(CategoryRepository.class));
        ProductRequest request = new ProductRequest(
            "Relógio", "Relógio de pulso", "", ProductType.WATCH, null, 10000, null,
            UUID.randomUUID(), false, true,
            List.of(new ProductRequest.ImageRequest("https://example.com/watch.jpg", "Relógio", null, 0)),
            List.of(new ProductRequest.VariantRequest("WATCH-38", "Prata", "Único", 1))
        );

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.create(request));

        assertEquals("Preencha largura, comprimento, altura e medida do mostrador do relógio",
            exception.getReason());
    }
}
