package com.busymumkitchen.dto.order;

import com.busymumkitchen.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private String orderNumber;
    private String dailyOrderNumber;
    private OrderStatus status;
    private List<OrderItemResponse> items;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal deliveryFee;
    private BigDecimal finalAmount;
    private String couponCode;
    private String deliveryAddress;
    private String customerName;
    private String customerPhone;
    private LocalDateTime pickupTime;
    private String notes;
    private String paymentStatus;
    private String stripeClientSecret;
    private Integer estimatedPrepMinutes;
    private LocalDateTime acceptedAt;
    private LocalDateTime prepStartedAt;
    private LocalDateTime readyAt;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private UUID menuItemId;
        private String itemName;
        private int quantity;
        private BigDecimal unitPrice;
        private BigDecimal totalPrice;
        private String specialRequest;
        private String imageUrl;
    }
}
