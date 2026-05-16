package com.laptopshop.repository.specification;

import com.laptopshop.dto.ProductFilterRequest;
import com.laptopshop.entity.Product;
import com.laptopshop.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {
    private static final List<String> CPU_KEYS = List.of("cpu", "CPU");
    private static final List<String> RAM_KEYS = List.of("ram", "RAM");
    private static final List<String> STORAGE_KEYS = List.of(
            "storage",
            "Storage",
            "rom",
            "ROM",
            "\u1ed5 c\u1ee9ng",
            "\u1ed4 c\u1ee9ng",
            "\u1ed5 \u0111\u0129a c\u1ee9ng - SSD",
            "\u1ed4 \u0111\u0129a c\u1ee9ng - SSD",
            "\u1ed5 \u0111\u0129a c\u1ee9ng - HDD",
            "\u1ed4 \u0111\u0129a c\u1ee9ng - HDD",
            "Dung l\u01b0\u1ee3ng",
            "Dung l\u01b0\u1ee3ng \u1ed5 c\u1ee9ng",
            "Hard Drive",
            "SSD",
            "HDD");
    private static final List<String> GPU_KEYS = List.of("gpu", "GPU", "vga", "VGA", "Card \u0111\u1ed3 h\u1ecda");
    private static final List<String> SCREEN_KEYS = List.of("screen", "Screen", "M\u00e0n h\u00ecnh");
    private static final List<String> OS_KEYS = List.of("os", "OS", "H\u1ec7 \u0111i\u1ec1u h\u00e0nh");

    public static Specification<Product> filter(ProductFilterRequest request, boolean isAdmin) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 0. Public/Admin Visibility Filter
            if (!isAdmin) {
                predicates.add(criteriaBuilder.equal(root.get("enabled"), true));
            }

            // 1. Keyword search (Name, Brand, Category) - Normalized
            if (request.getKeyword() != null && !request.getKeyword().isEmpty()) {
                String keyword = request.getKeyword().toLowerCase().trim();

                Predicate nameMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("name")),
                        "%" + keyword + "%");
                Predicate brandMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("brand").get("name")),
                        "%" + keyword + "%");
                Predicate categoryMatch = criteriaBuilder.like(criteriaBuilder.lower(root.get("category").get("name")),
                        "%" + keyword + "%");

                predicates.add(criteriaBuilder.or(nameMatch, brandMatch, categoryMatch));
            }

            // 2. Category filter
            if (request.getCategoryId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("category").get("id"), request.getCategoryId()));
            }

            // 3. Brand filter
            if (request.getBrandId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("brand").get("id"), request.getBrandId()));
            }

            // 4. Variant-related filters (Price, CPU, RAM, Storage)
            if (isVariantFilterActive(request)) {
                Join<Product, ProductVariant> variants = root.join("variants", JoinType.INNER);

                // Price range
                if (request.getMinPrice() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(variants.get("price"), request.getMinPrice()));
                }
                if (request.getMaxPrice() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(variants.get("price"), request.getMaxPrice()));
                }

                // Technical Specs (JSON filtering)
                if (request.getCpu() != null && !request.getCpu().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, CPU_KEYS, request.getCpu()));
                }
                if (request.getRam() != null && !request.getRam().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, RAM_KEYS, request.getRam()));
                }
                if (request.getStorage() != null && !request.getStorage().isEmpty()) {
                    predicates.add(createStorageFilter(criteriaBuilder, variants, request.getStorage()));
                }
                if (request.getGpu() != null && !request.getGpu().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, GPU_KEYS, request.getGpu()));
                }
                if (request.getScreen() != null && !request.getScreen().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, SCREEN_KEYS, request.getScreen()));
                }
                if (request.getOs() != null && !request.getOs().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, OS_KEYS, request.getOs()));
                }

                query.distinct(true);
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static boolean isVariantFilterActive(ProductFilterRequest request) {
        return request.getMinPrice() != null || request.getMaxPrice() != null ||
                (request.getCpu() != null && !request.getCpu().isEmpty()) ||
                (request.getRam() != null && !request.getRam().isEmpty()) ||
                (request.getStorage() != null && !request.getStorage().isEmpty()) ||
                (request.getGpu() != null && !request.getGpu().isEmpty()) ||
                (request.getScreen() != null && !request.getScreen().isEmpty()) ||
                (request.getOs() != null && !request.getOs().isEmpty());
    }

    private static Predicate createJsonFilter(CriteriaBuilder cb, Join<Product, ProductVariant> variants, String key,
            String value) {
        // SQL: JSON_UNQUOTE(JSON_EXTRACT(specs_json, '$."key"')) LIKE '%value%'
        Expression<String> extract = cb.function("JSON_EXTRACT", String.class,
                variants.get("specsJson"), cb.literal("$." + "\"" + key + "\""));
        Expression<String> unquote = cb.function("JSON_UNQUOTE", String.class, extract);
        return cb.like(normalizeSqlText(cb, unquote), "%" + normalizeSearchValue(value) + "%");
    }

    private static Predicate createStorageFilter(CriteriaBuilder cb, Join<Product, ProductVariant> variants,
            String value) {
        String normalizedValue = normalizeSearchValue(value);

        return cb.or(STORAGE_KEYS.stream()
                .map(key -> createStorageFilter(cb, variants, key, normalizedValue))
                .toArray(Predicate[]::new));
    }

    private static Predicate createStorageFilter(CriteriaBuilder cb, Join<Product, ProductVariant> variants, String key,
            String normalizedValue) {
        Expression<String> extract = cb.function("JSON_EXTRACT", String.class,
                variants.get("specsJson"), cb.literal("$." + "\"" + key + "\""));
        Expression<String> normalizedText = normalizeSqlText(cb,
                cb.function("JSON_UNQUOTE", String.class, extract));
        Expression<Integer> position = cb.function("LOCATE", Integer.class,
                cb.literal(normalizedValue), normalizedText);

        return cb.and(
                cb.greaterThan(position, 0),
                cb.lessThanOrEqualTo(position, 16));
    }

    private static Predicate createJsonFilter(CriteriaBuilder cb, Join<Product, ProductVariant> variants,
            List<String> keys, String value) {
        return cb.or(keys.stream()
                .map(key -> createJsonFilter(cb, variants, key, value))
                .toArray(Predicate[]::new));
    }

    private static Expression<String> normalizeSqlText(CriteriaBuilder cb, Expression<String> expression) {
        Expression<String> lower = cb.lower(expression);
        Expression<String> withoutSpaces = cb.function("REPLACE", String.class,
                lower, cb.literal(" "), cb.literal(""));
        return cb.function("REPLACE", String.class,
                withoutSpaces, cb.literal("\u00A0"), cb.literal(""));
    }

    private static String normalizeSearchValue(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[\\s\\u00A0]+", "");
    }
}
