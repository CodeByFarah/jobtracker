package com.jobtrack.exception;

/**
 * Google refused our credentials, e.g. the user revoked access or the refresh token expired.
 * The user has to connect Gmail again. Mapped to 409.
 */
public class GmailAuthException extends RuntimeException {

    public GmailAuthException(String message) {
        super(message);
    }

    public GmailAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
