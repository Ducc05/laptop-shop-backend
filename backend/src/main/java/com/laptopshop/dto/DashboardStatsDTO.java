package com.laptopshop.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DashboardStatsDTO {
    private long totalOrders;
    private double totalRevenue;
    private long successfulOrders;
    private Map<String, Double> revenueByStatus;
    private double monthlyRevenue;
    private long monthlyOrders;
    private List<DashboardRevenuePointDTO> revenueByDayThisMonth;
    private List<DashboardBranchRevenueDTO> revenueByBranchThisMonth;
    private List<DashboardTopProductDTO> topProductsThisMonth;
    private List<DashboardTopCustomerDTO> topCustomersThisMonth;
}
