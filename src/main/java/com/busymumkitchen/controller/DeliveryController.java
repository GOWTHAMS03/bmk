package com.busymumkitchen.controller;

import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.model.DeliveryPartner;
import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.security.SecurityUtils;
import com.busymumkitchen.service.DeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/delivery")
@PreAuthorize("hasRole('DELIVERY_PARTNER')")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final SecurityUtils securityUtils;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<DeliveryPartner>> getProfile() {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(
                deliveryService.getPartnerByUserId(userId)));
    }

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<Order>>> getAssignedOrders() {
        UUID userId = securityUtils.getCurrentUserId();
        DeliveryPartner partner = deliveryService.getPartnerByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success(
                deliveryService.getAssignedOrders(partner.getId())));
    }

    @PutMapping("/orders/{orderId}/accept")
    public ResponseEntity<ApiResponse<Void>> acceptOrder(@PathVariable UUID orderId) {
        UUID userId = securityUtils.getCurrentUserId();
        DeliveryPartner partner = deliveryService.getPartnerByUserId(userId);
        deliveryService.acceptOrder(partner.getId(), orderId);
        return ResponseEntity.ok(ApiResponse.success("Order accepted"));
    }

    @PutMapping("/orders/{orderId}/reject")
    public ResponseEntity<ApiResponse<Void>> rejectOrder(@PathVariable UUID orderId) {
        UUID userId = securityUtils.getCurrentUserId();
        DeliveryPartner partner = deliveryService.getPartnerByUserId(userId);
        deliveryService.rejectOrder(partner.getId(), orderId);
        return ResponseEntity.ok(ApiResponse.success("Order rejected"));
    }

    @PutMapping("/orders/{orderId}/status")
    public ResponseEntity<ApiResponse<Void>> updateDeliveryStatus(
            @PathVariable UUID orderId, @RequestBody Map<String, Object> body) {
        UUID userId = securityUtils.getCurrentUserId();
        DeliveryPartner partner = deliveryService.getPartnerByUserId(userId);

        OrderStatus status = OrderStatus.valueOf((String) body.get("status"));
        deliveryService.updateDeliveryStatus(partner.getId(), orderId, status);

        // Update location if provided
        if (body.containsKey("latitude") && body.containsKey("longitude")) {
            deliveryService.updateLocation(userId,
                    ((Number) body.get("latitude")).doubleValue(),
                    ((Number) body.get("longitude")).doubleValue());
        }

        return ResponseEntity.ok(ApiResponse.success("Delivery status updated"));
    }

    @PutMapping("/location")
    public ResponseEntity<ApiResponse<Void>> updateLocation(@RequestBody Map<String, Double> body) {
        UUID userId = securityUtils.getCurrentUserId();
        deliveryService.updateLocation(userId, body.get("latitude"), body.get("longitude"));
        return ResponseEntity.ok(ApiResponse.success("Location updated"));
    }

    @PutMapping("/availability")
    public ResponseEntity<ApiResponse<Void>> toggleAvailability(@RequestBody Map<String, Boolean> body) {
        UUID userId = securityUtils.getCurrentUserId();
        deliveryService.toggleAvailability(userId, body.getOrDefault("available", true));
        return ResponseEntity.ok(ApiResponse.success("Availability updated"));
    }

    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getEarnings(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        UUID userId = securityUtils.getCurrentUserId();
        DeliveryPartner partner = deliveryService.getPartnerByUserId(userId);

        LocalDateTime start = from != null ? LocalDateTime.parse(from + "T00:00:00") : LocalDateTime.now().minusDays(30);
        LocalDateTime end = to != null ? LocalDateTime.parse(to + "T23:59:59") : LocalDateTime.now();

        return ResponseEntity.ok(ApiResponse.success(
                deliveryService.getEarnings(partner.getId(), start, end)));
    }
}
