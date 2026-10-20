package br.com.fsistudio.store.order;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.com.fsistudio.store.account.CustomerAccount;
import br.com.fsistudio.store.account.CustomerAccountRepository;
import br.com.fsistudio.store.account.CustomerPrincipal;
import jakarta.validation.Valid;

@RestController
@Validated
public class OrderController {
    private final StoreOrderService orders;
    private final StoreOrderRepository repository;
    private final PixPaymentService pix;
    private final CustomerAccountRepository accounts;

    public OrderController(StoreOrderService orders, StoreOrderRepository repository, PixPaymentService pix,
                           CustomerAccountRepository accounts) {
        this.orders = orders;
        this.repository = repository;
        this.pix = pix;
        this.accounts = accounts;
    }

    @PostMapping("/api/orders")
    public StoreOrderView create(@RequestHeader("Idempotency-Key") String rawIdempotencyKey,
                                 @Valid @RequestBody CheckoutRequest request,
                                 @AuthenticationPrincipal CustomerPrincipal principal) {
        UUID idempotencyKey;
        try {
            idempotencyKey = UUID.fromString(rawIdempotencyKey);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chave de tentativa de pagamento inválida", exception);
        }
        StoreOrder existingOrder = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingOrder != null) return orders.getOrder(existingOrder.getId());
        if (!pix.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "O Pix da loja ainda não está configurado. Entre em contato com a loja.");
        }
        StoreOrderService.OrderCreation creation;
        try {
            CustomerAccount account = principal == null ? null : accounts.findById(principal.id())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sua sessão expirou. Entre novamente."));
            creation = orders.createOrder(request, idempotencyKey, account);
        } catch (DataIntegrityViolationException exception) {
            StoreOrder concurrentOrder = repository.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (concurrentOrder == null) throw exception;
            return orders.getOrder(concurrentOrder.getId());
        }
        StoreOrder order = creation.order();
        if (!creation.created()) return orders.getOrder(order.getId());
        try {
            PixPaymentService.PixCode code = pix.createCode(order);
            orders.savePixCode(order.getId(), code.payload(), code.qrCodeBase64());
        } catch (RuntimeException exception) {
            orders.markPaymentSetupFailed(order.getId());
            throw exception;
        }
        return orders.getOrder(order.getId());
    }

    @GetMapping("/api/orders/{id}")
    public StoreOrderView get(@PathVariable UUID id) {
        return orders.getOrder(id);
    }

    @GetMapping("/api/admin/orders")
    public List<AdminStoreOrderView> adminOrders() {
        return orders.listRecentOrders();
    }

    @PostMapping("/api/admin/orders/{id}/confirm-payment")
    public StoreOrderView confirmManualPayment(@PathVariable UUID id) {
        return orders.confirmManualPayment(id);
    }
}
