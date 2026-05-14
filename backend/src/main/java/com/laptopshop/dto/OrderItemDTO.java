package com.laptopshop.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemDTO {
    private Long id;
    private Long variantId;
    private String productName;
    private String sku;
    private String imageUrl;
    private Integer quantity;
    private Double price;
}
