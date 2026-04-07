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

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByRazorpayOrderId(String razorpayOrderId);

    /** Pessimistic lock — prevents double payment race condition */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.razorpayOrderId = :razorpayOrderId")
    Optional<Order> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);

    List<Order> findByStatusAndCreatedTimeBefore(Order.OrderStatus status, java.time.Instant cutoff);

    long countByCustomerEmailAndStatus(String email, Order.OrderStatus status);
}
