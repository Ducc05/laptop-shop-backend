package com.laptopshop.repository.specification;

import com.laptopshop.dto.ProductFilterRequest;
import com.laptopshop.entity.Product;
import com.laptopshop.entity.ProductVariant;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ProductSpecification {
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
                    predicates.add(createJsonFilter(criteriaBuilder, variants, "CPU", request.getCpu()));
                }
                if (request.getRam() != null && !request.getRam().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, "RAM", request.getRam()));
                }
                if (request.getStorage() != null && !request.getStorage().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, "Ổ cứng", request.getStorage()));
                }
                if (request.getGpu() != null && !request.getGpu().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, "Card đồ họa", request.getGpu()));
                }
                if (request.getScreen() != null && !request.getScreen().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, "Màn hình", request.getScreen()));
                }
                if (request.getOs() != null && !request.getOs().isEmpty()) {
                    predicates.add(createJsonFilter(criteriaBuilder, variants, "Hệ điều hành", request.getOs()));
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
        return cb.like(cb.lower(unquote), "%" + value.toLowerCase() + "%");
    }
}
