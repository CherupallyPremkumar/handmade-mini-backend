package com.pochampally.repository;

import com.pochampally.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, String> {

    List<Address> findByUserIdOrderByIsDefaultDescCreatedTimeDesc(String userId);

    Optional<Address> findByIdAndUserId(String id, String userId);

    long countByUserId(String userId);

    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false WHERE a.userId = :userId")
    void clearDefaultForUser(@Param("userId") String userId);
}
