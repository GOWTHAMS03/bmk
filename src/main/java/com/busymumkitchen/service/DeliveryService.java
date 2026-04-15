package com.busymumkitchen.service;

import com.busymumkitchen.exception.ResourceNotFoundException;
import com.busymumkitchen.model.DeliveryPartner;
import com.busymumkitchen.model.Order;
import com.busymumkitchen.model.enums.OrderStatus;
import com.busymumkitchen.repository.DeliveryPartnerRepository;
import com.busymumkitchen.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryPartnerRepository deliveryPartnerRepository;
    private final OrderRepository orderRepository;

    public DeliveryPartner getPartnerByUserId(UUID userId) {
        return deliveryPartnerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner profile not found"));
    }

    public List<Order> getAssignedOrders(UUID deliveryPartnerId) {
        return orderRepository.findByDeliveryPartnerIdAndStatusIn(
                deliveryPartnerId,
                List.of(OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY)
        );
    }

    @Transactional
    public void acceptOrder(UUID deliveryPartnerId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (!deliveryPartnerId.equals(order.getDeliveryPartnerId())) {
            throw new IllegalArgumentException("Order is not assigned to this delivery partner");
        }

        order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
        orderRepository.save(order);
    }

    @Transactional
    public void rejectOrder(UUID deliveryPartnerId, UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        order.setDeliveryPartnerId(null);
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        orderRepository.save(order);
    }

    @Transactional
    public void updateDeliveryStatus(UUID deliveryPartnerId, UUID orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (status == OrderStatus.DELIVERED) {
            DeliveryPartner partner = deliveryPartnerRepository.findById(deliveryPartnerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found"));
            partner.setTotalDeliveries(partner.getTotalDeliveries() + 1);
            partner.setIsAvailable(true);
            deliveryPartnerRepository.save(partner);
        }

        order.setStatus(status);
        orderRepository.save(order);
    }

    @Transactional
    public void updateLocation(UUID userId, double latitude, double longitude) {
        DeliveryPartner partner = deliveryPartnerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found"));
        partner.setCurrentLatitude(latitude);
        partner.setCurrentLongitude(longitude);
        deliveryPartnerRepository.save(partner);
    }

    public Map<String, Object> getEarnings(UUID deliveryPartnerId, LocalDateTime from, LocalDateTime to) {
        DeliveryPartner partner = deliveryPartnerRepository.findById(deliveryPartnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found"));

        Page<Order> deliveredOrders = orderRepository.findByDeliveryPartnerIdOrderByCreatedAtDesc(
                deliveryPartnerId, PageRequest.of(0, 100));

        long totalDeliveries = deliveredOrders.getTotalElements();

        return Map.of(
                "totalDeliveries", partner.getTotalDeliveries(),
                "totalEarnings", partner.getTotalEarnings(),
                "rating", partner.getRating(),
                "recentDeliveries", totalDeliveries
        );
    }

    @Transactional
    public void toggleAvailability(UUID userId, boolean available) {
        DeliveryPartner partner = deliveryPartnerRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner not found"));
        partner.setIsAvailable(available);
        deliveryPartnerRepository.save(partner);
    }
}
