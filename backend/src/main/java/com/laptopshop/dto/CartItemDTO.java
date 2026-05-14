package com.laptopshop.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemDTO {
    private Long id;
    private Long variantId;
    private String productName;
    private String variantSku;
    private String imageUrl;
    private Integer quantity;
    private Double price; // Current price or snapshot? Usually current for display, snapshot for order.
    private Double snapshotPrice; // The price when added to cart
}
