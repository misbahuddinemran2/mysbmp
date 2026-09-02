package com.sbmp.due.dto;

import com.sbmp.sales.enums.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentReceiptDTO {

    private Long paymentId;
    private String receiptNo;          // e.g. "RCPT-000045"

    private String businessName;
    private String businessAddress;

    private String customerName;
    private String customerMobile;

    private String invoiceNo;
    private LocalDate saleDate;
    private BigDecimal grandTotal;

    private BigDecimal amountPaidNow;
    private BigDecimal totalPaidSoFar;
    private BigDecimal remainingDue;

    private PaymentMethod paymentMethod;
    private LocalDate paymentDate;
    private String referenceNo;
    private String notes;

    private LocalDateTime issuedAt;
}