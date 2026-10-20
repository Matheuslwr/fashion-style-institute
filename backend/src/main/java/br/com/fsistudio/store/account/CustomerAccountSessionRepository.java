package br.com.fsistudio.store.account;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountSessionRepository extends JpaRepository<CustomerAccountSession, String> {
    Optional<CustomerAccountSession> findByTokenHashAndExpiresAtAfter(String tokenHash, Instant now);
    void deleteByAccountId(UUID accountId);
}
