package com.busymumkitchen.messaging;

import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;
        private final SqsClient sqsClient;
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

        @Value("${SQS_ORDER_CREATED_URL:}")
        private String sqsOrderCreatedUrl;

        @Value("${SQS_ORDER_UPDATED_URL:}")
        private String sqsOrderUpdatedUrl;

        @Value("${SQS_NOTIFICATION_URL:}")
        private String sqsNotificationUrl;

        @Value("${SQS_ANALYTICS_URL:}")
        private String sqsAnalyticsUrl;

        private final ObjectMapper objectMapper = new ObjectMapper();

        public OrderEventPublisher(
                        @Autowired(required = false) RabbitTemplate rabbitTemplate,
                        @Autowired(required = false) SqsClient sqsClient,
                        @Value("${rabbitmq.enabled:false}") boolean enabled) {
                this.rabbitTemplate = rabbitTemplate;
                this.sqsClient = sqsClient;
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
                } else if (sqsClient != null && sqsOrderCreatedUrl != null && !sqsOrderCreatedUrl.isEmpty()) {
                        try {
                            String body = objectMapper.writeValueAsString(event);
                            SendMessageRequest req = SendMessageRequest.builder()
                                    .queueUrl(sqsOrderCreatedUrl)
                                    .messageBody(body)
                                    .build();
                            sqsClient.sendMessage(req);
                            log.info("Published ORDER_CREATED event to SQS for order: {}", order.getOrderNumber());
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize ORDER_CREATED event", e);
                        }
                } else {
                        log.info("[DEV MODE] ORDER_CREATED event (messaging disabled): order={}, user={}, amount={}",
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
                } else if (sqsClient != null && sqsOrderUpdatedUrl != null && !sqsOrderUpdatedUrl.isEmpty()) {
                        try {
                            String body = objectMapper.writeValueAsString(event);
                            SendMessageRequest req = SendMessageRequest.builder()
                                    .queueUrl(sqsOrderUpdatedUrl)
                                    .messageBody(body)
                                    .build();
                            sqsClient.sendMessage(req);
                            log.info("Published ORDER_UPDATED event to SQS for order: {}", order.getOrderNumber());
                        } catch (JsonProcessingException e) {
                            log.error("Failed to serialize ORDER_UPDATED event", e);
                        }
                } else {
                        log.info("[DEV MODE] ORDER_UPDATED event (messaging disabled): order={}, {} -> {}",
                                        order.getOrderNumber(), previousStatus, order.getStatus());
                }
        }
}
