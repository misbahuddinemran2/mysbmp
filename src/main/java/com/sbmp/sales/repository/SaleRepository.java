package com.sbmp.sales.repository;

import com.sbmp.business.entity.Business;
import com.sbmp.customer.entity.Customer;
import com.sbmp.sales.dto.MonthlyTotalDto;
import com.sbmp.sales.dto.TopCustomerDto;
import com.sbmp.sales.entity.Sale;
import com.sbmp.sales.enums.SaleStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SaleRepository
        extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdAndBusiness(
            Long id,
            Business business
    );

    Optional<Sale> findByInvoiceNo(
            String invoiceNo
    );

    boolean existsByInvoiceNo(
            String invoiceNo
    );

    Page<Sale> findByBusiness(
            Business business,
            Pageable pageable
    );

    Page<Sale> findByBusinessAndStatus(
            Business business,
            SaleStatus status,
            Pageable pageable
    );

    Page<Sale> findByBusinessAndCustomer(
            Business business,
            Customer customer,
            Pageable pageable
    );

    Page<Sale> findByBusinessAndSaleDateBetween(
            Business business,
            LocalDate from,
            LocalDate to,
            Pageable pageable
    );

    long countByBusiness(
            Business business
    );

    long countByInvoiceNoStartingWith(String prefix);

    // ─────────────────────────────────────────────
    // Dashboard / Controller-এর জন্য নতুন মেথড (by businessId)
    // ─────────────────────────────────────────────

    long countByBusinessId(Long businessId);

    List<Sale> findByBusinessIdOrderBySaleDateDesc(Long businessId);

    List<Sale> findByBusinessIdOrderByCreatedAtDesc(Long businessId, Pageable pageable);

    List<Sale> findByBusinessIdAndStatusOrderByCreatedAtDesc(Long businessId, SaleStatus status);

    @Query("""
        SELECT COALESCE(SUM(s.grandTotal), 0)
        FROM Sale s
        WHERE s.business.id = :businessId
        AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
    """)
    BigDecimal sumGrandTotalByBusinessId(@Param("businessId") Long businessId);

    @Query("""
        SELECT COALESCE(SUM(s.paidAmount), 0)
        FROM Sale s
        WHERE s.business.id = :businessId
        AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
    """)
    BigDecimal sumPaidAmountByBusinessId(@Param("businessId") Long businessId);

    @Query("""
        SELECT COALESCE(SUM(s.dueAmount), 0)
        FROM Sale s
        WHERE s.business.id = :businessId
        AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
    """)
    BigDecimal sumDueAmountByBusinessId(@Param("businessId") Long businessId);

    @Query("""
        SELECT COALESCE(SUM(s.grandTotal), 0)
        FROM Sale s
        WHERE s.business.id = :businessId
        AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
        AND s.saleDate BETWEEN :from AND :to
    """)
    BigDecimal sumGrandTotalByBusinessIdAndSaleDateBetween(
            @Param("businessId") Long businessId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    // ─────────────────────────────────────────────
    // Chart data — Dashboard (native query, MySQL)
    // ─────────────────────────────────────────────

    @Query(
            value = """
            SELECT DATE_FORMAT(s.sale_date, '%b') AS label,
                   COALESCE(SUM(s.grand_total), 0) AS total
            FROM sales s
            WHERE s.business_id = :businessId
            AND s.status <> 'CANCELLED'
            AND s.sale_date >= :fromDate
            GROUP BY DATE_FORMAT(s.sale_date, '%b'), MONTH(s.sale_date)
            ORDER BY MONTH(s.sale_date)
            """,
            nativeQuery = true
    )
    List<Object[]> findMonthlyTotalsRaw(
            @Param("businessId") Long businessId,
            @Param("fromDate") LocalDate fromDate
    );

    @Query("""
        SELECT new com.sbmp.sales.dto.TopCustomerDto(c.name, SUM(s.grandTotal))
        FROM Sale s
        JOIN s.customer c
        WHERE s.business.id = :businessId
        AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
        GROUP BY c.name
        ORDER BY SUM(s.grandTotal) DESC
        """)
    List<TopCustomerDto> findTopCustomers(
            @Param("businessId") Long businessId,
            Pageable pageable
    );

    // default method — Object[] থেকে MonthlyTotalDto তে convert করে দেয়
    default List<MonthlyTotalDto> findMonthlyTotals(Long businessId, LocalDate fromDate) {
        return findMonthlyTotalsRaw(businessId, fromDate).stream()
                .map(row -> new MonthlyTotalDto(
                        (String) row[0],
                        row[1] instanceof BigDecimal
                                ? (BigDecimal) row[1]
                                : new BigDecimal(row[1].toString())
                ))
                .toList();
    }

    // ─────────────────────────────────────────────
    // DUE MODULE — customer-wise due queries
    // ─────────────────────────────────────────────

    @Query("""
        SELECT s.customer.id, s.customer.name, s.customer.mobile,
               SUM(s.dueAmount), COUNT(s.id)
        FROM Sale s
        WHERE s.business.id = :businessId
          AND s.dueAmount > 0
          AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
        GROUP BY s.customer.id, s.customer.name, s.customer.mobile
        ORDER BY SUM(s.dueAmount) DESC
    """)
    List<Object[]> findCustomerWiseDue(
            @Param("businessId") Long businessId
    );

    @Query("""
        SELECT s FROM Sale s
        WHERE s.business.id = :businessId
          AND s.customer.id = :customerId
          AND s.dueAmount > 0
          AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
        ORDER BY s.saleDate ASC
    """)
    List<Sale> findDueSalesByCustomer(
            @Param("businessId") Long businessId,
            @Param("customerId") Long customerId
    );

    @Query("""
        SELECT COALESCE(SUM(s.dueAmount), 0)
        FROM Sale s
        WHERE s.business.id = :businessId
          AND s.dueAmount > 0
          AND s.status <> com.sbmp.sales.enums.SaleStatus.CANCELLED
    """)
    BigDecimal getTotalOutstandingDue(
            @Param("businessId") Long businessId
    );

}