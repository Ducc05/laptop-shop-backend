package com.laptopshop.service;

import com.laptopshop.dto.OrderDTO;
import com.laptopshop.dto.OrderItemDTO;
import com.laptopshop.dto.OrderItemRequest;
import com.laptopshop.dto.OrderRequest;
import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.entity.*;
import com.laptopshop.exception.ResourceNotFoundException;
import com.laptopshop.repository.*;
import com.laptopshop.repository.specification.OrderSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryRepository inventoryRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final VoucherService voucherService;
    private final CartService cartService;
    private final PaymentRepository paymentRepository;
    private final InventoryLogRepository inventoryLogRepository;

    @Transactional
    public OrderDTO createOrder(OrderRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Branch branch = branchRepository.findById(request.getBranchId())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));

        Order order = Order.builder()
                .user(user)
                .branch(branch)
                .status(OrderStatus.PENDING)
                .totalPrice(0.0)
                .items(new ArrayList<>())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        double total = 0;
        for (OrderItemRequest itemReq : request.getItems()) {
            ProductVariant variant = variantRepository.findById(itemReq.getVariantId())
                    .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

            // 1. Check inventory
            InventoryId inventoryId = new InventoryId(branch.getId(), variant.getId());
            Inventory inventory = inventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RuntimeException("Item not in stock for this branch: " + variant.getSku()));

            if (inventory.getQuantity() < itemReq.getQuantity()) {
                throw new RuntimeException("Insufficient stock for SKU: " + variant.getSku());
            }

            // 2. Deduct inventory
            Integer oldQuantity = inventory.getQuantity();
            inventory.setQuantity(oldQuantity - itemReq.getQuantity());
            inventoryRepository.save(inventory);

            // Log inventory change
            inventoryLogRepository.save(InventoryLog.builder()
                    .branch(branch)
                    .variant(variant)
                    .oldQuantity(oldQuantity)
                    .newQuantity(inventory.getQuantity())
                    .action("ORDER_PLACEMENT")
                    .build());

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .variant(variant)
                    .quantity(itemReq.getQuantity())
                    .price(variant.getPrice())
                    .build();

            order.getItems().add(orderItem);
            total += variant.getPrice() * itemReq.getQuantity();
        }

        // Apply voucher if provided
        if (request.getVoucherCode() != null && !request.getVoucherCode().isEmpty()) {
            Voucher voucher = voucherService.validateVoucher(request.getVoucherCode(), total);
            double discount = calculateDiscount(voucher, total);
            
            order.setVoucher(voucher);
            order.setDiscountAmount(discount);
            total -= discount;
            
            voucherService.useVoucher(voucher);
        }

        order.setTotalPrice(total);
        Order savedOrder = orderRepository.save(order);

        // 3. Initialize first Payment (if not COD, but for now we set up the entity)
        PaymentMethod method = request.getPaymentMethod() != null ? 
                PaymentMethod.valueOf(request.getPaymentMethod()) : PaymentMethod.COD;
        
        savedOrder.setPaymentMethod(method);
        
        Payment payment = Payment.builder()
                .order(savedOrder)
                .amount(total)
                .method(method)
                .status(PaymentStatus.PENDING)
                .build();
        paymentRepository.save(payment);

        // 4. Clear Cart
        cartService.clearCart(userId);

        return mapToDTO(savedOrder);
    }

    private double calculateDiscount(Voucher voucher, double total) {
        double discount = 0;
        if (voucher.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = total * (voucher.getDiscountValue() / 100);
            if (voucher.getMaxDiscountValue() != null && discount > voucher.getMaxDiscountValue()) {
                discount = voucher.getMaxDiscountValue();
            }
        } else {
            discount = voucher.getDiscountValue();
        }
        return Math.min(discount, total);
    }

    public PageResponseDTO<OrderDTO> getAllOrders(com.laptopshop.dto.OrderFilterRequest filter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("id").descending());
        Specification<Order> spec = OrderSpecification.filter(
                filter.getStatus() != null ? filter.getStatus().name() : null, 
                filter.getBranchId(), 
                filter.getUserId()
        );
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);
        return PageResponseDTO.of(orderPage.map(this::mapToDTO));
    }

    @PreAuthorize("hasRole('ADMIN') or (hasRole('MANAGER') and @orderService.isOrderInBranch(#orderId, principal.branchId))")
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        
        validateStatusTransition(order.getStatus(), status);
        
        if (status == OrderStatus.CANCELLED) {
            restoreInventory(order, "ORDER_CANCELLATION_BY_ADMIN");
        }
        
        order.setStatus(status);
        orderRepository.save(order);
    }

    @Transactional
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found or you don't have permission"));
        
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new RuntimeException("Only PENDING orders can be cancelled by user");
        }
        
        restoreInventory(order, "ORDER_CANCELLATION_BY_USER");
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private void restoreInventory(Order order, String action) {
        for (OrderItem item : order.getItems()) {
            InventoryId inventoryId = new InventoryId(order.getBranch().getId(), item.getVariant().getId());
            Inventory inventory = inventoryRepository.findById(inventoryId)
                    .orElseThrow(() -> new RuntimeException("Inventory not found for SKU: " + item.getVariant().getSku()));
            
            Integer oldQuantity = inventory.getQuantity();
            inventory.setQuantity(oldQuantity + item.getQuantity());
            inventoryRepository.save(inventory);

            // Log inventory change
            inventoryLogRepository.save(InventoryLog.builder()
                    .branch(order.getBranch())
                    .variant(item.getVariant())
                    .oldQuantity(oldQuantity)
                    .newQuantity(inventory.getQuantity())
                    .action(action)
                    .build());
        }
    }

    private void validateStatusTransition(OrderStatus current, OrderStatus next) {
        if (current == OrderStatus.CANCELLED || current == OrderStatus.DELIVERED || current == OrderStatus.FAILED) {
            throw new RuntimeException("Cannot change status from " + current);
        }
        
        boolean valid = switch (current) {
            case PENDING -> next == OrderStatus.CONFIRMED || next == OrderStatus.CANCELLED || next == OrderStatus.FAILED;
            case CONFIRMED -> next == OrderStatus.SHIPPING || next == OrderStatus.CANCELLED;
            case SHIPPING -> next == OrderStatus.DELIVERED || next == OrderStatus.FAILED;
            default -> false;
        };

        if (!valid) {
            throw new RuntimeException("Invalid status transition from " + current + " to " + next);
        }
    }

    public boolean isOrderInBranch(Long orderId, Long branchId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        return order != null && order.getBranch().getId().equals(branchId);
    }

    public OrderDTO getMyOrderDetail(Long orderId, Long userId) {
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                .orElseThrow(() -> new AccessDeniedException("You do not have permission to view this order or order not found"));
        return mapToDTO(order);
    }

    public OrderDTO getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        return mapToDTO(order);
    }

    public OrderDTO getBranchOrderDetail(Long orderId, Long branchId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        if (branchId == null || !order.getBranch().getId().equals(branchId)) {
            throw new AccessDeniedException("You do not have permission to view this order");
        }

        return mapToDTO(order);
    }

    private OrderDTO mapToDTO(Order order) {
        return OrderDTO.builder()
                .id(order.getId())
                .userId(order.getUser().getId())
                .branchId(order.getBranch().getId())
                .status(order.getStatus().name())
                .paymentMethod(order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null)
                .paymentStatus(order.getPaymentStatus() != null ? order.getPaymentStatus().name() : null)
                .totalPrice(order.getTotalPrice())
                .discountAmount(order.getDiscountAmount())
                .voucherCode(order.getVoucher() != null ? order.getVoucher().getCode() : null)
                .createdAt(order.getCreatedAt())
                .items(order.getItems().stream()
                        .map(item -> OrderItemDTO.builder()
                                .id(item.getId())
                                .variantId(item.getVariant().getId())
                                .productName(item.getVariant().getProduct().getName())
                                .sku(item.getVariant().getSku())
                                .quantity(item.getQuantity())
                                .price(item.getPrice())
                                .build())
                        .toList())
                .build();
    }
}
