package com.flatmeals.account;

import com.flatmeals.account.LedgerDtos.AccountsResponse;
import com.flatmeals.account.LedgerDtos.CashReportResponse;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.security.CurrentUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *   GET /api/months/{y}/{m}/accounts              MANAGER  every member's account
 *   GET /api/months/{y}/{m}/accounts/me           anyone   my own account
 *   GET /api/months/{y}/{m}/accounts/{memberId}   MANAGER, or that member themself
 *   GET /api/months/{y}/{m}/cash                  MANAGER  cash per holder + reconciliation
 *
 * Privacy: a member can see their own dues, payments and credits, but not other members'.
 */
@RestController
@RequestMapping("/api/months/{year}/{month}")
public class LedgerController {

    private final LedgerService ledgerService;

    public LedgerController(LedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasRole('MANAGER')")
    public AccountsResponse accounts(@PathVariable("year") int year, @PathVariable("month") int month) {
        return ledgerService.accounts(year, month);
    }

    @GetMapping("/accounts/me")
    public MemberAccount myAccount(@PathVariable("year") int year, @PathVariable("month") int month,
                                   @AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = CurrentUser.from(jwt);
        if (me.memberId() == null) {
            throw new NotFoundException("Your login is not linked to a flat member");
        }
        return ledgerService.account(year, month, me.memberId());
    }

    @GetMapping("/accounts/{memberId}")
    public MemberAccount memberAccount(@PathVariable("year") int year, @PathVariable("month") int month,
                                       @PathVariable("memberId") Long memberId,
                                       @AuthenticationPrincipal Jwt jwt) {
        CurrentUser me = CurrentUser.from(jwt);
        if (!me.manager() && !memberId.equals(me.memberId())) {
            throw new AccessDeniedException("Members can only view their own account");
        }
        return ledgerService.account(year, month, memberId);
    }

    @GetMapping("/cash")
    @PreAuthorize("hasRole('MANAGER')")
    public CashReportResponse cash(@PathVariable("year") int year, @PathVariable("month") int month) {
        return ledgerService.cashReport(year, month);
    }
}
