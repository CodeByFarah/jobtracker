package com.jobtrack.exception;

/** The AI model (local Ollama or the online service) could not be reached or returned an unusable answer. Mapped to 503. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
