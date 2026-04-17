package com.busymumkitchen.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "production_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionQueue extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    @Column(nullable = false)
    @Builder.Default
    private Integer priority = 0;

    @Column(name = "estimated_prep_mins")
    private Integer estimatedPrepMins;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "prep_started_at")
    private LocalDateTime prepStartedAt;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(name = "picked_up_at")
    private LocalDateTime pickedUpAt;

    @Column(length = 500)
    private String notes;
}

