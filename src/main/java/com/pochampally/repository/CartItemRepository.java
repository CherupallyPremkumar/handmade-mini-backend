package com.pochampally.repository;

import com.pochampally.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, String> {

    List<CartItem> findBySessionId(String sessionId);

    Optional<CartItem> findBySessionIdAndProductId(String sessionId, String productId);

    void deleteBySessionId(String sessionId);
}
