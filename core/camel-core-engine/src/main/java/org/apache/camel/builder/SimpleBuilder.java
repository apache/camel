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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.language.simple.Simple;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.support.PredicateToExpressionAdapter;
import org.apache.camel.support.ScriptHelper;
import org.apache.camel.support.component.PropertyConfigurerSupport;

/**
 * Creates an {@link Simple} language builder.
 * <p/>
 * This builder is available in the Java DSL from the {@link RouteBuilder} which
 * means that using simple language for {@link Expression}s or
 * {@link Predicate}s is very easy with the help of this builder.
 */
public class SimpleBuilder implements Predicate, Expression, ExpressionResultTypeAware, PropertyConfigurer {

    private final String text;
    private Class<?> resultType;
    // cache the expression/predicate
    private volatile Expression expression;
    private volatile Predicate predicate;

    public SimpleBuilder(String text) {
        this.text = text;
    }

    public static SimpleBuilder simple(String text) {
        return new SimpleBuilder(text);
    }

    public static SimpleBuilder simple(String text, Class<?> resultType) {
        SimpleBuilder answer = simple(text);
        answer.setResultType(resultType);
        return answer;
    }

    public static SimpleBuilder simpleF(String formatText, Object... values) {
        return simple(String.format(formatText, values));
    }

    public static SimpleBuilder simpleF(String formatText, Class<?> resultType, Object... values) {
        return simple(String.format(formatText, values), resultType);
    }

    @Override
    public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
        if (target != this) {
            throw new IllegalStateException("Can only configure our own instance !");
        }
        switch (ignoreCase ? name.toLowerCase() : name) {
            case "resulttype":
            case "resultType":
                setResultType(PropertyConfigurerSupport.property(camelContext, Class.class, value)); return true;
            default:
                return false;
        }
    }

    public String getText() {
        return text;
    }

    @Override
    public String getExpressionText() {
        return getText();
    }

    @Override
    public Class<?> getResultType() {
        return resultType;
    }

    public void setResultType(Class<?> resultType) {
        this.resultType = resultType;
    }

    public SimpleBuilder resultType(Class<?> resultType) {
        setResultType(resultType);
        return this;
    }

    @Override
    public boolean matches(Exchange exchange) {
        if (predicate == null) {
            predicate = createPredicate(exchange);
        }
        return predicate.matches(exchange);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        if (expression == null) {
            expression = createExpression(exchange);
        }
        return expression.evaluate(exchange, type);
    }

    private Predicate createPredicate(Exchange exchange) {
        try {
            // resolve property placeholders
            String resolve = exchange.getContext().resolvePropertyPlaceholders(text);
            // and optional it be refer to an external script on the
            // file/classpath
            resolve = ScriptHelper.resolveOptionalExternalScript(exchange.getContext(), exchange, resolve);
            Language simple = exchange.getContext().resolveLanguage("simple");
            return simple.createPredicate(resolve);
        } catch (Exception e) {
            throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
        }
    }

    private Expression createExpression(Exchange exchange) {
        try {
            // resolve property placeholders
            String resolve = exchange.getContext().resolvePropertyPlaceholders(text);
            // and optional it be refer to an external script on the
            // file/classpath
            resolve = ScriptHelper.resolveOptionalExternalScript(exchange.getContext(), exchange, resolve);
            Language simple = exchange.getContext().resolveLanguage("simple");
            return createSimpleExpression(simple, resolve, resultType);
        } catch (Exception e) {
            throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
        }
    }

    private static Expression createSimpleExpression(Language simple, String expression, Class<?> resultType) {
        if (resultType == Boolean.class || resultType == boolean.class) {
            // if its a boolean as result then its a predicate
            Predicate predicate = simple.createPredicate(expression);
            return PredicateToExpressionAdapter.toExpression(predicate);
        } else {
            Expression exp = simple.createExpression(expression);
            if (resultType != null) {
                exp = ExpressionBuilder.convertToExpression(exp, resultType);
            }
            return exp;
        }
    }

    @Override
    public String toString() {
        return "Simple: " + text;
    }

}
