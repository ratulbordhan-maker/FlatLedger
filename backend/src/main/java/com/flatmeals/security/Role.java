package com.flatmeals.security;

/**
 * The two roles. Matches the users.role ENUM column.
 * Spring Security sees them as "ROLE_MANAGER" and "ROLE_MEMBER", which is
 * what hasRole('MANAGER') checks.
 */
public enum Role {
    MANAGER,
    MEMBER
}
