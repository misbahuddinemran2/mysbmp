package com.sbmp.sales.entity;

import com.sbmp.inventory.category.entity.Category;
import com.sbmp.inventory.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "sale_id",
            nullable = false
    )
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "product_id",
            nullable = false
    )
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            nullable = false
    )
    private Category category;

    // ------------------------------
    // Stock Snapshot
    // ------------------------------

    @Column(nullable = false)
    private Integer stockBefore;

    @Column(nullable = false)
    private Integer stockAfter;

    // ------------------------------
    // Sales
    // ------------------------------

    @Column(
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal unitPrice;

    @Column(
            precision = 15,
            scale = 2
    )
    private BigDecimal discountAmount;

    @Column(
            precision = 5,
            scale = 2
    )
    private BigDecimal discountPercentage;

    @Column(
            nullable = false,
            precision = 15,
            scale = 2
    )
    private BigDecimal itemTotal;

    @Column(nullable = false)
    private Integer quantity;

    // ------------------------------
    // Profit tracking (snapshot at sale time)
    // ------------------------------

    @Column(
            precision = 15,
            scale = 2
    )
    private BigDecimal costPriceAtSale;

    // ------------------------------
    // Invoice extras (manual, optional — sale করার সময় টেক্সট আকারে দেওয়া হয়)
    // ------------------------------

    /** যেমন: "1 YEAR", "3 YEARS", "6 MONTH" — ফ্রি-টেক্সট, sale-time এ ম্যানুয়ালি দেওয়া */
    @Column(length = 50)
    private String warranty;

    /** কমা/নিউলাইন দিয়ে আলাদা করা একাধিক serial/MAC number, ফ্রি-টেক্সট */
    @Column(columnDefinition = "TEXT")
    private String serialNumbers;
}