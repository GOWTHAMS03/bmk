package com.busymumkitchen.controller;
import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.dto.kitchen.KitchenDashboardResponse;
import com.busymumkitchen.dto.kitchen.KitchenOrderResponse;
import com.busymumkitchen.model.User;
import com.busymumkitchen.service.KitchenService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController
@RequestMapping("/kitchen")
@PreAuthorize("hasAnyRole('ADMIN', 'KITCHEN_STAFF')")
@RequiredArgsConstructor
public class KitchenController {
    private final KitchenService kitchenService;
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<KitchenDashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(kitchenService.getDashboard()));
    }
    @GetMapping("/queue")
    public ResponseEntity<ApiResponse<List<KitchenOrderResponse>>> getActiveQueue() {
        return ResponseEntity.ok(ApiResponse.success(kitchenService.getActiveQueue()));
    }
    @PostMapping("/orders/{id}/accept")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> acceptOrder(
            @PathVariable UUID id, @AuthenticationPrincipal User user) {
        UUID staffId = user != null ? user.getId() : null;
        return ResponseEntity.ok(ApiResponse.success(
                "Order accepted", kitchenService.acceptOrder(id, staffId)));
    }
    @PostMapping("/orders/{id}/prepare")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> startPreparing(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Preparation started", kitchenService.startPreparing(id)));
    }
    @PostMapping("/orders/{id}/ready")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> markReady(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Order is ready", kitchenService.markReady(id)));
    }
    @PostMapping("/orders/{id}/picked-up")
    public ResponseEntity<ApiResponse<KitchenOrderResponse>> markPickedUp(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Order picked up", kitchenService.markPickedUp(id)));
    }
}
