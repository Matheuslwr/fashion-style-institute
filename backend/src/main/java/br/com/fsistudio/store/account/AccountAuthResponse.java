package br.com.fsistudio.store.account;

import java.time.Instant;

public record AccountAuthResponse(String token, Instant expiresAt, CustomerAccountView account) { }
