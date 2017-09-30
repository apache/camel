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
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.language.GroovyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.JXPathExpression;
import org.apache.camel.model.language.JavaScriptExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.MvelExpression;
import org.apache.camel.model.language.OgnlExpression;
import org.apache.camel.model.language.PhpExpression;
import org.apache.camel.model.language.PythonExpression;
import org.apache.camel.model.language.RefExpression;
import org.apache.camel.model.language.RubyExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.SpELExpression;
import org.apache.camel.model.language.SqlExpression;
import org.apache.camel.model.language.TerserExpression;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;

/**
 * A support class for building expression clauses.
 *
 * @version 
 */
public class ExpressionClauseSupport<T> {

    private T result;
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
        // reuse simple as this allows the model to represent this as a known JAXB type
        return expression(new SimpleExpression("body"));
    }

    /**
     * An expression of an inbound message body converted to the expected type
     */
    public T body(Class<?> expectedType) {
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
    public T outBody(Class<?> expectedType) {
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
     * An expression of the inbound message attachments
     */
    public T attachments() {
        return expression(ExpressionBuilder.attachmentObjectValuesExpression());
    }

    /**
     * An expression of the exchange pattern
     */
    public T exchangePattern() {
        return expression(ExpressionBuilder.exchangePatternExpression());
    }

    /**
     * An expression of an exchange property of the given name
     *
     * @deprecated use {@link #exchangeProperty(String)} instead
     */
    @Deprecated
    public T property(String name) {
        return expression(new ExchangePropertyExpression(name));
    }

    /**
     * An expression of an exchange property of the given name
     */
    public T exchangeProperty(String name) {
        return expression(new ExchangePropertyExpression(name));
    }

    /**
     * An expression of the exchange properties
     *
     * @deprecated use {@link #exchangeProperties()} instead
     */
    @Deprecated
    public T properties() {
        return exchangeProperties();
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
    @Deprecated
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
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text) {
        return jsonpath(text, false);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(suppressExceptions);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param allowSimple whether to allow in inlined simple exceptions in the json path expression
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, boolean allowSimple) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(suppressExceptions);
        expression.setAllowSimple(allowSimple);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, Class<?> resultType) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, Class<?> resultType) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(suppressExceptions);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param allowSimple whether to allow in inlined simple exceptions in the json path expression
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, boolean allowSimple, Class<?> resultType) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(suppressExceptions);
        expression.setAllowSimple(allowSimple);
        expression.setResultType(resultType);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param allowSimple whether to allow in inlined simple exceptions in the json path expression
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, boolean allowSimple, Class<?> resultType, String headerName) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(suppressExceptions);
        expression.setAllowSimple(allowSimple);
        expression.setResultType(resultType);
        expression.setHeaderName(headerName);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text) {
        return jsonpathWriteAsString(text, false);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(true);
        expression.setSuppressExceptions(suppressExceptions);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param allowSimple whether to allow in inlined simple exceptions in the json path expression
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, boolean allowSimple) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(true);
        expression.setSuppressExceptions(suppressExceptions);
        expression.setAllowSimple(allowSimple);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param allowSimple whether to allow in inlined simple exceptions in the json path expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, boolean allowSimple, String headerName) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(true);
        expression.setSuppressExceptions(suppressExceptions);
        expression.setAllowSimple(allowSimple);
        expression.setHeaderName(headerName);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://commons.apache.org/jxpath/">JXPath expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    @Deprecated
    public T jxpath(String text) {
        return jxpath(text, false);
    }

    /**
     * Evaluates a <a href="http://commons.apache.org/jxpath/">JXPath expression</a>
     *
     * @param text the expression to be evaluated
     * @param lenient to configure whether lenient is in use or not
     * @return the builder to continue processing the DSL
     */
    @Deprecated
    public T jxpath(String text, boolean lenient) {
        JXPathExpression answer = new JXPathExpression(text);
        answer.setLenient(lenient);
        return expression(answer);
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
    @Deprecated
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
     * Evaluates an <a href="http://camel.apache.org/hl7.html">HL7 Terser
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T terser(String text) {
        return expression(new TerserExpression(text));
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token) {
        return tokenize(token, null, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, int group) {
        return tokenize(token, null, false, group);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param group to group by the given number
     * @param skipFirst whether to skip the very first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, int group, boolean skipFirst) {
        return tokenize(token, null, false, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex) {
        return tokenize(token, null, regex);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, int group) {
        return tokenize(token, null, regex, group);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName) {
        return tokenize(token, headerName, false);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @param regex whether the token is a regular expression or not
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(token);
        expression.setHeaderName(headerName);
        expression.setRegex(regex);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @param regex whether the token is a regular expression or not
     * @param group to group by number of parts
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, int group) {
        return tokenize(token, headerName, regex, group, false);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @param regex whether the token is a regular expression or not
     * @param skipFirst whether to skip the very first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, boolean skipFirst) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(token);
        expression.setHeaderName(headerName);
        expression.setRegex(regex);
        expression.setSkipFirst(skipFirst);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @param regex whether the token is a regular expression or not
     * @param group to group by number of parts
     * @param skipFirst whether to skip the very first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, int group, boolean skipFirst) {
        return tokenize(token, headerName, regex, "" + group, skipFirst);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @param regex whether the token is a regular expression or not
     * @param group to group by number of parts
     * @param skipFirst whether to skip the very first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, String group, boolean skipFirst) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(token);
        expression.setHeaderName(headerName);
        expression.setRegex(regex);
        expression.setGroup(group);
        expression.setSkipFirst(skipFirst);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a token pair expression on the message body
     *
     * @param startToken the start token
     * @param endToken   the end token
     * @param includeTokens whether to include tokens
     * @return the builder to continue processing the DSL
     */
    public T tokenizePair(String startToken, String endToken, boolean includeTokens) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(startToken);
        expression.setEndToken(endToken);
        expression.setIncludeTokens(includeTokens);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param tagName the the tag name of the child nodes to tokenize
     * @param inheritNamespaceTagName  optional parent or root tag name that contains namespace(s) to inherit
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenizeXMLPair(String tagName, String inheritNamespaceTagName, int group) {
        return tokenizeXMLPair(tagName, inheritNamespaceTagName, "" + group);
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param tagName the the tag name of the child nodes to tokenize
     * @param inheritNamespaceTagName  optional parent or root tag name that contains namespace(s) to inherit
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenizeXMLPair(String tagName, String inheritNamespaceTagName, String group) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(tagName);
        expression.setInheritNamespaceTagName(inheritNamespaceTagName);
        expression.setXml(true);
        expression.setGroup(group);
        setExpressionType(expression);
        return result;
    }

    /**
     * Evaluates an XML token expression on the message body with XML content
     * 
     * @param path the xpath like path notation specifying the child nodes to tokenize
     * @param mode one of 'i', 'w', or 'u' to inject the namespaces to the token, to
     *        wrap the token with its ancestor contet, or to unwrap to its element child
     * @param namespaces the namespace map to the namespace bindings 
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T xtokenize(String path, char mode, Namespaces namespaces, int group) {
        XMLTokenizerExpression expression = new XMLTokenizerExpression(path);
        expression.setMode(Character.toString(mode));
        expression.setNamespaces(namespaces.getNamespaces());

        if (group > 0) {
            expression.setGroup(group);
        }
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
     * expression</a> on the supplied header name's contents
     * 
     * @param text the expression to be evaluated
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, String headerName) {
        XPathExpression expression = new XPathExpression(text);
        expression.setHeaderName(headerName);
        return expression(expression);
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
     * expression</a> with the specified result type on the supplied
     * header name's contents
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, String headerName) {
        XPathExpression expression = new XPathExpression(text);
        expression.setHeaderName(headerName);
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
     * prefixes and URIs on the supplied header name's contents
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces.getNamespaces());
        expression.setHeaderName(headerName);
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
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a>
     * 
     * @param text the expression to be evaluated
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, String headerName) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setHeaderName(headerName);
        return expression(expression);
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
     * with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, String headerName) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setHeaderName(headerName);
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
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces.getNamespaces());
        expression.setHeaderName(headerName);
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
        LanguageExpression exp = new LanguageExpression(language, expression);
        setExpressionType(exp);
        return result;
    }

    // Properties
    // -------------------------------------------------------------------------

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
            } else {
                throw new IllegalStateException("No expression value configured");
            }
        }
        return getExpressionValue();
    }

    protected void configureExpression(CamelContext camelContext, Expression expression) {
    }

}
