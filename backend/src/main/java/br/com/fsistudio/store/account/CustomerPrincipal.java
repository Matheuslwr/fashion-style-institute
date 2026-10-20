package br.com.fsistudio.store.account;

import java.util.UUID;

public record CustomerPrincipal(UUID id, String email, String phone, String fullName) { }
