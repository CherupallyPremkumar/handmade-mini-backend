package com.pochampally.repository;

import com.pochampally.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, String> {

    List<Category> findByIsActiveTrueOrderByPositionAsc();

    List<Category> findByIsActiveTrueAndShowOnHomeTrueOrderByPositionAsc();

    List<Category> findAllByOrderByPositionAsc();

    Optional<Category> findBySlug(String slug);
}
