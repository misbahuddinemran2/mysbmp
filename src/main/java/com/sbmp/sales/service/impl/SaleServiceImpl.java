package com.sbmp.sales.service.impl;

import com.sbmp.business.entity.Business;
import com.sbmp.business.repository.BusinessRepository;
import com.sbmp.customer.entity.Customer;
import com.sbmp.customer.repository.CustomerRepository;
import com.sbmp.inventory.product.entity.Product;
import com.sbmp.inventory.product.repository.ProductRepository;
import com.sbmp.inventory.stock.service.StockService;
import com.sbmp.sales.dto.SaleItemRequestDto;
import com.sbmp.sales.dto.SaleItemResponseDto;
import com.sbmp.sales.dto.SalePaymentResponseDto;
import com.sbmp.sales.dto.SaleRequestDto;
import com.sbmp.sales.dto.SaleResponseDto;
import com.sbmp.sales.entity.Sale;
import com.sbmp.sales.entity.SaleItem;
import com.sbmp.sales.entity.SalePayment;
import com.sbmp.sales.enums.PaymentMethod;
import com.sbmp.sales.enums.PaymentStatus;
import com.sbmp.sales.enums.SaleStatus;
import com.sbmp.sales.repository.SaleItemRepository;
import com.sbmp.sales.repository.SaleRepository;
import com.sbmp.sales.service.SaleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.sbmp.sales.repository.SalePaymentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SaleServiceImpl implements SaleService {

    private final SaleRepository      saleRepository;
    private final SaleItemRepository  saleItemRepository;
    private final CustomerRepository  customerRepository;
    private final BusinessRepository  businessRepository;
    private final ProductRepository   productRepository;
    private final SalePaymentRepository salePaymentRepository;
    private final StockService        stockService;   // ✅ Stock module integration

    // ─────────────────────────────────────────────
    // CREATE SALE
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public SaleResponseDto createSale(SaleRequestDto dto) {

        Customer customer = customerRepository
                .findById(dto.getCustomerId())
                .orElseThrow(() ->
                        new RuntimeException("Customer not found: "
                                + dto.getCustomerId()));

        Business business = businessRepository
                .findById(dto.getBusinessId())
                .orElseThrow(() ->
                        new RuntimeException("Business not found: "
                                + dto.getBusinessId()));

        String invoiceNo = generateInvoiceNo();

        Sale sale = Sale.builder()
                .invoiceNo(invoiceNo)
                .customer(customer)
                .business(business)
                .saleDate(dto.getSaleDate() != null
                        ? dto.getSaleDate()
                        : LocalDate.now())
                .notes(dto.getNotes())
                .advancePaid(dto.getAdvancePaid() != null
                        ? dto.getAdvancePaid()
                        : BigDecimal.ZERO)
                .status(dto.getStatus() != null
                        ? dto.getStatus()
                        : SaleStatus.DRAFT)
                .subtotal(BigDecimal.ZERO)
                .totalDiscount(BigDecimal.ZERO)
                .invoiceDiscount(dto.getInvoiceDiscount() != null
                        ? dto.getInvoiceDiscount()
                        : BigDecimal.ZERO)
                .grandTotal(BigDecimal.ZERO)
                .paidAmount(BigDecimal.ZERO)
                .dueAmount(BigDecimal.ZERO)
                .paymentStatus(PaymentStatus.DUE)
                .build();

        BigDecimal[] totals = applyItems(sale, dto.getItems(), dto.getStatus(), business.getId());
        BigDecimal subtotal      = totals[0];
        BigDecimal totalDiscount = totals[1];

        finalizeTotals(sale, subtotal, totalDiscount);

        recordAdvancePayment(sale, dto);

        Sale saved = saleRepository.save(sale);

        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────
    // ADVANCE PAYMENT (sale তৈরির সময় auto-record)
    // ─────────────────────────────────────────────
    private void recordAdvancePayment(Sale sale, SaleRequestDto dto) {
        BigDecimal advancePaid = dto.getAdvancePaid();

        if (advancePaid == null || advancePaid.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        PaymentMethod method = dto.getPaymentMethod() != null
                ? dto.getPaymentMethod()
                : PaymentMethod.CASH;

        SalePayment payment = SalePayment.builder()
                .paymentMethod(method)
                .amount(advancePaid)
                .paymentDate(sale.getSaleDate())
                .build();

        sale.addPayment(payment);
    }

    // ─────────────────────────────────────────────
    // DASHBOARD KPIs
    // ─────────────────────────────────────────────
    @Override
    public long countByBusiness(Long businessId) {
        return saleRepository.countByBusinessId(businessId);
    }

    @Override
    public BigDecimal getTotalSale(Long businessId) {
        return saleRepository.sumGrandTotalByBusinessId(businessId);
    }

    @Override
    public BigDecimal getTotalPaid(Long businessId) {
        return saleRepository.sumPaidAmountByBusinessId(businessId);
    }

    @Override
    public BigDecimal getTotalDue(Long businessId) {
        return saleRepository.sumDueAmountByBusinessId(businessId);
    }

    @Override
    public BigDecimal getThisMonthTotal(Long businessId) {
        LocalDate now   = LocalDate.now();
        LocalDate first = now.withDayOfMonth(1);
        LocalDate last  = now.withDayOfMonth(now.lengthOfMonth());
        return saleRepository.sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, first, last);
    }

    @Override
    public BigDecimal getLastMonthTotal(Long businessId) {
        LocalDate lastMonth = LocalDate.now().minusMonths(1);
        LocalDate first     = lastMonth.withDayOfMonth(1);
        LocalDate last      = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
        return saleRepository.sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, first, last);
    }

    // ─────────────────────────────────────────────
    // TABLES
    // ─────────────────────────────────────────────
    @Override
    public List<SaleResponseDto> getRecentSales(Long businessId, int limit) {
        return saleRepository
                .findByBusinessIdOrderByCreatedAtDesc(businessId, PageRequest.of(0, limit))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SaleResponseDto> getDraftSales(Long businessId) {
        return saleRepository
                .findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, SaleStatus.DRAFT)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SaleResponseDto> getSalesByBusiness(Long businessId) {
        return saleRepository
                .findByBusinessIdOrderBySaleDateDesc(businessId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────
    // CHARTS
    // ─────────────────────────────────────────────
    @Override
    public List<String> getTrendLabels(Long businessId) {
        return new ArrayList<>(buildDailyTrend(businessId).keySet());
    }

    @Override
    public List<BigDecimal> getTrendValues(Long businessId) {
        return new ArrayList<>(buildDailyTrend(businessId).values());
    }

    private Map<String, BigDecimal> buildDailyTrend(Long businessId) {
        Map<String, BigDecimal> trend = new LinkedHashMap<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");

        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            BigDecimal total = saleRepository
                    .sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, day, day);
            trend.put(day.format(fmt), total);
        }
        return trend;
    }

    @Override
    public List<String> getTopProductLabels(Long businessId) {
        return topProducts(businessId)
                .stream()
                .map(row -> (String) row[0])
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getTopProductValues(Long businessId) {
        return topProducts(businessId)
                .stream()
                .map(row -> ((Number) row[1]).longValue())
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getPaymentLabels(Long businessId) {
        return paymentBreakdown(businessId)
                .stream()
                .map(row -> ((PaymentMethod) row[0]).name())
                .collect(Collectors.toList());
    }

    @Override
    public List<BigDecimal> getPaymentValues(Long businessId) {
        return paymentBreakdown(businessId)
                .stream()
                .map(row -> (BigDecimal) row[1])
                .collect(Collectors.toList());
    }

    private List<Object[]> paymentBreakdown(Long businessId) {
        return salePaymentRepository.findPaymentBreakdownByBusinessId(businessId);
    }
    private List<Object[]> topProducts(Long businessId) {
        return saleItemRepository
                .findTopProductsByBusinessId(businessId, PageRequest.of(0, 5));
    }
    @Override
    public List<String> getTopCustomerLabels(Long businessId) {
        return topCustomers(businessId)
                .stream()
                .map(com.sbmp.sales.dto.TopCustomerDto::name)
                .collect(Collectors.toList());
    }

    @Override
    public List<BigDecimal> getTopCustomerValues(Long businessId) {
        return topCustomers(businessId)
                .stream()
                .map(dto -> dto.total() != null ? dto.total() : BigDecimal.ZERO)
                .collect(Collectors.toList());
    }

    private List<com.sbmp.sales.dto.TopCustomerDto> topCustomers(Long businessId) {
        return saleRepository.findTopCustomers(businessId, PageRequest.of(0, 5));
    }

    // ─────────────────────────────────────────────
    // SINGLE SALE
    // ─────────────────────────────────────────────
    @Override
    public SaleResponseDto getSaleById(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
        return mapToResponse(sale);
    }

    @Override
    public SaleRequestDto getSaleForEdit(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));

        SaleRequestDto dto = new SaleRequestDto();
        dto.setCustomerId(sale.getCustomer().getId());
        dto.setBusinessId(sale.getBusiness().getId());
        dto.setSaleDate(sale.getSaleDate());
        dto.setNotes(sale.getNotes());
        dto.setAdvancePaid(sale.getAdvancePaid());
        dto.setInvoiceDiscount(sale.getInvoiceDiscount());
        dto.setStatus(sale.getStatus());

        List<SaleItemRequestDto> items = sale.getItems()
                .stream()
                .map(item -> {
                    SaleItemRequestDto itemDto = new SaleItemRequestDto();
                    itemDto.setProductId(item.getProduct().getId());
                    itemDto.setQuantity(item.getQuantity());
                    itemDto.setUnitPrice(item.getUnitPrice());
                    itemDto.setDiscountAmount(item.getDiscountAmount());
                    itemDto.setDiscountPercentage(item.getDiscountPercentage());
                    return itemDto;
                })
                .collect(Collectors.toList());

        dto.setItems(items);
        return dto;
    }

    // ─────────────────────────────────────────────
    // UPDATE SALE  (শুধু DRAFT অবস্থায় edit করা যাবে)
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public SaleResponseDto updateSale(Long id, SaleRequestDto dto) {

        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));

        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT sales can be edited");
        }

        Customer customer = customerRepository
                .findById(dto.getCustomerId())
                .orElseThrow(() -> new RuntimeException("Customer not found: " + dto.getCustomerId()));

        Business business = businessRepository
                .findById(dto.getBusinessId())
                .orElseThrow(() -> new RuntimeException("Business not found: " + dto.getBusinessId()));

        sale.setCustomer(customer);
        sale.setBusiness(business);
        sale.setSaleDate(dto.getSaleDate() != null ? dto.getSaleDate() : LocalDate.now());
        sale.setNotes(dto.getNotes());
        sale.setAdvancePaid(dto.getAdvancePaid() != null ? dto.getAdvancePaid() : BigDecimal.ZERO);
        sale.setInvoiceDiscount(dto.getInvoiceDiscount() != null ? dto.getInvoiceDiscount() : BigDecimal.ZERO);

        // পুরোনো items মুছে নতুনগুলো বসানো হচ্ছে (orphanRemoval = true)
        sale.getItems().clear();

        BigDecimal[] totals = applyItems(sale, dto.getItems(), dto.getStatus(), business.getId());
        finalizeTotals(sale, totals[0], totals[1]);

        sale.setStatus(dto.getStatus() != null ? dto.getStatus() : sale.getStatus());

        Sale saved = saleRepository.save(sale);
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────
    // CANCEL SALE
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public void cancelSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));

        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new RuntimeException("Sale is already cancelled");
        }

        // COMPLETED ছিল মানে stock কাটা হয়ে গেছে — ফিরিয়ে দিতে হবে
        if (sale.getStatus() == SaleStatus.COMPLETED) {
            restoreStock(sale);
        }

        sale.setStatus(SaleStatus.CANCELLED);
        saleRepository.save(sale);
    }

    // ─────────────────────────────────────────────
    // COMPLETE SALE  (DRAFT → COMPLETED, stock deduct)
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public void completeSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));

        if (sale.getStatus() != SaleStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT sales can be completed");
        }

        Long businessId = sale.getBusiness().getId();

        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();

            if (product.getStockQuantity() < item.getQuantity()) {
                throw new RuntimeException(
                        "Insufficient stock for: " + product.getName()
                                + ". Available: " + product.getStockQuantity()
                                + ", Requested: " + item.getQuantity()
                );
            }

            int stockBefore = product.getStockQuantity();

            // ✅ StockService দিয়ে Stock + StockTransaction ও sync হবে
            stockService.stockOut(
                    product.getId(),
                    businessId,
                    item.getQuantity(),
                    "SALE",
                    sale.getInvoiceNo(),
                    sale.getId(),
                    "Sale completed"
            );

            int stockAfter = stockBefore - item.getQuantity();
            item.setStockBefore(stockBefore);
            item.setStockAfter(stockAfter);
        }

        sale.setStatus(SaleStatus.COMPLETED);
        saleRepository.save(sale);
    }

    // ─────────────────────────────────────────────
    // DELETE SALE
    // ─────────────────────────────────────────────
    @Override
    @Transactional
    public void deleteSale(Long id) {
        Sale sale = saleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));

        if (sale.getStatus() == SaleStatus.COMPLETED) {
            restoreStock(sale);
        }

        saleRepository.delete(sale);
    }

    // ✅ StockService.stockIn() ব্যবহার করে Stock entity + history ঠিক থাকবে
    private void restoreStock(Sale sale) {
        Long businessId = sale.getBusiness().getId();
        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            try {
                stockService.stockIn(
                        product.getId(),
                        businessId,
                        item.getQuantity(),
                        product.getPurchasePrice(),
                        "SALE",
                        sale.getInvoiceNo(),
                        sale.getId()
                );
            } catch (Exception e) {
                log.error("Stock restore failed for product {} (sale {}): {}",
                        product.getId(), sale.getId(), e.getMessage());
                throw new RuntimeException(
                        "Stock restore failed for product " + product.getName() + ": " + e.getMessage()
                );
            }
        }
    }

    // ─────────────────────────────────────────────
    // ITEM PROCESSING (create + update দুটোতেই ব্যবহৃত)
    // ─────────────────────────────────────────────
    private BigDecimal[] applyItems(
            Sale sale,
            List<SaleItemRequestDto> itemDtos,
            SaleStatus targetStatus,
            Long businessId
    ) {
        BigDecimal subtotal      = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        for (SaleItemRequestDto itemDto : itemDtos) {

            Product product = productRepository
                    .findById(itemDto.getProductId())
                    .orElseThrow(() ->
                            new RuntimeException("Product not found: "
                                    + itemDto.getProductId()));

            if (SaleStatus.COMPLETED.equals(targetStatus)) {
                if (product.getStockQuantity() < itemDto.getQuantity()) {
                    throw new RuntimeException(
                            "Insufficient stock for: " + product.getName()
                                    + ". Available: " + product.getStockQuantity()
                                    + ", Requested: " + itemDto.getQuantity()
                    );
                }
            }

            BigDecimal discountAmount = resolveDiscountAmount(
                    itemDto, itemDto.getQuantity(), itemDto.getUnitPrice());

            BigDecimal discountPct = resolveDiscountPercentage(
                    itemDto, itemDto.getQuantity(), itemDto.getUnitPrice());

            BigDecimal lineSubtotal = itemDto.getUnitPrice()
                    .multiply(BigDecimal.valueOf(itemDto.getQuantity()));

            BigDecimal itemTotal = lineSubtotal
                    .subtract(discountAmount)
                    .max(BigDecimal.ZERO);

            int stockBefore = product.getStockQuantity();
            int stockAfter  = stockBefore;

            if (SaleStatus.COMPLETED.equals(targetStatus)) {
                // ✅ StockService দিয়ে stock out (Stock + StockTransaction + Product সব sync)
                stockService.stockOut(
                        product.getId(),
                        businessId,
                        itemDto.getQuantity(),
                        "SALE",
                        sale.getInvoiceNo(),
                        sale.getId(),
                        "Sale created/updated as COMPLETED"
                );
                stockAfter = stockBefore - itemDto.getQuantity();
            }

            SaleItem saleItem = SaleItem.builder()
                    .product(product)
                    .category(product.getCategory())
                    .quantity(itemDto.getQuantity())
                    .unitPrice(itemDto.getUnitPrice())
                    .discountAmount(discountAmount)
                    .discountPercentage(discountPct)
                    .itemTotal(itemTotal)
                    .stockBefore(stockBefore)
                    .stockAfter(stockAfter)
                    .costPriceAtSale(product.getPurchasePrice())
                    .warranty(itemDto.getWarranty())
                    .serialNumbers(itemDto.getSerialNumbers())
                    .build();

            sale.addItem(saleItem);

            subtotal      = subtotal.add(lineSubtotal);
            totalDiscount = totalDiscount.add(discountAmount);
        }

        return new BigDecimal[] { subtotal, totalDiscount };
    }

    private void finalizeTotals(Sale sale, BigDecimal subtotal, BigDecimal totalDiscount) {
        BigDecimal invoiceDiscount = sale.getInvoiceDiscount() != null
                ? sale.getInvoiceDiscount()
                : BigDecimal.ZERO;

        BigDecimal grandTotal  = subtotal
                .subtract(totalDiscount)
                .subtract(invoiceDiscount)
                .max(BigDecimal.ZERO);
        BigDecimal advancePaid = sale.getAdvancePaid();
        BigDecimal dueAmount   = grandTotal.subtract(advancePaid).max(BigDecimal.ZERO);

        PaymentStatus paymentStatus;
        if (dueAmount.compareTo(BigDecimal.ZERO) == 0) {
            paymentStatus = PaymentStatus.PAID;
        } else if (advancePaid.compareTo(BigDecimal.ZERO) > 0) {
            paymentStatus = PaymentStatus.PARTIAL;
        } else {
            paymentStatus = PaymentStatus.DUE;
        }

        sale.setSubtotal(subtotal);
        sale.setTotalDiscount(totalDiscount);
        sale.setGrandTotal(grandTotal);
        sale.setPaidAmount(advancePaid);
        sale.setDueAmount(dueAmount);
        sale.setPaymentStatus(paymentStatus);
    }

    // ─────────────────────────────────────────────
    // INVOICE NUMBER GENERATOR
    // ─────────────────────────────────────────────
    private String generateInvoiceNo() {
        String datePart = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String prefix = "INV-" + datePart + "-";

        long count = saleRepository.countByInvoiceNoStartingWith(prefix);

        String sequence  = String.format("%03d", count + 1);
        String invoiceNo = prefix + sequence;

        while (saleRepository.existsByInvoiceNo(invoiceNo)) {
            count++;
            sequence  = String.format("%03d", count + 1);
            invoiceNo = prefix + sequence;
        }

        return invoiceNo;
    }

    // ─────────────────────────────────────────────
    // DISCOUNT HELPERS
    // ─────────────────────────────────────────────
    private BigDecimal resolveDiscountAmount(
            SaleItemRequestDto dto, int qty, BigDecimal unitPrice
    ) {
        if (dto.getDiscountAmount() != null
                && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            return dto.getDiscountAmount();
        }

        if (dto.getDiscountPercentage() != null
                && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            return lineTotal
                    .multiply(dto.getDiscountPercentage())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    private BigDecimal resolveDiscountPercentage(
            SaleItemRequestDto dto, int qty, BigDecimal unitPrice
    ) {
        if (dto.getDiscountPercentage() != null
                && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
            return dto.getDiscountPercentage();
        }

        if (dto.getDiscountAmount() != null
                && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
            if (lineTotal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
            return dto.getDiscountAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(lineTotal, 2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    // ─────────────────────────────────────────────
    // ✅ NEW: AMOUNT IN WORDS (English, Lakh/Crore system)
    // ─────────────────────────────────────────────
    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private String convertToWords(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        amount = amount.setScale(2, RoundingMode.HALF_UP);

        long taka   = amount.longValue();
        int  poisha = amount.subtract(BigDecimal.valueOf(taka))
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .intValue();

        String takaWords = (taka == 0) ? "Zero" : numberToWords(taka);
        StringBuilder result = new StringBuilder("Taka ").append(takaWords);

        if (poisha > 0) {
            result.append(" and ").append(numberToWords(poisha)).append(" Poisha");
        }
        result.append(" Only");

        return result.toString();
    }

    private String numberToWords(long number) {
        if (number == 0) return "";
        if (number < 20) return ONES[(int) number];
        if (number < 100) {
            return TENS[(int) (number / 10)]
                    + (number % 10 != 0 ? " " + ONES[(int) (number % 10)] : "");
        }
        if (number < 1000) {
            return ONES[(int) (number / 100)] + " Hundred"
                    + (number % 100 != 0 ? " " + numberToWords(number % 100) : "");
        }
        if (number < 100000) { // Thousand
            return numberToWords(number / 1000) + " Thousand"
                    + (number % 1000 != 0 ? " " + numberToWords(number % 1000) : "");
        }
        if (number < 10000000) { // Lakh
            return numberToWords(number / 100000) + " Lakh"
                    + (number % 100000 != 0 ? " " + numberToWords(number % 100000) : "");
        }
        // Crore
        return numberToWords(number / 10000000) + " Crore"
                + (number % 10000000 != 0 ? " " + numberToWords(number % 10000000) : "");
    }

    // ─────────────────────────────────────────────
    // MAPPER
    // ─────────────────────────────────────────────
    private SaleResponseDto mapToResponse(Sale sale) {

        List<SaleItemResponseDto> itemDtos = sale.getItems()
                .stream()
                .map(item -> SaleItemResponseDto.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .categoryName(item.getCategory() != null ? item.getCategory().getName() : "N/A")
                        .unit(item.getProduct().getUnit())
                        .stockBefore(item.getStockBefore())
                        .stockAfter(item.getStockAfter())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .discountAmount(item.getDiscountAmount())
                        .discountPercentage(item.getDiscountPercentage())
                        .itemTotal(item.getItemTotal())
                        .warranty(item.getWarranty())
                        .serialNumbers(item.getSerialNumbers())
                        .build())
                .collect(Collectors.toList());

        List<SalePaymentResponseDto> paymentDtos = sale.getPayments()
                .stream()
                .map(payment -> SalePaymentResponseDto.builder()
                        .id(payment.getId())
                        .paymentMethod(payment.getPaymentMethod())
                        .amount(payment.getAmount())
                        .paymentDate(payment.getPaymentDate())
                        .referenceNo(payment.getReferenceNo())
                        .notes(payment.getNotes())
                        .build())
                .collect(Collectors.toList());

        return SaleResponseDto.builder()
                .id(sale.getId())
                .invoiceNo(sale.getInvoiceNo())
                .customerId(sale.getCustomer().getId())
                .customerName(sale.getCustomer().getName())
                .customerPhone(sale.getCustomer().getMobile())
                .businessId(sale.getBusiness().getId())
                .businessName(sale.getBusiness().getBusinessName())
                .businessAddress(sale.getBusiness().getAddress())
                .saleDate(sale.getSaleDate())
                .status(sale.getStatus())
                .paymentStatus(sale.getPaymentStatus())
                .subtotal(sale.getSubtotal())
                .totalDiscount(sale.getTotalDiscount())
                .invoiceDiscount(sale.getInvoiceDiscount())
                .grandTotal(sale.getGrandTotal())
                .paidAmount(sale.getPaidAmount())
                .dueAmount(sale.getDueAmount())
                .advancePaid(sale.getAdvancePaid())
                .notes(sale.getNotes())
                .amountInWords(convertToWords(sale.getGrandTotal()))   // ✅ NEW
                .items(itemDtos)
                .payments(paymentDtos)
                .createdAt(sale.getCreatedAt())
                .updatedAt(sale.getUpdatedAt())
                .build();
    }
}


//package com.sbmp.sales.service.impl;
//
//import com.sbmp.business.entity.Business;
//import com.sbmp.business.repository.BusinessRepository;
//import com.sbmp.customer.entity.Customer;
//import com.sbmp.customer.repository.CustomerRepository;
//import com.sbmp.inventory.product.entity.Product;
//import com.sbmp.inventory.product.repository.ProductRepository;
//import com.sbmp.inventory.stock.service.StockService;
//import com.sbmp.sales.dto.SaleItemRequestDto;
//import com.sbmp.sales.dto.SaleItemResponseDto;
//import com.sbmp.sales.dto.SalePaymentResponseDto;
//import com.sbmp.sales.dto.SaleRequestDto;
//import com.sbmp.sales.dto.SaleResponseDto;
//import com.sbmp.sales.entity.Sale;
//import com.sbmp.sales.entity.SaleItem;
//import com.sbmp.sales.entity.SalePayment;
//import com.sbmp.sales.enums.PaymentMethod;
//import com.sbmp.sales.enums.PaymentStatus;
//import com.sbmp.sales.enums.SaleStatus;
//import com.sbmp.sales.repository.SaleItemRepository;
//import com.sbmp.sales.repository.SaleRepository;
//import com.sbmp.sales.service.SaleService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import com.sbmp.sales.repository.SalePaymentRepository;
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//@Slf4j
//public class SaleServiceImpl implements SaleService {
//
//    private final SaleRepository      saleRepository;
//    private final SaleItemRepository  saleItemRepository;
//    private final CustomerRepository  customerRepository;
//    private final BusinessRepository  businessRepository;
//    private final ProductRepository   productRepository;
//    private final SalePaymentRepository salePaymentRepository;
//    private final StockService        stockService;   // ✅ NEW: Stock module integration
//
//    // ─────────────────────────────────────────────
//    // CREATE SALE
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public SaleResponseDto createSale(SaleRequestDto dto) {
//
//        Customer customer = customerRepository
//                .findById(dto.getCustomerId())
//                .orElseThrow(() ->
//                        new RuntimeException("Customer not found: "
//                                + dto.getCustomerId()));
//
//        Business business = businessRepository
//                .findById(dto.getBusinessId())
//                .orElseThrow(() ->
//                        new RuntimeException("Business not found: "
//                                + dto.getBusinessId()));
//
//        String invoiceNo = generateInvoiceNo();
//
//        Sale sale = Sale.builder()
//                .invoiceNo(invoiceNo)
//                .customer(customer)
//                .business(business)
//                .saleDate(dto.getSaleDate() != null
//                        ? dto.getSaleDate()
//                        : LocalDate.now())
//                .notes(dto.getNotes())
//                .advancePaid(dto.getAdvancePaid() != null
//                        ? dto.getAdvancePaid()
//                        : BigDecimal.ZERO)
//                .status(dto.getStatus() != null
//                        ? dto.getStatus()
//                        : SaleStatus.DRAFT)
//                .subtotal(BigDecimal.ZERO)
//                .totalDiscount(BigDecimal.ZERO)
//                .invoiceDiscount(dto.getInvoiceDiscount() != null
//                        ? dto.getInvoiceDiscount()
//                        : BigDecimal.ZERO)
//                .grandTotal(BigDecimal.ZERO)
//                .paidAmount(BigDecimal.ZERO)
//                .dueAmount(BigDecimal.ZERO)
//                .paymentStatus(PaymentStatus.DUE)
//                .build();
//
//        BigDecimal[] totals = applyItems(sale, dto.getItems(), dto.getStatus(), business.getId());
//        BigDecimal subtotal      = totals[0];
//        BigDecimal totalDiscount = totals[1];
//
//        finalizeTotals(sale, subtotal, totalDiscount);
//
//        recordAdvancePayment(sale, dto);
//
//        Sale saved = saleRepository.save(sale);
//
//        return mapToResponse(saved);
//    }
//
//    // ─────────────────────────────────────────────
//    // ADVANCE PAYMENT (sale তৈরির সময় auto-record)
//    // ─────────────────────────────────────────────
//    private void recordAdvancePayment(Sale sale, SaleRequestDto dto) {
//        BigDecimal advancePaid = dto.getAdvancePaid();
//
//        if (advancePaid == null || advancePaid.compareTo(BigDecimal.ZERO) <= 0) {
//            return;
//        }
//
//        PaymentMethod method = dto.getPaymentMethod() != null
//                ? dto.getPaymentMethod()
//                : PaymentMethod.CASH;
//
//        SalePayment payment = SalePayment.builder()
//                .paymentMethod(method)
//                .amount(advancePaid)
//                .paymentDate(sale.getSaleDate())
//                .build();
//
//        sale.addPayment(payment);
//    }
//
//    // ─────────────────────────────────────────────
//    // DASHBOARD KPIs
//    // ─────────────────────────────────────────────
//    @Override
//    public long countByBusiness(Long businessId) {
//        return saleRepository.countByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getTotalSale(Long businessId) {
//        return saleRepository.sumGrandTotalByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getTotalPaid(Long businessId) {
//        return saleRepository.sumPaidAmountByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getTotalDue(Long businessId) {
//        return saleRepository.sumDueAmountByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getThisMonthTotal(Long businessId) {
//        LocalDate now   = LocalDate.now();
//        LocalDate first = now.withDayOfMonth(1);
//        LocalDate last  = now.withDayOfMonth(now.lengthOfMonth());
//        return saleRepository.sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, first, last);
//    }
//
//    @Override
//    public BigDecimal getLastMonthTotal(Long businessId) {
//        LocalDate lastMonth = LocalDate.now().minusMonths(1);
//        LocalDate first     = lastMonth.withDayOfMonth(1);
//        LocalDate last      = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
//        return saleRepository.sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, first, last);
//    }
//
//    // ─────────────────────────────────────────────
//    // TABLES
//    // ─────────────────────────────────────────────
//    @Override
//    public List<SaleResponseDto> getRecentSales(Long businessId, int limit) {
//        return saleRepository
//                .findByBusinessIdOrderByCreatedAtDesc(businessId, PageRequest.of(0, limit))
//                .stream()
//                .map(this::mapToResponse)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<SaleResponseDto> getDraftSales(Long businessId) {
//        return saleRepository
//                .findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, SaleStatus.DRAFT)
//                .stream()
//                .map(this::mapToResponse)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<SaleResponseDto> getSalesByBusiness(Long businessId) {
//        return saleRepository
//                .findByBusinessIdOrderBySaleDateDesc(businessId)
//                .stream()
//                .map(this::mapToResponse)
//                .collect(Collectors.toList());
//    }
//
//    // ─────────────────────────────────────────────
//    // CHARTS
//    // ─────────────────────────────────────────────
//    @Override
//    public List<String> getTrendLabels(Long businessId) {
//        return new ArrayList<>(buildDailyTrend(businessId).keySet());
//    }
//
//    @Override
//    public List<BigDecimal> getTrendValues(Long businessId) {
//        return new ArrayList<>(buildDailyTrend(businessId).values());
//    }
//
//    private Map<String, BigDecimal> buildDailyTrend(Long businessId) {
//        Map<String, BigDecimal> trend = new LinkedHashMap<>();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");
//
//        for (int i = 6; i >= 0; i--) {
//            LocalDate day = LocalDate.now().minusDays(i);
//            BigDecimal total = saleRepository
//                    .sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, day, day);
//            trend.put(day.format(fmt), total);
//        }
//        return trend;
//    }
//
//    @Override
//    public List<String> getTopProductLabels(Long businessId) {
//        return topProducts(businessId)
//                .stream()
//                .map(row -> (String) row[0])
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<Long> getTopProductValues(Long businessId) {
//        return topProducts(businessId)
//                .stream()
//                .map(row -> ((Number) row[1]).longValue())
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<String> getPaymentLabels(Long businessId) {
//        return paymentBreakdown(businessId)
//                .stream()
//                .map(row -> ((PaymentMethod) row[0]).name())
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<BigDecimal> getPaymentValues(Long businessId) {
//        return paymentBreakdown(businessId)
//                .stream()
//                .map(row -> (BigDecimal) row[1])
//                .collect(Collectors.toList());
//    }
//
//    private List<Object[]> paymentBreakdown(Long businessId) {
//        return salePaymentRepository.findPaymentBreakdownByBusinessId(businessId);
//    }
//    private List<Object[]> topProducts(Long businessId) {
//        return saleItemRepository
//                .findTopProductsByBusinessId(businessId, PageRequest.of(0, 5));
//    }
//    @Override
//    public List<String> getTopCustomerLabels(Long businessId) {
//        return topCustomers(businessId)
//                .stream()
//                .map(com.sbmp.sales.dto.TopCustomerDto::name)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<BigDecimal> getTopCustomerValues(Long businessId) {
//        return topCustomers(businessId)
//                .stream()
//                .map(dto -> dto.total() != null ? dto.total() : BigDecimal.ZERO)
//                .collect(Collectors.toList());
//    }
//
//    private List<com.sbmp.sales.dto.TopCustomerDto> topCustomers(Long businessId) {
//        return saleRepository.findTopCustomers(businessId, PageRequest.of(0, 5));
//    }
//
//    // ─────────────────────────────────────────────
//    // SINGLE SALE
//    // ─────────────────────────────────────────────
//    @Override
//    public SaleResponseDto getSaleById(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//        return mapToResponse(sale);
//    }
//
//    @Override
//    public SaleRequestDto getSaleForEdit(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        SaleRequestDto dto = new SaleRequestDto();
//        dto.setCustomerId(sale.getCustomer().getId());
//        dto.setBusinessId(sale.getBusiness().getId());
//        dto.setSaleDate(sale.getSaleDate());
//        dto.setNotes(sale.getNotes());
//        dto.setAdvancePaid(sale.getAdvancePaid());
//        dto.setInvoiceDiscount(sale.getInvoiceDiscount());
//        dto.setStatus(sale.getStatus());
//
//        List<SaleItemRequestDto> items = sale.getItems()
//                .stream()
//                .map(item -> {
//                    SaleItemRequestDto itemDto = new SaleItemRequestDto();
//                    itemDto.setProductId(item.getProduct().getId());
//                    itemDto.setQuantity(item.getQuantity());
//                    itemDto.setUnitPrice(item.getUnitPrice());
//                    itemDto.setDiscountAmount(item.getDiscountAmount());
//                    itemDto.setDiscountPercentage(item.getDiscountPercentage());
//                    return itemDto;
//                })
//                .collect(Collectors.toList());
//
//        dto.setItems(items);
//        return dto;
//    }
//
//    // ─────────────────────────────────────────────
//    // UPDATE SALE  (শুধু DRAFT অবস্থায় edit করা যাবে)
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public SaleResponseDto updateSale(Long id, SaleRequestDto dto) {
//
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() != SaleStatus.DRAFT) {
//            throw new RuntimeException("Only DRAFT sales can be edited");
//        }
//
//        Customer customer = customerRepository
//                .findById(dto.getCustomerId())
//                .orElseThrow(() -> new RuntimeException("Customer not found: " + dto.getCustomerId()));
//
//        Business business = businessRepository
//                .findById(dto.getBusinessId())
//                .orElseThrow(() -> new RuntimeException("Business not found: " + dto.getBusinessId()));
//
//        sale.setCustomer(customer);
//        sale.setBusiness(business);
//        sale.setSaleDate(dto.getSaleDate() != null ? dto.getSaleDate() : LocalDate.now());
//        sale.setNotes(dto.getNotes());
//        sale.setAdvancePaid(dto.getAdvancePaid() != null ? dto.getAdvancePaid() : BigDecimal.ZERO);
//        sale.setInvoiceDiscount(dto.getInvoiceDiscount() != null ? dto.getInvoiceDiscount() : BigDecimal.ZERO);
//
//        // পুরোনো items মুছে নতুনগুলো বসানো হচ্ছে (orphanRemoval = true)
//        sale.getItems().clear();
//
//        BigDecimal[] totals = applyItems(sale, dto.getItems(), dto.getStatus(), business.getId());
//        finalizeTotals(sale, totals[0], totals[1]);
//
//        sale.setStatus(dto.getStatus() != null ? dto.getStatus() : sale.getStatus());
//
//        Sale saved = saleRepository.save(sale);
//        return mapToResponse(saved);
//    }
//
//    // ─────────────────────────────────────────────
//    // CANCEL SALE
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public void cancelSale(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() == SaleStatus.CANCELLED) {
//            throw new RuntimeException("Sale is already cancelled");
//        }
//
//        // COMPLETED ছিল মানে stock কাটা হয়ে গেছে — ফিরিয়ে দিতে হবে
//        if (sale.getStatus() == SaleStatus.COMPLETED) {
//            restoreStock(sale);
//        }
//
//        sale.setStatus(SaleStatus.CANCELLED);
//        saleRepository.save(sale);
//    }
//
//    // ─────────────────────────────────────────────
//    // COMPLETE SALE  (DRAFT → COMPLETED, stock deduct)
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public void completeSale(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() != SaleStatus.DRAFT) {
//            throw new RuntimeException("Only DRAFT sales can be completed");
//        }
//
//        Long businessId = sale.getBusiness().getId();
//
//        for (SaleItem item : sale.getItems()) {
//            Product product = item.getProduct();
//
//            if (product.getStockQuantity() < item.getQuantity()) {
//                throw new RuntimeException(
//                        "Insufficient stock for: " + product.getName()
//                                + ". Available: " + product.getStockQuantity()
//                                + ", Requested: " + item.getQuantity()
//                );
//            }
//
//            int stockBefore = product.getStockQuantity();
//
//            // ✅ FIX: এখন StockService দিয়ে Stock + StockTransaction ও sync হবে
//            stockService.stockOut(
//                    product.getId(),
//                    businessId,
//                    item.getQuantity(),
//                    "SALE",
//                    sale.getInvoiceNo(),
//                    sale.getId(),
//                    "Sale completed"
//            );
//
//            int stockAfter = stockBefore - item.getQuantity();
//            item.setStockBefore(stockBefore);
//            item.setStockAfter(stockAfter);
//        }
//
//        sale.setStatus(SaleStatus.COMPLETED);
//        saleRepository.save(sale);
//    }
//
//    // ─────────────────────────────────────────────
//    // DELETE SALE
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public void deleteSale(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() == SaleStatus.COMPLETED) {
//            restoreStock(sale);
//        }
//
//        saleRepository.delete(sale);
//    }
//
//    // ✅ FIX: এখন StockService.stockIn() ব্যবহার করে Stock entity + history ঠিক থাকবে
//    private void restoreStock(Sale sale) {
//        Long businessId = sale.getBusiness().getId();
//        for (SaleItem item : sale.getItems()) {
//            Product product = item.getProduct();
//            try {
//                stockService.stockIn(
//                        product.getId(),
//                        businessId,
//                        item.getQuantity(),
//                        product.getPurchasePrice(),
//                        "SALE",
//                        sale.getInvoiceNo(),
//                        sale.getId()
//                );
//            } catch (Exception e) {
//                log.error("Stock restore failed for product {} (sale {}): {}",
//                        product.getId(), sale.getId(), e.getMessage());
//                throw new RuntimeException(
//                        "Stock restore failed for product " + product.getName() + ": " + e.getMessage()
//                );
//            }
//        }
//    }
//
//    // ─────────────────────────────────────────────
//    // ITEM PROCESSING (create + update দুটোতেই ব্যবহৃত)
//    // ─────────────────────────────────────────────
//    private BigDecimal[] applyItems(
//            Sale sale,
//            List<SaleItemRequestDto> itemDtos,
//            SaleStatus targetStatus,
//            Long businessId
//    ) {
//        BigDecimal subtotal      = BigDecimal.ZERO;
//        BigDecimal totalDiscount = BigDecimal.ZERO;
//
//        for (SaleItemRequestDto itemDto : itemDtos) {
//
//            Product product = productRepository
//                    .findById(itemDto.getProductId())
//                    .orElseThrow(() ->
//                            new RuntimeException("Product not found: "
//                                    + itemDto.getProductId()));
//
//            if (SaleStatus.COMPLETED.equals(targetStatus)) {
//                if (product.getStockQuantity() < itemDto.getQuantity()) {
//                    throw new RuntimeException(
//                            "Insufficient stock for: " + product.getName()
//                                    + ". Available: " + product.getStockQuantity()
//                                    + ", Requested: " + itemDto.getQuantity()
//                    );
//                }
//            }
//
//            BigDecimal discountAmount = resolveDiscountAmount(
//                    itemDto, itemDto.getQuantity(), itemDto.getUnitPrice());
//
//            BigDecimal discountPct = resolveDiscountPercentage(
//                    itemDto, itemDto.getQuantity(), itemDto.getUnitPrice());
//
//            BigDecimal lineSubtotal = itemDto.getUnitPrice()
//                    .multiply(BigDecimal.valueOf(itemDto.getQuantity()));
//
//            BigDecimal itemTotal = lineSubtotal
//                    .subtract(discountAmount)
//                    .max(BigDecimal.ZERO);
//
//            int stockBefore = product.getStockQuantity();
//            int stockAfter  = stockBefore;
//
//            if (SaleStatus.COMPLETED.equals(targetStatus)) {
//                // ✅ FIX: StockService দিয়ে stock out (Stock + StockTransaction + Product সব sync)
//                stockService.stockOut(
//                        product.getId(),
//                        businessId,
//                        itemDto.getQuantity(),
//                        "SALE",
//                        sale.getInvoiceNo(),
//                        sale.getId(),
//                        "Sale created/updated as COMPLETED"
//                );
//                stockAfter = stockBefore - itemDto.getQuantity();
//            }
//
//            SaleItem saleItem = SaleItem.builder()
//                    .product(product)
//                    .category(product.getCategory())
//                    .quantity(itemDto.getQuantity())
//                    .unitPrice(itemDto.getUnitPrice())
//                    .discountAmount(discountAmount)
//                    .discountPercentage(discountPct)
//                    .itemTotal(itemTotal)
//                    .stockBefore(stockBefore)
//                    .stockAfter(stockAfter)
//                    .costPriceAtSale(product.getPurchasePrice())
//                    .warranty(itemDto.getWarranty())               // ✅ FIX: যোগ করা হলো
//                    .serialNumbers(itemDto.getSerialNumbers())
//                    .build();
//
//            sale.addItem(saleItem);
//
//            subtotal      = subtotal.add(lineSubtotal);
//            totalDiscount = totalDiscount.add(discountAmount);
//        }
//
//        return new BigDecimal[] { subtotal, totalDiscount };
//    }
//
//    private void finalizeTotals(Sale sale, BigDecimal subtotal, BigDecimal totalDiscount) {
//        BigDecimal invoiceDiscount = sale.getInvoiceDiscount() != null
//                ? sale.getInvoiceDiscount()
//                : BigDecimal.ZERO;
//
//        BigDecimal grandTotal  = subtotal
//                .subtract(totalDiscount)
//                .subtract(invoiceDiscount)
//                .max(BigDecimal.ZERO);
//        BigDecimal advancePaid = sale.getAdvancePaid();
//        BigDecimal dueAmount   = grandTotal.subtract(advancePaid).max(BigDecimal.ZERO);
//
//        PaymentStatus paymentStatus;
//        if (dueAmount.compareTo(BigDecimal.ZERO) == 0) {
//            paymentStatus = PaymentStatus.PAID;
//        } else if (advancePaid.compareTo(BigDecimal.ZERO) > 0) {
//            paymentStatus = PaymentStatus.PARTIAL;
//        } else {
//            paymentStatus = PaymentStatus.DUE;
//        }
//
//        sale.setSubtotal(subtotal);
//        sale.setTotalDiscount(totalDiscount);
//        sale.setGrandTotal(grandTotal);
//        sale.setPaidAmount(advancePaid);
//        sale.setDueAmount(dueAmount);
//        sale.setPaymentStatus(paymentStatus);
//    }
//
//    // ─────────────────────────────────────────────
//    // INVOICE NUMBER GENERATOR
//    // ─────────────────────────────────────────────
//    private String generateInvoiceNo() {
//        String datePart = LocalDate.now()
//                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//
//        String prefix = "INV-" + datePart + "-";
//
//        long count = saleRepository.countByInvoiceNoStartingWith(prefix);
//
//        String sequence  = String.format("%03d", count + 1);
//        String invoiceNo = prefix + sequence;
//
//        while (saleRepository.existsByInvoiceNo(invoiceNo)) {
//            count++;
//            sequence  = String.format("%03d", count + 1);
//            invoiceNo = prefix + sequence;
//        }
//
//        return invoiceNo;
//    }
//
//    // ─────────────────────────────────────────────
//    // DISCOUNT HELPERS
//    // ─────────────────────────────────────────────
//    private BigDecimal resolveDiscountAmount(
//            SaleItemRequestDto dto, int qty, BigDecimal unitPrice
//    ) {
//        if (dto.getDiscountAmount() != null
//                && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
//            return dto.getDiscountAmount();
//        }
//
//        if (dto.getDiscountPercentage() != null
//                && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
//            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
//            return lineTotal
//                    .multiply(dto.getDiscountPercentage())
//                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
//        }
//
//        return BigDecimal.ZERO;
//    }
//
//    private BigDecimal resolveDiscountPercentage(
//            SaleItemRequestDto dto, int qty, BigDecimal unitPrice
//    ) {
//        if (dto.getDiscountPercentage() != null
//                && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
//            return dto.getDiscountPercentage();
//        }
//
//        if (dto.getDiscountAmount() != null
//                && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
//            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
//            if (lineTotal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
//            return dto.getDiscountAmount()
//                    .multiply(BigDecimal.valueOf(100))
//                    .divide(lineTotal, 2, RoundingMode.HALF_UP);
//        }
//
//        return BigDecimal.ZERO;
//    }
//
//    // ─────────────────────────────────────────────
//    // MAPPER
//    // ─────────────────────────────────────────────
//    private SaleResponseDto mapToResponse(Sale sale) {
//
//        List<SaleItemResponseDto> itemDtos = sale.getItems()
//                .stream()
//                .map(item -> SaleItemResponseDto.builder()
//                        .id(item.getId())
//                        .productId(item.getProduct().getId())
//                        .productName(item.getProduct().getName())
//                        .categoryName(item.getCategory() != null ? item.getCategory().getName() : "N/A")
//                        .unit(item.getProduct().getUnit())              // ✅ FIX: ResponseDto তে unit ফিল্ড আছে, সেটাও missing ছিল
//                        .stockBefore(item.getStockBefore())
//                        .stockAfter(item.getStockAfter())
//                        .quantity(item.getQuantity())
//                        .unitPrice(item.getUnitPrice())
//                        .discountAmount(item.getDiscountAmount())
//                        .discountPercentage(item.getDiscountPercentage())
//                        .itemTotal(item.getItemTotal())
//                        .warranty(item.getWarranty())                   // ✅ FIX: যোগ করা হলো
//                        .serialNumbers(item.getSerialNumbers())          // ✅ FIX: যোগ করা হলো
//                        .build())
//                .collect(Collectors.toList());
//
//        List<SalePaymentResponseDto> paymentDtos = sale.getPayments()
//                .stream()
//                .map(payment -> SalePaymentResponseDto.builder()
//                        .id(payment.getId())
//                        .paymentMethod(payment.getPaymentMethod())
//                        .amount(payment.getAmount())
//                        .paymentDate(payment.getPaymentDate())
//                        .referenceNo(payment.getReferenceNo())
//                        .notes(payment.getNotes())
//                        .build())
//                .collect(Collectors.toList());
//
//        return SaleResponseDto.builder()
//                .id(sale.getId())
//                .invoiceNo(sale.getInvoiceNo())
//                .customerId(sale.getCustomer().getId())
//                .customerName(sale.getCustomer().getName())
//                .customerPhone(sale.getCustomer().getMobile())
//                .businessId(sale.getBusiness().getId())
//                .businessName(sale.getBusiness().getBusinessName())
//                .businessAddress(sale.getBusiness().getAddress())
//                .saleDate(sale.getSaleDate())
//                .status(sale.getStatus())
//                .paymentStatus(sale.getPaymentStatus())
//                .subtotal(sale.getSubtotal())
//                .totalDiscount(sale.getTotalDiscount())
//                .invoiceDiscount(sale.getInvoiceDiscount())
//                .grandTotal(sale.getGrandTotal())
//                .paidAmount(sale.getPaidAmount())
//                .dueAmount(sale.getDueAmount())
//                .advancePaid(sale.getAdvancePaid())
//                .notes(sale.getNotes())
//                .items(itemDtos)
//                .payments(paymentDtos)
//                .createdAt(sale.getCreatedAt())
//                .updatedAt(sale.getUpdatedAt())
//                .build();
//    }
//}

//package com.sbmp.sales.service.impl;
//
//import com.sbmp.business.entity.Business;
//import com.sbmp.business.repository.BusinessRepository;
//import com.sbmp.customer.entity.Customer;
//import com.sbmp.customer.repository.CustomerRepository;
//import com.sbmp.inventory.product.entity.Product;
//import com.sbmp.inventory.product.repository.ProductRepository;
//import com.sbmp.sales.dto.SaleItemRequestDto;
//import com.sbmp.sales.dto.SaleItemResponseDto;
//import com.sbmp.sales.dto.SalePaymentResponseDto;
//import com.sbmp.sales.dto.SaleRequestDto;
//import com.sbmp.sales.dto.SaleResponseDto;
//import com.sbmp.sales.entity.Sale;
//import com.sbmp.sales.entity.SaleItem;
//import com.sbmp.sales.entity.SalePayment;
//import com.sbmp.sales.enums.PaymentMethod;
//import com.sbmp.sales.enums.PaymentStatus;
//import com.sbmp.sales.enums.SaleStatus;
//import com.sbmp.sales.repository.SaleItemRepository;
//import com.sbmp.sales.repository.SaleRepository;
//import com.sbmp.sales.service.SaleService;
//import lombok.RequiredArgsConstructor;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//import com.sbmp.sales.repository.SalePaymentRepository;
//import java.math.BigDecimal;
//import java.math.RoundingMode;
//import java.time.LocalDate;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.LinkedHashMap;
//import java.util.List;
//import java.util.Map;
//import java.util.stream.Collectors;
//
//@Service
//@RequiredArgsConstructor
//public class SaleServiceImpl implements SaleService {
//
//    private final SaleRepository      saleRepository;
//    private final SaleItemRepository  saleItemRepository;
//    private final CustomerRepository  customerRepository;
//    private final BusinessRepository  businessRepository;
//    private final ProductRepository   productRepository;
//    private final SalePaymentRepository salePaymentRepository;
//
//    // ─────────────────────────────────────────────
//    // CREATE SALE
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public SaleResponseDto createSale(SaleRequestDto dto) {
//
//        Customer customer = customerRepository
//                .findById(dto.getCustomerId())
//                .orElseThrow(() ->
//                        new RuntimeException("Customer not found: "
//                                + dto.getCustomerId()));
//
//        Business business = businessRepository
//                .findById(dto.getBusinessId())
//                .orElseThrow(() ->
//                        new RuntimeException("Business not found: "
//                                + dto.getBusinessId()));
//
//        String invoiceNo = generateInvoiceNo();
//
//        Sale sale = Sale.builder()
//                .invoiceNo(invoiceNo)
//                .customer(customer)
//                .business(business)
//                .saleDate(dto.getSaleDate() != null
//                        ? dto.getSaleDate()
//                        : LocalDate.now())
//                .notes(dto.getNotes())
//                .advancePaid(dto.getAdvancePaid() != null
//                        ? dto.getAdvancePaid()
//                        : BigDecimal.ZERO)
//                .status(dto.getStatus() != null
//                        ? dto.getStatus()
//                        : SaleStatus.DRAFT)
//                .subtotal(BigDecimal.ZERO)
//                .totalDiscount(BigDecimal.ZERO)
//                .invoiceDiscount(dto.getInvoiceDiscount() != null
//                        ? dto.getInvoiceDiscount()
//                        : BigDecimal.ZERO)
//                .grandTotal(BigDecimal.ZERO)
//                .paidAmount(BigDecimal.ZERO)
//                .dueAmount(BigDecimal.ZERO)
//                .paymentStatus(PaymentStatus.DUE)
//                .build();
//
//        BigDecimal[] totals = applyItems(sale, dto.getItems(), dto.getStatus());
//        BigDecimal subtotal      = totals[0];
//        BigDecimal totalDiscount = totals[1];
//
//        finalizeTotals(sale, subtotal, totalDiscount);
//
//        recordAdvancePayment(sale, dto);
//
//        Sale saved = saleRepository.save(sale);
//
//        return mapToResponse(saved);
//    }
//
//    // ─────────────────────────────────────────────
//    // ADVANCE PAYMENT (sale তৈরির সময় auto-record)
//    // ─────────────────────────────────────────────
//    private void recordAdvancePayment(Sale sale, SaleRequestDto dto) {
//        BigDecimal advancePaid = dto.getAdvancePaid();
//
//        if (advancePaid == null || advancePaid.compareTo(BigDecimal.ZERO) <= 0) {
//            return;
//        }
//
//        PaymentMethod method = dto.getPaymentMethod() != null
//                ? dto.getPaymentMethod()
//                : PaymentMethod.CASH;
//
//        SalePayment payment = SalePayment.builder()
//                .paymentMethod(method)
//                .amount(advancePaid)
//                .paymentDate(sale.getSaleDate())
//                .build();
//
//        sale.addPayment(payment);
//    }
//
//    // ─────────────────────────────────────────────
//    // DASHBOARD KPIs
//    // ─────────────────────────────────────────────
//    @Override
//    public long countByBusiness(Long businessId) {
//        return saleRepository.countByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getTotalSale(Long businessId) {
//        return saleRepository.sumGrandTotalByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getTotalPaid(Long businessId) {
//        return saleRepository.sumPaidAmountByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getTotalDue(Long businessId) {
//        return saleRepository.sumDueAmountByBusinessId(businessId);
//    }
//
//    @Override
//    public BigDecimal getThisMonthTotal(Long businessId) {
//        LocalDate now   = LocalDate.now();
//        LocalDate first = now.withDayOfMonth(1);
//        LocalDate last  = now.withDayOfMonth(now.lengthOfMonth());
//        return saleRepository.sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, first, last);
//    }
//
//    @Override
//    public BigDecimal getLastMonthTotal(Long businessId) {
//        LocalDate lastMonth = LocalDate.now().minusMonths(1);
//        LocalDate first     = lastMonth.withDayOfMonth(1);
//        LocalDate last      = lastMonth.withDayOfMonth(lastMonth.lengthOfMonth());
//        return saleRepository.sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, first, last);
//    }
//
//    // ─────────────────────────────────────────────
//    // TABLES
//    // ─────────────────────────────────────────────
//    @Override
//    public List<SaleResponseDto> getRecentSales(Long businessId, int limit) {
//        return saleRepository
//                .findByBusinessIdOrderByCreatedAtDesc(businessId, PageRequest.of(0, limit))
//                .stream()
//                .map(this::mapToResponse)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<SaleResponseDto> getDraftSales(Long businessId) {
//        return saleRepository
//                .findByBusinessIdAndStatusOrderByCreatedAtDesc(businessId, SaleStatus.DRAFT)
//                .stream()
//                .map(this::mapToResponse)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<SaleResponseDto> getSalesByBusiness(Long businessId) {
//        return saleRepository
//                .findByBusinessIdOrderBySaleDateDesc(businessId)
//                .stream()
//                .map(this::mapToResponse)
//                .collect(Collectors.toList());
//    }
//
//    // ─────────────────────────────────────────────
//    // CHARTS
//    // ─────────────────────────────────────────────
//    @Override
//    public List<String> getTrendLabels(Long businessId) {
//        return new ArrayList<>(buildDailyTrend(businessId).keySet());
//    }
//
//    @Override
//    public List<BigDecimal> getTrendValues(Long businessId) {
//        return new ArrayList<>(buildDailyTrend(businessId).values());
//    }
//
//    private Map<String, BigDecimal> buildDailyTrend(Long businessId) {
//        Map<String, BigDecimal> trend = new LinkedHashMap<>();
//        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM");
//
//        for (int i = 6; i >= 0; i--) {
//            LocalDate day = LocalDate.now().minusDays(i);
//            BigDecimal total = saleRepository
//                    .sumGrandTotalByBusinessIdAndSaleDateBetween(businessId, day, day);
//            trend.put(day.format(fmt), total);
//        }
//        return trend;
//    }
//
//    @Override
//    public List<String> getTopProductLabels(Long businessId) {
//        return topProducts(businessId)
//                .stream()
//                .map(row -> (String) row[0])
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<Long> getTopProductValues(Long businessId) {
//        return topProducts(businessId)
//                .stream()
//                .map(row -> ((Number) row[1]).longValue())
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<String> getPaymentLabels(Long businessId) {
//        return paymentBreakdown(businessId)
//                .stream()
//                .map(row -> ((PaymentMethod) row[0]).name())
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<BigDecimal> getPaymentValues(Long businessId) {
//        return paymentBreakdown(businessId)
//                .stream()
//                .map(row -> (BigDecimal) row[1])
//                .collect(Collectors.toList());
//    }
//
//    private List<Object[]> paymentBreakdown(Long businessId) {
//        return salePaymentRepository.findPaymentBreakdownByBusinessId(businessId);
//    }
//    private List<Object[]> topProducts(Long businessId) {
//        return saleItemRepository
//                .findTopProductsByBusinessId(businessId, PageRequest.of(0, 5));
//    }
//    @Override
//    public List<String> getTopCustomerLabels(Long businessId) {
//        return topCustomers(businessId)
//                .stream()
//                .map(com.sbmp.sales.dto.TopCustomerDto::name)
//                .collect(Collectors.toList());
//    }
//
//    @Override
//    public List<BigDecimal> getTopCustomerValues(Long businessId) {
//        return topCustomers(businessId)
//                .stream()
//                .map(dto -> dto.total() != null ? dto.total() : BigDecimal.ZERO)
//                .collect(Collectors.toList());
//    }
//
//    private List<com.sbmp.sales.dto.TopCustomerDto> topCustomers(Long businessId) {
//        return saleRepository.findTopCustomers(businessId, PageRequest.of(0, 5));
//    }
//
//    // ─────────────────────────────────────────────
//    // SINGLE SALE
//    // ─────────────────────────────────────────────
//    @Override
//    public SaleResponseDto getSaleById(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//        return mapToResponse(sale);
//    }
//
//    @Override
//    public SaleRequestDto getSaleForEdit(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        SaleRequestDto dto = new SaleRequestDto();
//        dto.setCustomerId(sale.getCustomer().getId());
//        dto.setBusinessId(sale.getBusiness().getId());
//        dto.setSaleDate(sale.getSaleDate());
//        dto.setNotes(sale.getNotes());
//        dto.setAdvancePaid(sale.getAdvancePaid());
//        dto.setInvoiceDiscount(sale.getInvoiceDiscount());
//        dto.setStatus(sale.getStatus());
//
//        List<SaleItemRequestDto> items = sale.getItems()
//                .stream()
//                .map(item -> {
//                    SaleItemRequestDto itemDto = new SaleItemRequestDto();
//                    itemDto.setProductId(item.getProduct().getId());
//                    itemDto.setQuantity(item.getQuantity());
//                    itemDto.setUnitPrice(item.getUnitPrice());
//                    itemDto.setDiscountAmount(item.getDiscountAmount());
//                    itemDto.setDiscountPercentage(item.getDiscountPercentage());
//                    return itemDto;
//                })
//                .collect(Collectors.toList());
//
//        dto.setItems(items);
//        return dto;
//    }
//
//    // ─────────────────────────────────────────────
//    // UPDATE SALE  (শুধু DRAFT অবস্থায় edit করা যাবে)
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public SaleResponseDto updateSale(Long id, SaleRequestDto dto) {
//
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() != SaleStatus.DRAFT) {
//            throw new RuntimeException("Only DRAFT sales can be edited");
//        }
//
//        Customer customer = customerRepository
//                .findById(dto.getCustomerId())
//                .orElseThrow(() -> new RuntimeException("Customer not found: " + dto.getCustomerId()));
//
//        Business business = businessRepository
//                .findById(dto.getBusinessId())
//                .orElseThrow(() -> new RuntimeException("Business not found: " + dto.getBusinessId()));
//
//        sale.setCustomer(customer);
//        sale.setBusiness(business);
//        sale.setSaleDate(dto.getSaleDate() != null ? dto.getSaleDate() : LocalDate.now());
//        sale.setNotes(dto.getNotes());
//        sale.setAdvancePaid(dto.getAdvancePaid() != null ? dto.getAdvancePaid() : BigDecimal.ZERO);
//        sale.setInvoiceDiscount(dto.getInvoiceDiscount() != null ? dto.getInvoiceDiscount() : BigDecimal.ZERO);
//
//        // পুরোনো items মুছে নতুনগুলো বসানো হচ্ছে (orphanRemoval = true)
//        sale.getItems().clear();
//
//        BigDecimal[] totals = applyItems(sale, dto.getItems(), dto.getStatus());
//        finalizeTotals(sale, totals[0], totals[1]);
//
//        sale.setStatus(dto.getStatus() != null ? dto.getStatus() : sale.getStatus());
//
//        Sale saved = saleRepository.save(sale);
//        return mapToResponse(saved);
//    }
//
//    // ─────────────────────────────────────────────
//    // CANCEL SALE
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public void cancelSale(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() == SaleStatus.CANCELLED) {
//            throw new RuntimeException("Sale is already cancelled");
//        }
//
//        // COMPLETED ছিল মানে stock কাটা হয়ে গেছে — ফিরিয়ে দিতে হবে
//        if (sale.getStatus() == SaleStatus.COMPLETED) {
//            restoreStock(sale);
//        }
//
//        sale.setStatus(SaleStatus.CANCELLED);
//        saleRepository.save(sale);
//    }
//
//    // ─────────────────────────────────────────────
//    // COMPLETE SALE  (DRAFT → COMPLETED, stock deduct)
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public void completeSale(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() != SaleStatus.DRAFT) {
//            throw new RuntimeException("Only DRAFT sales can be completed");
//        }
//
//        for (SaleItem item : sale.getItems()) {
//            Product product = item.getProduct();
//
//            if (product.getStockQuantity() < item.getQuantity()) {
//                throw new RuntimeException(
//                        "Insufficient stock for: " + product.getName()
//                                + ". Available: " + product.getStockQuantity()
//                                + ", Requested: " + item.getQuantity()
//                );
//            }
//
//            int stockBefore = product.getStockQuantity();
//            int stockAfter  = stockBefore - item.getQuantity();
//
//            product.setStockQuantity(stockAfter);
//            productRepository.save(product);
//
//            item.setStockBefore(stockBefore);
//            item.setStockAfter(stockAfter);
//        }
//
//        sale.setStatus(SaleStatus.COMPLETED);
//        saleRepository.save(sale);
//    }
//
//    // ─────────────────────────────────────────────
//    // DELETE SALE
//    // ─────────────────────────────────────────────
//    @Override
//    @Transactional
//    public void deleteSale(Long id) {
//        Sale sale = saleRepository.findById(id)
//                .orElseThrow(() -> new RuntimeException("Sale not found: " + id));
//
//        if (sale.getStatus() == SaleStatus.COMPLETED) {
//            restoreStock(sale);
//        }
//
//        saleRepository.delete(sale);
//    }
//
//    private void restoreStock(Sale sale) {
//        for (SaleItem item : sale.getItems()) {
//            Product product = item.getProduct();
//            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
//            productRepository.save(product);
//        }
//    }
//
//    // ─────────────────────────────────────────────
//    // ITEM PROCESSING (create + update দুটোতেই ব্যবহৃত)
//    // ─────────────────────────────────────────────
//    private BigDecimal[] applyItems(
//            Sale sale,
//            List<SaleItemRequestDto> itemDtos,
//            SaleStatus targetStatus
//    ) {
//        BigDecimal subtotal      = BigDecimal.ZERO;
//        BigDecimal totalDiscount = BigDecimal.ZERO;
//
//        for (SaleItemRequestDto itemDto : itemDtos) {
//
//            Product product = productRepository
//                    .findById(itemDto.getProductId())
//                    .orElseThrow(() ->
//                            new RuntimeException("Product not found: "
//                                    + itemDto.getProductId()));
//
//            if (SaleStatus.COMPLETED.equals(targetStatus)) {
//                if (product.getStockQuantity() < itemDto.getQuantity()) {
//                    throw new RuntimeException(
//                            "Insufficient stock for: " + product.getName()
//                                    + ". Available: " + product.getStockQuantity()
//                                    + ", Requested: " + itemDto.getQuantity()
//                    );
//                }
//            }
//
//            BigDecimal discountAmount = resolveDiscountAmount(
//                    itemDto, itemDto.getQuantity(), itemDto.getUnitPrice());
//
//            BigDecimal discountPct = resolveDiscountPercentage(
//                    itemDto, itemDto.getQuantity(), itemDto.getUnitPrice());
//
//            BigDecimal lineSubtotal = itemDto.getUnitPrice()
//                    .multiply(BigDecimal.valueOf(itemDto.getQuantity()));
//
//            BigDecimal itemTotal = lineSubtotal
//                    .subtract(discountAmount)
//                    .max(BigDecimal.ZERO);
//
//            int stockBefore = product.getStockQuantity();
//            int stockAfter  = stockBefore;
//
//            if (SaleStatus.COMPLETED.equals(targetStatus)) {
//                stockAfter = stockBefore - itemDto.getQuantity();
//                product.setStockQuantity(stockAfter);
//                productRepository.save(product);
//            }
//
//            SaleItem saleItem = SaleItem.builder()
//                    .product(product)
//                    .category(product.getCategory())
//                    .quantity(itemDto.getQuantity())
//                    .unitPrice(itemDto.getUnitPrice())
//                    .discountAmount(discountAmount)
//                    .discountPercentage(discountPct)
//                    .itemTotal(itemTotal)
//                    .stockBefore(stockBefore)
//                    .stockAfter(stockAfter)
//                    .costPriceAtSale(product.getPurchasePrice())
//                    .warranty(itemDto.getWarranty())
//                    .serialNumbers(itemDto.getSerialNumbers())
//                    .build();
//
//            sale.addItem(saleItem);
//
//            subtotal      = subtotal.add(lineSubtotal);
//            totalDiscount = totalDiscount.add(discountAmount);
//        }
//
//        return new BigDecimal[] { subtotal, totalDiscount };
//    }
//
//    private void finalizeTotals(Sale sale, BigDecimal subtotal, BigDecimal totalDiscount) {
//        BigDecimal invoiceDiscount = sale.getInvoiceDiscount() != null
//                ? sale.getInvoiceDiscount()
//                : BigDecimal.ZERO;
//
//        BigDecimal grandTotal  = subtotal
//                .subtract(totalDiscount)
//                .subtract(invoiceDiscount)
//                .max(BigDecimal.ZERO);
//        BigDecimal advancePaid = sale.getAdvancePaid();
//        BigDecimal dueAmount   = grandTotal.subtract(advancePaid).max(BigDecimal.ZERO);
//
//        PaymentStatus paymentStatus;
//        if (dueAmount.compareTo(BigDecimal.ZERO) == 0) {
//            paymentStatus = PaymentStatus.PAID;
//        } else if (advancePaid.compareTo(BigDecimal.ZERO) > 0) {
//            paymentStatus = PaymentStatus.PARTIAL;
//        } else {
//            paymentStatus = PaymentStatus.DUE;
//        }
//
//        sale.setSubtotal(subtotal);
//        sale.setTotalDiscount(totalDiscount);
//        sale.setGrandTotal(grandTotal);
//        sale.setPaidAmount(advancePaid);
//        sale.setDueAmount(dueAmount);
//        sale.setPaymentStatus(paymentStatus);
//    }
//
//    // ─────────────────────────────────────────────
//    // INVOICE NUMBER GENERATOR
//    // ─────────────────────────────────────────────
//    private String generateInvoiceNo() {
//        String datePart = LocalDate.now()
//                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
//
//        String prefix = "INV-" + datePart + "-";
//
//        long count = saleRepository.countByInvoiceNoStartingWith(prefix);
//
//        String sequence  = String.format("%03d", count + 1);
//        String invoiceNo = prefix + sequence;
//
//        while (saleRepository.existsByInvoiceNo(invoiceNo)) {
//            count++;
//            sequence  = String.format("%03d", count + 1);
//            invoiceNo = prefix + sequence;
//        }
//
//        return invoiceNo;
//    }
//
//    // ─────────────────────────────────────────────
//    // DISCOUNT HELPERS
//    // ─────────────────────────────────────────────
//    private BigDecimal resolveDiscountAmount(
//            SaleItemRequestDto dto, int qty, BigDecimal unitPrice
//    ) {
//        if (dto.getDiscountAmount() != null
//                && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
//            return dto.getDiscountAmount();
//        }
//
//        if (dto.getDiscountPercentage() != null
//                && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
//            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
//            return lineTotal
//                    .multiply(dto.getDiscountPercentage())
//                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
//        }
//
//        return BigDecimal.ZERO;
//    }
//
//    private BigDecimal resolveDiscountPercentage(
//            SaleItemRequestDto dto, int qty, BigDecimal unitPrice
//    ) {
//        if (dto.getDiscountPercentage() != null
//                && dto.getDiscountPercentage().compareTo(BigDecimal.ZERO) > 0) {
//            return dto.getDiscountPercentage();
//        }
//
//        if (dto.getDiscountAmount() != null
//                && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
//            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(qty));
//            if (lineTotal.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
//            return dto.getDiscountAmount()
//                    .multiply(BigDecimal.valueOf(100))
//                    .divide(lineTotal, 2, RoundingMode.HALF_UP);
//        }
//
//        return BigDecimal.ZERO;
//    }
//
//    // ─────────────────────────────────────────────
//    // MAPPER
//    // ─────────────────────────────────────────────
//    private SaleResponseDto mapToResponse(Sale sale) {
//
//        List<SaleItemResponseDto> itemDtos = sale.getItems()
//                .stream()
//                .map(item -> SaleItemResponseDto.builder()
//                        .id(item.getId())
//                        .productId(item.getProduct().getId())
//                        .productName(item.getProduct().getName())
//                        .categoryName(item.getCategory().getName())
//                        .unit(item.getProduct().getUnit())
//                        .stockBefore(item.getStockBefore())
//                        .stockAfter(item.getStockAfter())
//                        .quantity(item.getQuantity())
//                        .unitPrice(item.getUnitPrice())
//                        .discountAmount(item.getDiscountAmount())
//                        .discountPercentage(item.getDiscountPercentage())
//                        .itemTotal(item.getItemTotal())
//                        .warranty(item.getWarranty())
//                        .serialNumbers(item.getSerialNumbers())
//                        .build())
//                .collect(Collectors.toList());
//
//        List<SalePaymentResponseDto> paymentDtos = sale.getPayments()
//                .stream()
//                .map(payment -> SalePaymentResponseDto.builder()
//                        .id(payment.getId())
//                        .paymentMethod(payment.getPaymentMethod())
//                        .amount(payment.getAmount())
//                        .paymentDate(payment.getPaymentDate())
//                        .referenceNo(payment.getReferenceNo())
//                        .notes(payment.getNotes())
//                        .build())
//                .collect(Collectors.toList());
//
//        return SaleResponseDto.builder()
//                .id(sale.getId())
//                .invoiceNo(sale.getInvoiceNo())
//                .customerId(sale.getCustomer().getId())
//                .customerName(sale.getCustomer().getName())
//                .customerPhone(sale.getCustomer().getMobile())
//                .customerAddress(sale.getCustomer().getAddress())
//                .businessId(sale.getBusiness().getId())
//                .businessName(sale.getBusiness().getBusinessName())
//                .businessAddress(sale.getBusiness().getAddress())
//                .saleDate(sale.getSaleDate())
//                .status(sale.getStatus())
//                .paymentStatus(sale.getPaymentStatus())
//                .subtotal(sale.getSubtotal())
//                .totalDiscount(sale.getTotalDiscount())
//                .invoiceDiscount(sale.getInvoiceDiscount())
//                .grandTotal(sale.getGrandTotal())
//                .paidAmount(sale.getPaidAmount())
//                .dueAmount(sale.getDueAmount())
//                .advancePaid(sale.getAdvancePaid())
//                .notes(sale.getNotes())
//                .items(itemDtos)
//                .payments(paymentDtos)
//                .createdAt(sale.getCreatedAt())
//                .updatedAt(sale.getUpdatedAt())
//                .build();
//    }
//}