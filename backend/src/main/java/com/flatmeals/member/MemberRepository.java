package com.flatmeals.member;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByOrderByNameAsc();

    List<Member> findByStatusOrderByNameAsc(MemberStatus status);

    Optional<Member> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    /** Same name used by a DIFFERENT member (for renaming). */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
