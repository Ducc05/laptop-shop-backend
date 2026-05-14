package com.laptopshop.controller.admin;

import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.dto.BrandDTO;
import com.laptopshop.dto.CategoryDTO;
import com.laptopshop.service.BrandService;
import com.laptopshop.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Catalog", description = "Quản lý Danh mục & Thương hiệu (Catalog)")
public class AdminCatalogController {

    private final CategoryService categoryService;
    private final BrandService brandService;

    // Categories
    @GetMapping("/categories")
    public PageResponseDTO<CategoryDTO> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return categoryService.getCategories(page, size, true);
    }

    @GetMapping("/categories/{id}")
    public CategoryDTO getCategoryById(@PathVariable Long id) {
        return categoryService.getCategoryById(id);
    }

    @PostMapping("/categories")
    public CategoryDTO createCategory(@RequestBody CategoryDTO dto) {
        return categoryService.createCategory(dto);
    }

    @PutMapping("/categories/{id}")
    public CategoryDTO updateCategory(@PathVariable Long id, @RequestBody CategoryDTO dto) {
        return categoryService.updateCategory(id, dto);
    }

    @DeleteMapping("/categories/{id}")
    public void deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
    }

    // Brands
    @GetMapping("/brands")
    public PageResponseDTO<BrandDTO> getAllBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return brandService.getBrands(page, size, true);
    }

    @GetMapping("/brands/{id}")
    public BrandDTO getBrandById(@PathVariable Long id) {
        return brandService.getBrandById(id);
    }

    @PostMapping("/brands")
    public BrandDTO createBrand(@RequestBody BrandDTO dto) {
        return brandService.createBrand(dto);
    }

    @PutMapping("/brands/{id}")
    public BrandDTO updateBrand(@PathVariable Long id, @RequestBody BrandDTO dto) {
        return brandService.updateBrand(id, dto);
    }

    @DeleteMapping("/brands/{id}")
    public void deleteBrand(@PathVariable Long id) {
        brandService.deleteBrand(id);
    }
}
