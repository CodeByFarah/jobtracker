package com.jobtrack.exception;

/** The local AI model (Ollama) could not be reached or returned an unusable answer. Mapped to 503. */
public class AiUnavailableException extends RuntimeException {

    public AiUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
