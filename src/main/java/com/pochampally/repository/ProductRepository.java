package com.pochampally.repository;

import com.pochampally.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String> {

    @Modifying
    @Query("UPDATE Product p SET p.stock = p.stock - :qty WHERE p.id = :id AND p.stock >= :qty")
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    List<Product> findByIsActiveTrue();

    List<Product> findByFabricAndIsActiveTrue(Product.Fabric fabric);

    List<Product> findByWeaveTypeAndIsActiveTrue(Product.WeaveType weaveType);

    @Query("SELECT s FROM Product s WHERE s.isActive = true AND LOWER(s.name) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Product> searchByNameContaining(@Param("query") String query);

    @Query("SELECT s FROM Product s WHERE s.isActive = true AND s.sellingPrice BETWEEN :minPrice AND :maxPrice")
    List<Product> findByPriceRange(@Param("minPrice") Long minPrice, @Param("maxPrice") Long maxPrice);

    @Query("SELECT s FROM Product s WHERE s.isActive = true AND LOWER(s.color) = LOWER(:color)")
    List<Product> findByColorAndIsActiveTrue(@Param("color") String color);
}
