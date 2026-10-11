package com.flatmeals.common;

/**
 * Thrown when a request is valid but clashes with existing data,
 * e.g. a duplicate member name. Becomes HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
