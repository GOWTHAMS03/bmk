package com.busymumkitchen.messaging;

import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final boolean enabled;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-keys.order-created}")
    private String orderCreatedKey;

    @Value("${rabbitmq.routing-keys.order-updated}")
    private String orderUpdatedKey;

    @Value("${rabbitmq.routing-keys.notification}")
    private String notificationKey;

    @Value("${rabbitmq.routing-keys.analytics}")
    private String analyticsKey;

    public OrderEventPublisher(
            @Autowired(required = false) RabbitTemplate rabbitTemplate,
            @Value("${rabbitmq.enabled:false}") boolean enabled) {
        this.rabbitTemplate = rabbitTemplate;
        this.enabled = enabled;
    }

    public void publishOrderCreated(Order order) {
                String userId = null;
                if (order.getUser() != null && order.getUser().getId() != null) {
                        userId = order.getUser().getId().toString();
                }

                java.util.Map<String, Object> event = new java.util.HashMap<>();
                event.put("eventType", "ORDER_CREATED");
                event.put("orderId", order.getId().toString());
                event.put("orderNumber", order.getOrderNumber());
                if (userId != null) event.put("userId", userId);
                event.put("totalAmount", order.getFinalAmount() != null ? order.getFinalAmount().toString() : null);
                event.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);

                if (enabled && rabbitTemplate != null) {
                        rabbitTemplate.convertAndSend(exchange, orderCreatedKey, event);
                        log.info("Published ORDER_CREATED event for order: {}", order.getOrderNumber());

                        // Also send notification event
                        java.util.Map<String, Object> notif = new java.util.HashMap<>();
                        notif.put("type", "ORDER_CREATED");
                        notif.put("orderId", order.getId().toString());
                        notif.put("orderNumber", order.getOrderNumber());
                        if (userId != null) notif.put("userId", userId);
                        rabbitTemplate.convertAndSend(exchange, notificationKey, notif);

                        // Analytics event
                        java.util.Map<String, Object> analytics = new java.util.HashMap<>();
                        analytics.put("eventType", "ORDER_PLACED");
                        if (userId != null) analytics.put("userId", userId);
                        java.util.Map<String, Object> data = new java.util.HashMap<>();
                        data.put("orderId", order.getId().toString());
                        data.put("amount", order.getFinalAmount() != null ? order.getFinalAmount().toString() : "0");
                        data.put("itemCount", order.getItems() != null ? order.getItems().size() : 0);
                        analytics.put("data", data);
                        rabbitTemplate.convertAndSend(exchange, analyticsKey, analytics);
                } else {
                        log.info("[DEV MODE] ORDER_CREATED event (RabbitMQ disabled): order={}, user={}, amount={}",
                                        order.getOrderNumber(), userId, order.getFinalAmount());
                }
    }

        public void publishOrderUpdated(Order order, OrderStatus previousStatus) {
                String userId = null;
                if (order.getUser() != null && order.getUser().getId() != null) {
                        userId = order.getUser().getId().toString();
                }

                java.util.Map<String, Object> event = new java.util.HashMap<>();
                event.put("eventType", "ORDER_UPDATED");
                event.put("orderId", order.getId().toString());
                event.put("orderNumber", order.getOrderNumber());
                event.put("previousStatus", previousStatus != null ? previousStatus.name() : null);
                event.put("newStatus", order.getStatus() != null ? order.getStatus().name() : null);
                if (userId != null) event.put("userId", userId);

                if (enabled && rabbitTemplate != null) {
                        rabbitTemplate.convertAndSend(exchange, orderUpdatedKey, event);
                        log.info("Published ORDER_UPDATED event for order: {} ({} -> {})",
                                        order.getOrderNumber(), previousStatus, order.getStatus());

                        // Send notification
                        java.util.Map<String, Object> notif = new java.util.HashMap<>();
                        notif.put("type", "ORDER_STATUS_CHANGED");
                        notif.put("orderId", order.getId().toString());
                        notif.put("orderNumber", order.getOrderNumber());
                        if (userId != null) notif.put("userId", userId);
                        notif.put("newStatus", order.getStatus() != null ? order.getStatus().name() : null);
                        rabbitTemplate.convertAndSend(exchange, notificationKey, notif);
                } else {
                        log.info("[DEV MODE] ORDER_UPDATED event (RabbitMQ disabled): order={}, {} -> {}",
                                        order.getOrderNumber(), previousStatus, order.getStatus());
                }
        }
}
