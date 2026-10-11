package com.flatmeals.account;

import com.flatmeals.month.FlatMonth;
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

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MonthClosingController.class)
@Import(SecurityConfig.class)
class MonthClosingControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private MonthClosingService closingService;

    private static RequestPostProcessor asManager() {
        return jwt().jwt(token -> token.claim("uid", 1L).claim("memberId", 1L).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private static RequestPostProcessor asMember() {
        return jwt().jwt(token -> token.claim("uid", 2L).claim("memberId", 2L).claim("roles", List.of("MEMBER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Test
    void memberCannotCloseOrReopenAMonth() throws Exception {
        mvc.perform(post("/api/months/2026/10/close").with(asMember()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/months/2026/10/reopen").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"I want to\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(closingService);
    }

    @Test
    void managerCanCloseAMonth() throws Exception {
        FlatMonth october = new FlatMonth(2026, 10);
        october.close(new BigDecimal("135.75"), new BigDecimal("12500.00"), new BigDecimal("92.081031"), 1L);
        when(closingService.close(eq(2026), eq(10), any())).thenReturn(october);

        mvc.perform(post("/api/months/2026/10/close").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.mealRate").value(92.081031));
    }

    @Test
    void reopeningWithoutAReasonIsRejected() throws Exception {
        mvc.perform(post("/api/months/2026/10/reopen").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
    }
}
