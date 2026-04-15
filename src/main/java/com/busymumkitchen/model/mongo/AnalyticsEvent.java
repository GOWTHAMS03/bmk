package com.busymumkitchen.model.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "analytics_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyticsEvent {

    @Id
    private String id;

    @Indexed
    private String eventType; // PAGE_VIEW, ITEM_VIEW, ORDER_PLACED, SEARCH, etc.

    @Indexed
    private String userId;

    private String sessionId;
    private String ipAddress;
    private String userAgent;
    private Map<String, Object> data;

    @Builder.Default
    @Indexed
    private LocalDateTime timestamp = LocalDateTime.now();
}
