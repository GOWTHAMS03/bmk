package com.busymumkitchen.model.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "notification_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    @Id
    private String id;

    @Indexed
    private String type; // SMS, WHATSAPP, PUSH

    @Indexed
    private String recipient;

    private String templateName;
    private String message;
    private String status; // SENT, FAILED, DELIVERED
    private String providerMessageId;
    private String providerResponse;
    private String failureReason;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private Map<String, Object> metadata;
}
