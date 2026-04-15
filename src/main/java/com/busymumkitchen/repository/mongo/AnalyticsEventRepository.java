package com.busymumkitchen.repository.mongo;

import com.busymumkitchen.model.mongo.AnalyticsEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends MongoRepository<AnalyticsEvent, String> {

    List<AnalyticsEvent> findByEventTypeAndTimestampBetween(
            String eventType, LocalDateTime start, LocalDateTime end);

    List<AnalyticsEvent> findByUserIdOrderByTimestampDesc(String userId);

    long countByEventTypeAndTimestampBetween(String eventType, LocalDateTime start, LocalDateTime end);
}
