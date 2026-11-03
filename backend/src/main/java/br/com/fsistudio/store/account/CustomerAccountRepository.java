package br.com.fsistudio.store.account;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, UUID> {
    Optional<CustomerAccount> findByEmailIgnoreCase(String email);
    Optional<CustomerAccount> findByPhone(String phone);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    List<CustomerAccount> findTop20ByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrPhoneContainingOrderByCreatedAtDesc(
        String fullName, String email, String phone
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CustomerAccount account where account.id = :id")
    Optional<CustomerAccount> findByIdForUpdate(@Param("id") UUID id);
}
