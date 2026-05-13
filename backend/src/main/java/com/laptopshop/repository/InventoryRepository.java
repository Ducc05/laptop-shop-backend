package com.laptopshop.repository;

import com.laptopshop.entity.Inventory;
import com.laptopshop.entity.InventoryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface InventoryRepository extends JpaRepository<Inventory, InventoryId> {
    List<Inventory> findByBranchIdAndQuantityLessThan(Long branchId, Integer quantity);
    List<Inventory> findByVariantId(Long variantId);
    List<Inventory> findByQuantityLessThan(Integer quantity);

    @Transactional
    @Modifying
    void deleteByVariantId(Long variantId);
}
