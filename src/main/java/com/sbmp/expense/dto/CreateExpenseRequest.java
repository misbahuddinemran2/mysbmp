package com.sbmp.expense.dto;

import com.sbmp.sales.enums.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
public class CreateExpenseRequest {

    @NotNull(message = "Category is required")
    private Long categoryId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate expenseDate;

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    private String referenceNo;
}