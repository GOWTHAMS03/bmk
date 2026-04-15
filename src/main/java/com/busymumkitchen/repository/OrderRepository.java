package com.busymumkitchen.repository;

import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Page<Order> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<Order> findByUserIdAndStatusOrderByCreatedAtDesc(UUID userId, OrderStatus status, Pageable pageable);


    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    

    List<Order> findByDeliveryPartnerIdAndStatusIn(UUID deliveryPartnerId, List<OrderStatus> statuses);

    Page<Order> findByDeliveryPartnerIdOrderByCreatedAtDesc(UUID deliveryPartnerId, Pageable pageable);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId AND o.coupon.id = :couponId")
    long countByUserAndCoupon(@Param("userId") UUID userId, @Param("couponId") UUID couponId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :start AND :end")
    long countOrdersBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT SUM(o.finalAmount) FROM Order o WHERE o.status = 'DELIVERED' " +
            "AND o.createdAt BETWEEN :start AND :end")
    Double totalRevenueBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query(value = "SELECT DATE(created_at) as date, SUM(final_amount) as revenue " +
            "FROM orders WHERE status = 'DELIVERED' " +
            "AND created_at BETWEEN :start AND :end " +
            "GROUP BY DATE(created_at) ORDER BY date", nativeQuery = true)
    List<Object[]> dailyRevenue(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
