package com.laptopshop.controller.manager;

import com.laptopshop.dto.OrderDTO;
import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.entity.OrderStatus;
import com.laptopshop.security.SecurityUtils;
import com.laptopshop.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
@Tag(name = "Order", description = "Quản lý đơn hàng (Orders)")
public class ManagerOrderController {

    private final OrderService orderService;

    @GetMapping
    public PageResponseDTO<OrderDTO> getBranchOrders(
            @org.springdoc.core.annotations.ParameterObject com.laptopshop.dto.OrderFilterRequest filterRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (filterRequest == null) filterRequest = new com.laptopshop.dto.OrderFilterRequest();
        filterRequest.setBranchId(SecurityUtils.getCurrentBranchId());
        return orderService.getAllOrders(filterRequest, page, size);
    }

    @GetMapping("/{id}")
    public OrderDTO getOrderDetail(@PathVariable Long id) {
        return orderService.getBranchOrderDetail(id, SecurityUtils.getCurrentBranchId());
    }

    @PutMapping("/{id}/status")
    public void updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        orderService.updateOrderStatus(id, status);
    }
}
