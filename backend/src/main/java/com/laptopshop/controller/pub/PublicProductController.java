package com.laptopshop.controller.pub;

import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.dto.ProductDTO;
import com.laptopshop.dto.ProductFilterRequest;
import com.laptopshop.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/products")
@RequiredArgsConstructor
@Tag(name = "Product", description = "Quản lý sản phẩm (Products)")
public class PublicProductController {

    private final ProductService productService;

    @GetMapping
    public PageResponseDTO<ProductDTO> getProducts(
            @org.springdoc.core.annotations.ParameterObject ProductFilterRequest filterRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (filterRequest == null) filterRequest = new ProductFilterRequest();
        return productService.getProducts(filterRequest, page, size, false);
    }

    @GetMapping("/suggestions")
    public List<String> getSuggestions(@RequestParam String q) {
        return productService.getSuggestions(q);
    }

    @GetMapping("/best-selling")
    public List<ProductDTO> getBestSellingProducts(@RequestParam(defaultValue = "4") int limit) {
        return productService.getBestSellingProducts(limit);
    }

    @GetMapping("/{id}")
    public ProductDTO getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @GetMapping("/{id}/images")
    public List<String> getProductImages(@PathVariable Long id) {
        return productService.getProductImages(id);
    }
}
