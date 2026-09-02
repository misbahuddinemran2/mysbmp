package com.sbmp.sales.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SaleItemRequestDto {

    @NotNull
    private Long productId;

    @NotNull
    private Integer quantity;

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal unitPrice;

    private BigDecimal discountAmount;

    private BigDecimal discountPercentage;

    /** Optional — যেমন "1 YEAR", "3 YEARS" */
    private String warranty;

    /** Optional — কমা/নিউলাইন দিয়ে আলাদা serial/MAC নম্বর */
    private String serialNumbers;
}