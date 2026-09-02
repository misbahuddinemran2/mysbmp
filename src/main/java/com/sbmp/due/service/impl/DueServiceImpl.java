package com.sbmp.due.service.impl;

import com.sbmp.due.dto.CollectDueRequest;
import com.sbmp.due.dto.CustomerDueDTO;
import com.sbmp.due.dto.DueSaleDTO;
import com.sbmp.due.dto.PaymentReceiptDTO;
import com.sbmp.due.service.DueService;
import com.sbmp.sales.entity.Sale;
import com.sbmp.sales.entity.SalePayment;
import com.sbmp.sales.enums.PaymentStatus;
import com.sbmp.sales.repository.SalePaymentRepository;
import com.sbmp.sales.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DueServiceImpl implements DueService {

    private final SaleRepository        saleRepository;
    private final SalePaymentRepository salePaymentRepository;

    @Override
    public List<CustomerDueDTO> getCustomerWiseDue(Long businessId) {

        List<Object[]> rows = saleRepository.findCustomerWiseDue(businessId);

        return rows.stream()
                .map(r -> CustomerDueDTO.builder()
                        .customerId((Long) r[0])
                        .customerName((String) r[1])
                        .customerPhone((String) r[2])
                        .totalDue((BigDecimal) r[3])
                        .dueInvoiceCount((Long) r[4])
                        .build())
                .toList();
    }

    @Override
    public List<DueSaleDTO> getDueSalesByCustomer(Long businessId, Long customerId) {

        List<Sale> sales = saleRepository.findDueSalesByCustomer(businessId, customerId);

        return sales.stream()
                .map(s -> DueSaleDTO.builder()
                        .saleId(s.getId())
                        .invoiceNo(s.getInvoiceNo())
                        .saleDate(s.getSaleDate())
                        .grandTotal(s.getGrandTotal())
                        .paidAmount(s.getPaidAmount())
                        .dueAmount(s.getDueAmount())
                        .daysOverdue(ChronoUnit.DAYS.between(s.getSaleDate(), LocalDate.now()))
                        .build())
                .toList();
    }

    @Override
    public BigDecimal getTotalOutstandingDue(Long businessId) {
        return saleRepository.getTotalOutstandingDue(businessId);
    }

    @Override
    @Transactional
    public Long collectDuePayment(Long businessId, CollectDueRequest request) {

        Sale sale = saleRepository.findById(request.getSaleId())
                .orElseThrow(() -> new RuntimeException("Sale not found"));

        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new RuntimeException("Sale does not belong to this business");
        }

        BigDecimal amount = request.getAmount();

        if (amount.compareTo(sale.getDueAmount()) > 0) {
            throw new RuntimeException("Payment amount cannot exceed due amount");
        }

        // ── payment তৈরি করে সরাসরি save (JPA merge()-cascade গোলযোগ এড়াতে) ──
        SalePayment payment = SalePayment.builder()
                .sale(sale)
                .paymentMethod(request.getPaymentMethod())
                .amount(amount)
                .paymentDate(request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now())
                .referenceNo(request.getReferenceNo())
                .notes(request.getNotes())
                .build();

        payment = salePaymentRepository.save(payment);   // ← persist() হবে, ID সরাসরি payment-এ বসবে

        // ── sale-এর paid/due amount আপডেট ──
        sale.setPaidAmount(sale.getPaidAmount().add(amount));
        sale.setDueAmount(sale.getDueAmount().subtract(amount));

        if (sale.getDueAmount().compareTo(BigDecimal.ZERO) <= 0) {
            sale.setDueAmount(BigDecimal.ZERO);
            sale.setPaymentStatus(PaymentStatus.PAID);
        } else {
            sale.setPaymentStatus(PaymentStatus.PARTIAL);
        }

        saleRepository.save(sale);

        return payment.getId();
    }

    @Override
    public PaymentReceiptDTO getPaymentReceipt(Long businessId, Long paymentId) {

        SalePayment payment = salePaymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        Sale sale = payment.getSale();

        if (!sale.getBusiness().getId().equals(businessId)) {
            throw new RuntimeException("Payment does not belong to this business");
        }

        return PaymentReceiptDTO.builder()
                .paymentId(payment.getId())
                .receiptNo("RCPT-" + String.format("%06d", payment.getId()))
                .businessName(sale.getBusiness().getBusinessName())
                .businessAddress(sale.getBusiness().getAddress())
                .customerName(sale.getCustomer().getName())
                .customerMobile(sale.getCustomer().getMobile())
                .invoiceNo(sale.getInvoiceNo())
                .saleDate(sale.getSaleDate())
                .grandTotal(sale.getGrandTotal())
                .amountPaidNow(payment.getAmount())
                .totalPaidSoFar(sale.getPaidAmount())
                .remainingDue(sale.getDueAmount())
                .paymentMethod(payment.getPaymentMethod())
                .paymentDate(payment.getPaymentDate())
                .referenceNo(payment.getReferenceNo())
                .notes(payment.getNotes())
                .issuedAt(LocalDateTime.now())
                .build();
    }
}