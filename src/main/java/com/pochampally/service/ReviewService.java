package com.pochampally.service;

import com.pochampally.entity.Order;
import com.pochampally.entity.Product;
import com.pochampally.entity.Review;
import com.pochampally.entity.User;
import com.pochampally.repository.OrderRepository;
import com.pochampally.repository.ProductRepository;
import com.pochampally.repository.ReviewRepository;
import com.pochampally.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public List<Review> getApprovedReviews(String productId) {
        return reviewRepository.findByProductIdAndIsApprovedTrueOrderByCreatedTimeDesc(productId);
    }

    public Map<String, Object> getReviewSummary(String productId) {
        Double avg = reviewRepository.averageRatingByProductId(productId);
        long count = reviewRepository.countApprovedByProductId(productId);
        return Map.of(
                "averageRating", avg != null ? Math.round(avg * 10) / 10.0 : 0,
                "reviewCount", count
        );
    }

    @Transactional
    public Review submitReview(String userId, String productId, int rating, String title, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (reviewRepository.existsByUserIdAndProductId(userId, productId)) {
            throw new IllegalStateException("You have already reviewed this product");
        }

        // Only purchasers can review — single optimized query
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean purchased = user.getEmail() != null && orderRepository.existsPurchase(
                user.getEmail(), productId,
                Set.of(Order.OrderStatus.PAID, Order.OrderStatus.SHIPPED, Order.OrderStatus.DELIVERED));

        if (!purchased) {
            throw new IllegalStateException("You can only review products you have purchased");
        }

        Review review = Review.builder()
                .userId(userId)
                .productId(productId)
                .rating(rating)
                .title(title)
                .comment(comment)
                .reviewerName(user.getName())
                .isVerified(true)
                .isApproved(true) // auto-approved since only verified purchasers can review
                .build();

        Review saved = reviewRepository.save(review);
        updateProductRating(productId);
        log.info("Review submitted and auto-approved for product {} by user {}", productId, userId);
        return saved;
    }

    @Transactional
    public Review approve(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        review.setIsApproved(true);
        Review saved = reviewRepository.save(review);
        updateProductRating(review.getProductId());
        return saved;
    }

    @Transactional
    public void reject(String reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("Review not found"));
        reviewRepository.delete(review);
        updateProductRating(review.getProductId());
    }

    public List<Review> getPendingReviews() {
        return reviewRepository.findByIsApprovedFalseOrderByCreatedTimeDesc();
    }

    private void updateProductRating(String productId) {
        Double avg = reviewRepository.averageRatingByProductId(productId);
        long count = reviewRepository.countApprovedByProductId(productId);
        Product product = productRepository.findById(productId).orElse(null);
        if (product != null) {
            product.setAverageRating(avg);
            product.setReviewCount((int) count);
            productRepository.save(product);
        }
    }
}
