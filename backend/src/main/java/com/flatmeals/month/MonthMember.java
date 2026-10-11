package com.flatmeals.month;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * "This member is part of this month" (table month_members).
 *
 * This is what makes history safe: the rows of a month's meal grid come from
 * here, not from the members' current status. Deactivating someone later
 * never removes them from months they were part of.
 */
@Entity
@Table(name = "month_members")
@IdClass(MonthMemberId.class)
public class MonthMember {

    @Id
    @Column(name = "month_id")
    private Long monthId;

    @Id
    @Column(name = "member_id")
    private Long memberId;

    protected MonthMember() {
        // required by JPA
    }

    public MonthMember(Long monthId, Long memberId) {
        this.monthId = monthId;
        this.memberId = memberId;
    }

    public Long getMonthId() { return monthId; }
    public Long getMemberId() { return memberId; }
}
