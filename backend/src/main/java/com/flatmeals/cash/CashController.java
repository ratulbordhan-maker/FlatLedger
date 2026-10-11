package com.flatmeals.cash;

import com.flatmeals.cash.CashDtos.CashAdjustmentRequest;
import com.flatmeals.cash.CashDtos.CashAdjustmentResponse;
import com.flatmeals.cash.CashDtos.CountRequest;
import com.flatmeals.cash.CashDtos.CountResponse;
import com.flatmeals.cash.CashDtos.ReceiptRequest;
import com.flatmeals.cash.CashDtos.ReceiptResponse;
import com.flatmeals.cash.CashDtos.TransferRequest;
import com.flatmeals.cash.CashDtos.TransferResponse;
import com.flatmeals.common.VoidRequest;
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
 * All MANAGER only.  Base path: /api/months/{y}/{m}/cash
 *
 *   GET/POST /transfers     POST /transfers/{id}/void
 *   GET/POST /receipts      POST /receipts/{id}/void
 *   GET/POST /counts
 *   GET/POST /adjustments   POST /adjustments/{id}/void
 *
 * The cash report and reconciliation are at GET /api/months/{y}/{m}/cash (LedgerController).
 */
@RestController
@RequestMapping("/api/months/{year}/{month}/cash")
@PreAuthorize("hasRole('MANAGER')")
public class CashController {

    private final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @GetMapping("/transfers")
    public List<TransferResponse> listTransfers(@PathVariable("year") int year, @PathVariable("month") int month) {
        return cashService.listTransfers(year, month).stream().map(TransferResponse::from).toList();
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse createTransfer(@PathVariable("year") int year, @PathVariable("month") int month,
                                           @Valid @RequestBody TransferRequest request, @AuthenticationPrincipal Jwt jwt) {
        return TransferResponse.from(cashService.createTransfer(year, month, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/transfers/{id}/void")
    public TransferResponse voidTransfer(@PathVariable("year") int year, @PathVariable("month") int month,
                                         @PathVariable("id") Long id,
                                         @Valid @RequestBody VoidRequest request, @AuthenticationPrincipal Jwt jwt) {
        return TransferResponse.from(cashService.voidTransfer(year, month, id, request.reason(), CurrentUser.from(jwt)));
    }

    @GetMapping("/receipts")
    public List<ReceiptResponse> listReceipts(@PathVariable("year") int year, @PathVariable("month") int month) {
        return cashService.listReceipts(year, month).stream().map(ReceiptResponse::from).toList();
    }

    @PostMapping("/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptResponse createReceipt(@PathVariable("year") int year, @PathVariable("month") int month,
                                         @Valid @RequestBody ReceiptRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ReceiptResponse.from(cashService.createReceipt(year, month, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/receipts/{id}/void")
    public ReceiptResponse voidReceipt(@PathVariable("year") int year, @PathVariable("month") int month,
                                       @PathVariable("id") Long id,
                                       @Valid @RequestBody VoidRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ReceiptResponse.from(cashService.voidReceipt(year, month, id, request.reason(), CurrentUser.from(jwt)));
    }

    @GetMapping("/counts")
    public List<CountResponse> listCounts(@PathVariable("year") int year, @PathVariable("month") int month) {
        return cashService.listCounts(year, month).stream().map(CountResponse::from).toList();
    }

    @PostMapping("/counts")
    @ResponseStatus(HttpStatus.CREATED)
    public CountResponse createCount(@PathVariable("year") int year, @PathVariable("month") int month,
                                     @Valid @RequestBody CountRequest request, @AuthenticationPrincipal Jwt jwt) {
        return CountResponse.from(cashService.createCount(year, month, request, CurrentUser.from(jwt)));
    }

    @GetMapping("/adjustments")
    public List<CashAdjustmentResponse> listAdjustments(@PathVariable("year") int year, @PathVariable("month") int month) {
        return cashService.listCashAdjustments(year, month).stream().map(CashAdjustmentResponse::from).toList();
    }

    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public CashAdjustmentResponse createAdjustment(@PathVariable("year") int year, @PathVariable("month") int month,
                                                   @Valid @RequestBody CashAdjustmentRequest request,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return CashAdjustmentResponse.from(cashService.createCashAdjustment(year, month, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/adjustments/{id}/void")
    public CashAdjustmentResponse voidAdjustment(@PathVariable("year") int year, @PathVariable("month") int month,
                                                 @PathVariable("id") Long id,
                                                 @Valid @RequestBody VoidRequest request, @AuthenticationPrincipal Jwt jwt) {
        return CashAdjustmentResponse.from(cashService.voidCashAdjustment(year, month, id, request.reason(), CurrentUser.from(jwt)));
    }
}
