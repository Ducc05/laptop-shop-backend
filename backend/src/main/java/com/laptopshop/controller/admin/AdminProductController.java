package com.laptopshop.controller.admin;

import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.dto.ProductDTO;
import com.laptopshop.dto.ProductFilterRequest;
import com.laptopshop.dto.ProductImageDTO;
import com.laptopshop.entity.ProductImage;
import com.laptopshop.entity.ProductVariant;
import com.laptopshop.repository.ProductImageRepository;
import com.laptopshop.repository.ProductVariantRepository;
import com.laptopshop.service.DataImportService;
import com.laptopshop.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Product", description = "Quản lý sản phẩm (Products)")
public class AdminProductController {

    private final ProductService productService;
    private final DataImportService importService;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;

    @GetMapping
    public PageResponseDTO<ProductDTO> getAllProducts(
            @org.springdoc.core.annotations.ParameterObject ProductFilterRequest filterRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (filterRequest == null) filterRequest = new ProductFilterRequest();
        return productService.getProducts(filterRequest, page, size, true);
    }

    @GetMapping("/{id}")
    public ProductDTO getProductById(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    @PostMapping
    public ProductDTO createProduct(@RequestBody ProductDTO dto) {
        return productService.createProduct(dto);
    }

    @PostMapping("/import")
    public ResponseEntity<String> importData() throws java.io.IOException {
        importService.importData();
        return ResponseEntity.ok("Data imported successfully");
    }

    @PutMapping("/{id}")
    public ProductDTO updateProduct(@PathVariable Long id, @RequestBody ProductDTO dto) {
        return productService.updateProduct(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
    }

    @PostMapping("/variants/{variantId}/images")
    public ResponseEntity<ProductImageDTO> addProductImage(@PathVariable Long variantId, @RequestParam String imageUrl) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new RuntimeException("Variant not found"));
        
        ProductImage image = ProductImage.builder()
                .imageUrl(imageUrl)
                .variant(variant)
                .build();
        
        ProductImage saved = productImageRepository.save(image);
        return ResponseEntity.ok(ProductImageDTO.builder()
                .id(saved.getId())
                .imageUrl(saved.getImageUrl())
                .build());
    }
}
