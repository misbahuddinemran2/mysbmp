package com.sbmp.profit.controller;

import com.sbmp.business.entity.Business;
import com.sbmp.profit.service.ProfitService;
import com.sbmp.user.entity.User;
import com.sbmp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
@RequestMapping("/profit")
@RequiredArgsConstructor
public class ProfitController {

    private final ProfitService  profitService;
    private final UserRepository userRepository;

    @GetMapping
    public String profitPage(
            @RequestParam(defaultValue = "MONTH") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        Business business = getCurrentBusiness(userDetails);

        LocalDate today = LocalDate.now();
        LocalDate rangeFrom;
        LocalDate rangeTo = today;

        if (from != null && to != null) {
            rangeFrom = from;
            rangeTo   = to;
            period    = "CUSTOM";
        } else {
            switch (period) {
                case "TODAY"   -> rangeFrom = today;
                case "WEEK"    -> rangeFrom = today.minusDays(6);
                case "OVERALL" -> rangeFrom = LocalDate.of(2000, 1, 1);
                default        -> { rangeFrom = today.withDayOfMonth(1); period = "MONTH"; }
            }
        }

        model.addAttribute("productProfits", profitService.getProductWiseProfit(business.getId(), rangeFrom, rangeTo));
        model.addAttribute("summary", profitService.getProfitSummary(business.getId(), rangeFrom, rangeTo));
        model.addAttribute("period", period);
        model.addAttribute("fromDate", rangeFrom);
        model.addAttribute("toDate", rangeTo);
        model.addAttribute("activePage", "profit");
        model.addAttribute("businessName", business.getBusinessName());

        return "profit/profit-report";
    }

    private Business getCurrentBusiness(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getBusiness();
    }
}