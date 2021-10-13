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
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.ExpressionResultTypeAware;
import org.apache.camel.spi.Language;

/**
 * Creates a Simple language builder.
 * <p/>
 * This builder is available in the Java DSL from the {@link RouteBuilder} which means that using simple language for
 * {@link Expression}s or {@link Predicate}s is very easy with the help of this builder.
 */
@Deprecated
public class SimpleBuilder implements Predicate, Expression, ExpressionResultTypeAware {

    private String text;
    private Class<?> resultType;
    // cache the expression/predicate
    private Language simple;
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
    public String toString() {
        return text;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        if (expression == null) {
            if (simple == null) {
                init(exchange.getContext());
            }
            if (resultType != null) {
                Object[] properties = new Object[2];
                properties[0] = resultType;
                expression = simple.createExpression(text, properties);
            } else {
                expression = simple.createExpression(text);
            }
            expression.init(exchange.getContext());
        }
        return expression.evaluate(exchange, type);
    }

    @Override
    public boolean matches(Exchange exchange) {
        if (predicate == null) {
            if (simple == null) {
                init(exchange.getContext());
            }
            predicate = simple.createPredicate(text);
            predicate.init(exchange.getContext());
        }
        return predicate.matches(exchange);
    }

    @Override
    public void init(CamelContext context) {
        simple = context.resolveLanguage("simple");
        text = context.resolvePropertyPlaceholders(text);
    }
}
