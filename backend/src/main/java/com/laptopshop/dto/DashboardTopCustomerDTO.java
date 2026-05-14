package com.laptopshop.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardTopCustomerDTO {
    private Long userId;
    private String username;
    private String fullName;
    private String email;
    private Long orderCount;
    private Double totalSpent;
}
