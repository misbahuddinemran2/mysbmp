package com.sbmp.profit.service;

import com.sbmp.profit.dto.ProductProfitDTO;
import com.sbmp.profit.dto.ProfitSummaryDTO;

import java.time.LocalDate;
import java.util.List;

public interface ProfitService {

    List<ProductProfitDTO> getProductWiseProfit(Long businessId, LocalDate from, LocalDate to);

    ProfitSummaryDTO getProfitSummary(Long businessId, LocalDate from, LocalDate to);
}