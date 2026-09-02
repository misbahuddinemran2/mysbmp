package com.sbmp.due.controller;

import com.sbmp.business.entity.Business;
import com.sbmp.due.dto.CollectDueRequest;
import com.sbmp.due.service.DueService;
import com.sbmp.user.entity.User;
import com.sbmp.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Controller
@RequestMapping("/due-management")
@RequiredArgsConstructor
public class DueController {

    private final DueService     dueService;
    private final UserRepository userRepository;

    @GetMapping
    public String dueList(
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        Business business = getCurrentBusiness(userDetails);

        var customerDues = dueService.getCustomerWiseDue(business.getId());

        model.addAttribute("customerDues", customerDues);
        model.addAttribute("totalDue", dueService.getTotalOutstandingDue(business.getId()));
        model.addAttribute("dueCount", customerDues.size());
        model.addAttribute("activePage", "due-management");
        model.addAttribute("businessName", business.getBusinessName());

        return "due/due-list";
    }

    @GetMapping("/customer/{customerId}")
    public String customerDueDetails(
            @PathVariable Long customerId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        Business business = getCurrentBusiness(userDetails);

        model.addAttribute("dueSales", dueService.getDueSalesByCustomer(business.getId(), customerId));
        model.addAttribute("customerId", customerId);
        model.addAttribute("activePage", "due-management");
        model.addAttribute("businessName", business.getBusinessName());

        return "due/due-customer-detail";
    }

    @PostMapping("/collect")
    public String collectPayment(
            @Valid @ModelAttribute CollectDueRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        Business business = getCurrentBusiness(userDetails);

        Long paymentId = dueService.collectDuePayment(business.getId(), request);

        return "redirect:/due-management/receipt/" + paymentId;   // ← receipt পেজে redirect
    }

    @GetMapping("/receipt/{paymentId}")
    public String paymentReceipt(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal UserDetails userDetails,
            Model model
    ) {
        Business business = getCurrentBusiness(userDetails);

        model.addAttribute("receipt", dueService.getPaymentReceipt(business.getId(), paymentId));

        return "due/payment-receipt";
    }

    private Business getCurrentBusiness(UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getBusiness();
    }
}