package com.laptopshop.repository;

import com.laptopshop.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderId(Long orderId);

    Optional<Payment> findTopByOrderIdOrderByIdDesc(Long orderId);
}
