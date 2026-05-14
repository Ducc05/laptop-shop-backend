package com.laptopshop.service;

import com.laptopshop.dto.CartDTO;
import com.laptopshop.dto.CartItemDTO;
import com.laptopshop.entity.*;
import com.laptopshop.exception.ResourceNotFoundException;
import com.laptopshop.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository productImageRepository;
    private final UserRepository userRepository;

    public CartDTO getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return convertToDTO(cart);
    }

    @Transactional
    public CartDTO addToCart(Long userId, Long variantId, Integer quantity) {
        Cart cart = getOrCreateCart(userId);
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        CartItem cartItem = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variantId)
                .orElse(null);

        if (cartItem != null) {
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        } else {
            cartItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(quantity)
                    .price(variant.getPrice()) // Snapshot price
                    .build();
            cart.getItems().add(cartItem);
        }

        cartItemRepository.save(cartItem);
        return convertToDTO(cart);
    }

    @Transactional
    public CartDTO updateQuantity(Long userId, Long cartItemId, Integer quantity) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!cartItem.getCart().getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to update this cart item");
        }

        if (quantity <= 0) {
            cartItemRepository.delete(cartItem);
        } else {
            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        }

        return convertToDTO(cartItem.getCart());
    }

    @Transactional
    public CartDTO removeFromCart(Long userId, Long cartItemId) {
        CartItem cartItem = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart item not found"));

        if (!cartItem.getCart().getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized to remove this cart item");
        }

        cartItemRepository.delete(cartItem);
        return convertToDTO(cartItem.getCart());
    }

    @Transactional
    public void clearCart(Long userId) {
        Cart cart = cartRepository.findByUserId(userId).orElse(null);
        if (cart != null) {
            cart.getItems().clear();
            cartRepository.save(cart);
        }
    }

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    Cart newCart = Cart.builder()
                            .user(user)
                            .items(new ArrayList<>())
                            .build();
                    return cartRepository.save(newCart);
                });
    }

    private CartDTO convertToDTO(Cart cart) {
        List<CartItemDTO> itemDTOs = cart.getItems().stream()
                .map(item -> {
                    String imageUrl = productImageRepository.findByVariantId(item.getVariant().getId()).stream()
                            .findFirst()
                            .map(ProductImage::getImageUrl)
                            .orElse(null);

                    return CartItemDTO.builder()
                            .id(item.getId())
                            .variantId(item.getVariant().getId())
                            .productName(item.getVariant().getProduct().getName())
                            .variantSku(item.getVariant().getSku())
                            .imageUrl(imageUrl)
                            .quantity(item.getQuantity())
                            .price(item.getVariant().getPrice()) // Current price
                            .snapshotPrice(item.getPrice()) // Snapshot price when added
                            .build();
                })
                .collect(Collectors.toList());

        double total = itemDTOs.stream()
                .mapToDouble(item -> item.getSnapshotPrice() * item.getQuantity())
                .sum();

        return CartDTO.builder()
                .id(cart.getId())
                .items(itemDTOs)
                .totalPrice(total)
                .build();
    }
}
