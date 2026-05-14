package com.laptopshop.controller.admin;

import com.laptopshop.dto.DashboardStatsDTO;
import com.laptopshop.dto.PageResponseDTO;
import com.laptopshop.dto.OrderDTO;
import com.laptopshop.entity.OrderStatus;
import com.laptopshop.service.DashboardService;
import com.laptopshop.service.OrderService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Order", description = "Quản lý đơn hàng (Orders)")
public class AdminOrderController {

    private final OrderService orderService;
    private final DashboardService dashboardService;

    @GetMapping
    public PageResponseDTO<OrderDTO> getAllOrders(
            @org.springdoc.core.annotations.ParameterObject com.laptopshop.dto.OrderFilterRequest filterRequest,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (filterRequest == null) filterRequest = new com.laptopshop.dto.OrderFilterRequest();
        return orderService.getAllOrders(filterRequest, page, size);
    }

    @GetMapping("/{id}")
    public OrderDTO getOrderDetail(@PathVariable Long id) {
        return orderService.getOrderDetail(id);
    }

    @PutMapping("/{id}/status")
    public void updateOrderStatus(@PathVariable Long id, @RequestParam OrderStatus status) {
        orderService.updateOrderStatus(id, status);
    }

    @GetMapping("/dashboard/stats")
    public DashboardStatsDTO getGlobalStats() {
        return dashboardService.getStats(null);
    }
}
