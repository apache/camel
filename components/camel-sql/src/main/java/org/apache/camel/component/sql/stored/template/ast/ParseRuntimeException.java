package org.apache.camel.component.sql.stored.template.ast;

/**
 * Created by snurmine on 1/3/16.
 */
public class ParseRuntimeException extends RuntimeException {
    public ParseRuntimeException(String message) {
        super(message);
    }

    public ParseRuntimeException(Throwable cause) {
        super(cause);
    }
}
