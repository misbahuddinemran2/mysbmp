package com.sbmp.sales.dto;

import com.sbmp.sales.enums.PaymentMethod;
import com.sbmp.sales.enums.SaleStatus;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SaleRequestDto {

    // ⚠️ @NotNull সরিয়ে দিন — কারণ নতুন কাস্টমার হলে এটা শুরুতে null থাকবে
    private Long customerId;

    private Long businessId;

    private LocalDate saleDate;

    private String notes;

    private BigDecimal advancePaid;

    private PaymentMethod paymentMethod;

    private BigDecimal invoiceDiscount;

    private SaleStatus status;

    // ───── NEW: Inline new-customer fields ─────
    private String newCustomerName;
    private String newCustomerMobile;
    private String newCustomerEmail;
    private String newCustomerAddress;

    @Valid
    private List<SaleItemRequestDto> items = new ArrayList<>();
}