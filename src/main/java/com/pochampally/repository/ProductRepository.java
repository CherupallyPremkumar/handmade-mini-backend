package com.pochampally.repository;

import com.pochampally.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, String> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock + :qty WHERE p.id = :id")
    void incrementStock(@Param("id") String id, @Param("qty") int qty);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isDeleted = false")
    List<Product> findByIsActiveTrue();

    @Query("SELECT p FROM Product p WHERE p.fabric = :fabric AND p.isActive = true AND p.isDeleted = false")
    List<Product> findByFabricAndIsActiveTrue(@Param("fabric") Product.Fabric fabric);

    @Query("SELECT p FROM Product p WHERE p.weaveType = :weaveType AND p.isActive = true AND p.isDeleted = false")
    List<Product> findByWeaveTypeAndIsActiveTrue(@Param("weaveType") Product.WeaveType weaveType);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isDeleted = false AND LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Product> searchByNameContaining(@Param("query") String query);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isDeleted = false AND p.sellingPrice BETWEEN :minPrice AND :maxPrice")
    List<Product> findByPriceRange(@Param("minPrice") Long minPrice, @Param("maxPrice") Long maxPrice);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isDeleted = false AND LOWER(p.bodyColor) = LOWER(:color)")
    List<Product> findByBodyColorAndIsActiveTrue(@Param("color") String color);

    @Query("SELECT p FROM Product p WHERE p.isDeleted = false")
    List<Product> findAllNotDeleted();

    Optional<Product> findBySku(String sku);
}
