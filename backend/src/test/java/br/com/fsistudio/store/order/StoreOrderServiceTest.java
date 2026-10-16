package br.com.fsistudio.store.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.product.Product;
import br.com.fsistudio.store.product.ProductRepository;
import br.com.fsistudio.store.product.ProductVariant;

class StoreOrderServiceTest {
    private static final UUID ORDER_ID = UUID.fromString("d4a77445-d501-4fbe-b8ca-86278d94111f");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("074c4f1e-ecbb-45ac-aa80-c00f1ce5b963");

    private final StoreOrderRepository repository = mock(StoreOrderRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StoreOrderService service = new StoreOrderService(products, repository, mock(ShippingEstimator.class));

    @Test
    void returnsExistingOrderForSameIdempotencyKeyWithoutCreatingAnother() {
        StoreOrder order = createOrder(List.of());
        when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.of(order));

        StoreOrderService.OrderCreation result = service.createOrder(null, IDEMPOTENCY_KEY);

        assertEquals(order, result.order());
        assertFalse(result.created());
    }

    @Test
    void reservesAvailableVariantStockWhenCreatingOrder() {
        ShippingEstimator shipping = mock(ShippingEstimator.class);
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(shipping.quote("80000000", "Curitiba", "PR"))
            .thenReturn(new ShippingEstimator.Quote("Curitiba", "PR", 5, 990));
        when(products.lockIdBySlug("camisa-teste")).thenReturn(Optional.of(UUID.randomUUID()));
        when(products.findBySlugAndActiveTrue("camisa-teste")).thenReturn(Optional.of(product));
        when(product.getSlug()).thenReturn("camisa-teste");
        when(product.getName()).thenReturn("Camisa");
        when(product.getPriceCents()).thenReturn(1000L);
        when(product.getPromotionalPriceCents()).thenReturn(null);
        when(product.getVariants()).thenReturn(List.of(variant));
        when(variant.getColor()).thenReturn("Preto");
        when(variant.getSize()).thenReturn("M");
        when(variant.getStock()).thenReturn(3);
        when(repository.sumReservedQuantity(org.mockito.ArgumentMatchers.eq("camisa-teste"),
            org.mockito.ArgumentMatchers.eq("Preto"), org.mockito.ArgumentMatchers.eq("M"),
            org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any(StoreOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        StoreOrderService checkoutService = new StoreOrderService(products, repository, shipping);

        StoreOrderService.OrderCreation creation = checkoutService.createOrder(validRequest(1), IDEMPOTENCY_KEY);

        assertTrue(creation.created());
        assertEquals("PROCESSING", creation.order().getPaymentStatus());
        assertEquals(1990L, creation.order().getTotalCents());
        verify(products).lockIdBySlug("camisa-teste");
    }

    @Test
    void rejectsCheckoutWhenOtherOrdersHaveReservedTheRemainingStock() {
        ShippingEstimator shipping = mock(ShippingEstimator.class);
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        when(repository.findByIdempotencyKey(IDEMPOTENCY_KEY)).thenReturn(Optional.empty());
        when(shipping.quote("80000000", "Curitiba", "PR"))
            .thenReturn(new ShippingEstimator.Quote("Curitiba", "PR", 5, 990));
        when(products.lockIdBySlug("camisa-teste")).thenReturn(Optional.of(UUID.randomUUID()));
        when(products.findBySlugAndActiveTrue("camisa-teste")).thenReturn(Optional.of(product));
        when(product.getSlug()).thenReturn("camisa-teste");
        when(product.getName()).thenReturn("Camisa");
        when(product.getPriceCents()).thenReturn(1000L);
        when(product.getPromotionalPriceCents()).thenReturn(null);
        when(product.getVariants()).thenReturn(List.of(variant));
        when(variant.getColor()).thenReturn("Preto");
        when(variant.getSize()).thenReturn("M");
        when(variant.getStock()).thenReturn(3);
        when(repository.sumReservedQuantity(org.mockito.ArgumentMatchers.eq("camisa-teste"),
            org.mockito.ArgumentMatchers.eq("Preto"), org.mockito.ArgumentMatchers.eq("M"),
            org.mockito.ArgumentMatchers.any())).thenReturn(3L);
        StoreOrderService checkoutService = new StoreOrderService(products, repository, shipping);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> checkoutService.createOrder(validRequest(1), IDEMPOTENCY_KEY));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any(StoreOrder.class));
    }

    @Test
    void confirmsManualPaymentAndDeductsStockOnlyOnce() {
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        StoreOrder order = createOrder(List.of(
            new StoreOrderItem("camisa-teste", "Camisa", "Preto", "M", 1, 1000)));
        when(repository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(products.lockIdBySlug("camisa-teste")).thenReturn(Optional.of(UUID.randomUUID()));
        when(products.findBySlug("camisa-teste")).thenReturn(Optional.of(product));
        when(product.getSlug()).thenReturn("camisa-teste");
        when(product.getVariants()).thenReturn(List.of(variant));
        when(variant.getColor()).thenReturn("Preto");
        when(variant.getSize()).thenReturn("M");
        when(variant.getStock()).thenReturn(1);
        when(repository.sumReservedQuantity(org.mockito.ArgumentMatchers.eq("camisa-teste"),
            org.mockito.ArgumentMatchers.eq("Preto"), org.mockito.ArgumentMatchers.eq("M"),
            org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        service.confirmManualPayment(ORDER_ID);
        service.confirmManualPayment(ORDER_ID);

        assertEquals("RECEIVED", order.getPaymentStatus());
        verify(variant).removeStock(1);
    }

    @Test
    void flagsReceivedPaymentForManualReviewWhenStockIsUnavailable() {
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        StoreOrder order = createOrder(List.of(
            new StoreOrderItem("camisa-teste", "Camisa", "Preto", "M", 1, 1000)));
        when(repository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(products.lockIdBySlug("camisa-teste")).thenReturn(Optional.of(UUID.randomUUID()));
        when(products.findBySlug("camisa-teste")).thenReturn(Optional.of(product));
        when(product.getSlug()).thenReturn("camisa-teste");
        when(product.getVariants()).thenReturn(List.of(variant));
        when(variant.getColor()).thenReturn("Preto");
        when(variant.getSize()).thenReturn("M");
        when(variant.getStock()).thenReturn(0);
        when(repository.sumReservedQuantity(org.mockito.ArgumentMatchers.eq("camisa-teste"),
            org.mockito.ArgumentMatchers.eq("Preto"), org.mockito.ArgumentMatchers.eq("M"),
            org.mockito.ArgumentMatchers.any())).thenReturn(1L);

        service.confirmManualPayment(ORDER_ID);

        assertEquals("RECEIVED_REVIEW", order.getPaymentStatus());
        verify(variant, never()).removeStock(1);
    }

    @Test
    void rejectsManualConfirmationForNonPendingOrder() {
        StoreOrder order = createOrder(List.of());
        order.updatePaymentStatus("PAYMENT_FAILED");
        when(repository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
            () -> service.confirmManualPayment(ORDER_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    private static StoreOrder createOrder(List<StoreOrderItem> items) {
        CheckoutRequest.Customer customer = new CheckoutRequest.Customer(
            "Pessoa Teste", "teste@example.com", "41999999999");
        StoreOrder order = new StoreOrder(ORDER_ID, IDEMPOTENCY_KEY, "FSI-D4A77445", customer, "80000000", "PR",
            "Curitiba", "Rua Teste", "1", null, "Centro", 0, 1000, 0, items);
        order.setManualPixPayment("payload", "qr");
        return order;
    }

    private static CheckoutRequest validRequest(int quantity) {
        return new CheckoutRequest(
            new CheckoutRequest.Customer("Pessoa Teste", "teste@example.com", "41999999999"),
            new CheckoutRequest.Address("80000000", "PR", "Curitiba", "Rua Teste", "1", null, "Centro"),
            List.of(new CheckoutRequest.Item("camisa-teste", "Preto", "M", quantity)));
    }
}
