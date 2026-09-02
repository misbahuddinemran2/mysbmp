package com.sbmp.sales.repository;

import com.sbmp.sales.entity.Sale;
import com.sbmp.sales.entity.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SalePaymentRepository
        extends JpaRepository<SalePayment, Long> {

    List<SalePayment> findBySale(
            Sale sale
    );

    void deleteBySale(
            Sale sale
    );

    // ─────────────────────────────────────────────
    // Payment method breakdown (dashboard chart-এর জন্য)
    // ─────────────────────────────────────────────
    @Query("""
        SELECT sp.paymentMethod, SUM(sp.amount)
        FROM SalePayment sp
        WHERE sp.sale.business.id = :businessId
        AND sp.sale.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
        GROUP BY sp.paymentMethod
        """)
    List<Object[]> findPaymentBreakdownByBusinessId(
            @Param("businessId") Long businessId
    );
}