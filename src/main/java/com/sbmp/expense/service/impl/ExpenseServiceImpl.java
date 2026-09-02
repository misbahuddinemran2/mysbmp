package com.sbmp.expense.service.impl;

import com.sbmp.business.entity.Business;
import com.sbmp.expense.dto.CreateExpenseRequest;
import com.sbmp.expense.dto.ExpenseListItemDTO;
import com.sbmp.expense.dto.ExpenseSummaryDTO;
import com.sbmp.expense.entity.Expense;
import com.sbmp.expense.entity.ExpenseCategory;
import com.sbmp.expense.repository.ExpenseCategoryRepository;
import com.sbmp.expense.repository.ExpenseRepository;
import com.sbmp.expense.service.ExpenseService;
import com.sbmp.user.entity.User;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository         expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final EntityManager             entityManager;

    @Override
    public Page<ExpenseListItemDTO> getExpenses(Long businessId, Pageable pageable) {

        return expenseRepository.findByBusinessIdOrderByExpenseDateDesc(businessId, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional
    public Long createExpense(Long businessId, Long userId, CreateExpenseRequest request) {

        ExpenseCategory category = categoryRepository.findByIdAndBusinessId(request.getCategoryId(), businessId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        Expense expense = Expense.builder()
                .category(category)
                .amount(request.getAmount())
                .expenseDate(request.getExpenseDate())
                .title(request.getTitle())
                .description(request.getDescription())
                .paymentMethod(request.getPaymentMethod())
                .referenceNo(request.getReferenceNo())
                .business(entityManager.getReference(Business.class, businessId))
                .recordedBy(userId != null ? entityManager.getReference(User.class, userId) : null)
                .build();

        expense = expenseRepository.save(expense);

        return expense.getId();
    }

    @Override
    @Transactional
    public void deleteExpense(Long businessId, Long expenseId) {

        Expense expense = expenseRepository.findByIdAndBusinessId(expenseId, businessId)
                .orElseThrow(() -> new RuntimeException("Expense not found"));

        expenseRepository.delete(expense);
    }

    @Override
    public BigDecimal getTotalExpense(Long businessId) {
        return expenseRepository.getTotalExpense(businessId);
    }

    @Override
    public BigDecimal getTotalExpenseThisMonth(Long businessId) {
        LocalDate now = LocalDate.now();
        LocalDate firstDay = now.withDayOfMonth(1);
        LocalDate lastDay = now.withDayOfMonth(now.lengthOfMonth());
        return expenseRepository.getTotalExpenseBetween(businessId, firstDay, lastDay);
    }

    @Override
    public List<ExpenseSummaryDTO> getCategoryWiseExpense(Long businessId, LocalDate from, LocalDate to) {
        return expenseRepository.getCategoryWiseExpense(businessId, from, to)
                .stream()
                .map(r -> ExpenseSummaryDTO.builder()
                        .categoryName((String) r[0])
                        .totalAmount((BigDecimal) r[1])
                        .build())
                .toList();
    }

    private ExpenseListItemDTO toDto(Expense e) {
        return ExpenseListItemDTO.builder()
                .id(e.getId())
                .categoryName(e.getCategory() != null ? e.getCategory().getName() : "—")
                .title(e.getTitle())
                .amount(e.getAmount())
                .expenseDate(e.getExpenseDate())
                .paymentMethod(e.getPaymentMethod())
                .referenceNo(e.getReferenceNo())
                .recordedByName(e.getRecordedBy() != null ? e.getRecordedBy().getName() : "—")
                .build();
    }
}