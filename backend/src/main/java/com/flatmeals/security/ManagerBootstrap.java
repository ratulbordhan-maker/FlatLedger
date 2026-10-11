package com.flatmeals.security;

import com.flatmeals.member.Member;
import com.flatmeals.member.MemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Solves the chicken-and-egg problem: only a manager can create logins,
 * so who creates the first manager?
 *
 * On startup, if the users table is empty, this creates one flat member and a
 * MANAGER login for them, using the app.bootstrap.* settings in application.yml.
 * If any user already exists, it does nothing.
 */
@Component
public class ManagerBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ManagerBootstrap.class);

    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final String managerName;
    private final String managerUsername;
    private final String managerPassword;

    public ManagerBootstrap(UserRepository userRepository,
                            MemberRepository memberRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.bootstrap.manager-name}") String managerName,
                            @Value("${app.bootstrap.manager-username}") String managerUsername,
                            @Value("${app.bootstrap.manager-password}") String managerPassword) {
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.managerName = managerName;
        this.managerUsername = managerUsername;
        this.managerPassword = managerPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        Member member = memberRepository.findByNameIgnoreCase(managerName)
                .orElseGet(() -> memberRepository.save(new Member(managerName, LocalDate.now())));
        userRepository.save(new AppUser(managerUsername, passwordEncoder.encode(managerPassword),
                Role.MANAGER, member.getId()));

        log.warn("Created the first manager login '{}' for member '{}'. "
                + "Log in and change the password straight away.", managerUsername, managerName);
    }
}
