package com.flatmeals.payment;

import com.flatmeals.common.VoidRequest;
import com.flatmeals.payment.PaymentDtos.AdjustmentRequest;
import com.flatmeals.payment.PaymentDtos.AdjustmentResponse;
import com.flatmeals.payment.PaymentDtos.PaymentRequest;
import com.flatmeals.payment.PaymentDtos.PaymentResponse;
import com.flatmeals.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * All MANAGER only. (Members see their own payments in their personal
 * account: GET /api/months/{y}/{m}/accounts/me)
 *
 *   GET/POST /api/months/{y}/{m}/payments          POST /payments/{id}/void
 *   GET/POST /api/months/{y}/{m}/adjustments       POST /adjustments/{id}/void
 */
@RestController
@RequestMapping("/api/months/{year}/{month}")
@PreAuthorize("hasRole('MANAGER')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/payments")
    public List<PaymentResponse> listPayments(@PathVariable("year") int year, @PathVariable("month") int month) {
        return paymentService.listPayments(year, month).stream().map(PaymentResponse::from).toList();
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@PathVariable("year") int year, @PathVariable("month") int month,
                                         @Valid @RequestBody PaymentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return PaymentResponse.from(paymentService.createPayment(year, month, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/payments/{id}/void")
    public PaymentResponse voidPayment(@PathVariable("year") int year, @PathVariable("month") int month,
                                       @PathVariable("id") Long id,
                                       @Valid @RequestBody VoidRequest request, @AuthenticationPrincipal Jwt jwt) {
        return PaymentResponse.from(paymentService.voidPayment(year, month, id, request.reason(), CurrentUser.from(jwt)));
    }

    @GetMapping("/adjustments")
    public List<AdjustmentResponse> listAdjustments(@PathVariable("year") int year, @PathVariable("month") int month) {
        return paymentService.listAdjustments(year, month).stream().map(AdjustmentResponse::from).toList();
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public AdjustmentResponse createAdjustment(@PathVariable("year") int year, @PathVariable("month") int month,
                                               @Valid @RequestBody AdjustmentRequest request,
                                               @AuthenticationPrincipal Jwt jwt) {
        return AdjustmentResponse.from(paymentService.createAdjustment(year, month, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/adjustments/{id}/void")
    public AdjustmentResponse voidAdjustment(@PathVariable("year") int year, @PathVariable("month") int month,
                                             @PathVariable("id") Long id,
                                             @Valid @RequestBody VoidRequest request, @AuthenticationPrincipal Jwt jwt) {
        return AdjustmentResponse.from(paymentService.voidAdjustment(year, month, id, request.reason(), CurrentUser.from(jwt)));
    }
}
