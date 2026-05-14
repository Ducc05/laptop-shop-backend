package com.laptopshop.service;

import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.dto.ProductDTO;
import com.laptopshop.dto.ProductFilterRequest;
import com.laptopshop.dto.InventoryDTO;
import com.laptopshop.dto.ProductImageDTO;
import com.laptopshop.dto.ProductVariantDTO;
import com.laptopshop.entity.Brand;
import com.laptopshop.entity.Branch;
import com.laptopshop.entity.Category;
import com.laptopshop.entity.Inventory;
import com.laptopshop.entity.InventoryId;
import com.laptopshop.entity.OrderStatus;
import com.laptopshop.entity.Product;
import com.laptopshop.entity.ProductImage;
import com.laptopshop.entity.ProductVariant;
import com.laptopshop.mapper.ProductMapper;
import com.laptopshop.repository.CartItemRepository;
import com.laptopshop.repository.BrandRepository;
import com.laptopshop.repository.BranchRepository;
import com.laptopshop.repository.CategoryRepository;
import com.laptopshop.repository.InventoryRepository;
import com.laptopshop.repository.InventoryLogRepository;
import com.laptopshop.repository.OrderItemRepository;
import com.laptopshop.repository.ProductImageRepository;
import com.laptopshop.repository.ProductRepository;
import com.laptopshop.repository.ProductVariantRepository;
import com.laptopshop.repository.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryLogRepository inventoryLogRepository;
    private final BranchRepository branchRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductMapper productMapper;

    // tìm kiếm gợi ý
    public List<String> getSuggestions(String query) {
        String trimmedQuery = query.trim();
        List<String> suggestions = new java.util.ArrayList<>();

        // Tìm tên sp phù hợp
        productRepository.findTop10ByNameContainingIgnoreCase(trimmedQuery)
                .forEach(p -> suggestions.add(p.getName()));

        // Tìm tên thương hiệu phù hợp
        brandRepository.findTop10ByNameContainingIgnoreCase(trimmedQuery)
                .forEach(b -> suggestions.add(b.getName()));

        // Tìm tên danh mục phù hợp
        categoryRepository.findTop10ByNameContainingIgnoreCase(trimmedQuery)
                .forEach(c -> suggestions.add(c.getName()));

        return suggestions.stream().distinct().limit(10).toList();
    }

    @Transactional(readOnly = true)
    public PageResponseDTO<ProductDTO> getProducts(ProductFilterRequest request, int page, int size, boolean isAdmin) {
        Sort sort = Sort.by(Sort.Direction.fromString(request.getSortDir() != null ? request.getSortDir() : "DESC"),
                request.getSortBy() != null ? request.getSortBy() : "id");
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<Product> spec = ProductSpecification.filter(request, isAdmin);
        Page<Product> productPage = productRepository.findAll(spec, pageable);

        Page<ProductDTO> dtoPage = productPage.map(product -> {
            ProductDTO dto = productMapper.toDto(product);
            
            // Populate ONLY THE FIRST image at product level for performance (Avoid N+1)
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                ProductVariant firstVariant = product.getVariants().get(0);
                if (firstVariant.getImages() != null && !firstVariant.getImages().isEmpty()) {
                    dto.setImages(java.util.List.of(firstVariant.getImages().get(0).getImageUrl()));
                } else {
                    dto.setImages(java.util.List.of());
                }
            } else {
                dto.setImages(java.util.List.of());
            }
            return dto;
        });

        return PageResponseDTO.of(dtoPage);
    }

    @Transactional(readOnly = true)
    public List<ProductDTO> getBestSellingProducts(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 12));
        Pageable pageable = PageRequest.of(0, safeLimit);
        List<Product> products = orderItemRepository.findBestSellingProducts(
                List.of(OrderStatus.CANCELLED, OrderStatus.FAILED),
                pageable);

        if (products.isEmpty()) {
            ProductFilterRequest fallbackFilter = new ProductFilterRequest();
            Pageable fallbackPageable = PageRequest.of(0, safeLimit, Sort.by(Sort.Direction.DESC, "id"));
            products = productRepository.findAll(ProductSpecification.filter(fallbackFilter, false), fallbackPageable)
                    .getContent();
        }

        return products.stream().map(this::toDtoWithPrimaryImage).toList();
    }

    @Transactional(readOnly = true)
    public ProductDTO getProduct(Long id) {
        return productRepository.findById(id)
                .map(productMapper::toDto)
                .orElseThrow(() -> new RuntimeException("Product not found"));
    }

    private ProductDTO toDtoWithPrimaryImage(Product product) {
        ProductDTO dto = productMapper.toDto(product);

        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            ProductVariant firstVariant = product.getVariants().get(0);
            if (firstVariant.getImages() != null && !firstVariant.getImages().isEmpty()) {
                dto.setImages(java.util.List.of(firstVariant.getImages().get(0).getImageUrl()));
            } else {
                dto.setImages(java.util.List.of());
            }
        } else {
            dto.setImages(java.util.List.of());
        }

        return dto;
    }

    @Transactional(readOnly = true)
    public List<String> getProductImages(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getVariants() == null) {
            return new ArrayList<>();
        }

        return product.getVariants().stream()
                .filter(variant -> variant.getImages() != null)
                .flatMap(variant -> variant.getImages().stream())
                .map(ProductImage::getImageUrl)
                .distinct()
                .toList();
    }

    @Transactional
    public ProductDTO createProduct(ProductDTO dto) {
        Product product = Product.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .category(resolveCategory(dto))
                .brand(resolveBrand(dto))
                .build();

        Product savedProduct = productRepository.save(product);
        syncVariants(savedProduct, dto.getVariants());
        productRepository.save(savedProduct);
        return getProduct(savedProduct.getId());
    }

    @Transactional
    public ProductDTO updateProduct(Long id, ProductDTO dto) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setName(dto.getName());
        product.setDescription(dto.getDescription());
        product.setCategory(resolveCategory(dto));
        product.setBrand(resolveBrand(dto));
        Product savedProduct = productRepository.save(product);
        syncVariants(savedProduct, dto.getVariants());
        productRepository.save(savedProduct);
        return getProduct(savedProduct.getId());
    }

    @Transactional
    public void deleteProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        List<ProductVariant> variants = product.getVariants() == null ? new ArrayList<>()
                : new ArrayList<>(product.getVariants());
        for (ProductVariant variant : variants) {
            Long variantId = variant.getId();
            if (variantId == null) {
                continue;
            }

            inventoryLogRepository.deleteByVariantId(variantId);
            inventoryRepository.deleteByVariantId(variantId);
            cartItemRepository.deleteByVariantId(variantId);
            orderItemRepository.deleteByVariantId(variantId);
            productImageRepository.deleteByVariantId(variantId);
            productVariantRepository.delete(variant);
        }

        productRepository.delete(product);
    }

    private Category resolveCategory(ProductDTO dto) {
        if (dto.getCategoryId() != null) {
            return categoryRepository.findById(dto.getCategoryId())
                    .orElseThrow(() -> new RuntimeException("Category not found"));
        }
        if (dto.getCategoryName() != null && !dto.getCategoryName().isBlank()) {
            return categoryRepository.findByName(dto.getCategoryName()).orElse(null);
        }
        return null;
    }

    private Brand resolveBrand(ProductDTO dto) {
        if (dto.getBrandId() != null) {
            return brandRepository.findById(dto.getBrandId())
                    .orElseThrow(() -> new RuntimeException("Brand not found"));
        }
        if (dto.getBrandName() != null && !dto.getBrandName().isBlank()) {
            return brandRepository.findByName(dto.getBrandName()).orElse(null);
        }
        return null;
    }

    private void syncVariants(Product product, List<ProductVariantDTO> variantDtos) {
        if (variantDtos == null || variantDtos.isEmpty()) {
            return;
        }

        List<ProductVariant> savedVariants = new ArrayList<>();
        for (ProductVariantDTO variantDto : variantDtos) {
            ProductVariant variant = resolveVariant(product, variantDto);
            variant.setProduct(product);
            variant.setSku(resolveSku(product, variantDto, variant));
            variant.setPrice(variantDto.getPrice() != null ? variantDto.getPrice() : 0D);
            variant.setColor(variantDto.getColor());
            variant.setSpecsJson(variantDto.getSpecsJson());

            ProductVariant savedVariant = productVariantRepository.save(variant);
            syncImages(savedVariant, variantDto.getImages());
            syncInventories(savedVariant, variantDto.getInventories(), variantDto.getQuantity());
            savedVariants.add(savedVariant);
        }
        product.setVariants(savedVariants);
    }

    private ProductVariant resolveVariant(Product product, ProductVariantDTO variantDto) {
        if (variantDto.getId() != null) {
            return productVariantRepository.findById(variantDto.getId())
                    .filter(existing -> Objects.equals(existing.getProduct().getId(), product.getId()))
                    .orElseThrow(() -> new RuntimeException("Variant not found"));
        }
        if (variantDto.getSku() != null && !variantDto.getSku().isBlank()) {
            Optional<ProductVariant> existing = productVariantRepository.findBySku(variantDto.getSku());
            if (existing.isPresent()) {
                ProductVariant variant = existing.get();
                if (!Objects.equals(variant.getProduct().getId(), product.getId())) {
                    throw new RuntimeException("SKU already exists");
                }
                return variant;
            }
        }
        return new ProductVariant();
    }

    private String resolveSku(Product product, ProductVariantDTO variantDto, ProductVariant variant) {
        if (variantDto.getSku() != null && !variantDto.getSku().isBlank()) {
            return variantDto.getSku().trim();
        }
        if (variant.getSku() != null && !variant.getSku().isBlank()) {
            return variant.getSku();
        }
        return "SP-" + product.getId() + "-" + System.currentTimeMillis();
    }

    private void syncImages(ProductVariant variant, List<ProductImageDTO> imageDtos) {
        productImageRepository.deleteAll(productImageRepository.findByVariantId(variant.getId()));
        if (imageDtos == null || imageDtos.isEmpty()) {
            variant.setImages(new ArrayList<>());
            return;
        }

        List<ProductImage> images = imageDtos.stream()
                .map(ProductImageDTO::getImageUrl)
                .filter(url -> url != null && !url.isBlank())
                .distinct()
                .map(url -> ProductImage.builder()
                        .imageUrl(url.trim())
                        .variant(variant)
                        .build())
                .toList();
        variant.setImages(productImageRepository.saveAll(images));
    }

    private void syncInventories(ProductVariant variant, List<InventoryDTO> inventoryDtos, Integer fallbackQuantity) {
        List<Branch> branches = branchRepository.findAll();
        if (branches.isEmpty()) {
            return;
        }

        if (inventoryDtos == null || inventoryDtos.isEmpty()) {
            if (fallbackQuantity == null) {
                return;
            }
            saveInventory(branches.get(0), variant, fallbackQuantity);
            return;
        }

        inventoryRepository.deleteAll(inventoryRepository.findByVariantId(variant.getId()));
        for (InventoryDTO inventoryDto : inventoryDtos) {
            if (inventoryDto.getBranchId() == null || inventoryDto.getQuantity() == null) {
                continue;
            }
            Branch branch = branches.stream()
                    .filter(item -> Objects.equals(item.getId(), inventoryDto.getBranchId()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Branch not found"));
            saveInventory(branch, variant, inventoryDto.getQuantity());
        }
    }

    private void saveInventory(Branch branch, ProductVariant variant, Integer quantity) {
        InventoryId inventoryId = new InventoryId(branch.getId(), variant.getId());
        Inventory inventory = Inventory.builder()
                .id(inventoryId)
                .branch(branch)
                .variant(variant)
                .quantity(Math.max(quantity, 0))
                .build();
        inventoryRepository.save(inventory);
    }
}
