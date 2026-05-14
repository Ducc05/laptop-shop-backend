package com.laptopshop.repository;

import com.laptopshop.entity.OrderStatus;
import com.laptopshop.entity.OrderItem;
import com.laptopshop.entity.Product;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
	@Query("""
			select variant.product
			from OrderItem item
			join item.variant variant
			join item.order order
			where variant.product.enabled = true
			  and order.status not in :excludedStatuses
			group by variant.product
			order by sum(item.quantity) desc
			""")
	List<Product> findBestSellingProducts(
			@Param("excludedStatuses") List<OrderStatus> excludedStatuses,
			Pageable pageable);

	@Transactional
	@Modifying
	void deleteByVariantId(Long variantId);
}
