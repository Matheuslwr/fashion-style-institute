package br.com.fsistudio.store.order;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.account.CustomerAccount;
import br.com.fsistudio.store.product.Product;
import br.com.fsistudio.store.product.ProductRepository;
import br.com.fsistudio.store.product.ProductVariant;

@Service
public class StoreOrderService {
    private static final Duration RESERVATION_WINDOW = Duration.ofHours(48);
    private final ProductRepository products;
    private final StoreOrderRepository orders;
    private final ShippingEstimator shippingEstimator;

    public StoreOrderService(
        ProductRepository products,
        StoreOrderRepository orders,
        ShippingEstimator shippingEstimator
    ) {
        this.products = products;
        this.orders = orders;
        this.shippingEstimator = shippingEstimator;
    }

    @Transactional
    public OrderCreation createOrder(CheckoutRequest request, UUID idempotencyKey) {
        return createOrder(request, idempotencyKey, null);
    }

    @Transactional
    public OrderCreation createOrder(CheckoutRequest request, UUID idempotencyKey, CustomerAccount account) {
        StoreOrder existing = orders.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) return new OrderCreation(existing, false);
        if (request.customer() == null || request.address() == null || request.items() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Preencha os dados de contato, entrega e produtos");
        }
        ShippingEstimator.Quote shipping = shippingEstimator.quote(
            request.address().postalCode(), request.address().city(), request.address().state());

