package com.busymumkitchen.dto.order;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CreateOrderRequest {

    private String customerName;

    private String customerPhone;

    private String deliveryAddress;

    private LocalDateTime pickupTime;

    private String notes;

    private String paymentMethod = "COD";
}
