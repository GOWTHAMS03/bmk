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
        Map<String, Object> event = Map.of(
                "eventType", "ORDER_CREATED",
                "orderId", order.getId().toString(),
                "orderNumber", order.getOrderNumber(),
                "userId", order.getUser().getId().toString(),
                "totalAmount", order.getFinalAmount().toString(),
                "itemCount", order.getItems().size()
        );

        if (enabled && rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(exchange, orderCreatedKey, event);
            log.info("Published ORDER_CREATED event for order: {}", order.getOrderNumber());

            // Also send notification event
            rabbitTemplate.convertAndSend(exchange, notificationKey, Map.of(
                    "type", "ORDER_CREATED",
                    "orderId", order.getId().toString(),
                    "orderNumber", order.getOrderNumber(),
                    "userId", order.getUser().getId().toString()
            ));

            // Analytics event
            rabbitTemplate.convertAndSend(exchange, analyticsKey, Map.of(
                    "eventType", "ORDER_PLACED",
                    "userId", order.getUser().getId().toString(),
                    "data", Map.of(
                            "orderId", order.getId().toString(),
                            "amount", order.getFinalAmount().toString(),
                            "itemCount", order.getItems().size()
                    )
            ));
        } else {
            log.info("[DEV MODE] ORDER_CREATED event (RabbitMQ disabled): order={}, user={}, amount={}",
                    order.getOrderNumber(), order.getUser().getId(), order.getFinalAmount());
        }
    }

    public void publishOrderUpdated(Order order, OrderStatus previousStatus) {
        Map<String, Object> event = Map.of(
                "eventType", "ORDER_UPDATED",
                "orderId", order.getId().toString(),
                "orderNumber", order.getOrderNumber(),
                "previousStatus", previousStatus.name(),
                "newStatus", order.getStatus().name(),
                "userId", order.getUser().getId().toString()
        );

        if (enabled && rabbitTemplate != null) {
            rabbitTemplate.convertAndSend(exchange, orderUpdatedKey, event);
            log.info("Published ORDER_UPDATED event for order: {} ({} -> {})",
                    order.getOrderNumber(), previousStatus, order.getStatus());

            // Send notification
            rabbitTemplate.convertAndSend(exchange, notificationKey, Map.of(
                    "type", "ORDER_STATUS_CHANGED",
                    "orderId", order.getId().toString(),
                    "orderNumber", order.getOrderNumber(),
                    "userId", order.getUser().getId().toString(),
                    "newStatus", order.getStatus().name()
            ));
        } else {
            log.info("[DEV MODE] ORDER_UPDATED event (RabbitMQ disabled): order={}, {} -> {}",
                    order.getOrderNumber(), previousStatus, order.getStatus());
        }
    }
}
