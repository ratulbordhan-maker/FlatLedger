package com.flatmeals.month;

import java.io.Serializable;
import java.util.Objects;

/**
 * The two-column primary key of month_members (month_id + member_id).
 * JPA needs a small class like this whenever a table's key has more than one column.
 */
public class MonthMemberId implements Serializable {

    private Long monthId;
    private Long memberId;

    public MonthMemberId() {
    }

    public MonthMemberId(Long monthId, Long memberId) {
        this.monthId = monthId;
        this.memberId = memberId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MonthMemberId that)) {
            return false;
        }
        return Objects.equals(monthId, that.monthId) && Objects.equals(memberId, that.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(monthId, memberId);
    }
}
