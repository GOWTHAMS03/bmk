package com.busymumkitchen.service;

import com.busymumkitchen.dto.cart.CartResponse;
import com.busymumkitchen.dto.order.*;
import com.busymumkitchen.dto.common.PagedResponse;
import com.busymumkitchen.exception.BadRequestException;
import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.*;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.model.enums.PaymentStatus;
import com.busymumkitchen.repository.*;
import com.busymumkitchen.model.ProductionQueue;
import com.busymumkitchen.messaging.OrderEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final CouponRepository couponRepository;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final PaymentService paymentService;
    private final OrderEventPublisher orderEventPublisher;
    private final WhatsAppService whatsAppService;
    private final SseNotificationService sseNotificationService;
    private final OrderNumberService orderNumberService;
    private final ProductionQueueRepository productionQueueRepository;

    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {
        // Get cart
        CartResponse cart = cartService.getCart(userId);
        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BadRequestException("Cart is empty");
        }

        // Build order
        String dailyNumber = orderNumberService.getNextDailyNumber();
        Order order = Order.builder()
                .user(User.builder().build())
                .orderNumber(generateOrderNumber())
                .dailyOrderNumber(dailyNumber)
                .status(OrderStatus.PLACED)
                .customerName(request.getCustomerName())
                .customerPhone(request.getCustomerPhone())
                .deliveryAddress(request.getDeliveryAddress())
                .pickupTime(request.getPickupTime())
                .notes(request.getNotes())
                .deliveryFee(BigDecimal.ZERO)
                .discountAmount(cart.getDiscount())
                .build();
        // Set user by ID
        User userRef = new User();
        userRef.setId(userId);
        order.setUser(userRef);

        // Apply coupon if present
        if (cart.getCouponApplied() != null) {
            Coupon coupon = couponRepository.findByCodeIgnoreCase(cart.getCouponApplied().getCode())
                    .orElse(null);
            if (coupon != null) {
                order.setCoupon(coupon);
                order.setDiscountAmount(cart.getCouponApplied().getDiscount());
                coupon.setUsedCount(coupon.getUsedCount() + 1);
                couponRepository.save(coupon);
            }
        }

        // Add order items
        for (CartResponse.CartItemResponse cartItem : cart.getItems()) {
            MenuItem menuItem = menuItemRepository.findById(cartItem.getMenuItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Menu item not found: " + cartItem.getName()));

            OrderItem orderItem = OrderItem.builder()
                    .menuItemId(menuItem.getId())
                    .itemName(menuItem.getName())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .totalPrice(cartItem.getTotalPrice())
                    .specialRequest(cartItem.getSpecialRequest())
                    .build();

            order.addItem(orderItem);
        }

        order.calculateTotals();
        Order savedOrder = orderRepository.save(order);

        // Create production queue entry for kitchen
        ProductionQueue pq = ProductionQueue.builder()
                .order(savedOrder)
                .estimatedPrepMins(15) // default 15 mins, can be calculated from items
                .build();
        productionQueueRepository.save(pq);
        savedOrder.setEstimatedPrepMinutes(15);

        // Create Stripe payment intent
        String clientSecret = null;
        if ("STRIPE".equalsIgnoreCase(request.getPaymentMethod())) {
            clientSecret = paymentService.createPaymentIntent(savedOrder);
        }

        // Clear cart
        cartService.clearCart(userId);

        // Publish order event (also sends WhatsApp via RabbitMQ if enabled)
        orderEventPublisher.publishOrderCreated(savedOrder);

        // Send WhatsApp placement notification directly when RabbitMQ disabled
        sendWhatsAppForStatus(savedOrder);

        log.info("Order created: {} for user: {}", savedOrder.getOrderNumber(), userId);

        return toOrderResponse(savedOrder, clientSecret);
    }

    public PagedResponse<OrderResponse> getUserOrders(UUID userId, OrderStatus status, int page, int size) {
        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(
                    userId, status, PageRequest.of(page, size));
        } else {
            orderPage = orderRepository.findByUserIdOrderByCreatedAtDesc(
                    userId, PageRequest.of(page, size));
        }

        return toPagedResponse(orderPage);
    }

    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toOrderResponse(order, null);
    }

    public OrderResponse getOrderByNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return toOrderResponse(order, null);
    }

    @Transactional
    public OrderResponse updateOrderStatus(UUID orderId, UpdateOrderStatusRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        validateStatusTransition(order.getStatus(), request.getStatus());

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(request.getStatus());

        if (request.getStatus() == OrderStatus.CANCELLED) {
            order.setCancelledReason(request.getReason());
            order.setCancelledAt(LocalDateTime.now());
        }

        Order savedOrder = orderRepository.save(order);

        // Publish status change event (publisher is now null-safe when no user attached)
        orderEventPublisher.publishOrderUpdated(savedOrder, oldStatus);

        // Send WhatsApp notification directly (covers both RabbitMQ-enabled and disabled modes)
        sendWhatsAppForStatus(savedOrder);

        // Push real-time SSE update to the connected user if user exists
        if (savedOrder.getUser() != null && savedOrder.getUser().getId() != null) {
            try {
                sseNotificationService.sendOrderUpdate(
                        savedOrder.getUser().getId(), savedOrder.getOrderNumber(), savedOrder.getStatus());
            } catch (Exception e) {
                log.warn("SSE notify failed for order {}: {}", savedOrder.getOrderNumber(), e.getMessage());
            }
        } else {
            log.debug("Order {} updated but no user attached; skipping SSE push", savedOrder.getOrderNumber());
        }

        return toOrderResponse(savedOrder, null);
    }

    @Transactional
    public OrderResponse reorder(UUID userId, UUID originalOrderId) {
        Order originalOrder = orderRepository.findById(originalOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Original order not found"));

        // Add items to cart
        for (OrderItem item : originalOrder.getItems()) {
            MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId()).orElse(null);
            if (menuItem != null && menuItem.getIsAvailable()) {
                com.busymumkitchen.dto.cart.AddToCartRequest cartRequest =
                        new com.busymumkitchen.dto.cart.AddToCartRequest();
                cartRequest.setMenuItemId(item.getMenuItemId());
                cartRequest.setQuantity(item.getQuantity());
                cartRequest.setSpecialRequest(item.getSpecialRequest());
                cartService.addToCart(userId, cartRequest);
            }
        }

        // Create new order
        CreateOrderRequest orderRequest = new CreateOrderRequest();
        orderRequest.setNotes(originalOrder.getNotes());
        orderRequest.setPaymentMethod("STRIPE");

        return createOrder(userId, orderRequest);
    }

    // Admin endpoints
    public PagedResponse<OrderResponse> getAllOrders(OrderStatus status, int page, int size) {
        Page<Order> orderPage;
        if (status != null) {
            orderPage = orderRepository.findByStatusOrderByCreatedAtDesc(
                    status, PageRequest.of(page, size));
        } else {
            orderPage = orderRepository.findAll(PageRequest.of(page, size));
        }
        return toPagedResponse(orderPage);
    }

    @Transactional
    public void assignDeliveryPartner(UUID orderId, UUID deliveryPartnerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setDeliveryPartnerId(deliveryPartnerId);
        orderRepository.save(order);
    }

    // ==================== HELPERS ====================

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        // Canonical admin flow:
        // PLACED → CONFIRMED → PREPARING → READY_FOR_PICKUP → OUT_FOR_DELIVERY → DELIVERED
        // ACCEPTED is kept in the enum for legacy/delivery-partner use but is no longer
        // required in the main flow — admins go directly CONFIRMED → PREPARING.
        boolean valid = switch (current) {
            case PLACED    -> next == OrderStatus.CONFIRMED  || next == OrderStatus.CANCELLED;
            case CONFIRMED -> next == OrderStatus.PREPARING  || next == OrderStatus.CANCELLED;
            case ACCEPTED  -> next == OrderStatus.PREPARING  || next == OrderStatus.CANCELLED;
            case PREPARING -> next == OrderStatus.READY_FOR_PICKUP || next == OrderStatus.CANCELLED;
            case READY_FOR_PICKUP -> next == OrderStatus.OUT_FOR_DELIVERY || next == OrderStatus.DELIVERED;
            case OUT_FOR_DELIVERY -> next == OrderStatus.DELIVERED;
            case DELIVERED -> next == OrderStatus.REFUNDED;
            default -> false;
        };

        if (!valid) {
            throw new BadRequestException(
                    "Cannot transition from " + current + " to " + next);
        }
    }

    private String generateOrderNumber() {
        // Unique order number for DB (UUID-based, not displayed to customer)
        return "BMK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /**
     * Sends a WhatsApp status message to the order owner.
     * Called directly from service methods so notifications are sent
     * even when RabbitMQ is disabled (dev/local mode).
     */
private void sendWhatsAppForStatus(Order order) {
        try {
            User user = userRepository.findById(order.getUser().getId()).orElse(null);
            if (user == null) return;

            String whatsappTo = user.getWhatsappNumber() != null
                    ? user.getWhatsappNumber()
                    : user.getPhoneNumber();
            if (whatsappTo == null) return;

            whatsAppService.sendOrderStatusNotification(
                    whatsappTo, order.getOrderNumber(), order.getStatus());
        } catch (Exception e) {
            log.warn("Failed to send WhatsApp for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
}

    private OrderResponse toOrderResponse(Order order, String clientSecret) {
        List<OrderResponse.OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderResponse.OrderItemResponse.builder()
                        .menuItemId(item.getMenuItemId())
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .totalPrice(item.getTotalPrice())
                        .specialRequest(item.getSpecialRequest())
                        .build())
                .collect(Collectors.toList());

        String paymentStatus = null;
        if (order.getPayment() != null) {
            paymentStatus = order.getPayment().getStatus().name();
        }

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .dailyOrderNumber(order.getDailyOrderNumber())
                .status(order.getStatus())
                .items(items)
                .totalAmount(order.getTotalAmount())
                .discountAmount(order.getDiscountAmount())
                .deliveryFee(order.getDeliveryFee())
                .finalAmount(order.getFinalAmount())
                .couponCode(order.getCoupon() != null ? order.getCoupon().getCode() : null)
                .deliveryAddress(order.getDeliveryAddress())
                .customerName(order.getCustomerName())
                .customerPhone(order.getCustomerPhone())
                .pickupTime(order.getPickupTime())
                .notes(order.getNotes())
                .paymentStatus(paymentStatus)
                .stripeClientSecret(clientSecret)
                .estimatedPrepMinutes(order.getEstimatedPrepMinutes())
                .acceptedAt(order.getAcceptedAt())
                .prepStartedAt(order.getPrepStartedAt())
                .readyAt(order.getReadyAt())
                .createdAt(order.getCreatedAt())
                .build();
    }

    private PagedResponse<OrderResponse> toPagedResponse(Page<Order> page) {
        List<OrderResponse> orders = page.getContent().stream()
                .map(o -> toOrderResponse(o, null))
                .collect(Collectors.toList());

        return PagedResponse.<OrderResponse>builder()
                .content(orders)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .first(page.isFirst())
                .build();
    }
}
