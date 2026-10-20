package br.com.fsistudio.store.account;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CustomerLoginAttemptLimiter {
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_IDENTIFIERS = 10_000;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Window> attempts = new HashMap<>();

    public synchronized void check(String identifier) {
        Window window = attempts.get(identifier);
        Instant now = Instant.now();
        if (window != null && now.isBefore(window.startedAt().plus(WINDOW)) && window.failures() >= MAX_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de acesso. Aguarde 15 minutos e tente novamente.");
        }
    }

    public synchronized void failed(String identifier) {
        Instant now = Instant.now();
        Window window = attempts.get(identifier);
        if (window == null || !now.isBefore(window.startedAt().plus(WINDOW))) {
            pruneExpired(now);
            if (attempts.size() >= MAX_IDENTIFIERS && !attempts.containsKey(identifier)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Muitas tentativas de acesso em andamento. Aguarde alguns minutos e tente novamente.");
            }
            attempts.put(identifier, new Window(now, 1));
        } else {
            attempts.put(identifier, new Window(window.startedAt(), window.failures() + 1));
        }
    }

    public synchronized void succeeded(String identifier) {
        attempts.remove(identifier);
    }

    private void pruneExpired(Instant now) {
        Iterator<Map.Entry<String, Window>> iterator = attempts.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!now.isBefore(iterator.next().getValue().startedAt().plus(WINDOW))) iterator.remove();
        }
    }

    private record Window(Instant startedAt, int failures) { }
}
