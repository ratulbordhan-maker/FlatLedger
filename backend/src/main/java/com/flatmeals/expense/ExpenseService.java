package com.flatmeals.expense;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.calc.MoneyUtil;
import com.flatmeals.common.ConflictException;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.common.RecordStatus;
import com.flatmeals.expense.ExpenseDtos.ExpenseRequest;
import com.flatmeals.expense.ExpenseDtos.ExpenseResponse;
import com.flatmeals.member.MemberService;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthService;
import com.flatmeals.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Adding, editing and voiding expenses. Every change is refused when the month
 * is closed, and every change is written to the audit log.
 */
@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final MonthService monthService;
    private final MemberService memberService;
    private final AuditService auditService;

    public ExpenseService(ExpenseRepository expenseRepository, MonthService monthService,
                          MemberService memberService, AuditService auditService) {
        this.expenseRepository = expenseRepository;
        this.monthService = monthService;
        this.memberService = memberService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<Expense> list(int year, int month) {
        FlatMonth flatMonth = monthService.find(year, month);
        return expenseRepository.findByMonthIdOrderByExpenseDateAscIdAsc(flatMonth.getId());
    }

    @Transactional
    public Expense create(int year, int month, ExpenseRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        BigDecimal amount = validate(flatMonth, request);

        boolean looksLikeDuplicate = expenseRepository.existsByMonthIdAndExpenseDateAndAmountAndHolderMemberIdAndStatus(
                flatMonth.getId(), request.expenseDate(), amount, request.holderMemberId(), RecordStatus.ACTIVE);
        if (looksLikeDuplicate && !Boolean.TRUE.equals(request.allowDuplicate())) {
            throw new ConflictException("An expense of " + amount + " paid by the same person on "
                    + request.expenseDate() + " already exists. If this really is a second expense, "
                    + "send it again with \"allowDuplicate\": true.");
        }

        Expense expense = expenseRepository.save(new Expense(flatMonth.getId(), actor.userId(),
                request.expenseDate(), request.category().trim(), trimOrNull(request.description()), amount,
                inMealRate(request), request.fundedBy(), request.holderMemberId()));
        auditService.record(actor.userId(), "EXPENSE", expense.getId(), AuditAction.CREATE,
                null, ExpenseResponse.from(expense), null);
        return expense;
    }

    @Transactional
    public Expense update(int year, int month, Long id, ExpenseRequest request, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        Expense expense = find(flatMonth, id);
        if (!expense.isActive()) {
            throw new ConflictException("A voided expense cannot be edited");
        }
        BigDecimal amount = validate(flatMonth, request);

        ExpenseResponse before = ExpenseResponse.from(expense);
        expense.update(request.expenseDate(), request.category().trim(), trimOrNull(request.description()),
                amount, inMealRate(request), request.fundedBy(), request.holderMemberId());
        auditService.record(actor.userId(), "EXPENSE", id, AuditAction.UPDATE,
                before, ExpenseResponse.from(expense), null);
        return expense;
    }

    @Transactional
    public Expense voidExpense(int year, int month, Long id, String reason, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);
        Expense expense = find(flatMonth, id);
        ExpenseResponse before = ExpenseResponse.from(expense);
        expense.voidRecord(reason);
        auditService.record(actor.userId(), "EXPENSE", id, AuditAction.VOID,
                before, ExpenseResponse.from(expense), reason);
        return expense;
    }

    private BigDecimal validate(FlatMonth flatMonth, ExpenseRequest request) {
        flatMonth.requireContains(request.expenseDate(), "Expense date");
        memberService.get(request.holderMemberId());   // 404 if the member doesn't exist
        return MoneyUtil.requirePositiveMoney(request.amount(), "amount");
    }

    private Expense find(FlatMonth flatMonth, Long id) {
        return expenseRepository.findByIdAndMonthId(id, flatMonth.getId())
                .orElseThrow(() -> new NotFoundException("Expense " + id + " not found in " + flatMonth.label()));
    }

    private static boolean inMealRate(ExpenseRequest request) {
        return request.inMealRate() == null || request.inMealRate();
    }

    private static String trimOrNull(String text) {
        return (text == null || text.isBlank()) ? null : text.trim();
    }
}
