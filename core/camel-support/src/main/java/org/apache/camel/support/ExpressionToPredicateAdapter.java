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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerAware;
import org.apache.camel.util.ObjectHelper;

/**
 * To adapt {@link org.apache.camel.Expression} as a {@link Predicate}
 */
public final class ExpressionToPredicateAdapter implements Predicate, CamelContextAware, PropertyConfigurerAware {
    private final Expression expression;

    public ExpressionToPredicateAdapter(Expression expression) {
        this.expression = expression;
    }

    @Override
    public boolean matches(Exchange exchange) {
        if (expression instanceof Predicate) {
            return ((Predicate) expression).matches(exchange);
        } else {
            Object value = expression.evaluate(exchange, Object.class);
            return ObjectHelper.evaluateValuePredicate(value);
        }
    }

    @Override
    public String toString() {
        return expression.toString();
    }

    /**
     * Converts the given expression into an {@link Predicate}
     */
    public static Predicate toPredicate(final Expression expression) {
        return new ExpressionToPredicateAdapter(expression);
    }

    @Override
    public void init(CamelContext context) {
        expression.init(context);
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        if (expression instanceof CamelContextAware) {
            ((CamelContextAware) expression).setCamelContext(camelContext);
        }
    }

    @Override
    public CamelContext getCamelContext() {
        if (expression instanceof CamelContextAware) {
            return ((CamelContextAware) expression).getCamelContext();
        } else {
            return null;
        }
    }

    @Override
    public PropertyConfigurer getPropertyConfigurer(Object instance) {
        if (expression instanceof PropertyConfigurer) {
            return (PropertyConfigurer) expression;
        } else if (expression instanceof PropertyConfigurerAware) {
            return ((PropertyConfigurerAware) expression).getPropertyConfigurer(expression);
        } else {
            return null;
        }
    }
}