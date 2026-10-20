package br.com.fsistudio.store.order;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface StoreOrderRepository extends JpaRepository<StoreOrder, UUID> {
    Optional<StoreOrder> findByIdempotencyKey(UUID idempotencyKey);

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from StoreOrder o where o.id = :id")
    Optional<StoreOrder> findByIdForUpdate(@Param("id") UUID id);

    List<StoreOrder> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<StoreOrder> findAllByCustomerAccountIdOrderByCreatedAtDesc(java.util.UUID accountId, Pageable pageable);

    @Modifying
    @Transactional
    @Query(value = """
        update store_orders set customer_account_id = :accountId
        where customer_account_id is null and lower(customer_email) = :email
        """, nativeQuery = true)
    int attachUnclaimedOrdersByEmail(@Param("email") String email, @Param("accountId") java.util.UUID accountId);

    @Query("""
        select coalesce(sum(item.quantity), 0L)
        from StoreOrderItem item
        where item.productSlug = :productSlug
          and item.color = :color
          and item.size = :size
          and item.order.paymentStatus in ('PROCESSING', 'PENDING')
          and item.order.createdAt >= :reservationCutoff
        """)
    long sumReservedQuantity(@Param("productSlug") String productSlug,
                             @Param("color") String color,
                             @Param("size") String size,
                             @Param("reservationCutoff") java.time.Instant reservationCutoff);
}
