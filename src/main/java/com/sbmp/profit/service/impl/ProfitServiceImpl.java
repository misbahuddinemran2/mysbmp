package com.sbmp.profit.service.impl;

import com.sbmp.expense.repository.ExpenseRepository;
import com.sbmp.profit.dto.ProductProfitDTO;
import com.sbmp.profit.dto.ProfitSummaryDTO;
import com.sbmp.profit.service.ProfitService;
import com.sbmp.sales.repository.SaleItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfitServiceImpl implements ProfitService {

    private final SaleItemRepository saleItemRepository;
    private final ExpenseRepository  expenseRepository;

    @Override
    public List<ProductProfitDTO> getProductWiseProfit(Long businessId, LocalDate from, LocalDate to) {

        List<Object[]> rows = saleItemRepository.findProductWiseProfit(businessId, from, to);

        return rows.stream()
                .map(r -> {
                    Long        productId = (Long) r[0];
                    String      name      = (String) r[1];
                    Long        qty       = (Long) r[2];
                    BigDecimal  revenue   = (BigDecimal) r[3];
                    BigDecimal  cost      = (BigDecimal) r[4];
                    BigDecimal  profit    = revenue.subtract(cost);

                    BigDecimal margin = revenue.compareTo(BigDecimal.ZERO) == 0
                            ? BigDecimal.ZERO
                            : profit.multiply(BigDecimal.valueOf(100))
                            .divide(revenue, 2, RoundingMode.HALF_UP);

                    return ProductProfitDTO.builder()
                            .productId(productId)
                            .productName(name)
                            .quantitySold(qty.intValue())
                            .revenue(revenue)
                            .cost(cost)
                            .profit(profit)
                            .profitMarginPct(margin)
                            .build();
                })
                .toList();
    }

    @Override
    public ProfitSummaryDTO getProfitSummary(Long businessId, LocalDate from, LocalDate to) {

        BigDecimal revenue = saleItemRepository.getTotalRevenueBetween(businessId, from, to);
        BigDecimal cost    = saleItemRepository.getTotalCostBetween(businessId, from, to);
        BigDecimal gross   = revenue.subtract(cost);

        BigDecimal expense = expenseRepository.getTotalExpenseBetween(businessId, from, to);
        BigDecimal net      = gross.subtract(expense);

        BigDecimal grossMargin = revenue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : gross.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);

        BigDecimal netMargin = revenue.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : net.multiply(BigDecimal.valueOf(100)).divide(revenue, 2, RoundingMode.HALF_UP);

        return ProfitSummaryDTO.builder()
                .totalRevenue(revenue)
                .totalCost(cost)
                .grossProfit(gross)
                .totalExpense(expense)
                .netProfit(net)
                .grossMarginPct(grossMargin)
                .netMarginPct(netMargin)
                .build();
    }
}