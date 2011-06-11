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

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.builder.xml.Namespaces;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.ELExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.GroovyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.JXPathExpression;
import org.apache.camel.model.language.JavaScriptExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.MvelExpression;
import org.apache.camel.model.language.OgnlExpression;
import org.apache.camel.model.language.PhpExpression;
import org.apache.camel.model.language.PropertyExpression;
import org.apache.camel.model.language.PythonExpression;
import org.apache.camel.model.language.RefExpression;
import org.apache.camel.model.language.RubyExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.SpELExpression;
import org.apache.camel.model.language.SqlExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;
import org.apache.camel.spi.Language;
import org.apache.camel.util.ObjectHelper;

/**
 * A support class for building expression clauses.
 *
 * @version 
 */
public class ExpressionClauseSupport<T> {

    private T result;
    private String language;
    private String expression;
    private Expression expressionValue;
    private ExpressionDefinition expressionType;

    public ExpressionClauseSupport(T result) {
        this.result = result;
    }

    // Helper expressions
    // -------------------------------------------------------------------------

    /**
     * Specify an {@link org.apache.camel.Expression} instance
     */
    public T expression(Expression expression) {
        setExpressionValue(expression);
        return result;
    }

    public T expression(ExpressionDefinition expression) {
        setExpressionType(expression);
        return result;
    }

    /**
     * Specify the constant expression value
     */
    public T constant(Object value) {
        if (value instanceof String) {
            return expression(new ConstantExpression((String) value));
        } else {
            return expression(ExpressionBuilder.constantExpression(value));
        }
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
     * An expression of an inbound message
     */
    public T outMessage() {
        return expression(ExpressionBuilder.outMessageExpression());
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
    @SuppressWarnings("unchecked")
    public T body(Class expectedType) {
        return expression(ExpressionBuilder.bodyExpression(expectedType));
    }

    /**
     * An expression of an outbound message body
     */
    public T outBody() {
        return expression(ExpressionBuilder.outBodyExpression());
    }

    /**
     * An expression of an outbound message body converted to the expected type
     */
    @SuppressWarnings("unchecked")
    public T outBody(Class expectedType) {
        return expression(ExpressionBuilder.outBodyExpression(expectedType));
    }

    /**
     * An expression of an inbound message header of the given name
     */
    public T header(String name) {
        return expression(new HeaderExpression(name));
    }

    /**
     * An expression of the inbound headers
     */
    public T headers() {
        return expression(ExpressionBuilder.headersExpression());
    }

    /**
     * An expression of an outbound message header of the given name
     */
    public T outHeader(String name) {
        return expression(ExpressionBuilder.outHeaderExpression(name));
    }

    /**
     * An expression of the outbound headers
     */
    public T outHeaders() {
        return expression(ExpressionBuilder.outHeadersExpression());
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
    public T property(String name) {
        return expression(new PropertyExpression(name));
    }

    /**
     * An expression of the exchange properties
     */
    public T properties() {
        return expression(ExpressionBuilder.propertiesExpression());
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
        return expression(new MethodCallExpression(bean));
    }

    /**
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param instance the instance of the bean
     * @return the builder to continue processing the DSL
     */
    public T method(Object instance) {
        return expression(new MethodCallExpression(instance));
    }

    /**
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param beanType the Class of the bean which we want to invoke
     * @return the builder to continue processing the DSL
     */
    public T method(Class<?> beanType) {
        return expression(new MethodCallExpression(beanType));
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
        return expression(new MethodCallExpression(bean, method));
    }

    /**
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param instance the instance of the bean
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T method(Object instance, String method) {
        return expression(new MethodCallExpression(instance, method));
    }

    /**
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html>bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param beanType the Class of the bean which we want to invoke
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, String method) {
        return expression(new MethodCallExpression(beanType, method));
    }

    /**
     * Evaluates the <a href="http://camel.apache.org/el.html">EL
     * Language from JSP and JSF</a> using the <a
     * href="http://camel.apache.org/juel.html">JUEL library</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T el(String text) {
        return expression(new ELExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/groovy.html">Groovy
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return expression(new GroovyExpression(text));
    }

    /**
     * Evaluates a <a
     * href="http://camel.apache.org/java-script.html">JavaScript
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T javaScript(String text) {
        return expression(new JavaScriptExpression(text));
    }

    /**
     * Evaluates a <a href="http://commons.apache.org/jxpath/">JXPath expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jxpath(String text) {
        return expression(new JXPathExpression(text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/ognl.html">OGNL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return expression(new OgnlExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/mvel.html">MVEL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T mvel(String text) {
        return expression(new MvelExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/php.html">PHP
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T php(String text) {
        return expression(new PhpExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/python.html">Python
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T python(String text) {
        return expression(new PythonExpression(text));
    }

    /**
     * Evaluates a {@link Expression} by looking up existing {@link Expression}
     * from the {@link org.apache.camel.spi.Registry}
     *
     * @param ref refers to the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ref(String ref) {
        return expression(new RefExpression(ref));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/ruby.html">Ruby
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ruby(String text) {
        return expression(new RubyExpression(text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/spel.html">SpEL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T spel(String text) {
        return expression(new SpELExpression(text));
    }
    
    /**
     * Evaluates an <a href="http://camel.apache.org/sql.html">SQL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T sql(String text) {
        return expression(new SqlExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T simple(String text) {
        return expression(new SimpleExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param resultType the result type
     * @return the builder to continue processing the DSL
     */
    public T simple(String text, Class<?> resultType) {
        SimpleExpression expression = new SimpleExpression(text);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return expression(new XPathExpression(text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified result type and set of namespace
     * prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Namespaces namespaces) {
        return xpath(text, resultType, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified result type and set of namespace
     * prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Map<String, String> namespaces) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Namespaces namespaces) {
        return xpath(text, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Map<String, String> namespaces) {
        XPathExpression expression = new XPathExpression(text);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return expression(new XQueryExpression(text));
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     * with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     * with the specified result type and set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces) {
        return xquery(text, resultType, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     * with the specified result type and set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Map<String, String> namespaces) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     * with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Namespaces namespaces) {
        return xquery(text, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     * with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Map<String, String> namespaces) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setNamespaces(namespaces);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param language the name of the language
     * @param expression the expression in the given language
     * @return the builder to continue processing the DSL
     */
    public T language(String language, String expression) {
        setLanguage(language);
        setExpression(expression);
        return result;
    }

    // TODO: Add support for TokenizerExpression

    // Properties
    // -------------------------------------------------------------------------
    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public Expression getExpressionValue() {
        return expressionValue;
    }

    public void setExpressionValue(Expression expressionValue) {
        this.expressionValue = expressionValue;
    }

    public ExpressionDefinition getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(ExpressionDefinition expressionType) {
        this.expressionType = expressionType;
    }

    protected Expression createExpression(CamelContext camelContext) {
        if (getExpressionValue() == null) {
            if (getExpressionType() != null) {
                setExpressionValue(getExpressionType().createExpression(camelContext));
            } else if (getExpression() != null) {
                ObjectHelper.notNull("language", getLanguage());
                Language language = camelContext.resolveLanguage(getLanguage());
                setExpressionValue(language.createExpression(getExpression()));
                configureExpression(camelContext, getExpressionValue());
            }
        }
        return getExpressionValue();
    }

    protected void configureExpression(CamelContext camelContext, Expression expression) {
    }

}
