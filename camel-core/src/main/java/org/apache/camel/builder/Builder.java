/**
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

import org.apache.camel.Expression;

/**
 * A helper class for including portions of the <a
 * href="http://camel.apache.org/expression.html">expression</a> and
 * <a href="http://camel.apache.org/predicate.html">predicate</a> <a
 * href="http://camel.apache.org/dsl.html">Java DSL</a>
 *
 * @version $Revision$
 */
public final class Builder {

    /**
     * Utility classes should not have a public constructor.
     */
    private Builder() {
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">bean expression</a>
     * value builder.
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a reference to a bean (String).
     *
     * @param beanOrBeanRef  either an instanceof a bean or a reference to bean to lookup in the Registry
     * @return the builder
     */
    public static ValueBuilder bean(final Object beanOrBeanRef) {
        return bean(beanOrBeanRef, null);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">bean expression</a>
     * value builder.
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a reference to a bean (String).
     *
     * @param beanOrBeanRef  either an instanceof a bean or a reference to bean to lookup in the Registry
     * @param method the method name
     * @return the builder
     */
    public static ValueBuilder bean(Object beanOrBeanRef, String method) {
        Expression expression;
        if (beanOrBeanRef instanceof String) {
            expression = ExpressionBuilder.beanExpression((String) beanOrBeanRef, method);
        } else {
            expression = ExpressionBuilder.beanExpression(beanOrBeanRef, method);
        }
        return new ValueBuilder(expression);
    }
    
    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">bean expression</a>
     * value builder
     *
     * @param beanType the bean class which will be invoked
     * @param method   name of method to invoke
     * @return the builder
     */
    public static ValueBuilder bean(Class<?> beanType, String method) {
        Expression expression = ExpressionBuilder.beanExpression(beanType, method);
        return new ValueBuilder(expression);
    }

    /**
     * Returns a constant expression
     */
    public static ValueBuilder constant(Object value) {
        Expression expression = ExpressionBuilder.constantExpression(value);
        return new ValueBuilder(expression);
    }
    
    /**
     * Returns a simple expression  
     */
    public static ValueBuilder simple(String value) {
        Expression expression = ExpressionBuilder.simpleExpression(value);
        return new ValueBuilder(expression);
    }
    
    /**
     * Returns a predicate and value builder for headers on an exchange
     */
    public static ValueBuilder header(String name) {
        Expression expression = ExpressionBuilder.headerExpression(name);
        return new ValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for properties on an exchange
     */
    public static ValueBuilder property(String name) {
        Expression expression = ExpressionBuilder.propertyExpression(name);
        return new ValueBuilder(expression);
    }    
    
    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public static ValueBuilder body() {
        Expression expression = ExpressionBuilder.bodyExpression();
        return new ValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a
     * specific type
     */
    public static <T> ValueBuilder bodyAs(Class<T> type) {
        Expression expression = ExpressionBuilder.bodyExpression(type);
        return new ValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound body on an
     * exchange
     */
    public static ValueBuilder outBody() {
        Expression expression = ExpressionBuilder.outBodyExpression();
        return new ValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the outbound message body as a
     * specific type
     */
    public static <T> ValueBuilder outBodyAs(Class<T> type) {
        Expression expression = ExpressionBuilder.outBodyExpression(type);
        return new ValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the fault body on an
     * exchange
     */
    public static ValueBuilder faultBody() {
        Expression expression = ExpressionBuilder.faultBodyExpression();
        return new ValueBuilder(expression);
    }

    /**
     * Returns a predicate and value builder for the fault message body as a
     * specific type
     */
    public static <T> ValueBuilder faultBodyAs(Class<T> type) {
        Expression expression = ExpressionBuilder.faultBodyExpression(type);
        return new ValueBuilder(expression);
    }

    /**
     * Returns an expression for the given system property
     */
    public static ValueBuilder systemProperty(final String name) {
        return systemProperty(name, null);
    }

    /**
     * Returns an expression for the given system property
     */
    public static ValueBuilder systemProperty(final String name, final String defaultValue) {
        return new ValueBuilder(ExpressionBuilder.systemPropertyExpression(name, defaultValue));
    }

    /**
     * Returns a predicate and value builder for the exception message on an exchange
     */
    public static ValueBuilder exceptionMessage() {
        Expression expression = ExpressionBuilder.exchangeExceptionMessageExpression();
        return new ValueBuilder(expression);
    }
    
    /**
     * Returns a predicate and value builder for the exception stacktrace on an exchange
     */
    public static ValueBuilder exceptionStackTrace() {
        Expression expression = ExpressionBuilder.exchangeExceptionStackTraceExpression();
        return new ValueBuilder(expression);
    }

    /**
     * Returns an expression that replaces all occurrences of the regular 
     * expression with the given replacement
     */
    public static ValueBuilder regexReplaceAll(Expression content, String regex, String replacement) {
        Expression newExp = ExpressionBuilder.regexReplaceAll(content, regex, replacement);
        return new ValueBuilder(newExp);
    }

    /**
     * Returns an expression that replaces all occurrences of the regular 
     * expression with the given replacement
     */
    public static ValueBuilder regexReplaceAll(Expression content, String regex, Expression replacement) {
        Expression newExp = ExpressionBuilder.regexReplaceAll(content, regex, replacement);
        return new ValueBuilder(newExp);
    }

    /**
     * Returns an expression processing the exchange to the given endpoint uri.
     *
     * @param uri   endpoint uri
     * @return the builder
     */
    public static ValueBuilder sendTo(String uri) {
        Expression expression = ExpressionBuilder.toExpression(uri);
        return new ValueBuilder(expression);
    }

}
