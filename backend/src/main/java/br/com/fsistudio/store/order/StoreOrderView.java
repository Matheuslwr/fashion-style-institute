package br.com.fsistudio.store.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StoreOrderView(
    UUID id,
    String orderNumber,
    String paymentStatus,
    Instant createdAt,
    Instant paidAt,
    long subtotalCents,
    long shippingCents,
    long totalCents,
    String city,
    String state,
    int distanceKm,
    String pixPayload,
    String pixQrCode,
    List<Item> items
) {
    static StoreOrderView from(StoreOrder order) {
        boolean awaitingPayment = "PENDING".equals(order.getPaymentStatus());
        return new StoreOrderView(
            order.getId(),
            order.getOrderNumber(),
            order.getPaymentStatus(),
            order.getCreatedAt(),
            order.getPaidAt(),
            order.getSubtotalCents(),
            order.getShippingCents(),
            order.getTotalCents(),
            order.getCity(),
            order.getState(),
            order.getDistanceKm(),
            awaitingPayment ? order.getPixPayload() : null,
            awaitingPayment && order.getPixQrCode() != null ? "data:image/png;base64," + order.getPixQrCode() : null,
            order.getItems().stream().map(item -> new Item(item.getProductSlug(), item.getProductName(),
                item.getColor(), item.getSize(), item.getQuantity(), item.getUnitPriceCents())).toList()
        );
    }

    public record Item(String productSlug, String productName, String color, String size, int quantity, long unitPriceCents) { }
}
