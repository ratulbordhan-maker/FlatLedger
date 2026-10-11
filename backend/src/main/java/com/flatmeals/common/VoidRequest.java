package com.flatmeals.common;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body for every ".../void" endpoint: {"reason": "Entered twice by mistake"} */
public record VoidRequest(
        @NotBlank @Size(max = 255) String reason) {
}
