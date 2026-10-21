package br.com.fsistudio.store.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminStoreOrderView(
    UUID id,
    String orderNumber,
    String paymentStatus,
    Instant createdAt,
    Instant paidAt,
    String customerName,
    String customerEmail,
    String customerPhone,
    AccountCustomer account,
    String postalCode,
    String state,
    String city,
    String address,
    String addressNumber,
    String complement,
    String neighborhood,
    long subtotalCents,
    long shippingCents,
    long totalCents,
    List<StoreOrderView.Item> items
) {
    static AdminStoreOrderView from(StoreOrder order) {
        return new AdminStoreOrderView(
            order.getId(),
            order.getOrderNumber(),
            order.getPaymentStatus(),
            order.getCreatedAt(),
            order.getPaidAt(),
            order.getCustomerName(),
            order.getCustomerEmail(),
            order.getCustomerPhone(),
            order.getCustomerAccount() == null ? null : new AccountCustomer(
                order.getCustomerAccount().getFullName(),
                order.getCustomerAccount().getEmail(),
                order.getCustomerAccount().getPhone()),
            order.getPostalCode(),
            order.getState(),
            order.getCity(),
            order.getAddress(),
            order.getAddressNumber(),
            order.getComplement(),
            order.getNeighborhood(),
            order.getSubtotalCents(),
            order.getShippingCents(),
            order.getTotalCents(),
            order.getItems().stream().map(item -> new StoreOrderView.Item(item.getProductSlug(), item.getProductName(),
                item.getColor(), item.getSize(), item.getQuantity(), item.getUnitPriceCents())).toList()
        );
    }

    public record AccountCustomer(String fullName, String email, String phone) { }
}
