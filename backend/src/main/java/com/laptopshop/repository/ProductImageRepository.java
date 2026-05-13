package com.laptopshop.repository;

import com.laptopshop.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {
    List<ProductImage> findByVariantId(Long variantId);

    @Transactional
    @Modifying
    void deleteByVariantId(Long variantId);
}
