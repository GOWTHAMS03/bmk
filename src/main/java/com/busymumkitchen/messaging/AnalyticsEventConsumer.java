package com.busymumkitchen.messaging;

import com.busymumkitchen.model.mongo.AnalyticsEvent;
import com.busymumkitchen.repository.mongo.AnalyticsEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "rabbitmq.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class AnalyticsEventConsumer {

    private final AnalyticsEventRepository analyticsEventRepository;

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = "${rabbitmq.queues.analytics}")
    public void handleAnalyticsEvent(Map<String, Object> event) {
        log.debug("Processing analytics event: {}", event.get("eventType"));

        AnalyticsEvent analyticsEvent = AnalyticsEvent.builder()
                .eventType((String) event.get("eventType"))
                .userId((String) event.get("userId"))
                .sessionId((String) event.get("sessionId"))
                .data((Map<String, Object>) event.get("data"))
                .timestamp(LocalDateTime.now())
                .build();

        analyticsEventRepository.save(analyticsEvent);
    }
}
