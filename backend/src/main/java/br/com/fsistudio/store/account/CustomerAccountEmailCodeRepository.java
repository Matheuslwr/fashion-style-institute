package br.com.fsistudio.store.account;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface CustomerAccountEmailCodeRepository extends JpaRepository<CustomerAccountEmailCode, UUID> {
    Optional<CustomerAccountEmailCode> findByAccountIdAndPurpose(UUID accountId, CustomerAccountEmailCode.Purpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
        select code from CustomerAccountEmailCode code
        where code.account.id = :accountId and code.purpose = :purpose
        """)
    Optional<CustomerAccountEmailCode> findForUpdate(@Param("accountId") UUID accountId,
                                                      @Param("purpose") CustomerAccountEmailCode.Purpose purpose);
}
