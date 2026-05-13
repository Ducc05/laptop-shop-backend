package com.laptopshop.repository;

import com.laptopshop.entity.InventoryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface InventoryLogRepository extends JpaRepository<InventoryLog, Long> {
	@Transactional
	@Modifying
	void deleteByVariantId(Long variantId);
}
