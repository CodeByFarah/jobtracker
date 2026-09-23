package com.jobtrack.exception;

/**
 * A request that passes annotation-based validation but breaks a rule that needs more context,
 * such as a salary range where the minimum exceeds the maximum.
 */
public class BadRequestException extends RuntimeException {

    private final String field;

    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The request field the error relates to, or null. */
    public String getField() {
        return field;
    }
}
