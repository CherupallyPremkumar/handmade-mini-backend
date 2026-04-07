package com.pochampally.repository;

import com.pochampally.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, String> {

    List<Address> findByUserIdOrderByIsDefaultDescCreatedTimeDesc(String userId);

    Optional<Address> findByIdAndUserId(String id, String userId);

    long countByUserId(String userId);
}
