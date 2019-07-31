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
package org.apache.camel.component.mock;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.support.builder.ExpressionBuilder;

/**
 * A support class for building expression clauses.
 * <p/>
 * This implementation is a derived copy of the <tt>org.apache.camel.builder.ExpressionClauseSupport</tt> from camel-core,
 * that are specialized for being used with the mock component and separated from camel-core.
 */
public class MockExpressionClauseSupport<T> {

    private T result;
    private Expression expressionValue;
    private ExpressionFactory expressionType;

    public MockExpressionClauseSupport(T result) {
        this.result = result;
    }

    // Helper expressions
    // -------------------------------------------------------------------------

    /**
     * Specify an {@link org.apache.camel.Expression} instance
     */
    public T expression(Expression expression) {
        setExpressionValue(expression);
        if (expression instanceof ExpressionFactory) {
            setExpressionType((ExpressionFactory) expression);
        }
        return result;
    }

    /**
     * Specify an {@link ExpressionFactory} instance
     */
    public T language(ExpressionFactory expression) {
        setExpressionType(expression);
        return result;
    }

    /**
     * Specify the constant expression value.
     *
     * <b>Important:</b> this is a fixed constant value that is only set once during starting up the route,
     * do not use this if you want dynamic values during routing.
     */
    public T constant(Object value) {
        return expression(ExpressionBuilder.constantExpression(value));
    }

    /**
     * An expression of the exchange
     */
    public T exchange() {
        return expression(ExpressionBuilder.exchangeExpression());
    }

    /**
     * An expression of an inbound message
     */
    public T inMessage() {
        return expression(ExpressionBuilder.inMessageExpression());
    }

    /**
     * An expression of an inbound message body
     */
    public T body() {
        return expression(ExpressionBuilder.bodyExpression());
    }

    /**
     * An expression of an inbound message body converted to the expected type
     */
    public T body(Class<?> expectedType) {
        return expression(ExpressionBuilder.bodyExpression(expectedType));
    }

    /**
     * An expression of an inbound message header of the given name
     */
    public T header(String name) {
        return expression(ExpressionBuilder.headerExpression(name));
    }

    /**
     * An expression of the inbound headers
     */
    public T headers() {
        return expression(ExpressionBuilder.headersExpression());
    }

    /**
     * An expression of the exchange pattern
     */
    public T exchangePattern() {
        return expression(ExpressionBuilder.exchangePatternExpression());
    }

    /**
     * An expression of an exchange property of the given name
     */
    public T exchangeProperty(String name) {
        return expression(ExpressionBuilder.exchangePropertyExpression(name));
    }

    /**
     * An expression of the exchange properties
     */
    public T exchangeProperties() {
        return expression(ExpressionBuilder.exchangePropertiesExpression());
    }

    // Languages
    // -------------------------------------------------------------------------

    /**
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param bean the name of the bean looked up the registry
     * @return the builder to continue processing the DSL
     */
    public T method(String bean) {
        return expression(ExpressionBuilder.beanExpression(bean));
    }

    /**
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param bean the name of the bean looked up the registry
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T method(String bean, String method) {
        if (method != null) {
            return expression(ExpressionBuilder.languageExpression("bean", bean + "?method=" + method));
        } else {
            return method(bean);
        }
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/groovy.html">Groovy
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return expression(ExpressionBuilder.languageExpression("groovy", text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text) {
        return expression(ExpressionBuilder.languageExpression("jsonpath", text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/ognl.html">OGNL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return expression(ExpressionBuilder.languageExpression("ognl", text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/mvel.html">MVEL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T mvel(String text) {
        return expression(ExpressionBuilder.languageExpression("mvel", text));
    }

    /**
     * Evaluates a {@link Expression} by looking up existing {@link Expression}
     * from the {@link org.apache.camel.spi.Registry}
     *
     * @param ref refers to the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ref(String ref) {
        return expression(ExpressionBuilder.languageExpression("ref", ref));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/spel.html">SpEL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T spel(String text) {
        return expression(ExpressionBuilder.languageExpression("spel", text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T simple(String text) {
        return expression(ExpressionBuilder.languageExpression("simple", text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/hl7.html">HL7 Terser
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T hl7terser(String text) {
        return expression(ExpressionBuilder.languageExpression("hl7terser", text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return expression(ExpressionBuilder.languageExpression("xpath", text));
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return expression(ExpressionBuilder.languageExpression("xquery", text));
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param language the name of the language
     * @param expression the expression in the given language
     * @return the builder to continue processing the DSL
     */
    public T language(String language, String expression) {
        return expression(ExpressionBuilder.languageExpression(language, expression));
    }

    // Properties
    // -------------------------------------------------------------------------

    public Expression getExpressionValue() {
        return expressionValue;
    }

    public void setExpressionValue(Expression expressionValue) {
        this.expressionValue = expressionValue;
    }

    public ExpressionFactory getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(ExpressionFactory expressionType) {
        this.expressionType = expressionType;
    }

    protected Expression createExpression(CamelContext camelContext) {
        if (getExpressionValue() == null) {
            if (getExpressionType() != null) {
                setExpressionValue(getExpressionType().createExpression(camelContext));
            } else {
                throw new IllegalStateException("No expression value configured");
            }
        }
        return getExpressionValue();
    }

    protected void configureExpression(CamelContext camelContext, Expression expression) {
        // noop
    }

}
