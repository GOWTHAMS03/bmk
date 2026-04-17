package com.busymumkitchen.dto.kitchen;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenDashboardResponse {
    private long pendingOrders;
    private long preparingOrders;
    private long readyOrders;
    private long totalTodayOrders;
    private Double avgPrepTimeMinutes;
    private List<KitchenOrderResponse> activeQueue;
}

