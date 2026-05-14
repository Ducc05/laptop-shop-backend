package com.laptopshop.mapper;

import com.laptopshop.dto.OrderItemDTO;
import com.laptopshop.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderItemMapper {
    @Mapping(target = "variantId", source = "variant.id")
    @Mapping(target = "productName", source = "variant.product.name")
    @Mapping(target = "sku", source = "variant.sku")
    @Mapping(target = "imageUrl", expression = "java(orderItem.getVariant() != null && orderItem.getVariant().getImages() != null && !orderItem.getVariant().getImages().isEmpty() ? orderItem.getVariant().getImages().get(0).getImageUrl() : null)")
    OrderItemDTO toDto(OrderItem orderItem);
}
