package org.apache.camel.processor.aggregate;

import org.apache.camel.Expression;

/**
 * Parameter information to be used for method invocation.
 */
public class AggregationStrategyParameterInfo {
    private final int index;
    private final Class<?> type;
    private Expression expression;

    public AggregationStrategyParameterInfo(int index, Class<?> type, Expression expression) {
        this.index = index;
        this.type = type;
        this.expression = expression;
    }

    public Expression getExpression() {
        return expression;
    }

    public int getIndex() {
        return index;
    }

    public Class<?> getType() {
        return type;
    }

    public void setExpression(Expression expression) {
        this.expression = expression;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ParameterInfo");
        sb.append("[index=").append(index);
        sb.append(", type=").append(type);
        sb.append(", expression=").append(expression);
        sb.append(']');
        return sb.toString();
    }
}
