package org.apache.camel.builder;

import org.apache.camel.Expression;
import org.apache.camel.model.language.DatasonnetExpression;

public class DatasonnetBuilder extends ValueBuilder {
    public DatasonnetBuilder(DatasonnetExpression expression) {
        super(expression);
    }

    public static DatasonnetBuilder datasonnet(String expression) {
        return new DatasonnetBuilder(new DatasonnetExpression(expression));
    }

    public static DatasonnetBuilder datasonnet(Expression expression) {
        return new DatasonnetBuilder(new DatasonnetExpression(expression));
    }

    public static DatasonnetBuilder datasonnet(String expression, Class<?> resultType) {
        DatasonnetExpression exp = new DatasonnetExpression(expression);
        exp.setResultType(resultType);
        return new DatasonnetBuilder(exp);
    }

    public static DatasonnetBuilder datasonnet(Expression expression, Class<?> resultType) {
        DatasonnetExpression exp = new DatasonnetExpression(expression);
        exp.setResultType(resultType);
        return new DatasonnetBuilder(exp);
    }

    public DatasonnetBuilder bodyMediaType(String bodyMediaType) {
        ((DatasonnetExpression) getExpression()).setBodyMediaType(bodyMediaType);
        return this;
    }

    public DatasonnetBuilder outputMediaType(String outputMediaType) {
        ((DatasonnetExpression) getExpression()).setOutputMediaType(outputMediaType);
        return this;
    }
}
