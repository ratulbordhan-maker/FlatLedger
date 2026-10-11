package com.flatmeals.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A person living in the flat (table "members").
 *
 * Members are never deleted: someone who leaves is marked INACTIVE, so their
 * meals, payments and balances in past months stay exactly as they were.
 */
@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MemberStatus status = MemberStatus.ACTIVE;

    @Column(name = "joined_on", nullable = false)
    private LocalDate joinedOn;

    @Column(name = "left_on")
    private LocalDate leftOn;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected Member() {
        // required by JPA
    }

    public Member(String name, LocalDate joinedOn) {
        this.name = name;
        this.joinedOn = joinedOn;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    /** Marks the member as left. Their history is untouched. */
    public void deactivate(LocalDate leftOn) {
        if (leftOn.isBefore(joinedOn)) {
            throw new IllegalArgumentException("Leaving date cannot be before the joining date (" + joinedOn + ")");
        }
        this.status = MemberStatus.INACTIVE;
        this.leftOn = leftOn;
    }

    public void reactivate() {
        this.status = MemberStatus.ACTIVE;
        this.leftOn = null;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public MemberStatus getStatus() { return status; }
    public LocalDate getJoinedOn() { return joinedOn; }
    public LocalDate getLeftOn() { return leftOn; }
    public Instant getCreatedAt() { return createdAt; }
}
