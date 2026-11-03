package br.com.fsistudio.store.account;

import java.time.Instant;
import java.util.UUID;

public record AdminCustomerAccountView(UUID id, String fullName, String email, String phone, Instant createdAt) {
    public static AdminCustomerAccountView from(CustomerAccount account) {
        return new AdminCustomerAccountView(account.getId(), account.getFullName(), account.getEmail(),
            account.getPhone(), account.getCreatedAt());
    }
}
