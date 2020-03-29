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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ExpressionToPredicateAdapter;
import org.apache.camel.support.builder.Namespaces;

/**
 * Represents an expression clause within the DSL which when the expression is
 * complete the clause continues to another part of the DSL
 */
public class ExpressionClause<T> implements Expression, Predicate {
    private ExpressionClauseSupport<T> delegate;
    private volatile Expression expr;

    public ExpressionClause(T result) {
        this.delegate = new ExpressionClauseSupport<>(result);
    }

    // Helper expressions
    // -------------------------------------------------------------------------

    /**
     * Specify an {@link Expression} instance
     */
    public T expression(Expression expression) {
        return delegate.expression(expression);
    }

    /**
     * Specify the constant expression value. <b>Important:</b> this is a fixed
     * constant value that is only set once during starting up the route, do not
     * use this if you want dynamic values during routing.
     */
    public T constant(Object value) {
        return delegate.constant(value);
    }

    /**
     * An expression of the exchange
     */
    public T exchange() {
        return delegate.exchange();
    }

    /**
     * A functional expression of the exchange
     */
    public T exchange(final Function<Exchange, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange);
            }
        });
    }

    /**
     * An expression of an inbound message
     */
    public T message() {
        return inMessage();
    }

    /**
     * A functional expression of an inbound message
     */
    public T message(final Function<Message, Object> function) {
        return inMessage(function);
    }

    /**
     * An expression of an inbound message
     */
    public T inMessage() {
        return delegate.inMessage();
    }

    /**
     * A functional expression of an inbound message
     */
    public T inMessage(final Function<Message, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn());
            }
        });
    }

    /**
     * An expression of an inbound message body
     */
    public T body() {
        return delegate.body();
    }

    /**
     * A functional expression of an inbound message body
     */
    public T body(final Function<Object, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn().getBody());
            }
        });
    }

    /**
     * A functional expression of an inbound message body and headers
     */
    public T body(final BiFunction<Object, Map<String, Object>, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn().getBody(), exchange.getIn().getHeaders());
            }
        });
    }

    /**
     * An expression of an inbound message body converted to the expected type
     */
    public T body(Class<?> expectedType) {
        return delegate.body(expectedType);
    }

    /**
     * A functional expression of an inbound message body converted to the
     * expected type
     */
    public <B> T body(Class<B> expectedType, final Function<B, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn().getBody(expectedType));
            }
        });
    }

    /**
     * A functional expression of an inbound message body converted to the
     * expected type and headers
     */
    public <B> T body(Class<B> expectedType, final BiFunction<B, Map<String, Object>, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn().getBody(expectedType), exchange.getIn().getHeaders());
            }
        });
    }

    /**
     * An expression of an inbound message header of the given name
     */
    public T header(String name) {
        return delegate.header(name);
    }

    /**
     * An expression of the inbound headers
     */
    public T headers() {
        return delegate.headers();
    }

    /**
     * An expression of an exchange property of the given name
     */
    public T exchangeProperty(String name) {
        return delegate.exchangeProperty(name);
    }

    /**
     * An expression of the exchange properties
     */
    public T exchangeProperties() {
        return delegate.exchangeProperties();
    }

    // Languages
    // -------------------------------------------------------------------------

    /**
     * Evaluates an expression using the
     * <a href="http://camel.apache.org/bean-language.html">bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param bean the name of the bean looked up the registry
     * @return the builder to continue processing the DSL
     */
    public T method(String bean) {
        return delegate.method(bean);
    }

    /**
     * Evaluates an expression using the
     * <a href="http://camel.apache.org/bean-language.html">bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param instance the instance of the bean
     * @return the builder to continue processing the DSL
     */
    public T method(Object instance) {
        return delegate.method(instance);
    }

    /**
     * Evaluates an expression using the
     * <a href="http://camel.apache.org/bean-language.html">bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param beanType the Class of the bean which we want to invoke
     * @return the builder to continue processing the DSL
     */
    public T method(Class<?> beanType) {
        return delegate.method(beanType);
    }

    /**
     * Evaluates an expression using the
     * <a href="http://camel.apache.org/bean-language.html">bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param bean the name of the bean looked up the registry
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T method(String bean, String method) {
        return delegate.method(bean, method);
    }

    /**
     * Evaluates an expression using the
     * <a href="http://camel.apache.org/bean-language.html">bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param instance the instance of the bean
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T method(Object instance, String method) {
        return delegate.method(instance, method);
    }

    /**
     * Evaluates an expression using the
     * <a href="http://camel.apache.org/bean-language.html">bean language</a>
     * which basically means the bean is invoked to determine the expression
     * value.
     *
     * @param beanType the Class of the bean which we want to invoke
     * @param method the name of the method to invoke on the bean
     * @return the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, String method) {
        return delegate.method(beanType, method);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/groovy.html">Groovy
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return delegate.groovy(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text) {
        return delegate.jsonpath(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as
     *            PathNotFoundException
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions) {
        return delegate.jsonpath(text, suppressExceptions);
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
        return delegate.jsonpath(text, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as
     *            PathNotFoundException
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, Class<?> resultType) {
        return delegate.jsonpath(text, suppressExceptions, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as
     *            PathNotFoundException
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, Class<?> resultType, String headerName) {
        return delegate.jsonpath(text, suppressExceptions, true, resultType, headerName);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text) {
        return delegate.jsonpathWriteAsString(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as
     *            PathNotFoundException
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions) {
        return delegate.jsonpathWriteAsString(text, suppressExceptions);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a> with writeAsString enabled.
     *
     * @param text the expression to be evaluated
     * @param suppressExceptions whether to suppress exceptions such as
     *            PathNotFoundException
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, String headerName) {
        return delegate.jsonpathWriteAsString(text, suppressExceptions, true, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/ognl.html">OGNL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return delegate.ognl(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/mvel.html">MVEL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T mvel(String text) {
        return delegate.mvel(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/ref-language.html">Ref
     * expression</a>
     *
     * @param ref refers to the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T ref(String ref) {
        return delegate.ref(ref);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/spel.html">SpEL
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T spel(String text) {
        return delegate.spel(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T simple(String text) {
        return delegate.simple(text);
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
        return delegate.simple(text, resultType);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token) {
        return delegate.tokenize(token);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex) {
        return tokenize(token, regex, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @param skipFirst whether to skip the first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, skipFirst);
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
        return tokenize(token, regex, group, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, String group) {
        return tokenize(token, regex, group, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @param group to group by the given number
     * @param skipFirst whether to skip the first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, int group, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @param group to group by the given number
     * @param skipFirst whether to skip the first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, String group, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param regex whether the token is a regular expression or not
     * @param group to group by the given number
     * @param skipFirst whether to skip the first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, int group, String groupDelimiter, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, "" + group, groupDelimiter, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, int group) {
        return delegate.tokenize(token, group);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param token the token
     * @param group to group by the given number
     * @param skipFirst whether to skip the first element
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, int group, boolean skipFirst) {
        return delegate.tokenize(token, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param token the token
     * @param headerName name of header to tokenize
     * @return the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName) {
        return delegate.tokenize(token, headerName);
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
        return delegate.tokenize(token, headerName, regex);
    }

    /**
     * Evaluates a token pair expression on the message body.
     * <p/>
     * Tokens is not included.
     *
     * @param startToken the start token
     * @param endToken the end token
     * @return the builder to continue processing the DSL
     */
    public T tokenizePair(String startToken, String endToken) {
        return tokenizePair(startToken, endToken, false);
    }

    /**
     * Evaluates a token pair expression on the message body
     *
     * @param startToken the start token
     * @param endToken the end token
     * @param includeTokens whether to include tokens
     * @return the builder to continue processing the DSL
     */
    public T tokenizePair(String startToken, String endToken, boolean includeTokens) {
        return delegate.tokenizePair(startToken, endToken, includeTokens);
    }

    /**
     * Evaluates a XML token expression on the message body with XML content
     *
     * @param tagName the tag name of the child nodes to tokenize
     * @return the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName) {
        return tokenizeXML(tagName, null);
    }

    /**
     * Evaluates a XML token expression on the message body with XML content
     *
     * @param tagName the tag name of the child nodes to tokenize
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName, int group) {
        return tokenizeXML(tagName, null, group);
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param tagName the tag name of the child nodes to tokenize
     * @param inheritNamespaceTagName parent or root tag name that contains
     *            namespace(s) to inherit
     * @return the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName, String inheritNamespaceTagName) {
        return tokenizeXML(tagName, inheritNamespaceTagName, 0);
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param tagName the tag name of the child nodes to tokenize
     * @param inheritNamespaceTagName parent or root tag name that contains
     *            namespace(s) to inherit
     * @param group to group by the given number
     * @return the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName, String inheritNamespaceTagName, int group) {
        return delegate.tokenizeXMLPair(tagName, inheritNamespaceTagName, group);
    }

    public T xtokenize(String path, Namespaces namespaces) {
        return xtokenize(path, 'i', namespaces);
    }

    public T xtokenize(String path, char mode, Namespaces namespaces) {
        return xtokenize(path, mode, namespaces, 0);
    }

    public T xtokenize(String path, char mode, Namespaces namespaces, int group) {
        return delegate.xtokenize(path, mode, namespaces, group);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return delegate.xpath(text);
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
        return delegate.xpath(text, headerName);
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
        return delegate.xpath(text, resultType);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified result type on the supplied header
     * name's contents
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, String headerName) {
        return delegate.xpath(text, resultType, headerName);
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
        return delegate.xpath(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> with the specified result type and set of namespace
     * prefixes and URIs on the supplied header name's contents
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        return delegate.xpath(text, resultType, namespaces, headerName);
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
        return delegate.xpath(text, resultType, namespaces);
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
        return delegate.xpath(text, namespaces);
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
        return delegate.xpath(text, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return delegate.xquery(text);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath
     * expression</a> on the supplied header name's contents
     *
     * @param text the expression to be evaluated
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, String headerName) {
        return delegate.xquery(text, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType) {
        return delegate.xquery(text, resultType);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, String headerName) {
        return delegate.xquery(text, resultType, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified result type and set of namespace
     * prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces) {
        return delegate.xquery(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified result type
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param headerName the name of the header to apply the expression to
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        return delegate.xquery(text, resultType, namespaces, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified result type and set of namespace
     * prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param resultType the return type expected by the expression
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Map<String, String> namespaces) {
        return delegate.xquery(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Namespaces namespaces) {
        return delegate.xquery(text, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery
     * expression</a> with the specified set of namespace prefixes and URIs
     *
     * @param text the expression to be evaluated
     * @param namespaces the namespace prefix and URIs to use
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text, Map<String, String> namespaces) {
        return delegate.xquery(text, namespaces);
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param language the name of the language
     * @param expression the expression in the given language
     * @return the builder to continue processing the DSL
     */
    public T language(String language, String expression) {
        return delegate.language(language, expression);
    }

    // Properties
    // -------------------------------------------------------------------------

    public Expression getExpressionValue() {
        return delegate.getExpressionValue();
    }

    public ExpressionFactory getExpressionType() {
        return delegate.getExpressionType();
    }

    @Override
    public void init(CamelContext context) {
        if (expr == null) {
            synchronized (this) {
                if (expr == null) {
                    Expression newExpression = getExpressionValue();
                    if (newExpression == null) {
                        newExpression = delegate.getExpressionType().createExpression(context);
                    }
                    newExpression.init(context);
                    expr = newExpression;
                }
            }
        }
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        init(exchange.getContext());
        return expr.evaluate(exchange, type);
    }

    @Override
    public boolean matches(Exchange exchange) {
        init(exchange.getContext());
        return new ExpressionToPredicateAdapter(expr).matches(exchange);
    }
}
