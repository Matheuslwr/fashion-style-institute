package br.com.fsistudio.store.account;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_accounts")
public class CustomerAccount {
    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 120)
    private String email;

    @Column(nullable = false, unique = true, length = 24)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected CustomerAccount() { }

    public CustomerAccount(String fullName, String email, String phone, String passwordHash) {
        this.id = UUID.randomUUID();
        this.fullName = fullName.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public void verifyEmail() {
        if (emailVerifiedAt == null) emailVerifiedAt = Instant.now();
    }

    public void updatePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getEmailVerifiedAt() { return emailVerifiedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
