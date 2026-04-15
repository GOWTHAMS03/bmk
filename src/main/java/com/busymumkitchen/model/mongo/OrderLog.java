package com.busymumkitchen.model.mongo;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "order_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLog {

    @Id
    private String id;

    @Indexed
    private String orderId;

    @Indexed
    private String orderNumber;

    private String action;
    private String oldStatus;
    private String newStatus;
    private String performedBy;
    private String performedByRole;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    private Map<String, Object> metadata;
}
