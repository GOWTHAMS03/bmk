package com.busymumkitchen.dto.kitchen;

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
public class KitchenOrderResponse {
    private UUID orderId;
    private String orderNumber;
    private String dailyOrderNumber;
    private OrderStatus status;
    private List<OrderItemSummary> items;
    private String customerName;
    private String notes;
    private Integer estimatedPrepMins;
    private Integer priority;
    private String assignedToName;
    private LocalDateTime placedAt;
    private LocalDateTime acceptedAt;
    private LocalDateTime prepStartedAt;
    private LocalDateTime readyAt;
    private BigDecimal totalAmount;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemSummary {
        private String itemName;
        private int quantity;
        private String specialRequest;
    }
}

