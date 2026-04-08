package com.pochampally.repository;

import com.pochampally.entity.Banner;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<Banner, String> {

    List<Banner> findByIsActiveTrueOrderByPositionAsc();

    List<Banner> findAllByOrderByPositionAsc();
}
