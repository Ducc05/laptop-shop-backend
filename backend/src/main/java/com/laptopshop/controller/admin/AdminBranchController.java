package com.laptopshop.controller.admin;

import com.laptopshop.dto.BranchDTO;
import com.laptopshop.dto.DashboardStatsDTO;
import com.laptopshop.dto.LowStockDTO;
import com.laptopshop.service.BranchService;
import com.laptopshop.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/branches")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Branch", description = "Quản lý chi nhánh cửa hàng (Branches)")
public class AdminBranchController {

    private final BranchService branchService;
    private final DashboardService dashboardService;

    @GetMapping
    public List<BranchDTO> getAllBranches() {
        return branchService.getAllBranches();
    }

    @GetMapping("/{id}")
    public BranchDTO getBranchById(@PathVariable Long id) {
        return branchService.getBranchById(id);
    }

    @PostMapping
    public BranchDTO createBranch(@RequestBody BranchDTO dto) {
        return branchService.createBranch(dto);
    }

    @PutMapping("/{id}")
    public BranchDTO updateBranch(@PathVariable Long id, @RequestBody BranchDTO dto) {
        return branchService.updateBranch(id, dto);
    }

    @DeleteMapping("/{id}")
    public void deleteBranch(@PathVariable Long id) {
        branchService.deleteBranch(id);
    }

    @GetMapping("/{id}/dashboard")
    public DashboardStatsDTO getBranchDashboard(@PathVariable Long id,
                                                 @RequestParam(required = false) String month) {
        return dashboardService.getStats(id, parseDashboardMonth(month));
    }

    @GetMapping("/{id}/low-stock")
    public List<LowStockDTO> getBranchLowStock(@PathVariable Long id) {
        return dashboardService.getLowStockAlerts(id);
    }

    private YearMonth parseDashboardMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ignored) {
            return YearMonth.now();
        }
    }
}
