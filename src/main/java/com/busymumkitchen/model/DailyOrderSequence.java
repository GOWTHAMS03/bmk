package com.busymumkitchen.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
@Entity
@Table(name = "daily_order_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyOrderSequence {
    @Id
    @Column(name = "order_date")
    private LocalDate orderDate;
    @Column(name = "last_sequence", nullable = false)
    @Builder.Default
    private Integer lastSequence = 0;
}
