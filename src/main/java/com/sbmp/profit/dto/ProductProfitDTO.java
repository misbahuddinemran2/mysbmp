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
public class ProductProfitDTO {

    private Long productId;
    private String productName;
    private Integer quantitySold;
    private BigDecimal revenue;
    private BigDecimal cost;
    private BigDecimal profit;
    private BigDecimal profitMarginPct;   // profit / revenue * 100
}