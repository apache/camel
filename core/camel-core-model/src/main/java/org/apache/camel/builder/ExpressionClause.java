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

import org.apache.camel.BeanScope;
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
 * Represents an expression clause within the DSL which when the expression is complete the clause continues to another
 * part of the DSL
 */
public class ExpressionClause<T> implements Expression, Predicate {
    private final ExpressionClauseSupport<T> delegate;
    private volatile Expression expr;
    private volatile Predicate pred;

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
     * Specify the constant expression value. <b>Important:</b> this is a fixed constant value that is only set once
     * during starting up the route, do not use this if you want dynamic values during routing.
     */
    public T constant(Object value) {
        return delegate.constant(value);
    }

    /**
     * Specify the constant expression value. <b>Important:</b> this is a fixed constant value that is only set once
     * during starting up the route, do not use this if you want dynamic values during routing.
     */
    public T constant(String value, Class<?> resultType) {
        return delegate.constant(value, resultType);
    }

    /**
     * Specify the constant expression value. <b>Important:</b> this is a fixed constant value that is only set once
     * during starting up the route, do not use this if you want dynamic values during routing.
     */
    public T constant(Object value, boolean trim) {
        return delegate.constant(value, trim);
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
            @Override
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
            @Override
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
            @Override
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
            @Override
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
     * A functional expression of an inbound message body converted to the expected type
     */
    public <B> T body(Class<B> expectedType, final Function<B, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getIn().getBody(expectedType));
            }
        });
    }

    /**
     * A functional expression of an inbound message body converted to the expected type and headers
     */
    public <B> T body(Class<B> expectedType, final BiFunction<B, Map<String, Object>, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            @Override
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
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref the name (bean id) of the bean to lookup from the registry
     * @return     the builder to continue processing the DSL
     */
    public T method(String ref) {
        return delegate.method(ref);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  instance the existing instance of the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Object instance) {
        return delegate.method(instance);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType) {
        return delegate.method(beanType);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref    the name (bean id) of the bean to lookup from the registry
     * @param  method the name of the method to invoke on the bean
     * @return        the builder to continue processing the DSL
     */
    public T method(String ref, String method) {
        return delegate.method(ref, method);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref   the name (bean id) of the bean to lookup from the registry
     * @param  scope the scope of the bean
     * @return       the builder to continue processing the DSL
     */
    public T method(String ref, BeanScope scope) {
        return delegate.method(ref, scope);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref    the name (bean id) of the bean to lookup from the registry
     * @param  method the name of the method to invoke on the bean
     * @param  scope  the scope of the bean
     * @return        the builder to continue processing the DSL
     */
    public T method(String ref, String method, BeanScope scope) {
        return delegate.method(ref, method, scope);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  instance the existing instance of the bean
     * @param  method   the name of the method to invoke on the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Object instance, String method) {
        return delegate.method(instance, method);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @param  method   the name of the method to invoke on the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, String method) {
        return delegate.method(beanType, method);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @param  scope    the scope of the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, BeanScope scope) {
        return delegate.method(beanType, scope);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html">bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @param  method   the name of the method to invoke on the bean
     * @param  scope    the scope of the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, String method, BeanScope scope) {
        return delegate.method(beanType, method, scope);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/groovy.html">Groovy expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return delegate.groovy(text);
    }

    /**
     * Returns a JOOR expression value builder
     */
    public T joor(String value) {
        return delegate.joor(value);
    }

    /**
     * Returns a JOOR expression value builder
     */
    public T joor(String value, Class<?> resultType) {
        return delegate.joor(value, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  value the expression to be evaluated
     * @return       the builder to continue processing the DSL
     */
    public T jq(String value) {
        return delegate.jq(value);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  value      the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jq(String value, Class<?> resultType) {
        return delegate.jq(value, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  value                the expression to be evaluated
     * @param  headerOrPropertyName the name of the header or property to apply the expression to
     * @return                      the builder to continue processing the DSL
     */
    public T jq(String value, String headerOrPropertyName) {
        return delegate.jq(value, headerOrPropertyName);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  value        the expression to be evaluated
     * @param  headerName   the name of the header to apply the expression to
     * @param  propertyName the name of the property to apply the expression to
     * @return              the builder to continue processing the DSL
     */
    public T jq(String value, String headerName, String propertyName) {
        return delegate.jq(value, headerName, propertyName);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  value                the expression to be evaluated
     * @param  resultType           the return type expected by the expression
     * @param  headerOrPropertyName the name of the header or property to apply the expression to
     * @return                      the builder to continue processing the DSL
     */
    public T jq(String value, Class<?> resultType, String headerOrPropertyName) {
        return delegate.jq(value, resultType, headerOrPropertyName);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  value        the expression to be evaluated
     * @param  resultType   the return type expected by the expression
     * @param  headerName   the name of the header to apply the expression to
     * @param  propertyName the name of the property to apply the expression to
     * @return              the builder to continue processing the DSL
     */
    public T jq(String value, Class<?> resultType, String headerName, String propertyName) {
        return delegate.jq(value, resultType, headerName, propertyName);
    }

    /**
     * Returns a JavaScript expression value builder
     */
    public T js(String value) {
        return delegate.js(value);
    }

    /**
     * Returns a JavaScript expression value builder
     */
    public T js(String value, Class<?> resultType) {
        return delegate.js(value, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/datasonnet.html">Datasonnet expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T datasonnet(String text) {
        return delegate.datasonnet(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T jsonpath(String text) {
        return delegate.jsonpath(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions) {
        return delegate.jsonpath(text, suppressExceptions);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jsonpath(String text, Class<?> resultType) {
        return delegate.jsonpath(text, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  resultType         the return type expected by the expression
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, Class<?> resultType) {
        return delegate.jsonpath(text, suppressExceptions, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  resultType         the return type expected by the expression
     * @param  headerName         the name of the header to apply the expression to
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, Class<?> resultType, String headerName) {
        return delegate.jsonpath(text, suppressExceptions, true, resultType, headerName);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text) {
        return delegate.jsonpathWriteAsString(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, Class<?> resultType) {
        return delegate.jsonpathWriteAsString(text, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions) {
        return delegate.jsonpathWriteAsString(text, suppressExceptions);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  resultType         the return type expected by the expression
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, Class<?> resultType) {
        return delegate.jsonpathWriteAsString(text, suppressExceptions, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  headerName         the name of the header to apply the expression to
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, String headerName) {
        return delegate.jsonpathWriteAsString(text, suppressExceptions, true, headerName);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with unpacking a
     * single-element array into an object enabled.
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jsonpathUnpack(String text, Class<?> resultType) {
        return delegate.jsonpathUnpack(text, resultType);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/ognl.html">OGNL expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return delegate.ognl(text);
    }

    /**
     * Evaluates a Python expression.
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T python(String text) {
        return delegate.python(text);
    }

    /**
     * Evaluates a Python expression.
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T python(String text, Class<?> resultType) {
        return delegate.python(text, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/mvel.html">MVEL expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T mvel(String text) {
        return delegate.mvel(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/ref-language.html">Ref expression</a>
     *
     * @param  ref refers to the expression to be evaluated
     * @return     the builder to continue processing the DSL
     */
    public T ref(String ref) {
        return delegate.ref(ref);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/spel.html">SpEL expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T spel(String text) {
        return delegate.spel(text);
    }

    /**
     * Returns a compiled simple expression value builder
     */
    public T csimple(String value) {
        return delegate.csimple(value);
    }

    /**
     * Returns a compile simple expression value builder
     */
    public T csimple(String value, Class<?> resultType) {
        return delegate.csimple(value, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T simple(String text) {
        return delegate.simple(text);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple expression</a>
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the result type
     * @return            the builder to continue processing the DSL
     */
    public T simple(String text, Class<?> resultType) {
        return delegate.simple(text, resultType);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token) {
        return delegate.tokenize(token);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @param  regex whether the token is a regular expression or not
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex) {
        return tokenize(token, regex, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token     the token
     * @param  regex     whether the token is a regular expression or not
     * @param  skipFirst whether to skip the first element
     * @return           the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @param  regex whether the token is a regular expression or not
     * @param  group to group by the given number
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, int group) {
        return tokenize(token, regex, group, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @param  regex whether the token is a regular expression or not
     * @param  group to group by the given number
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, String group) {
        return tokenize(token, regex, group, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token     the token
     * @param  regex     whether the token is a regular expression or not
     * @param  group     to group by the given number
     * @param  skipFirst whether to skip the first element
     * @return           the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, int group, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token     the token
     * @param  regex     whether the token is a regular expression or not
     * @param  group     to group by the given number
     * @param  skipFirst whether to skip the first element
     * @return           the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, String group, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token     the token
     * @param  regex     whether the token is a regular expression or not
     * @param  group     to group by the given number
     * @param  skipFirst whether to skip the first element
     * @return           the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex, int group, String groupDelimiter, boolean skipFirst) {
        return delegate.tokenize(token, null, regex, Integer.toString(group), groupDelimiter, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @param  group to group by the given number
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token, int group) {
        return delegate.tokenize(token, group);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token     the token
     * @param  group     to group by the given number
     * @param  skipFirst whether to skip the first element
     * @return           the builder to continue processing the DSL
     */
    public T tokenize(String token, int group, boolean skipFirst) {
        return delegate.tokenize(token, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName) {
        return delegate.tokenize(token, headerName);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @param  regex      whether the token is a regular expression or not
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex) {
        return delegate.tokenize(token, headerName, regex);
    }

    /**
     * Evaluates a token pair expression on the message body.
     * <p/>
     * Tokens is not included.
     *
     * @param  startToken the start token
     * @param  endToken   the end token
     * @return            the builder to continue processing the DSL
     */
    public T tokenizePair(String startToken, String endToken) {
        return tokenizePair(startToken, endToken, false);
    }

    /**
     * Evaluates a token pair expression on the message body
     *
     * @param  startToken    the start token
     * @param  endToken      the end token
     * @param  includeTokens whether to include tokens
     * @return               the builder to continue processing the DSL
     */
    public T tokenizePair(String startToken, String endToken, boolean includeTokens) {
        return delegate.tokenizePair(startToken, endToken, includeTokens);
    }

    /**
     * Evaluates a XML token expression on the message body with XML content
     *
     * @param  tagName the tag name of the child nodes to tokenize
     * @return         the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName) {
        return tokenizeXML(tagName, null);
    }

    /**
     * Evaluates a XML token expression on the message body with XML content
     *
     * @param  tagName the tag name of the child nodes to tokenize
     * @param  group   to group by the given number
     * @return         the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName, int group) {
        return tokenizeXML(tagName, null, group);
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param  tagName                 the tag name of the child nodes to tokenize
     * @param  inheritNamespaceTagName parent or root tag name that contains namespace(s) to inherit
     * @return                         the builder to continue processing the DSL
     */
    public T tokenizeXML(String tagName, String inheritNamespaceTagName) {
        return tokenizeXML(tagName, inheritNamespaceTagName, 0);
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param  tagName                 the tag name of the child nodes to tokenize
     * @param  inheritNamespaceTagName parent or root tag name that contains namespace(s) to inherit
     * @param  group                   to group by the given number
     * @return                         the builder to continue processing the DSL
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
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return delegate.xpath(text);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> on the supplied header name's
     * contents
     *
     * @param  text       the expression to be evaluated
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, String headerName) {
        return delegate.xpath(text, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType) {
        return delegate.xpath(text, resultType);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type on
     * the supplied header name's contents
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, String headerName) {
        return delegate.xpath(text, resultType, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type and
     * set of namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Namespaces namespaces) {
        return delegate.xpath(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type and
     * set of namespace prefixes and URIs on the supplied header name's contents
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  headerName the name of the header to apply the expression to
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        return delegate.xpath(text, resultType, namespaces, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type and
     * set of namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Map<String, String> namespaces) {
        return delegate.xpath(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified set of
     * namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Namespaces namespaces) {
        return delegate.xpath(text, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified set of
     * namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Map<String, String> namespaces) {
        return delegate.xpath(text, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return delegate.xquery(text);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> on the supplied header name's
     * contents
     *
     * @param  text       the expression to be evaluated
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, String headerName) {
        return delegate.xquery(text, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType) {
        return delegate.xquery(text, resultType);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, String headerName) {
        return delegate.xquery(text, resultType, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     * and set of namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces) {
        return delegate.xquery(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  headerName the name of the header to apply the expression to
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        return delegate.xquery(text, resultType, namespaces, headerName);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     * and set of namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Map<String, String> namespaces) {
        return delegate.xquery(text, resultType, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified set of
     * namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Namespaces namespaces) {
        return delegate.xquery(text, namespaces);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified set of
     * namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  namespaces the namespace prefix and URIs to use
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Map<String, String> namespaces) {
        return delegate.xquery(text, namespaces);
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param  language   the name of the language
     * @param  expression the expression in the given language
     * @return            the builder to continue processing the DSL
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
    public void initPredicate(CamelContext context) {
        if (pred == null) {
            synchronized (this) {
                if (pred == null) {
                    Expression newExpression = getExpressionValue();
                    Predicate newPredicate;
                    if (newExpression == null) {
                        newPredicate = delegate.getPredicateType().createPredicate(context);
                    } else {
                        newPredicate = ExpressionToPredicateAdapter.toPredicate(newExpression);
                    }
                    newPredicate.initPredicate(context);
                    pred = newPredicate;
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
        initPredicate(exchange.getContext());
        return pred.matches(exchange);
    }
}
