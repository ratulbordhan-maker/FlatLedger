package com.flatmeals.account;

import com.flatmeals.month.MonthDtos.MonthResponse;
import com.flatmeals.month.MonthDtos.ReopenMonthRequest;
import com.flatmeals.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *   POST /api/months/{y}/{m}/close     MANAGER  freeze the month
 *   POST /api/months/{y}/{m}/reopen    MANAGER  {"reason": "..."} unlock it again
 */
@RestController
@RequestMapping("/api/months/{year}/{month}")
@PreAuthorize("hasRole('MANAGER')")
public class MonthClosingController {

    private final MonthClosingService closingService;

    public MonthClosingController(MonthClosingService closingService) {
        this.closingService = closingService;
    }

    @PostMapping("/close")
    public MonthResponse close(@PathVariable("year") int year, @PathVariable("month") int month,
                               @AuthenticationPrincipal Jwt jwt) {
        return MonthResponse.from(closingService.close(year, month, CurrentUser.from(jwt)));
    }

    @PostMapping("/reopen")
    public MonthResponse reopen(@PathVariable("year") int year, @PathVariable("month") int month,
                                @Valid @RequestBody ReopenMonthRequest request, @AuthenticationPrincipal Jwt jwt) {
        return MonthResponse.from(closingService.reopen(year, month, request.reason(), CurrentUser.from(jwt)));
    }
}
