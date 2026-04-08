package com.pochampally.repository;

import com.pochampally.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByCustomerEmail(String customerEmail);

    List<Order> findByStatus(Order.OrderStatus status);

    org.springframework.data.domain.Page<Order> findByStatus(Order.OrderStatus status, org.springframework.data.domain.Pageable pageable);

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    /** Pessimistic lock — prevents double payment race condition */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.razorpayOrderId = :razorpayOrderId")
    Optional<Order> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);

    List<Order> findByStatusAndCreatedTimeBefore(Order.OrderStatus status, java.time.Instant cutoff);

    long countByCustomerEmailAndStatus(String email, Order.OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status IN :statuses AND o.createdTime >= :since")
    long sumRevenueByStatusesAndCreatedTimeAfter(@Param("statuses") java.util.Collection<Order.OrderStatus> statuses, @Param("since") java.time.Instant since);

    long countByCreatedTimeAfter(java.time.Instant since);

    List<Order> findTop10ByOrderByCreatedTimeDesc();

    long countByCustomerPhoneAndStatus(String phone, Order.OrderStatus status);

    @Query("SELECT o FROM Order o WHERE o.customerPhone = :phone AND o.status = 'PENDING_PAYMENT' " +
           "AND o.razorpayOrderId IS NOT NULL AND o.createdTime > :since ORDER BY o.createdTime DESC")
    List<Order> findRecentPendingByPhone(@Param("phone") String phone,
                                         @Param("since") java.time.Instant since);
}
