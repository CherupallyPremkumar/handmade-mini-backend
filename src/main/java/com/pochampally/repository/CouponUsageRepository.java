package com.pochampally.repository;

import com.pochampally.entity.CouponUsage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponUsageRepository extends JpaRepository<CouponUsage, String> {

    boolean existsByCouponIdAndUserId(String couponId, String userId);
}
