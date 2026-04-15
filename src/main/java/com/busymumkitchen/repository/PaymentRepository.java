package com.busymumkitchen.repository;

import com.busymumkitchen.model.Payment;
import com.busymumkitchen.model.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    Optional<Payment> findByStripePaymentId(String stripePaymentId);

    long countByStatus(PaymentStatus status);
}
