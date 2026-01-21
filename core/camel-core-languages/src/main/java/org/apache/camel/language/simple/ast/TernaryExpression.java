/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.language.simple.ast;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.BaseSimpleParser;
import org.apache.camel.language.simple.types.SimpleParserException;
import org.apache.camel.language.simple.types.SimpleToken;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Represents a ternary expression in the AST.
 * <p>
 * Syntax: condition ? trueValue : falseValue
 */
public class TernaryExpression extends BaseSimpleNode {

    private SimpleNode condition;
    private SimpleNode trueValue;
    private SimpleNode falseValue;

    public TernaryExpression(SimpleToken token) {
        super(token);
    }

    @Override
    public String toString() {
        return condition + " ? " + trueValue + " : " + falseValue;
    }

    public boolean acceptCondition(SimpleNode condition) {
        this.condition = condition;
        return true;
    }

    public boolean acceptTrueValue(SimpleNode trueValue) {
        this.trueValue = trueValue;
        return true;
    }

    public boolean acceptFalseValue(SimpleNode falseValue) {
        this.falseValue = falseValue;
        return true;
    }

    public SimpleNode getCondition() {
        return condition;
    }

    public SimpleNode getTrueValue() {
        return trueValue;
    }

    public SimpleNode getFalseValue() {
        return falseValue;
    }

    @Override
    public Expression createExpression(CamelContext camelContext, String expression) {
        if (condition == null) {
            throw new SimpleParserException("Ternary operator ? has no condition at index " + token.getIndex(), token.getIndex());
        }
        if (trueValue == null) {
            throw new SimpleParserException("Ternary operator ? has no true value at index " + token.getIndex(), token.getIndex());
        }
        if (falseValue == null) {
            throw new SimpleParserException("Ternary operator : has no false value at index " + token.getIndex(), token.getIndex());
        }

        // the expression parser does not parse literal text into single/double quote tokens
        // so we need to manually remove leading quotes from the literal text
        if (trueValue instanceof LiteralExpression le) {
            String text = le.getText();
            String changed = StringHelper.removeLeadingAndEndingQuotes(text);
            if (!changed.equals(text)) {
                le.replaceText(changed);
            }
        }
        if (falseValue instanceof LiteralExpression le) {
            String text = le.getText();
            String changed = StringHelper.removeLeadingAndEndingQuotes(text);
            if (!changed.equals(text)) {
                le.replaceText(changed);
            }
        }

        final Expression conditionExp = condition.createExpression(camelContext, expression);
        final Expression trueExp = trueValue.createExpression(camelContext, expression);
        final Expression falseExp = falseValue.createExpression(camelContext, expression);

        return createTernaryExpression(camelContext, conditionExp, trueExp, falseExp);
    }

    private Expression createTernaryExpression(
            final CamelContext camelContext, final Expression conditionExp,
            final Expression trueExp, final Expression falseExp) {
        return new Expression() {
            @Override
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                // Convert condition to predicate
                Predicate predicate = ExpressionToPredicateAdapter.toPredicate(conditionExp);

                if (predicate.matches(exchange)) {
                    return trueExp.evaluate(exchange, type);
                } else {
                    return falseExp.evaluate(exchange, type);
                }
            }

            @Override
            public String toString() {
                return condition + " ? " + trueValue + " : " + falseValue;
            }
        };
    }

    @Override
    public String createCode(CamelContext camelContext, String expression) throws SimpleParserException {
        return BaseSimpleParser.CODE_START + doCreateCode(camelContext, expression) + BaseSimpleParser.CODE_END;
    }

    private String doCreateCode(CamelContext camelContext, String expression) throws SimpleParserException {
        ObjectHelper.notNull(condition, "condition node", this);
        ObjectHelper.notNull(trueValue, "trueValue node", this);
        ObjectHelper.notNull(falseValue, "falseValue node", this);

        final String conditionCode = condition.createCode(camelContext, expression);
        final String trueCode = trueValue.createCode(camelContext, expression);
        final String falseCode = falseValue.createCode(camelContext, expression);

        return "ternary(exchange, " + conditionCode + ", " + trueCode + ", " + falseCode + ")";
    }

}
