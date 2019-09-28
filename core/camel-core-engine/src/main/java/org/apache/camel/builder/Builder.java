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

import org.apache.camel.Expression;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.util.ObjectHelper;

/**
 * A helper class for including portions of the
 * <a href="http://camel.apache.org/expression.html">expression</a> and
 * <a href="http://camel.apache.org/predicate.html">predicate</a>
 * <a href="http://camel.apache.org/dsl.html">Java DSL</a>
 * <p/>
 * Implementation of this builder should favor build expressions using the
 * definition classes from the <tt>org.apache.camel.model.language</tt> package,
 * to build the routes using the same types as it would happen when using XML
 * DSL.
 */
public final class Builder {

    /**
     * Utility classes should not have a public constructor.
     */
    private Builder() {
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">bean
     * expression</a> value builder.
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a
     * reference to a bean (String).
     *
     * @param beanOrBeanRef either an instanceof a bean or a reference to bean
     *            to lookup in the Registry
     * @return the builder
     */
    public static ValueBuilder bean(final Object beanOrBeanRef) {
        return bean(beanOrBeanRef, null);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">bean
     * expression</a> value builder.
     * <p/>
     * This method accepts dual parameters. Either an bean instance or a
     * reference to a bean (String).
     *
     * @param beanOrBeanRef either an instanceof a bean or a reference to bean
     *            to lookup in the Registry
     * @param method the method name
     * @return the builder
     */
    public static ValueBuilder bean(Object beanOrBeanRef, String method) {
        Expression exp;
        if (beanOrBeanRef instanceof String) {
            exp = new MethodCallExpression((String)beanOrBeanRef, method);
        } else {
            exp = new MethodCallExpression(beanOrBeanRef, method);
        }
        return new ValueBuilder(exp);
    }

    /**
     * Returns a <a href="http://camel.apache.org/bean-language.html">bean
     * expression</a> value builder
     *
     * @param beanType the bean class which will be invoked
     * @param method name of method to invoke
     * @return the builder
     */
    public static ValueBuilder bean(Class<?> beanType, String method) {
        Expression exp = new MethodCallExpression(beanType, method);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a constant expression
     */
    public static ValueBuilder constant(Object value) {
        Expression exp;
        if (value instanceof String) {
            exp = new ConstantExpression((String)value);
        } else {
            exp = ExpressionBuilder.constantExpression(value);
        }
        return new ValueBuilder(exp);
    }

    /**
     * Returns a constant expression
     */
    public static ValueBuilder language(String language, String expression) {
        Expression exp = new LanguageExpression(language, expression);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a simple expression
     */
    public static ValueBuilder simple(String value) {
        Expression exp = new SimpleExpression(value);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a simple expression
     */
    public static ValueBuilder simple(String value, Class<?> resultType) {
        SimpleExpression exp = new SimpleExpression(value);
        exp.setResultType(resultType);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a predicate and value builder for headers on an exchange
     */
    public static ValueBuilder header(String name) {
        Expression exp = new HeaderExpression(name);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a predicate and value builder for properties on an exchange
     */
    public static ValueBuilder exchangeProperty(String name) {
        Expression exp = new ExchangePropertyExpression(name);
        return new ValueBuilder(exp);
    }

    /**
     * Returns a predicate and value builder for the inbound body on an exchange
     */
    public static ValueBuilder body() {
        Expression exp = new SimpleExpression("${body}");
        return new ValueBuilder(exp);
    }

    /**
     * Returns a predicate and value builder for the inbound message body as a
     * specific type
     */
    public static <T> ValueBuilder bodyAs(Class<T> type) {
        ObjectHelper.notNull(type, "type");
        Expression exp = new SimpleExpression(String.format("${bodyAs(%s)}", type.getCanonicalName()));
        return new ValueBuilder(exp);
    }

    /**
     * Returns an expression for the given system property
     */
    public static ValueBuilder systemProperty(final String name) {
        Expression exp = new SimpleExpression(String.format("${sys.%s}", name));
        return new ValueBuilder(exp);
    }

    /**
     * Returns an expression for the given system property
     */
    public static ValueBuilder systemProperty(final String name, final String defaultValue) {
        return new ValueBuilder(ExpressionBuilder.systemPropertyExpression(name, defaultValue));
    }

    /**
     * Returns a predicate and value builder for the exception message on an
     * exchange
     */
    public static ValueBuilder exceptionMessage() {
        Expression exp = new SimpleExpression("${exception.message}");
        return new ValueBuilder(exp);
    }

    /**
     * Returns a predicate and value builder for the exception stacktrace on an
     * exchange
     */
    public static ValueBuilder exceptionStackTrace() {
        Expression exp = new SimpleExpression("${exception.stacktrace}");
        return new ValueBuilder(exp);
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

}
