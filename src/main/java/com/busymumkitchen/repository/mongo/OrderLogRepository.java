package com.busymumkitchen.repository.mongo;

import com.busymumkitchen.model.mongo.OrderLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderLogRepository extends MongoRepository<OrderLog, String> {

    List<OrderLog> findByOrderIdOrderByTimestampDesc(String orderId);

    List<OrderLog> findByOrderNumberOrderByTimestampDesc(String orderNumber);
}
