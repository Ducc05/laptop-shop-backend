package com.laptopshop.repository;

import com.laptopshop.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
	@Transactional
	@Modifying
	void deleteByVariantId(Long variantId);
}
