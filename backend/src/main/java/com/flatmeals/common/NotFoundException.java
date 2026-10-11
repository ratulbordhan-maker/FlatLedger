package com.flatmeals.common;

/** Thrown when a requested record does not exist. Becomes HTTP 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
