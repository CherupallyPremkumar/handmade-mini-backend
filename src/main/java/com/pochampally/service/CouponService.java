package com.pochampally.service;

import com.pochampally.entity.Coupon;
import com.pochampally.entity.CouponUsage;
import com.pochampally.repository.CouponRepository;
import com.pochampally.repository.CouponUsageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;
    private final CouponUsageRepository couponUsageRepository;

    /**
     * Validate coupon and calculate discount.
     * Returns {valid, discountAmount, error, couponCode, couponType}.
     */
    public Map<String, Object> validate(String code, long orderAmount, String userId) {
        if (code == null || code.isBlank()) {
            return error("Coupon code is required");
        }

        Coupon coupon = couponRepository.findByCodeAndIsActiveTrue(code.toUpperCase().trim())
                .orElse(null);
        if (coupon == null) {
            return error("Invalid coupon code");
        }

        Instant now = Instant.now();
        if (coupon.getValidFrom() != null && now.isBefore(coupon.getValidFrom())) {
            return error("Coupon is not yet active");
        }
        if (coupon.getValidTo() != null && now.isAfter(coupon.getValidTo())) {
            return error("Coupon has expired");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return error("Coupon usage limit reached");
        }
        if (userId != null && couponUsageRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            return error("You have already used this coupon");
        }
        if (coupon.getMinOrderAmount() != null && orderAmount < coupon.getMinOrderAmount()) {
            long minRupees = coupon.getMinOrderAmount() / 100;
            return error("Minimum order amount is ₹" + minRupees + " for this coupon");
        }

        long discount;
        if (coupon.getType() == Coupon.CouponType.PERCENTAGE) {
            discount = (orderAmount * coupon.getValue()) / 100;
            if (coupon.getMaxDiscount() != null && discount > coupon.getMaxDiscount()) {
                discount = coupon.getMaxDiscount();
            }
        } else {
            discount = coupon.getValue();
        }

        if (discount >= orderAmount) {
            discount = orderAmount - 100; // Keep at least ₹1
        }

        log.info("Coupon {} validated. Discount: {} paisa on order of {} paisa", code, discount, orderAmount);

        return Map.of(
                "valid", true,
                "discountAmount", discount,
                "couponCode", coupon.getCode(),
                "couponType", coupon.getType().name(),
                "couponValue", coupon.getValue()
        );
    }

    @Transactional
    public void recordUsage(String couponCode, String userId, String orderId) {
        Coupon coupon = couponRepository.findByCodeAndIsActiveTrue(couponCode.toUpperCase().trim())
                .orElse(null);
        if (coupon == null) return;

        if (!couponUsageRepository.existsByCouponIdAndUserId(coupon.getId(), userId)) {
            couponUsageRepository.save(CouponUsage.builder()
                    .couponId(coupon.getId())
                    .userId(userId)
                    .orderId(orderId)
                    .build());
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
            log.info("Coupon {} usage recorded for user {} order {}", couponCode, userId, orderId);
        }
    }

    public List<Coupon> listAll() {
        return couponRepository.findAllByOrderByCreatedTimeDesc();
    }

    @Transactional
    public Coupon create(Coupon coupon) {
        coupon.setCode(coupon.getCode().toUpperCase().trim());
        return couponRepository.save(coupon);
    }

    @Transactional
    public Coupon update(String id, Coupon updates) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Coupon not found"));
        if (updates.getCode() != null) coupon.setCode(updates.getCode().toUpperCase().trim());
        if (updates.getType() != null) coupon.setType(updates.getType());
        if (updates.getValue() != null) coupon.setValue(updates.getValue());
        coupon.setMinOrderAmount(updates.getMinOrderAmount());
        coupon.setMaxDiscount(updates.getMaxDiscount());
        coupon.setUsageLimit(updates.getUsageLimit());
        if (updates.getValidFrom() != null) coupon.setValidFrom(updates.getValidFrom());
        if (updates.getValidTo() != null) coupon.setValidTo(updates.getValidTo());
        if (updates.getIsActive() != null) coupon.setIsActive(updates.getIsActive());
        return couponRepository.save(coupon);
    }

    @Transactional
    public void delete(String id) {
        couponRepository.deleteById(id);
    }

    private Map<String, Object> error(String message) {
        return Map.of("valid", false, "error", message, "discountAmount", 0L);
    }
}
