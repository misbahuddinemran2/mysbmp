package com.sbmp.profit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitSummaryDTO {

    private BigDecimal totalRevenue;
    private BigDecimal totalCost;
    private BigDecimal grossProfit;      // revenue - cost

    private BigDecimal totalExpense;
    private BigDecimal netProfit;        // grossProfit - expense

    private BigDecimal grossMarginPct;
    private BigDecimal netMarginPct;
}