package com.flatmeals.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The bare address (https://your-site/) shows the React app.
 * Every other page of the app is handled by SpaWebConfig.
 */
@Controller
public class HomePageController {

    @GetMapping("/")
    public String home() {
        return "forward:/index.html";
    }
}
