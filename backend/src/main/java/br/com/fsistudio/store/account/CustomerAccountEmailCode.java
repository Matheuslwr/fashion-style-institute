package br.com.fsistudio.store.account;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "customer_account_email_codes", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "account_id", "purpose" })
})
public class CustomerAccountEmailCode {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private CustomerAccount account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Purpose purpose;

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_after", nullable = false)
    private Instant resendAfter;

    @Column(nullable = false)
    private int attempts;

    protected CustomerAccountEmailCode() { }

    public CustomerAccountEmailCode(CustomerAccount account, Purpose purpose, String codeHash,
                                    Instant createdAt, Instant expiresAt, Instant resendAfter) {
        this.id = UUID.randomUUID();
        this.account = account;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.resendAfter = resendAfter;
    }

    public boolean isExpired(Instant now) { return !expiresAt.isAfter(now); }
    public boolean canResend(Instant now) { return !resendAfter.isAfter(now); }
    public boolean isLocked() { return attempts >= 5; }
    public void incrementAttempts() { attempts = Math.min(attempts + 1, 5); }
    public void replace(String newCodeHash, Instant now, Instant newExpiresAt, Instant newResendAfter) {
        codeHash = newCodeHash;
        createdAt = now;
        expiresAt = newExpiresAt;
        resendAfter = newResendAfter;
        attempts = 0;
    }

    public CustomerAccount getAccount() { return account; }
    public Purpose getPurpose() { return purpose; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getResendAfter() { return resendAfter; }
    public int getAttempts() { return attempts; }

    public enum Purpose { VERIFY_EMAIL, RESET_PASSWORD }
}
