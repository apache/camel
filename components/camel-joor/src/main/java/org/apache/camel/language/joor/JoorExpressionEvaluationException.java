package org.apache.camel.language.joor;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;

public class JoorExpressionEvaluationException extends ExpressionEvaluationException {

    private final String className;
    private final String code;

    public JoorExpressionEvaluationException(Expression expression, String className, String code, Exchange exchange,
                                             Throwable cause) {
        super(expression, "jOOR evaluation error for class: " + className + " with code:\n" + code, exchange, cause);
        this.className = className;
        this.code = code;
    }

    public String getClassName() {
        return className;
    }

    public String getCode() {
        return code;
    }
}
