package com.busymumkitchen.controller;

import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.dto.common.PagedResponse;
import com.busymumkitchen.dto.order.*;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.security.SecurityUtils;
import com.busymumkitchen.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        OrderResponse response = orderService.createOrder(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getUserOrders(userId, status, page, size)));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrderById(orderId)));
    }

    @PostMapping("/{orderId}/reorder")
    public ResponseEntity<ApiResponse<OrderResponse>> reorder(@PathVariable UUID orderId) {
        UUID userId = securityUtils.getCurrentUserId();
        OrderResponse response = orderService.reorder(userId, orderId);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Reorder placed successfully", response));
    }
}
