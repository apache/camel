package org.apache.camel.component.sql.sspt.ast;

import org.apache.camel.Expression;
import org.apache.camel.builder.ExpressionBuilder;


public class InputParameter {

    String name;

    int sqlType;

    Expression valueExpression;

    Class javaType;


    public InputParameter(String name, int sqlType, String valueSrcAsStr, Class javaType) {
        this.name = name;
        this.sqlType = sqlType;
        this.javaType = javaType;
        this.valueExpression = parseValueExpression(valueSrcAsStr);
    }


    private Expression parseValueExpression(String str) {
        return ExpressionBuilder.simpleExpression(str);
    }

    public String getName() {
        return name;
    }

    public int getSqlType() {
        return sqlType;
    }

    public Expression getValueExpression() {
        return valueExpression;
    }

    public Class getJavaType() {
        return javaType;
    }


}
