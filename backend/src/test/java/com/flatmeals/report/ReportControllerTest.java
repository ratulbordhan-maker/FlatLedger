package com.flatmeals.report;

import com.flatmeals.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired private MockMvc mvc;

    @MockitoBean private MonthReportService reportService;

    private static RequestPostProcessor asManager() {
        return jwt().jwt(token -> token.claim("uid", 1L).claim("memberId", 1L).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private static RequestPostProcessor asMember() {
        return jwt().jwt(token -> token.claim("uid", 2L).claim("memberId", 2L).claim("roles", List.of("MEMBER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Test
    void managerDownloadsTheMonthAsACsvFile() throws Exception {
        when(reportService.build(2026, 10)).thenReturn(MonthReportCsvTest.october());

        mvc.perform(get("/api/months/2026/10/report.csv").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"flat-meals-2026-10.csv\""))
                .andExpect(content().string(containsString("Anila,0.00,60,6000.00")));
    }

    @Test
    void membersCannotDownloadEveryonesDues() throws Exception {
        mvc.perform(get("/api/months/2026/10/report.csv").with(asMember()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(reportService);
    }

    @Test
    void needsALogin() throws Exception {
        mvc.perform(get("/api/months/2026/10/report.csv")).andExpect(status().isUnauthorized());
    }
}
