package com.laptopshop.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardRevenuePointDTO {
    private String date;
    private Double revenue;
    private Long orders;
}
