package org.apache.camel.processor;

public class ThrottleException extends RuntimeException {

    private static final long serialVersionUID = 1993185881371058773L;

    public ThrottleException() {
        super();
    }

    public ThrottleException(String message) {
        super(message);
    }

    public ThrottleException(String message, Throwable cause) {
        super(message, cause);
    }

    public ThrottleException(Throwable cause) {
        super(cause);
    }
}
