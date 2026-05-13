package com.laptopshop.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.laptopshop.entity.*;
import com.laptopshop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DataImportService {

    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;
    private final BranchRepository branchRepository;
    private final InventoryRepository inventoryRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.import.path}")
    private String importPath;

    public void importData() throws IOException {
        File file = new File(importPath);
        if (!file.exists()) {
            throw new IOException("Import file not found at: " + importPath);
        }

        List<Map<String, Object>> rawProducts = objectMapper.readValue(file, new TypeReference<>() {});

        for (Map<String, Object> rawProduct : rawProducts) {
            String categoryName = (String) rawProduct.get("category");
            String brandName = (String) rawProduct.get("brand");

            Category category = categoryRepository.findByName(categoryName)
                    .orElseGet(() -> categoryRepository.save(Category.builder()
                            .name(categoryName)
                            .slug(categoryName.toLowerCase().replace(" ", "-"))
                            .build()));

            Brand brand = brandRepository.findByName(brandName)
                    .orElseGet(() -> brandRepository.save(Brand.builder()
                            .name(brandName)
                            .build()));

            String productName = (String) rawProduct.get("name");
            Product product = productRepository.findByName(productName).orElse(null);
            if (product == null) {
                product = Product.builder()
                        .name(productName)
                        .description((String) rawProduct.get("description"))
                        .category(category)
                        .brand(brand)
                        .build();
                product = productRepository.save(product);
            }

            List<Map<String, Object>> rawVariants = (List<Map<String, Object>>) rawProduct.get("variants");
            for (Map<String, Object> rawVariant : rawVariants) {
                String sku = (String) rawVariant.get("sku");
                if (variantRepository.findBySku(sku).isPresent()) {
                    continue; // Skip if already exists
                }

                ProductVariant variant = ProductVariant.builder()
                        .product(product)
                        .sku(sku)
                        .price(((Number) rawVariant.get("price")).doubleValue())
                        .color((String) rawVariant.get("color"))
                        .specsJson((Map<String, Object>) rawVariant.get("specs"))
                        .build();

                variant = variantRepository.save(variant);

                // Handle Multi-Images
                List<String> imageUrls = (List<String>) rawVariant.get("image_urls");
                if (imageUrls != null) {
                    for (String imageUrl : imageUrls) {
                        if (imageUrl != null && !imageUrl.isBlank()) {
                            productImageRepository.save(ProductImage.builder()
                                    .imageUrl(imageUrl)
                                    .variant(variant)
                                    .build());
                        }
                    }
                }

                // Add default inventory to all branches
                List<Branch> branches = branchRepository.findAll();
                for (Branch branch : branches) {
                    InventoryId inventoryId = new InventoryId(branch.getId(), variant.getId());
                    if (!inventoryRepository.existsById(inventoryId)) {
                        inventoryRepository.save(Inventory.builder()
                                .id(inventoryId)
                                .branch(branch)
                                .variant(variant)
                                .quantity(100) // Default 100 units
                                .build());
                    }
                }
            }
        }
    }
}
