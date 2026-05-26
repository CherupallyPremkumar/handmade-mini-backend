package com.homebase.mini.product.configuration.dao;

import com.homebase.mini.product.model.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for the new Chenile-based Product module (v2).
 * <p>
 * Direct DB access is used only by the {@code ProductEntityStore} (reads for the
 * workflow) and by the new service helpers (SKU generation, stock management).
 * <strong>All read-only queries from the frontend must go through the Query Module
 * ({@code POST /q/products} etc.) — not through this repository.</strong>
 * </p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    // ── Basic finders ────────────────────────────────────────────

    /** Active, non-deleted products (used by public store) */
    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isDeleted = false")
    List<Product> findAllActive();

    /** Everything not soft-deleted (used by admin) */
    @Query("SELECT p FROM Product p WHERE p.isDeleted = false")
    List<Product> findAllNotDeleted();

    Optional<Product> findBySku(String sku);

    // ── Fabric / weave / color / price filters ───────────────────

    @Query("SELECT p FROM Product p WHERE p.fabric = :fabric AND p.isActive = true AND p.isDeleted = false")
    List<Product> findByFabricAndActive(@Param("fabric") Product.Fabric fabric);

    @Query("SELECT p FROM Product p WHERE p.weaveType = :weaveType AND p.isActive = true AND p.isDeleted = false")
    List<Product> findByWeaveTypeAndActive(@Param("weaveType") Product.WeaveType weaveType);

    @Query("SELECT p FROM Product p WHERE p.bodyColor = :color AND p.isActive = true AND p.isDeleted = false")
    List<Product> findByBodyColorAndActive(@Param("color") String color);

    @Query("SELECT p FROM Product p WHERE p.sellingPrice >= :minPrice AND p.sellingPrice <= :maxPrice AND p.isActive = true AND p.isDeleted = false")
    List<Product> findByPriceRange(@Param("minPrice") Long minPrice, @Param("maxPrice") Long maxPrice);

    @Query("SELECT p FROM Product p WHERE (LOWER(p.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%',:q,'%'))) AND p.isActive = true AND p.isDeleted = false")
    List<Product> searchByNameOrDescription(@Param("q") String query);

    // ── Related products ─────────────────────────────────────────

    /**
     * Find products with the same fabric or weave type, excluding the current product.
     * Results are shuffled in the caller to provide variety.
     */
    @Query("SELECT p FROM Product p WHERE p.id <> :id AND p.isActive = true AND p.isDeleted = false AND (p.fabric = :fabric OR p.weaveType = :weaveType)")
    List<Product> findRelated(
            @Param("id")        String id,
            @Param("fabric")    Product.Fabric fabric,
            @Param("weaveType") Product.WeaveType weaveType,
            Pageable pageable);

    // ── Stock management ─────────────────────────────────────────

    /**
     * Atomically decrement stock only if enough units are available.
     * Returns number of rows updated (0 = out of stock).
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    /**
     * Atomically increment stock (used on order cancellation / refund).
     */
    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    void incrementStock(@Param("id") String id, @Param("qty") int qty);
}
