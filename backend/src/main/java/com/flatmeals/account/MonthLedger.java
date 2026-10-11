package com.flatmeals.account;

import com.flatmeals.account.LedgerDtos.CashHolderPosition;
import com.flatmeals.account.LedgerDtos.MemberAccount;
import com.flatmeals.account.LedgerDtos.ReconciliationRow;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.month.FlatMonth;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything calculated for one month: meals, accounts and cash. */
public record MonthLedger(
        FlatMonth month,
        MealSummaryResponse meals,
        List<MemberAccount> accounts,
        List<CashHolderPosition> cashHolders,
        List<ReconciliationRow> reconciliation) {

    /** member id → closing balance: next month's opening balances. */
    public Map<Long, BigDecimal> closingBalances() {
        Map<Long, BigDecimal> balances = new HashMap<>();
        for (MemberAccount account : accounts) {
            balances.put(account.memberId(), account.closingBalance());
        }
        return balances;
    }

    /** holder id → closing cash: next month's opening cash. */
    public Map<Long, BigDecimal> closingCash() {
        Map<Long, BigDecimal> cash = new HashMap<>();
        for (CashHolderPosition holder : cashHolders) {
            cash.put(holder.memberId(), holder.closingCash());
        }
        return cash;
    }
}
