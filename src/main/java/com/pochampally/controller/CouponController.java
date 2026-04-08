package com.pochampally.controller;

import com.pochampally.entity.Coupon;
import com.pochampally.service.CouponService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping("/api/coupons/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestBody Map<String, Object> body,
                                                         Authentication auth) {
        String code = (String) body.get("code");
        long orderAmount = ((Number) body.get("orderAmount")).longValue();
        String userId = auth != null ? auth.getName() : null;
        return ResponseEntity.ok(couponService.validate(code, orderAmount, userId));
    }

    // Admin endpoints
    @GetMapping("/api/admin/coupons")
    public ResponseEntity<List<Coupon>> listAll() {
        return ResponseEntity.ok(couponService.listAll());
    }

    @PostMapping("/api/admin/coupons")
    public ResponseEntity<Coupon> create(@RequestBody Coupon coupon) {
        return ResponseEntity.ok(couponService.create(coupon));
    }

    @PutMapping("/api/admin/coupons/{id}")
    public ResponseEntity<Coupon> update(@PathVariable String id, @RequestBody Coupon updates) {
        return ResponseEntity.ok(couponService.update(id, updates));
    }

    @DeleteMapping("/api/admin/coupons/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        couponService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
