package com.laptopshop.controller.manager;

import com.laptopshop.dto.BrandDTO;
import com.laptopshop.dto.CategoryDTO;
import com.laptopshop.service.BrandService;
import com.laptopshop.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/manager/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Manager Catalog", description = "Xem chi tiết danh mục và thương hiệu")
public class ManagerCatalogController {

    private final CategoryService categoryService;
    private final BrandService brandService;

    @GetMapping("/categories/{id}")
    public CategoryDTO getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id);
    }

    @GetMapping("/brands/{id}")
    public BrandDTO getBrandById(@PathVariable Long id) {
        return brandService.getBrandById(id);
    }
}
