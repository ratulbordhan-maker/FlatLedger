package com.flatmeals.common;

import com.flatmeals.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The backend also serves the website (Phase 8). These tests use a tiny
 * stand-in website from src/test/resources/static.
 */
@WebMvcTest(HomePageController.class)
@Import(SecurityConfig.class)
class WebsiteServingTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void homePageShowsTheApp() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void refreshingADeepLinkStillShowsTheApp() throws Exception {
        mvc.perform(get("/m/2026/10/meals"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test-index-page")))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void realFilesAreServedAndCachedForLong() throws Exception {
        mvc.perform(get("/assets/app-test.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test asset")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void missingFilesAreA404NotTheApp() throws Exception {
        mvc.perform(get("/assets/gone.js")).andExpect(status().isNotFound());
        mvc.perform(get("/logo.png")).andExpect(status().isNotFound());
    }

    @Test
    void apiStillNeedsALogin() throws Exception {
        mvc.perform(get("/api/months")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/no-such-thing")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownApiUrlIsA404NotTheApp() throws Exception {
        mvc.perform(get("/api/no-such-thing")
                        .with(jwt().jwt(token -> token.claim("uid", 1L).claim("roles", List.of("MEMBER")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void decidesWhichPathsArePagesOfTheApp() {
        assertThat(SpaWebConfig.isAppPage("m/2026/10/meals")).isTrue();
        assertThat(SpaWebConfig.isAppPage("login")).isTrue();
        assertThat(SpaWebConfig.isAppPage("api/months")).isFalse();
        assertThat(SpaWebConfig.isAppPage("api")).isFalse();
        assertThat(SpaWebConfig.isAppPage("favicon.ico")).isFalse();
        assertThat(SpaWebConfig.isAppPage("assets/app.js")).isFalse();
    }
}
