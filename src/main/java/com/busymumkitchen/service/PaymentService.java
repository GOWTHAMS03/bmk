package com.busymumkitchen.service;

import com.busymumkitchen.exception.BadRequestException;
import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.Payment;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.model.enums.PaymentStatus;
import com.busymumkitchen.repository.OrderRepository;
import com.busymumkitchen.repository.PaymentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    @Value("${stripe.currency}")
    private String currency;

    public String createPaymentIntent(Order order) {
        try {
            // Convert to smallest currency unit (paise for INR)
            long amountInSmallestUnit = order.getFinalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInSmallestUnit)
                    .setCurrency(currency)
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("orderNumber", order.getOrderNumber())
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Save payment record
            Payment payment = Payment.builder()
                    .order(order)
                    .stripePaymentIntentId(paymentIntent.getId())
                    .amount(order.getFinalAmount())
                    .currency(currency.toUpperCase())
                    .status(PaymentStatus.PENDING)
                    .build();

            paymentRepository.save(payment);

            return paymentIntent.getClientSecret();

        } catch (StripeException e) {
            log.error("Stripe payment intent creation failed for order: {}", order.getOrderNumber(), e);
            throw new BadRequestException("Payment processing failed: " + e.getMessage());
        }
    }

    @Transactional
    public void handlePaymentSuccess(String paymentIntentId) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new BadRequestException("Payment not found"));

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setStripePaymentId(paymentIntentId);
        paymentRepository.save(payment);

        // Update order status to CONFIRMED
        Order order = payment.getOrder();
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);

        log.info("Payment completed for order: {}", order.getOrderNumber());
    }

    @Transactional
    public void handlePaymentFailure(String paymentIntentId, String failureMessage) {
        Payment payment = paymentRepository.findByStripePaymentIntentId(paymentIntentId)
                .orElseThrow(() -> new BadRequestException("Payment not found"));

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(failureMessage);
        paymentRepository.save(payment);

        log.warn("Payment failed for order: {} - {}", payment.getOrder().getOrderNumber(), failureMessage);
    }

    @Transactional
    public void processRefund(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BadRequestException("Payment not found for order"));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Cannot refund a payment that is not completed");
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentIntentId())
                    .build();

            Refund refund = Refund.create(params);

            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundId(refund.getId());
            paymentRepository.save(payment);

            Order order = payment.getOrder();
            order.setStatus(OrderStatus.REFUNDED);
            orderRepository.save(order);

            log.info("Refund processed for order: {}", order.getOrderNumber());

        } catch (StripeException e) {
            log.error("Refund failed for order: {}", orderId, e);
            throw new BadRequestException("Refund processing failed: " + e.getMessage());
        }
    }

    public Map<String, Object> getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BadRequestException("Payment not found"));

        return Map.of(
                "id", payment.getId(),
                "orderId", orderId,
                "amount", payment.getAmount(),
                "currency", payment.getCurrency(),
                "status", payment.getStatus().name(),
                "paymentMethod", payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "STRIPE"
        );
    }
}
