package br.com.fsistudio.store.account;

import java.time.Instant;
import java.util.UUID;

public record CustomerAccountView(UUID id, String fullName, String email, String phone, Instant createdAt) {
    static CustomerAccountView from(CustomerAccount account) {
        return new CustomerAccountView(account.getId(), account.getFullName(), account.getEmail(),
            account.getPhone(), account.getCreatedAt());
    }
}
