package com.busymumkitchen.controller;

import com.busymumkitchen.security.SecurityUtils;
import com.busymumkitchen.service.SseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Provides a Server-Sent Events endpoint for real-time order notifications.
 *
 * Frontend connects via:
 *   const es = new EventSource('/api/v1/notifications/stream');
 *   es.addEventListener('order-update', e => { ... });
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final SseNotificationService sseService;
    private final SecurityUtils securityUtils;

    /**
     * SSE subscription endpoint (authenticated – JWT via Authorization header handled by filter).
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        UUID userId = securityUtils.getCurrentUserId();
        return sseService.subscribe(userId);
    }
}
