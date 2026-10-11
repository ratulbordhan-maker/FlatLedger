package com.flatmeals.month;

import com.flatmeals.audit.AuditAction;
import com.flatmeals.audit.AuditService;
import com.flatmeals.common.ConflictException;
import com.flatmeals.common.NotFoundException;
import com.flatmeals.meal.MealEntryRepository;
import com.flatmeals.member.Member;
import com.flatmeals.member.MemberRepository;
import com.flatmeals.member.MemberStatus;
import com.flatmeals.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Opening months and deciding who belongs to each month.
 *
 * Rule: months are opened one after another with no gaps (October, then
 * November, ...). Each month's opening balance comes from the month before,
 * so a gap would break the carry-forward chain.
 */
@Service
public class MonthService {

    private final MonthRepository monthRepository;
    private final MonthMemberRepository monthMemberRepository;
    private final MemberRepository memberRepository;
    private final MealEntryRepository mealEntryRepository;
    private final AuditService auditService;

    public MonthService(MonthRepository monthRepository, MonthMemberRepository monthMemberRepository,
                        MemberRepository memberRepository, MealEntryRepository mealEntryRepository,
                        AuditService auditService) {
        this.monthRepository = monthRepository;
        this.monthMemberRepository = monthMemberRepository;
        this.memberRepository = memberRepository;
        this.mealEntryRepository = mealEntryRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<FlatMonth> list() {
        return monthRepository.findAllByOrderByYearDescMonthNumberDesc();
    }

    /** Finds a month or throws 404. Also rejects impossible values like month 13 (400). */
    @Transactional(readOnly = true)
    public FlatMonth find(int year, int month) {
        YearMonth yearMonth = toYearMonth(year, month);
        return monthRepository.findByYearAndMonthNumber(year, month)
                .orElseThrow(() -> new NotFoundException(label(yearMonth) + " has not been opened yet"));
    }

    /** Like find(), but refuses closed months: use this before ANY change. */
    @Transactional(readOnly = true)
    public FlatMonth findOpen(int year, int month) {
        FlatMonth flatMonth = find(year, month);
        if (!flatMonth.isOpen()) {
            throw new ConflictException(flatMonth.label() + " is closed. Reopen it before making changes.");
        }
        return flatMonth;
    }

    /** The members of a month, sorted by name. */
    @Transactional(readOnly = true)
    public List<Member> membersOf(FlatMonth flatMonth) {
        List<Long> ids = monthMemberRepository.findByMonthId(flatMonth.getId()).stream()
                .map(MonthMember::getMemberId)
                .toList();
        List<Member> members = new ArrayList<>(memberRepository.findAllById(ids));
        members.sort(Comparator.comparing(Member::getName, String.CASE_INSENSITIVE_ORDER));
        return members;
    }

    /** Opens a new month and adds every currently ACTIVE member to it. */
    @Transactional
    public FlatMonth open(int year, int month, CurrentUser actor) {
        YearMonth requested = toYearMonth(year, month);
        if (monthRepository.existsByYearAndMonthNumber(year, month)) {
            throw new ConflictException(label(requested) + " is already open");
        }
        Optional<FlatMonth> latest = monthRepository.findTopByOrderByYearDescMonthNumberDesc();
        if (latest.isPresent()) {
            YearMonth expected = latest.get().yearMonth().plusMonths(1);
            if (!requested.equals(expected)) {
                throw new IllegalArgumentException("Months must be opened in order. The next month to open is "
                        + label(expected) + ".");
            }
        }

        FlatMonth flatMonth = monthRepository.save(new FlatMonth(year, month));
        List<MonthMember> rows = memberRepository.findByStatusOrderByNameAsc(MemberStatus.ACTIVE).stream()
                .map(member -> new MonthMember(flatMonth.getId(), member.getId()))
                .toList();
        monthMemberRepository.saveAll(rows);

        auditService.record(actor.userId(), "MONTH", flatMonth.getId(), AuditAction.CREATE, null,
                Map.of("month", flatMonth.label(), "memberCount", rows.size()), null);
        return flatMonth;
    }

    /** Adds a member to an open month, e.g. someone who moved in mid-month. */
    @Transactional
    public FlatMonth addMember(int year, int month, Long memberId, CurrentUser actor) {
        FlatMonth flatMonth = findOpen(year, month);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new NotFoundException("Member " + memberId + " not found"));
        if (!member.isActive()) {
            throw new ConflictException(member.getName() + " is inactive. Reactivate them first.");
        }
        if (monthMemberRepository.existsByMonthIdAndMemberId(flatMonth.getId(), memberId)) {
            throw new ConflictException(member.getName() + " is already part of " + flatMonth.label());
        }
        monthMemberRepository.save(new MonthMember(flatMonth.getId(), memberId));
        auditService.record(actor.userId(), "MONTH", flatMonth.getId(), AuditAction.UPDATE,
                null, Map.of("addedMemberId", memberId), "Added " + member.getName());
        return flatMonth;
    }

    /**
     * Removes a member from an open month, but only if they have no meal
     * entries in it, so no recorded history can disappear.
     */
    @Transactional
    public FlatMonth removeMember(int year, int month, Long memberId, CurrentUser actor) {
        FlatMonth flatMonth = findOpen(year, month);
        MonthMember row = monthMemberRepository.findByMonthIdAndMemberId(flatMonth.getId(), memberId)
                .orElseThrow(() -> new NotFoundException("Member " + memberId + " is not part of " + flatMonth.label()));
        if (mealEntryRepository.existsByMonthIdAndMemberId(flatMonth.getId(), memberId)) {
            throw new ConflictException("This member already has meal entries in " + flatMonth.label()
                    + " and cannot be removed from it.");
        }
        monthMemberRepository.delete(row);
        auditService.record(actor.userId(), "MONTH", flatMonth.getId(), AuditAction.UPDATE,
                Map.of("removedMemberId", memberId), null, "Removed member from month");
        return flatMonth;
    }

    private static YearMonth toYearMonth(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) {
            throw new IllegalArgumentException("Invalid month: " + year + "/" + month);
        }
        return YearMonth.of(year, month);
    }

    private static String label(YearMonth yearMonth) {
        return new FlatMonth(yearMonth.getYear(), yearMonth.getMonthValue()).label();
    }
}
