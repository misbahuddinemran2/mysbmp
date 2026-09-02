
package com.sbmp.due.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDueDTO {

    private Long customerId;
    private String customerName;
    private String customerPhone;
    private BigDecimal totalDue;
    private Long dueInvoiceCount;
}