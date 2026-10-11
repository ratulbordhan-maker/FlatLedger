package com.flatmeals.report;

import com.flatmeals.account.LedgerService;
import com.flatmeals.expense.Expense;
import com.flatmeals.expense.ExpenseService;
import com.flatmeals.expense.FundingSource;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberService;
import com.flatmeals.payment.Adjustment;
import com.flatmeals.payment.Payment;
import com.flatmeals.payment.PaymentService;
import com.flatmeals.payment.PaymentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects one month's figures for the report.
 *
 * It does no money maths of its own: balances, the meal rate and cash come
 * from LedgerService, and the lists come from the existing services. That way
 * the report can never disagree with what the app shows.
 */
@Service
public class MonthReportService {

    private final LedgerService ledgerService;
    private final ExpenseService expenseService;
    private final PaymentService paymentService;
    private final MemberService memberService;

    public MonthReportService(LedgerService ledgerService, ExpenseService expenseService,
                              PaymentService paymentService, MemberService memberService) {
        this.ledgerService = ledgerService;
        this.expenseService = expenseService;
        this.paymentService = paymentService;
        this.memberService = memberService;
    }

    @Transactional(readOnly = true)
    public MonthReport build(int year, int month) {
        Map<Long, String> names = new HashMap<>();
        for (Member m : memberService.list(null)) {
            names.put(m.getId(), m.getName());
        }

        List<MonthReport.ExpenseLine> expenses = expenseService.list(year, month).stream()
                .map(e -> new MonthReport.ExpenseLine(e.getExpenseDate(), e.getCategory(), e.getDescription(),
                        e.getAmount(), paidFrom(e, names), e.isInMealRate(), !e.isActive(), e.getVoidReason()))
                .toList();

        List<MonthReport.PaymentLine> payments = paymentService.listPayments(year, month).stream()
                .map(p -> new MonthReport.PaymentLine(p.getPaidOn(), name(names, p.getMemberId()),
                        p.getType() == PaymentType.REFUND ? "Refund" : "Payment", p.getAmount(),
                        name(names, p.getCashHolderId()), p.getNote(), !p.isActive(), p.getVoidReason()))
                .toList();

        List<MonthReport.AdjustmentLine> adjustments = paymentService.listAdjustments(year, month).stream()
                .map(a -> new MonthReport.AdjustmentLine(name(names, a.getMemberId()), adjustmentType(a),
                        a.getAmount(), a.getReason(), !a.isActive(), a.getVoidReason()))
                .toList();

        return new MonthReport(ledgerService.accounts(year, month), ledgerService.cashReport(year, month),
                expenses, payments, adjustments);
    }

    private static String paidFrom(Expense e, Map<Long, String> names) {
        String holder = name(names, e.getHolderMemberId());
        return e.getFundedBy() == FundingSource.MEMBER_POCKET
                ? holder + "'s own money"
                : "Flat cash held by " + holder;
    }

    private static String adjustmentType(Adjustment a) {
        return switch (a.getType()) {
            case OPENING -> "Opening balance";
            case CORRECTION -> "Correction";
            case OTHER -> "Other";
        };
    }

    private static String name(Map<Long, String> names, Long id) {
        return id == null ? "" : names.getOrDefault(id, "Member " + id);
    }
}
