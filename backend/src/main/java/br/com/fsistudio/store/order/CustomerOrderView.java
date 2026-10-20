package br.com.fsistudio.store.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerOrderView(
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
    List<Item> items
) {
    static CustomerOrderView from(StoreOrder order) {
        return new CustomerOrderView(
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
            order.getItems().stream().map(item -> new Item(item.getProductName(), item.getColor(),
                item.getSize(), item.getQuantity(), item.getUnitPriceCents())).toList()
        );
    }

    public record Item(String productName, String color, String size, int quantity, long unitPriceCents) { }
}
