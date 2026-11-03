package br.com.fsistudio.store.account;

import java.time.Instant;

public record AdminTemporaryPasswordResponse(String temporaryPassword, Instant expiresAt) { }
