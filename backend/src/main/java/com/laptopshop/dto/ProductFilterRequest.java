package com.laptopshop.dto;

import lombok.Data;

@Data
public class ProductFilterRequest {
    private String keyword;
    private Long categoryId;
    private Long brandId;
    private Double minPrice;
    private Double maxPrice;
    private String cpu;
    private String ram;
    private String storage;
    private String gpu;
    private String screen;
    private String os;
    private String sortBy;
    private String sortDir;
}
