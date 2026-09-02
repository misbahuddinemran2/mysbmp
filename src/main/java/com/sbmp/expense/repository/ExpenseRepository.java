package com.sbmp.expense.repository;

import com.sbmp.expense.entity.Expense;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndBusinessId(Long id, Long businessId);

    Page<Expense> findByBusinessIdOrderByExpenseDateDesc(Long businessId, Pageable pageable);

    List<Expense> findByBusinessIdAndExpenseDateBetweenOrderByExpenseDateDesc(
            Long businessId, LocalDate from, LocalDate to
    );

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.business.id = :businessId
    """)
    BigDecimal getTotalExpense(@Param("businessId") Long businessId);

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.business.id = :businessId
          AND e.expenseDate BETWEEN :from AND :to
    """)
    BigDecimal getTotalExpenseBetween(
            @Param("businessId") Long businessId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    @Query("""
        SELECT e.category.name, COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.business.id = :businessId
          AND e.expenseDate BETWEEN :from AND :to
        GROUP BY e.category.name
        ORDER BY SUM(e.amount) DESC
    """)
    List<Object[]> getCategoryWiseExpense(
            @Param("businessId") Long businessId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to
    );

    List<Expense> findTop5ByBusinessIdOrderByExpenseDateDesc(Long businessId);
}