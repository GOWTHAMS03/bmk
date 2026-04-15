package com.busymumkitchen.repository.mongo;

import com.busymumkitchen.model.mongo.NotificationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationLogRepository extends MongoRepository<NotificationLog, String> {

    Page<NotificationLog> findByRecipientOrderByTimestampDesc(String recipient, Pageable pageable);

    Page<NotificationLog> findByTypeOrderByTimestampDesc(String type, Pageable pageable);

    Page<NotificationLog> findByStatusOrderByTimestampDesc(String status, Pageable pageable);
}
