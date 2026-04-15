package com.busymumkitchen.messaging;

import com.busymumkitchen.model.User;
import com.busymumkitchen.model.mongo.OrderLog;
import com.busymumkitchen.repository.UserRepository;
import com.busymumkitchen.repository.mongo.OrderLogRepository;
import com.busymumkitchen.service.SmsService;
import com.busymumkitchen.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderLogRepository orderLogRepository;
    private final UserRepository userRepository;
    private final SmsService smsService;
    private final WhatsAppService whatsAppService;

    @RabbitListener(queues = "${rabbitmq.queues.order-created}")
    public void handleOrderCreated(Map<String, Object> event) {
        log.info("Processing ORDER_CREATED event: {}", event.get("orderNumber"));

        // Log to MongoDB
        OrderLog orderLog = OrderLog.builder()
                .orderId((String) event.get("orderId"))
                .orderNumber((String) event.get("orderNumber"))
                .action("ORDER_CREATED")
                .newStatus("PLACED")
                .performedBy((String) event.get("userId"))
                .performedByRole("CUSTOMER")
                .timestamp(LocalDateTime.now())
                .metadata(event)
                .build();
        orderLogRepository.save(orderLog);
    }

    @RabbitListener(queues = "${rabbitmq.queues.order-updated}")
    public void handleOrderUpdated(Map<String, Object> event) {
        log.info("Processing ORDER_UPDATED event: {} ({} -> {})",
                event.get("orderNumber"), event.get("previousStatus"), event.get("newStatus"));

        // Log to MongoDB
        OrderLog orderLog = OrderLog.builder()
                .orderId((String) event.get("orderId"))
                .orderNumber((String) event.get("orderNumber"))
                .action("STATUS_CHANGED")
                .oldStatus((String) event.get("previousStatus"))
                .newStatus((String) event.get("newStatus"))
                .timestamp(LocalDateTime.now())
                .metadata(event)
                .build();
        orderLogRepository.save(orderLog);
    }

    @RabbitListener(queues = "${rabbitmq.queues.notification}")
    public void handleNotification(Map<String, Object> event) {
        String type = (String) event.get("type");
        String userId = (String) event.get("userId");
        String orderNumber = (String) event.get("orderNumber");

        log.info("Processing notification: {} for order: {}", type, orderNumber);

        try {
            User user = userRepository.findById(UUID.fromString(userId)).orElse(null);
            if (user == null) return;

            switch (type) {
                case "ORDER_CREATED" -> {
                    smsService.sendOrderConfirmation(user.getPhoneNumber(), orderNumber, "");
                    if (user.getWhatsappNumber() != null && user.getIsWhatsappVerified()) {
                        whatsAppService.sendOrderConfirmation(
                                user.getWhatsappNumber(), orderNumber, "", java.util.List.of());
                    }
                }
                case "ORDER_STATUS_CHANGED" -> {
                    String newStatus = (String) event.get("newStatus");
                    smsService.sendOrderStatusUpdate(user.getPhoneNumber(), orderNumber, newStatus);
                    if (user.getWhatsappNumber() != null && user.getIsWhatsappVerified()) {
                        whatsAppService.sendOrderStatusUpdate(
                                user.getWhatsappNumber(), orderNumber, newStatus);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process notification for order: {}", orderNumber, e);
        }
    }
}
