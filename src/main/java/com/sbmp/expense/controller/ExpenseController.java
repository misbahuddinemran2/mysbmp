package com.sbmp.expense.controller;

import com.sbmp.business.entity.Business;
import com.sbmp.expense.dto.CreateExpenseRequest;
import com.sbmp.expense.dto.ExpenseCategoryDTO;
import com.sbmp.expense.service.ExpenseCategoryService;
import com.sbmp.expense.service.ExpenseService;
import com.sbmp.user.entity.User;
import com.sbmp.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService         expenseService;
    private final ExpenseCategoryService categoryService;
    private final UserRepository         userRepository;

    @GetMapping
    public String list(
            @RequestParam(defaultValue = "0") int page,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        Business business = getCurrentBusiness(userDetails);

        Pageable pageable = PageRequest.of(page, 15, Sort.by("expenseDate").descending());

        model.addAttribute("expensePage", expenseService.getExpenses(business.getId(), pageable));
        model.addAttribute("totalExpense", expenseService.getTotalExpense(business.getId()));
        model.addAttribute("totalExpenseThisMonth", expenseService.getTotalExpenseThisMonth(business.getId()));
        model.addAttribute("categories", categoryService.getAllActive(business.getId()));
        model.addAttribute("newExpense", new CreateExpenseRequest());
        model.addAttribute("activePage", "expenses");
        model.addAttribute("businessName", business.getBusinessName());

        return "expense/expense-list";
    }

    @PostMapping("/add")
    public String addExpense(
            @Valid @ModelAttribute("newExpense") CreateExpenseRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Business business = getCurrentBusiness(userDetails);
        User     user     = getCurrentUser(userDetails);

        expenseService.createExpense(business.getId(), user.getId(), request);

        return "redirect:/expenses";
    }

    @PostMapping("/{id}/delete")
    public String deleteExpense(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Business business = getCurrentBusiness(userDetails);

        expenseService.deleteExpense(business.getId(), id);

        return "redirect:/expenses";
    }

    // ── Category management ──

    @GetMapping("/categories")
    public String categoryList(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        Business business = getCurrentBusiness(userDetails);

        model.addAttribute("categories", categoryService.getAll(business.getId()));
        model.addAttribute("newCategory", new ExpenseCategoryDTO());
        model.addAttribute("activePage", "expenses");
        model.addAttribute("businessName", business.getBusinessName());

        return "expense/expense-category-list";
    }

    @PostMapping("/categories/add")
    public String addCategory(
            @Valid @ModelAttribute("newCategory") ExpenseCategoryDTO dto,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Business business = getCurrentBusiness(userDetails);

        categoryService.create(business.getId(), dto);

        return "redirect:/expenses/categories";
    }

    @PostMapping("/categories/{id}/toggle")
    public String toggleCategory(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Business business = getCurrentBusiness(userDetails);

        categoryService.toggleActive(business.getId(), id);

        return "redirect:/expenses/categories";
    }

    private Business getCurrentBusiness(UserDetails userDetails) {
        return getCurrentUser(userDetails).getBusiness();
    }

    private User getCurrentUser(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}