package com.flatmeals.month;

import com.flatmeals.member.MemberDtos.MemberResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** The JSON shapes sent to and from the /api/months endpoints. */
public final class MonthDtos {

    private MonthDtos() {
    }

    public record OpenMonthRequest(
            @NotNull @Min(2000) @Max(2100) Integer year,
            @NotNull @Min(1) @Max(12) Integer month) {
    }

    public record AddMonthMemberRequest(
            @NotNull Long memberId) {
    }

    public record MonthResponse(
            Long id,
            int year,
            int month,
            String label,
            MonthStatus status,
            int daysInMonth) {

        public static MonthResponse from(FlatMonth month) {
            return new MonthResponse(month.getId(), month.getYear(), month.getMonthNumber(),
                    month.label(), month.getStatus(), month.daysInMonth());
        }
    }

    public record MonthDetailResponse(
            MonthResponse month,
            List<MemberResponse> members) {
    }
}
