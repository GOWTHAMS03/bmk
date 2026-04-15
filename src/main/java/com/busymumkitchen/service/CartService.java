package com.busymumkitchen.service;

import com.busymumkitchen.dto.cart.AddToCartRequest;
import com.busymumkitchen.dto.cart.CartResponse;
import com.busymumkitchen.exception.BadRequestException;
import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.Coupon;
import com.busymumkitchen.model.MenuItem;
import com.busymumkitchen.repository.CouponRepository;
import com.busymumkitchen.repository.MenuItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final KeyValueStore keyValueStore;
    private final MenuItemRepository menuItemRepository;
    private final CouponRepository couponRepository;

    private static final String CART_PREFIX = "cart:";
    private static final String CART_COUPON_PREFIX = "cart_coupon:";
    private static final Duration CART_TTL = Duration.ofHours(24);

    @SuppressWarnings("unchecked")
    public CartResponse getCart(UUID userId) {
        String cartKey = CART_PREFIX + userId;
        Map<Object, Object> cartEntries = keyValueStore.getHashEntries(cartKey);

        if (cartEntries.isEmpty()) {
            return CartResponse.builder()
                    .items(Collections.emptyList())
                    .subtotal(BigDecimal.ZERO)
                    .deliveryFee(BigDecimal.ZERO)
                    .discount(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO)
                    .build();
        }

        // Parse cart items
        List<CartResponse.CartItemResponse> items = new ArrayList<>();
        // restaurant scoping removed
        BigDecimal subtotal = BigDecimal.ZERO;

        for (Map.Entry<Object, Object> entry : cartEntries.entrySet()) {
            Map<String, Object> itemData = (Map<String, Object>) entry.getValue();
            UUID menuItemId = UUID.fromString((String) itemData.get("menuItemId"));
            int quantity = (Integer) itemData.get("quantity");
            String specialRequest = (String) itemData.get("specialRequest");

            MenuItem menuItem = menuItemRepository.findById(menuItemId).orElse(null);
            if (menuItem == null || !menuItem.getIsAvailable()) continue;

            // restaurantId removed from cart logic

            BigDecimal unitPrice = menuItem.getEffectivePrice();
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(totalPrice);

            items.add(CartResponse.CartItemResponse.builder()
                    .menuItemId(menuItemId)
                    .name(menuItem.getName())
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(totalPrice)
                    .imageUrl(menuItem.getImageUrl())
                    .specialRequest(specialRequest)
                    .build());
        }

        // Check for applied coupon
        BigDecimal discount = BigDecimal.ZERO;
        CartResponse.CouponInfo couponInfo = null;
        String couponCode = (String) keyValueStore.getValue(CART_COUPON_PREFIX + userId);
        if (couponCode != null) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode).orElse(null);
            if (coupon != null && coupon.isValid()) {
                discount = coupon.calculateDiscount(subtotal);
                couponInfo = CartResponse.CouponInfo.builder()
                        .code(coupon.getCode())
                        .discount(discount)
                        .build();
            }
        }

        BigDecimal total = subtotal.subtract(discount);

        return CartResponse.builder()
                .items(items)
                .subtotal(subtotal)
                .deliveryFee(BigDecimal.ZERO)
                .discount(discount)
                .total(total.compareTo(BigDecimal.ZERO) > 0 ? total : BigDecimal.ZERO)
                .couponApplied(couponInfo)
                .build();
    }

    public void addToCart(UUID userId, AddToCartRequest request) {
        MenuItem menuItem = menuItemRepository.findById(request.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));

        if (!menuItem.getIsAvailable()) {
            throw new BadRequestException("This item is currently unavailable");
        }

        String cartKey = CART_PREFIX + userId;

        // No restaurant scoping: allow items from any source

        Map<String, Object> itemData = new HashMap<>();
        itemData.put("menuItemId", request.getMenuItemId().toString());
        itemData.put("quantity", request.getQuantity());
        itemData.put("specialRequest", request.getSpecialRequest());

        keyValueStore.putHashValue(cartKey, request.getMenuItemId().toString(), itemData);
        keyValueStore.expire(cartKey, CART_TTL);
    }

    public void updateCartItem(UUID userId, UUID menuItemId, int quantity) {
        String cartKey = CART_PREFIX + userId;
        Object existing = keyValueStore.getHashValue(cartKey, menuItemId.toString());
        if (existing == null) {
            throw new ResourceNotFoundException("Item not found in cart");
        }

        if (quantity <= 0) {
            keyValueStore.deleteHashValues(cartKey, menuItemId.toString());
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> itemData = (Map<String, Object>) existing;
        itemData.put("quantity", quantity);
        keyValueStore.putHashValue(cartKey, menuItemId.toString(), itemData);
    }

    public void removeFromCart(UUID userId, UUID menuItemId) {
        String cartKey = CART_PREFIX + userId;
        keyValueStore.deleteHashValues(cartKey, menuItemId.toString());
    }

    public void clearCart(UUID userId) {
        keyValueStore.delete(CART_PREFIX + userId);
        keyValueStore.delete(CART_COUPON_PREFIX + userId);
    }

    public BigDecimal applyCoupon(UUID userId, String couponCode) {
        Coupon coupon = couponRepository.findByCodeIgnoreCase(couponCode)
                .orElseThrow(() -> new BadRequestException("Invalid coupon code"));

        if (!coupon.isValid()) {
            throw new BadRequestException("This coupon has expired or is no longer valid");
        }

        CartResponse cart = getCart(userId);
        if (coupon.getMinOrderAmount() != null
                && cart.getSubtotal().compareTo(coupon.getMinOrderAmount()) < 0) {
            throw new BadRequestException(
                    "Minimum order amount of " + coupon.getMinOrderAmount() + " required for this coupon");
        }

        BigDecimal discount = coupon.calculateDiscount(cart.getSubtotal());
        keyValueStore.setValue(CART_COUPON_PREFIX + userId, couponCode, CART_TTL);

        return discount;
    }

    public void removeCoupon(UUID userId) {
        keyValueStore.delete(CART_COUPON_PREFIX + userId);
    }
}
