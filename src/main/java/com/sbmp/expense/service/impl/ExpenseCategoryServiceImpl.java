package com.sbmp.expense.service.impl;

import com.sbmp.business.entity.Business;
import com.sbmp.expense.dto.ExpenseCategoryDTO;
import com.sbmp.expense.entity.ExpenseCategory;
import com.sbmp.expense.repository.ExpenseCategoryRepository;
import com.sbmp.expense.service.ExpenseCategoryService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private final ExpenseCategoryRepository categoryRepository;
    private final EntityManager entityManager;

    @Override
    public List<ExpenseCategoryDTO> getAllActive(Long businessId) {
        return categoryRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(businessId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public List<ExpenseCategoryDTO> getAll(Long businessId) {
        return categoryRepository.findByBusinessIdOrderByNameAsc(businessId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    @Transactional
    public ExpenseCategoryDTO create(Long businessId, ExpenseCategoryDTO dto) {

        if (categoryRepository.existsByNameIgnoreCaseAndBusinessId(dto.getName(), businessId)) {
            throw new RuntimeException("This category already exists");
        }

        ExpenseCategory category = ExpenseCategory.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .active(true)
                .business(entityManager.getReference(Business.class, businessId))
                .build();

        category = categoryRepository.save(category);

        return toDto(category);
    }

    @Override
    @Transactional
    public ExpenseCategoryDTO update(Long businessId, Long categoryId, ExpenseCategoryDTO dto) {

        ExpenseCategory category = categoryRepository.findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        category.setName(dto.getName());
        category.setDescription(dto.getDescription());

        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void toggleActive(Long businessId, Long categoryId) {

        ExpenseCategory category = categoryRepository.findByIdAndBusinessId(categoryId, businessId)
                .orElseThrow(() -> new RuntimeException("Category not found"));

        category.setActive(!category.isActive());

        categoryRepository.save(category);
    }

    private ExpenseCategoryDTO toDto(ExpenseCategory c) {
        return ExpenseCategoryDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .active(c.getActive())
                .build();
    }
}