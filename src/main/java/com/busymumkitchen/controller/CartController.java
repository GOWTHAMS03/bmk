package com.busymumkitchen.controller;

import com.busymumkitchen.dto.cart.AddToCartRequest;
import com.busymumkitchen.dto.cart.CartResponse;
import com.busymumkitchen.dto.common.ApiResponse;
import com.busymumkitchen.security.SecurityUtils;
import com.busymumkitchen.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final SecurityUtils securityUtils;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        UUID userId = securityUtils.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.success(cartService.getCart(userId)));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addToCart(
            @Valid @RequestBody AddToCartRequest request) {
        UUID userId = securityUtils.getCurrentUserId();
        cartService.addToCart(userId, request);
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart",
                Map.of("cartItemCount", cart.getItems().size())));
    }

    @PutMapping("/items/{menuItemId}")
    public ResponseEntity<ApiResponse<Void>> updateCartItem(
            @PathVariable UUID menuItemId,
            @RequestBody Map<String, Integer> body) {
        UUID userId = securityUtils.getCurrentUserId();
        cartService.updateCartItem(userId, menuItemId, body.getOrDefault("quantity", 1));
        return ResponseEntity.ok(ApiResponse.success("Cart updated"));
    }

    @DeleteMapping("/items/{menuItemId}")
    public ResponseEntity<ApiResponse<Void>> removeFromCart(@PathVariable UUID menuItemId) {
        UUID userId = securityUtils.getCurrentUserId();
        cartService.removeFromCart(userId, menuItemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart"));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        UUID userId = securityUtils.getCurrentUserId();
        cartService.clearCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared"));
    }

    @PostMapping("/apply-coupon")
    public ResponseEntity<ApiResponse<Map<String, Object>>> applyCoupon(
            @RequestBody Map<String, String> body) {
        UUID userId = securityUtils.getCurrentUserId();
        BigDecimal discount = cartService.applyCoupon(userId, body.get("couponCode"));
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(ApiResponse.success("Coupon applied",
                Map.of("discount", discount, "newTotal", cart.getTotal())));
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<ApiResponse<Void>> removeCoupon() {
        UUID userId = securityUtils.getCurrentUserId();
        cartService.removeCoupon(userId);
        return ResponseEntity.ok(ApiResponse.success("Coupon removed"));
    }
}
