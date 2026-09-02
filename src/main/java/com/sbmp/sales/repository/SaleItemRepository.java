package com.sbmp.sales.repository;

import com.sbmp.sales.entity.Sale;
import com.sbmp.sales.entity.SaleItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SaleItemRepository
        extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySale(
            Sale sale
    );

    void deleteBySale(
            Sale sale
    );

    // ─────────────────────────────────────────────
    // Top selling products (dashboard chart-এর জন্য)
    // ─────────────────────────────────────────────
    @Query("""
        SELECT si.product.name, SUM(si.quantity)
        FROM SaleItem si
        WHERE si.sale.business.id = :businessId
        AND si.sale.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
        GROUP BY si.product.name
        ORDER BY SUM(si.quantity) DESC
    """)
    List<Object[]> findTopProductsByBusinessId(
            @Param("businessId") Long businessId,
            Pageable pageable
    );
    // ─────────────────────────────────────────────
    // PROFIT MODULE — product-wise profit/loss
    // ─────────────────────────────────────────────

    @Query("""
        SELECT si.product.id,
               si.product.name,
               SUM(si.quantity),
               SUM(si.itemTotal),
               SUM(si.quantity * COALESCE(si.costPriceAtSale, si.product.purchasePrice))
        FROM SaleItem si
        WHERE si.sale.business.id = :businessId
          AND si.sale.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
          AND si.sale.saleDate BETWEEN :from AND :to
        GROUP BY si.product.id, si.product.name
        ORDER BY SUM(si.itemTotal) - SUM(si.quantity * COALESCE(si.costPriceAtSale, si.product.purchasePrice)) DESC
    """)
    List<Object[]> findProductWiseProfit(
            @Param("businessId") Long businessId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to
    );

    @Query("""
        SELECT COALESCE(SUM(si.itemTotal), 0)
        FROM SaleItem si
        WHERE si.sale.business.id = :businessId
          AND si.sale.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
          AND si.sale.saleDate BETWEEN :from AND :to
    """)
    java.math.BigDecimal getTotalRevenueBetween(
            @Param("businessId") Long businessId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to
    );

    @Query("""
        SELECT COALESCE(SUM(si.quantity * COALESCE(si.costPriceAtSale, si.product.purchasePrice)), 0)
        FROM SaleItem si
        WHERE si.sale.business.id = :businessId
          AND si.sale.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
          AND si.sale.saleDate BETWEEN :from AND :to
    """)
    java.math.BigDecimal getTotalCostBetween(
            @Param("businessId") Long businessId,
            @Param("from") java.time.LocalDate from,
            @Param("to") java.time.LocalDate to
    );
}