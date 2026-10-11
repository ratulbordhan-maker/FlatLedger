package com.flatmeals.common;

import java.time.Instant;
import java.util.Map;

/**
 * The JSON body returned for every error, so the frontend can always read
 * errors the same way, e.g.
 *
 * { "status": 400, "error": "Bad Request", "message": "Please fix the highlighted fields",
 *   "fieldErrors": { "name": "must not be blank" }, "timestamp": "..." }
 */
public record ApiError(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(status, error, message, Map.of(), Instant.now());
    }
}
