package com.sbmp.sales.dto;

import java.math.BigDecimal;

public record PaymentBreakdownDto(String method, BigDecimal total) {}