package com.sbmp.expense.service;

import com.sbmp.expense.dto.CreateExpenseRequest;
import com.sbmp.expense.dto.ExpenseListItemDTO;
import com.sbmp.expense.dto.ExpenseSummaryDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ExpenseService {

    Page<ExpenseListItemDTO> getExpenses(Long businessId, Pageable pageable);

    Long createExpense(Long businessId, Long userId, CreateExpenseRequest request);

    void deleteExpense(Long businessId, Long expenseId);

    BigDecimal getTotalExpense(Long businessId);

    BigDecimal getTotalExpenseThisMonth(Long businessId);

    List<ExpenseSummaryDTO> getCategoryWiseExpense(Long businessId, LocalDate from, LocalDate to);
}