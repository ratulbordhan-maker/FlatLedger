package com.flatmeals.account;

import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.cash.CashController;
import com.flatmeals.cash.CashService;
import com.flatmeals.expense.Expense;
import com.flatmeals.expense.ExpenseController;
import com.flatmeals.expense.ExpenseService;
import com.flatmeals.expense.FundingSource;
import com.flatmeals.payment.PaymentController;
import com.flatmeals.payment.PaymentService;
import com.flatmeals.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Who may see and change money records:
 *   - expenses: everyone may view, only the manager may change
 *   - payments, adjustments, cash: manager only
 *   - personal accounts: the manager sees all; a member sees only their own
 */
@WebMvcTest(controllers = {ExpenseController.class, PaymentController.class, CashController.class, LedgerController.class})
@Import(SecurityConfig.class)
class MoneyControllersSecurityTest {

    private static final String EXPENSE_JSON = "{\"expenseDate\": \"2026-10-05\", \"category\": \"Groceries\", "
            + "\"amount\": 9000, \"fundedBy\": \"FLAT_CASH\", \"holderMemberId\": 1}";

    @Autowired private MockMvc mvc;

    @MockitoBean private ExpenseService expenseService;
    @MockitoBean private PaymentService paymentService;
    @MockitoBean private CashService cashService;
    @MockitoBean private LedgerService ledgerService;

    private static RequestPostProcessor asManager() {
        return jwt().jwt(token -> token.claim("uid", 1L).claim("memberId", 1L).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    /** Srijila: user 2, member 2. */
    private static RequestPostProcessor asMember() {
        return jwt().jwt(token -> token.claim("uid", 2L).claim("memberId", 2L).claim("roles", List.of("MEMBER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private static MemberAccount account(Long memberId) {
        BigDecimal zero = new BigDecimal("0.00");
        return new MemberAccount(memberId, "Srijila", zero, zero, zero, zero, zero, zero, zero, zero, "SETTLED", List.of());
    }

    @Test
    void memberCanViewExpenses() throws Exception {
        when(expenseService.list(2026, 10)).thenReturn(List.of(new Expense(10L, 1L, LocalDate.of(2026, 10, 5),
                "Groceries", null, new BigDecimal("9000.00"), true, FundingSource.FLAT_CASH, 1L)));

        mvc.perform(get("/api/months/2026/10/expenses").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Groceries"));
    }

    @Test
    void memberCannotAddExpenses() throws Exception {
        mvc.perform(post("/api/months/2026/10/expenses").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON).content(EXPENSE_JSON))
                .andExpect(status().isForbidden());
        verifyNoInteractions(expenseService);
    }

    @Test
    void managerCanAddExpenses() throws Exception {
        when(expenseService.create(eq(2026), eq(10), any(), any())).thenReturn(new Expense(10L, 1L,
                LocalDate.of(2026, 10, 5), "Groceries", null, new BigDecimal("9000.00"), true, FundingSource.FLAT_CASH, 1L));

        mvc.perform(post("/api/months/2026/10/expenses").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(EXPENSE_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(9000.00));
    }

    @Test
    void amountWithThreeDecimalsIsRejected() throws Exception {
        mvc.perform(post("/api/months/2026/10/expenses").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(EXPENSE_JSON.replace("9000", "10.555")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
    }

    @Test
    void voidWithoutAReasonIsRejected() throws Exception {
        mvc.perform(post("/api/months/2026/10/expenses/7/void").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void memberCannotSeeOrRecordPayments() throws Exception {
        mvc.perform(get("/api/months/2026/10/payments").with(asMember()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/months/2026/10/payments").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": 2, \"cashHolderId\": 2, \"amount\": 100, \"paidOn\": \"2026-10-05\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(paymentService);
    }

    @Test
    void memberCannotTouchCash() throws Exception {
        mvc.perform(post("/api/months/2026/10/cash/transfers").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromMemberId\": 1, \"toMemberId\": 2, \"amount\": 100, \"transferredOn\": \"2026-10-05\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/months/2026/10/cash").with(asMember()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cashService);
    }

    @Test
    void memberCannotSeeEveryonesAccounts() throws Exception {
        mvc.perform(get("/api/months/2026/10/accounts").with(asMember()))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberCanSeeTheirOwnAccount() throws Exception {
        when(ledgerService.account(2026, 10, 2L)).thenReturn(account(2L));

        mvc.perform(get("/api/months/2026/10/accounts/me").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberId").value(2));
        mvc.perform(get("/api/months/2026/10/accounts/2").with(asMember()))
                .andExpect(status().isOk());
    }

    @Test
    void memberCannotSeeSomeoneElsesAccount() throws Exception {
        mvc.perform(get("/api/months/2026/10/accounts/1").with(asMember()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(ledgerService);
    }

    @Test
    void managerCanSeeAnyAccount() throws Exception {
        when(ledgerService.account(2026, 10, 2L)).thenReturn(account(2L));

        mvc.perform(get("/api/months/2026/10/accounts/2").with(asManager()))
                .andExpect(status().isOk());
    }
}
