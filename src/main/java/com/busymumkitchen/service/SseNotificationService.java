package com.busymumkitchen.service;

import com.busymumkitchen.model.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Server-Sent Events (SSE) connections per user.
 * The frontend connects once and receives real-time order status updates.
 */
@Service
@Slf4j
public class SseNotificationService {

    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID userId) {
        // 10-minute timeout; frontend reconnects automatically
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);

        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> {
            emitters.remove(userId);
            emitter.complete();
        });
        emitter.onError(e -> emitters.remove(userId));

        // Send a ping immediately to confirm connection
        try {
            emitter.send(SseEmitter.event().name("ping").data("connected"));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        log.debug("SSE subscribed for user: {}", userId);
        return emitter;
    }

    /**
     * Push an order status update to a specific user's SSE stream.
     */
    public void sendOrderUpdate(UUID userId, String orderNumber, OrderStatus status) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) return;

        try {
            String message = WhatsAppService.buildStatusMessage(orderNumber, status);
            Map<String, String> payload = Map.of(
                    "orderNumber", orderNumber,
                    "status", status.name(),
                    "message", message
            );
            emitter.send(SseEmitter.event()
                    .name("order-update")
                    .data(payload));
            log.debug("SSE order update sent to user {}: {} -> {}", userId, orderNumber, status);
        } catch (IOException e) {
            log.warn("SSE send failed for user {}, removing emitter", userId);
            emitters.remove(userId);
        }
    }

    public boolean isConnected(UUID userId) {
        return emitters.containsKey(userId);
    }
}
