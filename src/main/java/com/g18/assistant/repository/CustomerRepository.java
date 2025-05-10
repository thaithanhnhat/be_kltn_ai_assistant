package com.g18.assistant.repository;

import com.g18.assistant.entity.Customer;
import com.g18.assistant.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findByShop(Shop shop);
    List<Customer> findByShopId(Long shopId);
    Optional<Customer> findByEmailAndShopId(String email, Long shopId);
    Optional<Customer> findByPhoneAndShopId(String phone, Long shopId);
} 