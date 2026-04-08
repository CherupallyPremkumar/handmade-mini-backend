package com.pochampally.repository;

import com.pochampally.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, String> {

    Optional<Coupon> findByCodeAndIsActiveTrue(String code);

    List<Coupon> findAllByOrderByCreatedTimeDesc();
}
