package br.com.fsistudio.store.order;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.fsistudio.store.account.CustomerAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "store_orders")
public class StoreOrder {
    @Id
    private UUID id;

    @Column(name = "order_number", nullable = false, unique = true, length = 20)
    private String orderNumber;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_email", nullable = false, length = 120)
    private String customerEmail;

    @Column(name = "customer_phone", nullable = false, length = 24)
    private String customerPhone;

    @Column(name = "postal_code", nullable = false, length = 8)
    private String postalCode;

    @Column(nullable = false, length = 2)
    private String state;

    @Column(nullable = false, length = 80)
    private String city;

    @Column(nullable = false, length = 120)
    private String address;

    @Column(name = "address_number", nullable = false, length = 12)
    private String addressNumber;

    @Column(length = 80)
    private String complement;

    @Column(nullable = false, length = 80)
    private String neighborhood;

    @Column(name = "distance_km", nullable = false)
    private int distanceKm;

    @Column(name = "subtotal_cents", nullable = false)
    private long subtotalCents;

    @Column(name = "shipping_cents", nullable = false)
    private long shippingCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "payment_status", nullable = false, length = 24)
    private String paymentStatus = "PROCESSING";

    @Column(name = "pix_payload", columnDefinition = "text")
    private String pixPayload;

    @Column(name = "pix_qr_code", columnDefinition = "text")
    private String pixQrCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "paid_at")
    private Instant paidAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id")
    private CustomerAccount customerAccount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName ASC")
    private List<StoreOrderItem> items = new ArrayList<>();

    protected StoreOrder() { }

    StoreOrder(UUID id, UUID idempotencyKey, String orderNumber, CheckoutRequest.Customer customer, String postalCode,
               String state, String city, String address, String addressNumber, String complement,
               String neighborhood, int distanceKm, long subtotalCents, long shippingCents,
               List<StoreOrderItem> items) {
        this.id = id;
        this.idempotencyKey = idempotencyKey;
        this.orderNumber = orderNumber;
        this.customerName = customer.name().trim();
        this.customerEmail = customer.email().trim().toLowerCase(java.util.Locale.ROOT);
        this.customerPhone = customer.phone().trim();
        this.postalCode = postalCode;
        this.state = state;
        this.city = city;
        this.address = address;
        this.addressNumber = addressNumber;
        this.complement = complement;
        this.neighborhood = neighborhood;
        this.distanceKm = distanceKm;
        this.subtotalCents = subtotalCents;
        this.shippingCents = shippingCents;
        this.totalCents = subtotalCents + shippingCents;
        items.forEach(item -> item.attachTo(this));
        this.items.addAll(items);
    }

    public void setManualPixPayment(String payload, String qrCode) {
        this.pixPayload = payload;
        this.pixQrCode = qrCode;
        this.paymentStatus = "PENDING";
    }

    public void markPaid() {
        if (!"RECEIVED".equals(paymentStatus)) {
            paymentStatus = "RECEIVED";
            paidAt = Instant.now();
        }
    }

    public void markReceivedNeedsReview() {
        paymentStatus = "RECEIVED_REVIEW";
        paidAt = Instant.now();
    }

    public void updatePaymentStatus(String status) { this.paymentStatus = status; }
    public void assignToAccount(CustomerAccount account) { this.customerAccount = account; }
    public UUID getId() { return id; }
    public String getOrderNumber() { return orderNumber; }
    public UUID getIdempotencyKey() { return idempotencyKey; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public String getCustomerPhone() { return customerPhone; }
    public String getPostalCode() { return postalCode; }
    public String getState() { return state; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public String getAddressNumber() { return addressNumber; }
    public String getComplement() { return complement; }
    public String getNeighborhood() { return neighborhood; }
    public int getDistanceKm() { return distanceKm; }
    public long getSubtotalCents() { return subtotalCents; }
    public long getShippingCents() { return shippingCents; }
    public long getTotalCents() { return totalCents; }
    public String getPaymentStatus() { return paymentStatus; }
    public String getPixPayload() { return pixPayload; }
    public String getPixQrCode() { return pixQrCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPaidAt() { return paidAt; }
    public CustomerAccount getCustomerAccount() { return customerAccount; }
    public List<StoreOrderItem> getItems() { return items; }
}
