package com.sbmp.expense.repository;

import com.sbmp.expense.entity.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findByBusinessIdAndActiveTrueOrderByNameAsc(Long businessId);

    List<ExpenseCategory> findByBusinessIdOrderByNameAsc(Long businessId);

    Optional<ExpenseCategory> findByIdAndBusinessId(Long id, Long businessId);

    boolean existsByNameIgnoreCaseAndBusinessId(String name, Long businessId);
}