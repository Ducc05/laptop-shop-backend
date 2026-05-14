package com.laptopshop.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardTopProductDTO {
    private Long productId;
    private String productName;
    private Long variantId;
    private String sku;
    private Long quantitySold;
    private Double revenue;
}
