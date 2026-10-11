package com.flatmeals.meal;

import com.flatmeals.meal.MealDtos.MealGridResponse;
import com.flatmeals.meal.MealDtos.MealSummaryResponse;
import com.flatmeals.meal.MealDtos.SaveMealsRequest;
import com.flatmeals.meal.MealDtos.SaveMealsResponse;
import com.flatmeals.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 *   GET /api/months/{year}/{month}/meals          any logged-in user   the grid
 *   PUT /api/months/{year}/{month}/meals          MANAGER              save changed cells
 *   GET /api/months/{year}/{month}/meal-summary   any logged-in user   live meal rate + costs
 */
@RestController
@RequestMapping("/api/months/{year}/{month}")
public class MealController {

    private final MealService mealService;

    public MealController(MealService mealService) {
        this.mealService = mealService;
    }

    @GetMapping("/meals")
    public MealGridResponse grid(@PathVariable("year") int year, @PathVariable("month") int month) {
        return mealService.getGrid(year, month);
    }

    @PutMapping("/meals")
    @PreAuthorize("hasRole('MANAGER')")
    public SaveMealsResponse save(@PathVariable("year") int year, @PathVariable("month") int month,
                                  @Valid @RequestBody SaveMealsRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        int changed = mealService.saveMeals(year, month, request.entries(), CurrentUser.from(jwt));
        return new SaveMealsResponse(changed, mealService.getGrid(year, month));
    }

    @GetMapping("/meal-summary")
    public MealSummaryResponse summary(@PathVariable("year") int year, @PathVariable("month") int month) {
        return mealService.getSummary(year, month);
    }
}
