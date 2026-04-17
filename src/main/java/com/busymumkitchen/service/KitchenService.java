package com.busymumkitchen.service;

import com.busymumkitchen.dto.kitchen.KitchenDashboardResponse;
import com.busymumkitchen.dto.kitchen.KitchenOrderResponse;
import com.busymumkitchen.exception.BadRequestException;
import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.ProductionQueue;
import com.busymumkitchen.model.User;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.repository.OrderRepository;
import com.busymumkitchen.repository.ProductionQueueRepository;
import com.busymumkitchen.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KitchenService {

    private final ProductionQueueRepository productionQueueRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final SseNotificationService sseNotificationService;
    private final WhatsAppService whatsAppService;

    private static final List<OrderStatus> ACTIVE_STATUSES = Arrays.asList(
            OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP
    );

    public List<KitchenOrderResponse> getActiveQueue() {
        List<ProductionQueue> queue = productionQueueRepository.findActiveOrders(ACTIVE_STATUSES);
        return queue.stream().map(this::toKitchenResponse).collect(Collectors.toList());
    }

    public KitchenDashboardResponse getDashboard() {
        List<KitchenOrderResponse> activeQueue = getActiveQueue();

        return KitchenDashboardResponse.builder()
                .pendingOrders(productionQueueRepository.countByOrderStatus(OrderStatus.PLACED))
                .preparingOrders(productionQueueRepository.countByOrderStatus(OrderStatus.PREPARING))
                .readyOrders(productionQueueRepository.countByOrderStatus(OrderStatus.READY_FOR_PICKUP))
                .totalTodayOrders(activeQueue.size())
                .avgPrepTimeMinutes(productionQueueRepository.avgPrepTimeForStatuses(
                        Arrays.asList(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP)))
                .activeQueue(activeQueue)
                .build();
    }

    @Transactional
    public KitchenOrderResponse acceptOrder(UUID orderId, UUID staffId) {
        Order order = getOrder(orderId);
        validateStatus(order, OrderStatus.PLACED);

        order.setStatus(OrderStatus.ACCEPTED);
        order.setAcceptedAt(LocalDateTime.now());
        orderRepository.save(order);

        ProductionQueue pq = getOrCreateProductionQueue(order);
        pq.setAcceptedAt(LocalDateTime.now());
        if (staffId != null) {
            pq.setAssignedTo(userRepository.findById(staffId).orElse(null));
        }
        productionQueueRepository.save(pq);

        notifyCustomer(order);
        log.info("Order {} accepted by staff {}", order.getDailyOrderNumber(), staffId);
        return toKitchenResponse(pq);
    }

    @Transactional
    public KitchenOrderResponse startPreparing(UUID orderId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != OrderStatus.ACCEPTED && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order must be ACCEPTED before preparing. Current: " + order.getStatus());
        }

        order.setStatus(OrderStatus.PREPARING);
        order.setPrepStartedAt(LocalDateTime.now());
        orderRepository.save(order);

        ProductionQueue pq = getOrCreateProductionQueue(order);
        pq.setPrepStartedAt(LocalDateTime.now());
        productionQueueRepository.save(pq);

        notifyCustomer(order);
        log.info("Order {} is now being prepared", order.getDailyOrderNumber());
        return toKitchenResponse(pq);
    }

    @Transactional
    public KitchenOrderResponse markReady(UUID orderId) {
        Order order = getOrder(orderId);
        validateStatus(order, OrderStatus.PREPARING);

        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        order.setReadyAt(LocalDateTime.now());
        orderRepository.save(order);

        ProductionQueue pq = getOrCreateProductionQueue(order);
        pq.setReadyAt(LocalDateTime.now());
        productionQueueRepository.save(pq);

        notifyCustomer(order);
        log.info("Order {} is READY for pickup", order.getDailyOrderNumber());
        return toKitchenResponse(pq);
    }

    @Transactional
    public KitchenOrderResponse markPickedUp(UUID orderId) {
        Order order = getOrder(orderId);
        validateStatus(order, OrderStatus.READY_FOR_PICKUP);

        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        orderRepository.save(order);

        ProductionQueue pq = getOrCreateProductionQueue(order);
        pq.setPickedUpAt(LocalDateTime.now());
        productionQueueRepository.save(pq);

        notifyCustomer(order);
        return toKitchenResponse(pq);
    }

    // ==================== HELPERS ====================

    private Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));
    }

    private void validateStatus(Order order, OrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new BadRequestException(
                    "Expected status " + expected + " but got " + order.getStatus());
        }
    }

    private ProductionQueue getOrCreateProductionQueue(Order order) {
        return productionQueueRepository.findByOrderId(order.getId())
                .orElseGet(() -> {
                    ProductionQueue pq = ProductionQueue.builder()
                            .order(order)
                            .estimatedPrepMins(order.getEstimatedPrepMinutes())
                            .build();
                    return productionQueueRepository.save(pq);
                });
    }

    private void notifyCustomer(Order order) {
        try {
            sseNotificationService.sendOrderUpdate(
                    order.getUser().getId(), order.getOrderNumber(), order.getStatus());
        } catch (Exception e) {
            log.warn("SSE notification failed for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
        try {
            User user = userRepository.findById(order.getUser().getId()).orElse(null);
            if (user != null) {
                String phone = user.getWhatsappNumber() != null ? user.getWhatsappNumber() : user.getPhoneNumber();
                if (phone != null) {
                    whatsAppService.sendOrderStatusNotification(phone, order.getDailyOrderNumber() != null
                            ? order.getDailyOrderNumber() : order.getOrderNumber(), order.getStatus());
                }
            }
        } catch (Exception e) {
            log.warn("WhatsApp notification failed for order {}: {}", order.getOrderNumber(), e.getMessage());
        }
    }

    private KitchenOrderResponse toKitchenResponse(ProductionQueue pq) {
        Order order = pq.getOrder();
        List<KitchenOrderResponse.OrderItemSummary> items = order.getItems().stream()
                .map(item -> KitchenOrderResponse.OrderItemSummary.builder()
                        .itemName(item.getItemName())
                        .quantity(item.getQuantity())
                        .specialRequest(item.getSpecialRequest())
                        .build())
                .collect(Collectors.toList());

        return KitchenOrderResponse.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .dailyOrderNumber(order.getDailyOrderNumber())
                .status(order.getStatus())
                .items(items)
                .customerName(order.getCustomerName())
                .notes(order.getNotes())
                .estimatedPrepMins(pq.getEstimatedPrepMins())
                .priority(pq.getPriority())
                .assignedToName(pq.getAssignedTo() != null ? pq.getAssignedTo().getFullName() : null)
                .placedAt(order.getCreatedAt())
                .acceptedAt(pq.getAcceptedAt())
                .prepStartedAt(pq.getPrepStartedAt())
                .readyAt(pq.getReadyAt())
                .totalAmount(order.getFinalAmount())
                .build();
    }
}

