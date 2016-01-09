package org.apache.camel.component.sql.stored.template.ast;

public class ParseRuntimeException extends RuntimeException {
    public ParseRuntimeException(String message) {
        super(message);
    }

    public ParseRuntimeException(Throwable cause) {
        super(cause);
    }
}
