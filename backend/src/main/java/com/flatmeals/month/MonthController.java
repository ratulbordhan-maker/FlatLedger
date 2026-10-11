package com.flatmeals.month;

import com.flatmeals.member.MemberDtos.MemberResponse;
import com.flatmeals.month.MonthDtos.AddMonthMemberRequest;
import com.flatmeals.month.MonthDtos.MonthDetailResponse;
import com.flatmeals.month.MonthDtos.MonthResponse;
import com.flatmeals.month.MonthDtos.OpenMonthRequest;
import com.flatmeals.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 *   GET    /api/months                                  any logged-in user
 *   POST   /api/months                                  MANAGER  open the next month
 *   GET    /api/months/{year}/{month}                   any logged-in user
 *   POST   /api/months/{year}/{month}/members           MANAGER  add a member to the month
 *   DELETE /api/months/{year}/{month}/members/{id}      MANAGER  only if they have no meals
 */
@RestController
@RequestMapping("/api/months")
public class MonthController {

    private final MonthService monthService;

    public MonthController(MonthService monthService) {
        this.monthService = monthService;
    }

    @GetMapping
    public List<MonthResponse> list() {
        return monthService.list().stream().map(MonthResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('MANAGER')")
    public MonthDetailResponse open(@Valid @RequestBody OpenMonthRequest request,
                                    @AuthenticationPrincipal Jwt jwt) {
        FlatMonth month = monthService.open(request.year(), request.month(), CurrentUser.from(jwt));
        return detail(month);
    }

    @GetMapping("/{year}/{month}")
    public MonthDetailResponse get(@PathVariable("year") int year, @PathVariable("month") int month) {
        return detail(monthService.find(year, month));
    }

    @PostMapping("/{year}/{month}/members")
    @PreAuthorize("hasRole('MANAGER')")
    public MonthDetailResponse addMember(@PathVariable("year") int year, @PathVariable("month") int month,
                                         @Valid @RequestBody AddMonthMemberRequest request,
                                         @AuthenticationPrincipal Jwt jwt) {
        return detail(monthService.addMember(year, month, request.memberId(), CurrentUser.from(jwt)));
    }

    @DeleteMapping("/{year}/{month}/members/{memberId}")
    @PreAuthorize("hasRole('MANAGER')")
    public MonthDetailResponse removeMember(@PathVariable("year") int year, @PathVariable("month") int month,
                                            @PathVariable("memberId") Long memberId,
                                            @AuthenticationPrincipal Jwt jwt) {
        return detail(monthService.removeMember(year, month, memberId, CurrentUser.from(jwt)));
    }

    private MonthDetailResponse detail(FlatMonth month) {
        List<MemberResponse> members = monthService.membersOf(month).stream().map(MemberResponse::from).toList();
        return new MonthDetailResponse(MonthResponse.from(month), members);
    }
}
