package com.flatmeals.account;

import java.io.Serializable;
import java.util.Objects;

/** Two-column primary key of month_cash_summary (month_id + holder_member_id). */
public class MonthHolderId implements Serializable {

    private Long monthId;
    private Long holderMemberId;

    public MonthHolderId() {
    }

    public MonthHolderId(Long monthId, Long holderMemberId) {
        this.monthId = monthId;
        this.holderMemberId = holderMemberId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MonthHolderId that)) {
            return false;
        }
        return Objects.equals(monthId, that.monthId) && Objects.equals(holderMemberId, that.holderMemberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(monthId, holderMemberId);
    }
}
