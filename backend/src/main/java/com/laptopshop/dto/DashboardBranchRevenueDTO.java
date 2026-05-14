package com.laptopshop.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardBranchRevenueDTO {
    private Long branchId;
    private String branchName;
    private Double revenue;
    private Long orders;
}
