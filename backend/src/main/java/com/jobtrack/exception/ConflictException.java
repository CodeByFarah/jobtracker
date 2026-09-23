package com.jobtrack.exception;

/**
 * The request is well-formed but conflicts with the current state of the data, e.g. a duplicate
 * email, a disallowed status transition, or deleting a company that still has applications.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
