package com.pochampally.repository;

import com.pochampally.entity.WishlistItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, String> {

    List<WishlistItem> findByUserIdOrderByAddedAtDesc(String userId);

    boolean existsByUserIdAndProductId(String userId, String productId);

    void deleteByUserIdAndProductId(String userId, String productId);

    long countByUserId(String userId);
}
