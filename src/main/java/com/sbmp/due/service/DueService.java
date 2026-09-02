package com.sbmp.due.service;

import com.sbmp.due.dto.CollectDueRequest;
import com.sbmp.due.dto.CustomerDueDTO;
import com.sbmp.due.dto.DueSaleDTO;
import com.sbmp.due.dto.PaymentReceiptDTO;

import java.math.BigDecimal;
import java.util.List;

public interface DueService {

    List<CustomerDueDTO> getCustomerWiseDue(Long businessId);

    List<DueSaleDTO> getDueSalesByCustomer(Long businessId, Long customerId);

    BigDecimal getTotalOutstandingDue(Long businessId);

    Long collectDuePayment(Long businessId, CollectDueRequest request);   // ← Long রিটার্ন করে

    PaymentReceiptDTO getPaymentReceipt(Long businessId, Long paymentId);  // ← নতুন
}