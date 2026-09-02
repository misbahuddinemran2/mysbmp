package com.sbmp.expense.service;

import com.sbmp.expense.dto.ExpenseCategoryDTO;

import java.util.List;

public interface ExpenseCategoryService {

    List<ExpenseCategoryDTO> getAllActive(Long businessId);

    List<ExpenseCategoryDTO> getAll(Long businessId);

    ExpenseCategoryDTO create(Long businessId, ExpenseCategoryDTO dto);

    ExpenseCategoryDTO update(Long businessId, Long categoryId, ExpenseCategoryDTO dto);

    void toggleActive(Long businessId, Long categoryId);
}