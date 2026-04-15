package com.busymumkitchen.controller;

import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.dto.common.PagedResponse;
import com.busymumkitchen.dto.menu.*;
import com.busymumkitchen.dto.order.*;
import com.busymumkitchen.model.Coupon;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.model.enums.UserRole;
import com.busymumkitchen.repository.*;
import com.busymumkitchen.service.MenuService;
import com.busymumkitchen.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final MenuService menuService;
    private final OrderService orderService;
    private final CouponRepository couponRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    // Restaurant endpoints removed.

    // ==================== CATEGORIES ====================

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(menuService.createCategory(request)));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.success(menuService.updateCategory(id, request)));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable UUID id) {
        menuService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted"));
    }

    // ==================== MENU ITEMS ====================

    @PostMapping("/menu-items")
    public ResponseEntity<ApiResponse<MenuItemResponse>> createMenuItem(
            @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(menuService.createMenuItem(request)));
    }

    @PutMapping("/menu-items/{id}")
    public ResponseEntity<ApiResponse<MenuItemResponse>> updateMenuItem(
            @PathVariable UUID id, @Valid @RequestBody MenuItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success(menuService.updateMenuItem(id, request)));
    }

    @DeleteMapping("/menu-items/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteMenuItem(@PathVariable UUID id) {
        menuService.deleteMenuItem(id);
        return ResponseEntity.ok(ApiResponse.success("Menu item deleted"));
    }

    @PutMapping("/menu-items/{id}/availability")
    public ResponseEntity<ApiResponse<Void>> toggleAvailability(
            @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        menuService.updateAvailability(id, body.getOrDefault("available", true));
        return ResponseEntity.ok(ApiResponse.success("Availability updated"));
    }

    // ==================== ORDERS ====================

    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<PagedResponse<OrderResponse>>> getOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getAllOrders(status, page, size)));
    }

    @PutMapping("/orders/{id}/status")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.updateOrderStatus(id, request)));
    }

    @PutMapping("/orders/{id}/assign-delivery")
    public ResponseEntity<ApiResponse<Void>> assignDelivery(
            @PathVariable UUID id, @RequestBody Map<String, UUID> body) {
        orderService.assignDeliveryPartner(id, body.get("deliveryPartnerId"));
        return ResponseEntity.ok(ApiResponse.success("Delivery partner assigned"));
    }

    // ==================== COUPONS ====================

    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<List<Coupon>>> getCoupons() {
        return ResponseEntity.ok(ApiResponse.success(couponRepository.findAll()));
    }

    @PostMapping("/coupons")
    public ResponseEntity<ApiResponse<Coupon>> createCoupon(@RequestBody Coupon coupon) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(couponRepository.save(coupon)));
    }

    @PutMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<Coupon>> updateCoupon(
            @PathVariable UUID id, @RequestBody Coupon updated) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Coupon not found"));

        coupon.setCode(updated.getCode());
        coupon.setDescription(updated.getDescription());
        coupon.setDiscountType(updated.getDiscountType());
        coupon.setDiscountValue(updated.getDiscountValue());
        coupon.setMinOrderAmount(updated.getMinOrderAmount());
        coupon.setMaxDiscount(updated.getMaxDiscount());
        coupon.setUsageLimit(updated.getUsageLimit());
        coupon.setValidFrom(updated.getValidFrom());
        coupon.setValidUntil(updated.getValidUntil());
        coupon.setIsActive(updated.getIsActive());

        return ResponseEntity.ok(ApiResponse.success(couponRepository.save(coupon)));
    }

    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable UUID id) {
        couponRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Coupon deleted"));
    }

    // ==================== ANALYTICS ====================

    @GetMapping("/analytics/sales")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSalesAnalytics(
            @RequestParam String from, @RequestParam String to) {
        LocalDateTime start = LocalDateTime.parse(from + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(to + "T23:59:59");

        long totalOrders = orderRepository.countOrdersBetween(start, end);
        Double revenue = orderRepository.totalRevenueBetween(start, end);
        long totalCustomers = userRepository.countByRole(UserRole.CUSTOMER);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalOrders", totalOrders,
                "totalRevenue", revenue != null ? revenue : 0.0,
                "totalCustomers", totalCustomers,
                "ordersByStatus", orderRepository.countByStatus()
        )));
    }

    @GetMapping("/analytics/revenue")
    public ResponseEntity<ApiResponse<List<Object[]>>> getRevenueChart(
            @RequestParam String from, @RequestParam String to) {
        LocalDateTime start = LocalDateTime.parse(from + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(to + "T23:59:59");
        return ResponseEntity.ok(ApiResponse.success(
                orderRepository.dailyRevenue(start, end)));
    }

    // ==================== CUSTOMERS ====================

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<?>> getCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(
                    userRepository.searchByRoleAndKeyword(UserRole.CUSTOMER, search, PageRequest.of(page, size))));
        }
        return ResponseEntity.ok(ApiResponse.success(
                userRepository.findByRole(UserRole.CUSTOMER, PageRequest.of(page, size))));
    }

    @PutMapping("/customers/{id}/status")
    public ResponseEntity<ApiResponse<Void>> updateCustomerStatus(
            @PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        com.busymumkitchen.service.UserService userService = null; // Will be injected
        // For now, direct repo update
        var user = userRepository.findById(id).orElseThrow();
        user.setIsActive(body.getOrDefault("active", true));
        userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success("Customer status updated"));
    }
}
