package br.com.fsistudio.store.account;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class CustomerLoginAttemptLimiterTest {
    @Test
    void blocksRepeatedFailedLoginAttempts() {
        CustomerLoginAttemptLimiter limiter = new CustomerLoginAttemptLimiter();
        for (int attempt = 0; attempt < 8; attempt += 1) {
            limiter.check("customer@example.com");
            limiter.failed("customer@example.com");
        }
        assertThrows(ResponseStatusException.class, () -> limiter.check("customer@example.com"));
    }

    @Test
    void successfulLoginClearsTheAttemptWindow() {
        CustomerLoginAttemptLimiter limiter = new CustomerLoginAttemptLimiter();
        for (int attempt = 0; attempt < 8; attempt += 1) limiter.failed("customer@example.com");
        limiter.succeeded("customer@example.com");
        limiter.check("customer@example.com");
    }
}
