package com.jobtrack.exception;

/**
 * Thrown when a resource does not exist <em>or belongs to another user</em>. Both cases produce
 * the same 404 so that clients cannot probe which ids exist.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Long id) {
        super(resource + " " + id + " not found");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
