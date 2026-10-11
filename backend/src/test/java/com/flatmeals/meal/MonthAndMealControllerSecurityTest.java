package com.flatmeals.meal;

import com.flatmeals.meal.MealDtos.MealGridResponse;
import com.flatmeals.month.FlatMonth;
import com.flatmeals.month.MonthController;
import com.flatmeals.month.MonthService;
import com.flatmeals.month.MonthStatus;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Members can VIEW months and meals; only the manager can CHANGE them. */
@WebMvcTest(controllers = {MonthController.class, MealController.class})
@Import(SecurityConfig.class)
class MonthAndMealControllerSecurityTest {

    private static final String ONE_CELL =
            "{\"entries\": [{\"memberId\": 1, \"date\": \"2026-10-05\", \"meals\": 2}]}";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MonthService monthService;

    @MockitoBean
    private MealService mealService;

    private static RequestPostProcessor asManager() {
        return jwt().jwt(token -> token.claim("uid", 1L).claim("memberId", 1L).claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));
    }

    private static RequestPostProcessor asMember() {
        return jwt().jwt(token -> token.claim("uid", 2L).claim("memberId", 2L).claim("roles", List.of("MEMBER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    private static MealGridResponse emptyOctober() {
        return new MealGridResponse(2026, 10, "October 2026", MonthStatus.OPEN, 31,
                List.of(), List.of(), new BigDecimal("0.00"));
    }

    @Test
    void memberCanViewTheMealGrid() throws Exception {
        when(mealService.getGrid(2026, 10)).thenReturn(emptyOctober());

        mvc.perform(get("/api/months/2026/10/meals").with(asMember()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.daysInMonth").value(31))
                .andExpect(jsonPath("$.label").value("October 2026"));
    }

    @Test
    void memberCannotEditMeals() throws Exception {
        mvc.perform(put("/api/months/2026/10/meals").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON).content(ONE_CELL))
                .andExpect(status().isForbidden());

        verifyNoInteractions(mealService);
    }

    @Test
    void managerCanEditMeals() throws Exception {
        when(mealService.saveMeals(eq(2026), eq(10), anyList(), any())).thenReturn(1);
        when(mealService.getGrid(2026, 10)).thenReturn(emptyOctober());

        mvc.perform(put("/api/months/2026/10/meals").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(ONE_CELL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedCells").value(1))
                .andExpect(jsonPath("$.grid.label").value("October 2026"));
    }

    @Test
    void cellWithoutADateIsRejected() throws Exception {
        mvc.perform(put("/api/months/2026/10/meals").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entries\": [{\"memberId\": 1, \"meals\": 2}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void notLoggedInCannotSeeMeals() throws Exception {
        mvc.perform(get("/api/months/2026/10/meals"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void memberCannotOpenAMonth() throws Exception {
        mvc.perform(post("/api/months").with(asMember())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\": 2026, \"month\": 11}"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(monthService);
    }

    @Test
    void managerCanOpenAMonth() throws Exception {
        FlatMonth november = new FlatMonth(2026, 11);
        when(monthService.open(eq(2026), eq(11), any())).thenReturn(november);
        when(monthService.membersOf(november)).thenReturn(List.of());

        mvc.perform(post("/api/months").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\": 2026, \"month\": 11}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.month.label").value("November 2026"))
                .andExpect(jsonPath("$.month.daysInMonth").value(30));
    }

    @Test
    void month13IsRejected() throws Exception {
        mvc.perform(post("/api/months").with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\": 2026, \"month\": 13}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.month").exists());
    }
}
