package com.sbmp.expense.dto;

import com.sbmp.sales.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseListItemDTO {

    private Long id;
    private String categoryName;
    private String title;
    private BigDecimal amount;
    private LocalDate expenseDate;
    private PaymentMethod paymentMethod;
    private String referenceNo;
    private String recordedByName;
}