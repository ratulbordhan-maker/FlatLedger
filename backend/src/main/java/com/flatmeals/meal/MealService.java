package com.flatmeals.meal;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.calc.MealRateCalculator;
import com.flatmeals.calc.MealRateResult;
import com.flatmeals.calc.MealValues;
import com.flatmeals.meal.MealDtos.MealCell;
import com.flatmeals.meal.MealDtos.MealGridResponse;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.meal.MealDtos.MemberMealCost;
import com.flatmeals.meal.MealDtos.MemberMealRow;
import com.flatmeals.meal.MealGridBuilder.MealGrid;
import com.flatmeals.member.Member;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthMember;
import com.flatmeals.month.MonthMemberRepository;
import com.flatmeals.month.MonthRepository;
import com.flatmeals.month.MonthService;
import com.flatmeals.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Reading and saving the meal grid, and the live meal rate. */
@Service
public class MealService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final MonthService monthService;
    private final MonthRepository monthRepository;
    private final MonthMemberRepository monthMemberRepository;
    private final MealEntryRepository mealEntryRepository;
    private final AuditService auditService;

    public MealService(MonthService monthService, MonthRepository monthRepository,
                       MonthMemberRepository monthMemberRepository, MealEntryRepository mealEntryRepository,
                       AuditService auditService) {
        this.monthService = monthService;
        this.monthRepository = monthRepository;
        this.monthMemberRepository = monthMemberRepository;
        this.mealEntryRepository = mealEntryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public MealGridResponse getGrid(int year, int month) {
        FlatMonth flatMonth = monthService.find(year, month);
        MealGrid grid = buildGrid(flatMonth);
        return new MealGridResponse(flatMonth.getYear(), flatMonth.getMonthNumber(), flatMonth.label(),
                flatMonth.getStatus(), grid.daysInMonth(), grid.rows(), grid.dayTotals(), grid.totalMeals());
    }

    /**
     * Saves a batch of meal cells in ONE transaction: either every cell is
     * saved, or (if any cell is invalid) none are.
     *
     * - Only changed cells are written; one audit row lists every change.
     * - A new cell with 0 meals is not stored (no row already means 0).
     * - An existing cell set to 0 is updated, not deleted, so history stays visible.
     *
     * @return how many cells actually changed
     */
    @Transactional
    public int saveMeals(int year, int month, List<MealCell> cells, CurrentUser actor) {
        FlatMonth flatMonth = monthService.findOpen(year, month);   // closed months are refused here
        YearMonth yearMonth = flatMonth.yearMonth();
        Set<Long> monthMemberIds = monthMemberRepository.findByMonthId(flatMonth.getId()).stream()
                .map(MonthMember::getMemberId)
                .collect(Collectors.toSet());

        // 1. Validate everything first, before changing anything
        Map<CellKey, BigDecimal> requested = new LinkedHashMap<>();
        for (MealCell cell : cells) {
            if (!YearMonth.from(cell.date()).equals(yearMonth)) {
                throw new IllegalArgumentException(cell.date() + " is not in " + flatMonth.label());
            }
            if (!monthMemberIds.contains(cell.memberId())) {
                throw new IllegalArgumentException("Member " + cell.memberId() + " is not part of "
                        + flatMonth.label() + ". Add them to the month first.");
            }
            BigDecimal value = MealValues.requireValid(cell.meals());
            if (requested.put(new CellKey(cell.memberId(), cell.date()), value) != null) {
                throw new IllegalArgumentException("Member " + cell.memberId() + " on " + cell.date()
                        + " appears more than once in this request");
            }
        }

        // 2. Compare with what is stored and apply only the differences
        Map<CellKey, MealEntry> existing = new HashMap<>();
        for (MealEntry entry : mealEntryRepository.findByMonthId(flatMonth.getId())) {
            existing.put(new CellKey(entry.getMemberId(), entry.getMealDate()), entry);
        }

        List<MealEntry> toInsert = new ArrayList<>();
        List<Map<String, Object>> changes = new ArrayList<>();
        for (Map.Entry<CellKey, BigDecimal> request : requested.entrySet()) {
            CellKey key = request.getKey();
            BigDecimal newValue = request.getValue();
            MealEntry entry = existing.get(key);
            BigDecimal oldValue = (entry == null) ? ZERO : entry.getMeals().setScale(2);

            if (oldValue.compareTo(newValue) == 0) {
                continue;   // nothing changed
            }
            if (entry == null) {
                toInsert.add(new MealEntry(flatMonth.getId(), key.memberId(), key.date(), newValue, actor.userId()));
            } else {
                entry.changeMeals(newValue, actor.userId());   // saved automatically at the end
            }
            Map<String, Object> change = new LinkedHashMap<>();
            change.put("memberId", key.memberId());
            change.put("date", key.date().toString());
            change.put("from", oldValue);
            change.put("to", newValue);
            changes.add(change);
        }

        if (!toInsert.isEmpty()) {
            mealEntryRepository.saveAll(toInsert);
        }
        if (!changes.isEmpty()) {
            auditService.record(actor.userId(), "MEAL_ENTRIES", flatMonth.getId(), AuditAction.UPDATE,
                    null, Map.of("changes", changes), changes.size() + " meal cell(s) changed");
        }
        return changes.size();
    }

    /** Live meal rate and each member's cost for a month. */
    @Transactional(readOnly = true)
    public MealSummaryResponse getSummary(int year, int month) {
        FlatMonth flatMonth = monthService.find(year, month);
        MealGrid grid = buildGrid(flatMonth);

        Map<Long, BigDecimal> mealsByMember = new LinkedHashMap<>();
        for (MemberMealRow row : grid.rows()) {
            mealsByMember.put(row.memberId(), row.total());
        }
        BigDecimal expense = monthRepository.sumMealRateExpenses(flatMonth.getId());
        expense = (expense == null ? ZERO : expense).setScale(2, RoundingMode.HALF_UP);

        MealRateResult result = MealRateCalculator.calculate(mealsByMember, expense);

        List<MemberMealCost> members = new ArrayList<>();
        for (MemberMealRow row : grid.rows()) {
            members.add(new MemberMealCost(row.memberId(), row.name(), row.total(),
                    result.memberCosts().get(row.memberId())));
        }
        return new MealSummaryResponse(flatMonth.getYear(), flatMonth.getMonthNumber(), flatMonth.label(),
                flatMonth.getStatus(), result.totalMeals(), result.totalExpense(), result.mealRate(),
                result.canBeFinalized(), result.warning(), members);
    }

    private MealGrid buildGrid(FlatMonth flatMonth) {
        List<Member> members = monthService.membersOf(flatMonth);
        return MealGridBuilder.build(flatMonth.yearMonth(), members,
                mealEntryRepository.findByMonthId(flatMonth.getId()));
    }

    /** Identifies one cell of the grid. */
    private record CellKey(Long memberId, LocalDate date) {
    }
}
