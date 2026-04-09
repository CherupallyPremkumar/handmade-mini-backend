package com.pochampally.repository;

import com.pochampally.entity.Policy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRepository extends JpaRepository<Policy, String> {
    List<Policy> findAllByOrderBySlugAsc();
}
