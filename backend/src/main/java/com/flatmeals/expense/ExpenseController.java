package com.flatmeals.expense;

import com.flatmeals.common.VoidRequest;
import com.flatmeals.expense.ExpenseDtos.ExpenseRequest;
import com.flatmeals.expense.ExpenseDtos.ExpenseResponse;
import com.flatmeals.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *   GET  /api/months/{y}/{m}/expenses              any logged-in user (shared costs are visible to all)
 *   POST /api/months/{y}/{m}/expenses              MANAGER
 *   PUT  /api/months/{y}/{m}/expenses/{id}         MANAGER  correct an expense
 *   POST /api/months/{y}/{m}/expenses/{id}/void    MANAGER  cancel with a reason
 */
@RestController
@RequestMapping("/api/months/{year}/{month}/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;

    public ExpenseController(ExpenseService expenseService) {
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<ExpenseResponse> list(@PathVariable("year") int year, @PathVariable("month") int month) {
        return expenseService.list(year, month).stream().map(ExpenseResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public ExpenseResponse create(@PathVariable("year") int year, @PathVariable("month") int month,
                                  @Valid @RequestBody ExpenseRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ExpenseResponse.from(expenseService.create(year, month, request, CurrentUser.from(jwt)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ExpenseResponse update(@PathVariable("year") int year, @PathVariable("month") int month,
                                  @PathVariable("id") Long id,
                                  @Valid @RequestBody ExpenseRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ExpenseResponse.from(expenseService.update(year, month, id, request, CurrentUser.from(jwt)));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('MANAGER')")
    public ExpenseResponse voidExpense(@PathVariable("year") int year, @PathVariable("month") int month,
                                       @PathVariable("id") Long id,
                                       @Valid @RequestBody VoidRequest request, @AuthenticationPrincipal Jwt jwt) {
        return ExpenseResponse.from(expenseService.voidExpense(year, month, id, request.reason(), CurrentUser.from(jwt)));
    }
}
