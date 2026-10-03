package com.github.shikejing.apideprecationpolicyjava.version;

/**
 * Thrown when a lifecycle transition is not allowed from the current state.
 */
public class InvalidVersionTransitionException extends RuntimeException {
    public InvalidVersionTransitionException(String message) {
        super(message);
    }
}
