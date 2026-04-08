package com.pochampally.controller;

import com.pochampally.entity.Review;
import com.pochampally.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    // Public
    @GetMapping("/api/products/{productId}/reviews")
    public ResponseEntity<List<Review>> getReviews(@PathVariable String productId) {
        return ResponseEntity.ok(reviewService.getApprovedReviews(productId));
    }

    @GetMapping("/api/products/{productId}/reviews/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String productId) {
        return ResponseEntity.ok(reviewService.getReviewSummary(productId));
    }

    // Authenticated
    @PostMapping("/api/products/{productId}/reviews")
    public ResponseEntity<Review> submitReview(@PathVariable String productId,
                                                @RequestBody Map<String, Object> body,
                                                Authentication auth) {
        int rating = ((Number) body.get("rating")).intValue();
        String title = (String) body.getOrDefault("title", "");
        String comment = (String) body.getOrDefault("comment", "");
        return ResponseEntity.ok(reviewService.submitReview(auth.getName(), productId, rating, title, comment));
    }

    // Admin
    @GetMapping("/api/admin/reviews/pending")
    public ResponseEntity<List<Review>> getPending() {
        return ResponseEntity.ok(reviewService.getPendingReviews());
    }

    @PatchMapping("/api/admin/reviews/{id}/approve")
    public ResponseEntity<Review> approve(@PathVariable String id) {
        return ResponseEntity.ok(reviewService.approve(id));
    }

    @DeleteMapping("/api/admin/reviews/{id}")
    public ResponseEntity<Void> reject(@PathVariable String id) {
        reviewService.reject(id);
        return ResponseEntity.noContent().build();
    }
}
