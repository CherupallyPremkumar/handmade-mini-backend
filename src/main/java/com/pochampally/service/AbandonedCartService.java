package com.pochampally.service;

import com.pochampally.entity.CartItem;
import com.pochampally.entity.Product;
import com.pochampally.entity.User;
import com.pochampally.repository.CartItemRepository;
import com.pochampally.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AbandonedCartService {

    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductService productService;
    private final EmailService emailService;
    private final SettingsService settingsService;

    /**
     * Runs every hour. Finds carts abandoned for X hours (configurable) and sends reminder emails.
     * Only sends to users who haven't received a reminder in the last 7 days.
     */
    @Scheduled(fixedRate = 60 * 60 * 1000) // every hour
    @Transactional
    public void sendAbandonedCartEmails() {
        if (!"true".equals(settingsService.get("abandoned_cart_email_enabled"))) {
            return;
        }

        int hours = settingsService.getInt("abandoned_cart_email_hours");
        if (hours <= 0) hours = 24;

        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        Instant reminderCooldown = Instant.now().minus(7, ChronoUnit.DAYS);

        // Find cart items with userId, older than cutoff
        List<CartItem> abandonedItems = cartItemRepository.findAll().stream()
                .filter(ci -> ci.getUserId() != null)
                .filter(ci -> ci.getAddedAt().isBefore(cutoff))
                .toList();

        // Group by userId
        Map<String, List<CartItem>> byUser = abandonedItems.stream()
                .collect(Collectors.groupingBy(CartItem::getUserId));

        int sent = 0;
        for (Map.Entry<String, List<CartItem>> entry : byUser.entrySet()) {
            String userId = entry.getKey();
            List<CartItem> items = entry.getValue();

            User user = userRepository.findById(userId).orElse(null);
            if (user == null || user.getEmail() == null) continue;

            // Skip if already reminded recently
            if (user.getLastCartReminderSent() != null && user.getLastCartReminderSent().isAfter(reminderCooldown)) {
                continue;
            }

            // Build product details
            StringBuilder itemList = new StringBuilder();
            for (CartItem ci : items) {
                try {
                    Product p = productService.getById(ci.getProductId());
                    itemList.append(p.getName()).append(" (x").append(ci.getQuantity()).append("), ");
                } catch (Exception e) {
                    // product may have been deleted
                }
            }

            if (itemList.isEmpty()) continue;

            emailService.sendAbandonedCartEmail(
                    user.getEmail(),
                    user.getName(),
                    itemList.substring(0, itemList.length() - 2)
            );

            user.setLastCartReminderSent(Instant.now());
            userRepository.save(user);
            sent++;
        }

        if (sent > 0) {
            log.info("Sent {} abandoned cart reminder emails", sent);
        }
    }
}
