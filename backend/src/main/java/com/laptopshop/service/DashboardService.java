package com.laptopshop.service;

import com.laptopshop.dto.DashboardBranchRevenueDTO;
import com.laptopshop.dto.DashboardRevenuePointDTO;
import com.laptopshop.dto.DashboardStatsDTO;
import com.laptopshop.dto.DashboardTopCustomerDTO;
import com.laptopshop.dto.DashboardTopProductDTO;
import com.laptopshop.dto.LowStockDTO;
import com.laptopshop.entity.Inventory;
import com.laptopshop.entity.Order;
import com.laptopshop.entity.OrderItem;
import com.laptopshop.entity.OrderStatus;
import com.laptopshop.entity.ProductVariant;
import com.laptopshop.entity.User;
import com.laptopshop.repository.InventoryRepository;
import com.laptopshop.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;

    public DashboardStatsDTO getStats(Long branchId) {
        return getStats(branchId, YearMonth.now());
    }

    public DashboardStatsDTO getStats(Long branchId, YearMonth selectedMonth) {
        List<Order> orders;
        if (branchId != null) {
            orders = orderRepository.findByBranchId(branchId);
        } else {
            orders = orderRepository.findAll();
        }

        long totalOrders = orders.size();
        double totalRevenue = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .mapToDouble(o -> Objects.requireNonNullElse(o.getTotalPrice(), 0D))
                .sum();
        
        long successfulOrders = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .count();

        List<Order> deliveredOrdersThisMonth = orders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .filter(o -> isInMonth(o, selectedMonth))
                .toList();

        Map<String, Double> revenueByStatus = deliveredOrdersThisMonth.stream()
                .collect(Collectors.groupingBy(o -> o.getStatus().name(),
                        Collectors.summingDouble(o -> Objects.requireNonNullElse(o.getTotalPrice(), 0D))));

        double monthlyRevenue = deliveredOrdersThisMonth.stream()
                .mapToDouble(o -> Objects.requireNonNullElse(o.getTotalPrice(), 0D))
                .sum();

        List<DashboardRevenuePointDTO> revenueByDayThisMonth = buildRevenueByDay(deliveredOrdersThisMonth);
        List<DashboardBranchRevenueDTO> revenueByBranchThisMonth = buildRevenueByBranch(deliveredOrdersThisMonth);
        List<DashboardTopProductDTO> topProductsThisMonth = buildTopProducts(deliveredOrdersThisMonth);
        List<DashboardTopCustomerDTO> topCustomersThisMonth = buildTopCustomers(deliveredOrdersThisMonth);

        return DashboardStatsDTO.builder()
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .successfulOrders(successfulOrders)
                .revenueByStatus(revenueByStatus)
                .monthlyRevenue(monthlyRevenue)
                .monthlyOrders(deliveredOrdersThisMonth.size())
                .revenueByDayThisMonth(revenueByDayThisMonth)
                .revenueByBranchThisMonth(revenueByBranchThisMonth)
                .topProductsThisMonth(topProductsThisMonth)
                .topCustomersThisMonth(topCustomersThisMonth)
                .build();
    }

    private boolean isInMonth(Order order, YearMonth month) {
        LocalDateTime revenueRecordedAt = getRevenueRecordedAt(order);
        if (revenueRecordedAt == null) return false;

        LocalDateTime startOfMonth = month.atDay(1).atStartOfDay();
        LocalDateTime startOfNextMonth = month.plusMonths(1).atDay(1).atStartOfDay();
        return !revenueRecordedAt.isBefore(startOfMonth) && revenueRecordedAt.isBefore(startOfNextMonth);
    }

    private LocalDateTime getRevenueRecordedAt(Order order) {
        if (order.getStatus() == OrderStatus.DELIVERED && order.getUpdatedAt() != null) {
            return order.getUpdatedAt();
        }

        return order.getCreatedAt();
    }

    private List<DashboardRevenuePointDTO> buildRevenueByDay(List<Order> orders) {
        Map<LocalDate, List<Order>> ordersByDay = orders.stream()
                .filter(order -> getRevenueRecordedAt(order) != null)
                .collect(Collectors.groupingBy(
                        order -> getRevenueRecordedAt(order).toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        return ordersByDay.entrySet().stream()
                .map(entry -> DashboardRevenuePointDTO.builder()
                        .date(entry.getKey().toString())
                        .revenue(entry.getValue().stream()
                                .mapToDouble(order -> Objects.requireNonNullElse(order.getTotalPrice(), 0D))
                                .sum())
                        .orders((long) entry.getValue().size())
                        .build())
                .toList();
    }

    private List<DashboardBranchRevenueDTO> buildRevenueByBranch(List<Order> orders) {
        Map<Long, BranchRevenueAccumulator> branches = new HashMap<>();

        orders.stream()
                .filter(order -> order.getBranch() != null)
                .forEach(order -> {
                    Long branchId = order.getBranch().getId();
                    BranchRevenueAccumulator acc = branches.computeIfAbsent(
                            branchId,
                            ignored -> new BranchRevenueAccumulator(branchId, order.getBranch().getName())
                    );
                    acc.orders += 1;
                    acc.revenue += Objects.requireNonNullElse(order.getTotalPrice(), 0D);
                });

        return branches.values().stream()
                .sorted(Comparator.comparingDouble((BranchRevenueAccumulator acc) -> acc.revenue).reversed())
                .map(acc -> DashboardBranchRevenueDTO.builder()
                        .branchId(acc.branchId)
                        .branchName(acc.branchName)
                        .revenue(acc.revenue)
                        .orders(acc.orders)
                        .build())
                .toList();
    }

    private List<DashboardTopProductDTO> buildTopProducts(List<Order> orders) {
        Map<Long, ProductAccumulator> products = new HashMap<>();

        orders.stream()
                .filter(order -> order.getItems() != null)
                .flatMap(order -> order.getItems().stream())
                .filter(item -> item.getVariant() != null && item.getVariant().getProduct() != null)
                .forEach(item -> {
                    ProductVariant variant = item.getVariant();
                    Long productId = variant.getProduct().getId();
                    ProductAccumulator acc = products.computeIfAbsent(productId, ignored -> new ProductAccumulator(
                            productId,
                            variant.getProduct().getName(),
                            variant.getId(),
                            variant.getSku()
                    ));

                    long quantity = item.getQuantity() == null ? 0L : item.getQuantity();
                    double revenue = (item.getPrice() == null ? 0D : item.getPrice()) * quantity;
                    acc.quantitySold += quantity;
                    acc.revenue += revenue;
                });

        return products.values().stream()
                .sorted(Comparator.comparingLong((ProductAccumulator acc) -> acc.quantitySold).reversed())
                .limit(5)
                .map(acc -> DashboardTopProductDTO.builder()
                        .productId(acc.productId)
                        .productName(acc.productName)
                        .variantId(acc.variantId)
                        .sku(acc.sku)
                        .quantitySold(acc.quantitySold)
                        .revenue(acc.revenue)
                        .build())
                .toList();
    }

    private List<DashboardTopCustomerDTO> buildTopCustomers(List<Order> orders) {
        Map<Long, CustomerAccumulator> customers = new HashMap<>();

        orders.stream()
                .filter(order -> order.getUser() != null)
                .forEach(order -> {
                    User user = order.getUser();
                    CustomerAccumulator acc = customers.computeIfAbsent(user.getId(), ignored -> new CustomerAccumulator(user));
                    acc.orderCount += 1;
                    acc.totalSpent += Objects.requireNonNullElse(order.getTotalPrice(), 0D);
                });

        return customers.values().stream()
                .sorted(Comparator.comparingDouble((CustomerAccumulator acc) -> acc.totalSpent).reversed())
                .limit(5)
                .map(acc -> DashboardTopCustomerDTO.builder()
                        .userId(acc.userId)
                        .username(acc.username)
                        .fullName(acc.fullName)
                        .email(acc.email)
                        .orderCount(acc.orderCount)
                        .totalSpent(acc.totalSpent)
                        .build())
                .toList();
    }

    public List<LowStockDTO> getLowStockAlerts(Long branchId) {
        List<Inventory> lowStockItems;
        if (branchId != null) {
            lowStockItems = inventoryRepository.findByBranchIdAndQuantityLessThan(branchId, 5);
        } else {
            lowStockItems = inventoryRepository.findByQuantityLessThan(5);
        }

        return lowStockItems.stream()
                .map(item -> LowStockDTO.builder()
                        .branchId(item.getBranch().getId())
                        .branchName(item.getBranch().getName())
                        .variantId(item.getVariant().getId())
                        .sku(item.getVariant().getSku())
                        .productName(item.getVariant().getProduct().getName())
                        .quantity(item.getQuantity())
                        .build())
                .collect(Collectors.toList());
    }

    private static class ProductAccumulator {
        private final Long productId;
        private final String productName;
        private final Long variantId;
        private final String sku;
        private long quantitySold;
        private double revenue;

        private ProductAccumulator(Long productId, String productName, Long variantId, String sku) {
            this.productId = productId;
            this.productName = productName;
            this.variantId = variantId;
            this.sku = sku;
        }
    }

    private static class CustomerAccumulator {
        private final Long userId;
        private final String username;
        private final String fullName;
        private final String email;
        private long orderCount;
        private double totalSpent;

        private CustomerAccumulator(User user) {
            this.userId = user.getId();
            this.username = user.getUsername();
            this.fullName = user.getFullName();
            this.email = user.getEmail();
        }
    }

    private static class BranchRevenueAccumulator {
        private final Long branchId;
        private final String branchName;
        private long orders;
        private double revenue;

        private BranchRevenueAccumulator(Long branchId, String branchName) {
            this.branchId = branchId;
            this.branchName = branchName;
        }
    }
}
