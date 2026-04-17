package com.busymumkitchen.repository;

import com.busymumkitchen.model.ProductionQueue;
import com.busymumkitchen.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductionQueueRepository extends JpaRepository<ProductionQueue, UUID> {

    Optional<ProductionQueue> findByOrderId(UUID orderId);

    @Query("SELECT pq FROM ProductionQueue pq JOIN FETCH pq.order o JOIN FETCH o.items " +
           "WHERE o.status IN :statuses ORDER BY pq.priority DESC, pq.createdAt ASC")
    List<ProductionQueue> findActiveOrders(@Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT pq FROM ProductionQueue pq JOIN FETCH pq.order o " +
           "WHERE pq.assignedTo.id = :staffId AND o.status IN :statuses")
    List<ProductionQueue> findByAssignedToAndStatuses(@Param("staffId") UUID staffId,
                                                      @Param("statuses") List<OrderStatus> statuses);

    @Query("SELECT COUNT(pq) FROM ProductionQueue pq JOIN pq.order o WHERE o.status = :status")
    long countByOrderStatus(@Param("status") OrderStatus status);

    @Query("SELECT AVG(pq.estimatedPrepMins) FROM ProductionQueue pq JOIN pq.order o " +
           "WHERE o.status IN :statuses AND pq.estimatedPrepMins IS NOT NULL")
    Double avgPrepTimeForStatuses(@Param("statuses") List<OrderStatus> statuses);
}

