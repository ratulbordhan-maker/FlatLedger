package com.flatmeals.member;

import com.flatmeals.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the permission rules are enforced by the BACKEND.
 *
 * @WebMvcTest starts only the web layer (controller + security + error handling),
 * not the database. The service is a mock, so we can check it is never even
 * called when a member tries a manager-only action.
 */
@WebMvcTest(MemberController.class)
@Import(SecurityConfig.class)
class MemberControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MemberService memberService;

    /** Pretend to be logged in as the manager (user 1, member 1). */
    private static RequestPostProcessor asManager() {
        return jwt().jwt(token -> token.claim("uid", 1L).claim("memberId", 1L).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    /** Pretend to be logged in as an ordinary member (user 2, member 2). */
    private static RequestPostProcessor asMember() {
        return jwt().jwt(token -> token.claim("uid", 2L).claim("memberId", 2L).claim("roles", List.of("MEMBER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Test
    void notLoggedInGets401() throws Exception {
        mvc.perform(get("/api/members"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCanSeeTheMemberList() throws Exception {
        when(memberService.list(null)).thenReturn(List.of(new Member("Anila", LocalDate.of(2026, 10, 1))));

        mvc.perform(get("/api/members").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Anila"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void memberCannotAddMembers() throws Exception {
        mvc.perform(post("/api/members").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Intruder\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(memberService);
    }

    @Test
    void memberCannotDeactivateAnyone() throws Exception {
        mvc.perform(post("/api/members/3/deactivate").with(asMember()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(memberService);
    }

    @Test
    void memberCannotCreateLoginsOrResetPasswords() throws Exception {
        mvc.perform(post("/api/members/3/login").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"hacker\", \"password\": \"password123\", \"role\": \"MANAGER\"}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/members/3/login/reset-password").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"password123\"}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(memberService);
    }

    @Test
    void managerCanAddAMember() throws Exception {
        when(memberService.create(eq("Srijila"), any(), any()))
                .thenReturn(new Member("Srijila", LocalDate.of(2026, 10, 1)));

        mvc.perform(post("/api/members").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Srijila\", \"joinedOn\": \"2026-10-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Srijila"))
                .andExpect(jsonPath("$.joinedOn").value("2026-10-01"));
    }

    @Test
    void blankNameIsRejectedWithAFieldError() throws Exception {
        mvc.perform(post("/api/members").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void managerCanDeactivateAMember() throws Exception {
        Member member = new Member("Srijila", LocalDate.of(2026, 1, 1));
        member.deactivate(LocalDate.of(2026, 10, 11));
        when(memberService.deactivate(eq(3L), isNull(), any())).thenReturn(member);

        mvc.perform(post("/api/members/3/deactivate").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }
}