        Set<String> distinctVariants = new HashSet<>();
        long subtotalCents = 0;
        var orderItems = new java.util.ArrayList<StoreOrderItem>();
        List<CheckoutRequest.Item> requestedItems = request.items().stream()
            .sorted(java.util.Comparator.comparing(CheckoutRequest.Item::productSlug)
                .thenComparing(CheckoutRequest.Item::color).thenComparing(CheckoutRequest.Item::size))
            .toList();
        for (CheckoutRequest.Item requestedItem : requestedItems) {
            String itemKey = requestedItem.productSlug() + "|" + requestedItem.color() + "|" + requestedItem.size();
            if (!distinctVariants.add(itemKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Remova produtos repetidos do carrinho e tente novamente");
            }
            if (products.lockIdBySlug(requestedItem.productSlug()).isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um produto do carrinho não está mais disponível");
            }
            Product product = products.findBySlugAndActiveTrue(requestedItem.productSlug())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Um produto do carrinho não está mais disponível"));
            ProductVariant variant = product.getVariants().stream()
                .filter(candidate -> candidate.getColor().equals(requestedItem.color())
                    && candidate.getSize().equals(requestedItem.size()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Uma variante do carrinho não está mais disponível"));
            long reserved = orders.sumReservedQuantity(product.getSlug(), requestedItem.color(), requestedItem.size(),
                Instant.now().minus(RESERVATION_WINDOW));
            if (variant.getStock() - reserved < requestedItem.quantity()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente para " + product.getName()
                    + " (" + requestedItem.color() + ", " + requestedItem.size() + ")");
            }
            long unitPrice = product.getPromotionalPriceCents() == null
                ? product.getPriceCents() : product.getPromotionalPriceCents();
            subtotalCents = Math.addExact(subtotalCents, Math.multiplyExact(unitPrice, requestedItem.quantity()));
            orderItems.add(new StoreOrderItem(product.getSlug(), product.getName(), requestedItem.color(),
                requestedItem.size(), requestedItem.quantity(), unitPrice));
        }
        if (subtotalCents <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O carrinho não contém produtos válidos");
        }

        UUID id = UUID.randomUUID();
        String orderNumber = "FSI-" + id.toString().substring(0, 8).toUpperCase(Locale.ROOT);
        CheckoutRequest.Address address = request.address();
        StoreOrder order = new StoreOrder(id, idempotencyKey, orderNumber, request.customer(), address.postalCode(),
            shipping.state(), shipping.city(), address.street().trim(), address.number().trim(),
            address.complement(), address.neighborhood().trim(), shipping.distanceKm(), subtotalCents,
            shipping.shippingCents(), orderItems);
        if (account != null) order.assignToAccount(account);
        return new OrderCreation(orders.saveAndFlush(order), true);
    }

    @Transactional
    public void savePixCode(UUID id, String payload, String qrCode) {
        StoreOrder order = orders.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
        order.setManualPixPayment(payload, qrCode);
    }

    @Transactional
    public void markPaymentSetupFailed(UUID id) {
        orders.findById(id).ifPresent(order -> order.updatePaymentStatus("PAYMENT_FAILED"));
    }

    @Transactional(readOnly = true)
    public List<AdminStoreOrderView> listRecentOrders() {
        return orders.findAllByOrderByCreatedAtDesc(org.springframework.data.domain.PageRequest.of(0, 100))
            .stream().map(AdminStoreOrderView::from).toList();
    }

    @Transactional
    public StoreOrderView confirmManualPayment(UUID id) {
        StoreOrder order = orders.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
        if ("RECEIVED".equals(order.getPaymentStatus()) || "RECEIVED_REVIEW".equals(order.getPaymentStatus())) {
            return StoreOrderView.from(order);
        }
        if (!"PENDING".equals(order.getPaymentStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Só é possível confirmar o pagamento de um pedido aguardando Pix");
        }
        if (allocateStock(order)) order.markPaid();
        else order.markReceivedNeedsReview();
        return StoreOrderView.from(order);
    }

    @Transactional(readOnly = true)
    public StoreOrderView getOrder(UUID id) {
        return orders.findById(id).map(StoreOrderView::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pedido não encontrado"));
    }

    @Transactional(readOnly = true)
    public List<CustomerOrderView> listCustomerOrders(UUID accountId) {
        return orders.findAllByCustomerAccountIdOrderByCreatedAtDesc(accountId,
            org.springframework.data.domain.PageRequest.of(0, 100)).stream()
            .map(CustomerOrderView::from).toList();
    }

    private boolean allocateStock(StoreOrder order) {
        List<String> slugs = order.getItems().stream()
            .map(StoreOrderItem::getProductSlug).distinct().sorted().toList();
        for (String slug : slugs) {
            if (products.lockIdBySlug(slug).isEmpty()) return false;
        }
        var lockedProducts = slugs.stream()
            .map(slug -> products.findBySlug(slug).orElse(null))
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toMap(Product::getSlug, java.util.function.Function.identity()));
        for (StoreOrderItem item : order.getItems()) {
            Product product = lockedProducts.get(item.getProductSlug());
            if (product == null) return false;
            ProductVariant variant = product.getVariants().stream()
                .filter(candidate -> candidate.getColor().equals(item.getColor())
                    && candidate.getSize().equals(item.getSize()))
                .findFirst().orElse(null);
            if (variant == null) return false;
            Instant cutoff = Instant.now().minus(RESERVATION_WINDOW);
            long reserved = orders.sumReservedQuantity(item.getProductSlug(), item.getColor(), item.getSize(), cutoff);
            long ownReservation = isReservationActive(order, cutoff) ? item.getQuantity() : 0;
            if (variant.getStock() - (reserved - ownReservation) < item.getQuantity()) return false;
        }
        for (StoreOrderItem item : order.getItems()) {
            Product product = lockedProducts.get(item.getProductSlug());
            ProductVariant variant = product.getVariants().stream()
                .filter(candidate -> candidate.getColor().equals(item.getColor())
                    && candidate.getSize().equals(item.getSize()))
                .findFirst().orElseThrow();
            variant.removeStock(item.getQuantity());
        }
        return true;
    }

    private static boolean isReservationActive(StoreOrder order, Instant cutoff) {
        String status = order.getPaymentStatus();
        return !order.getCreatedAt().isBefore(cutoff)
            && ("PROCESSING".equals(status) || "PENDING".equals(status));
    }

    public record OrderCreation(StoreOrder order, boolean created) { }

}
