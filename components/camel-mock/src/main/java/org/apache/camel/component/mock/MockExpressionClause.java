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

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.Message;
import org.apache.camel.Predicate;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.ExpressionToPredicateAdapter;

/**
 * Represents an expression clause within the DSL which when the expression is
 * complete the clause continues to another part of the DSL
 * <p/>
 * This implementation is a derived copy of the <tt>org.apache.camel.builder.ExpressionClause</tt> from camel-core,
 * that are specialized for being used with the mock component and separated from camel-core.
 */
public class MockExpressionClause<T> implements Expression, Predicate {
    private MockExpressionClauseSupport<T> delegate;

    private volatile Expression expr;

    public MockExpressionClause(T result) {
        this.delegate = new MockExpressionClauseSupport<>(result);
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
     * Specify the constant expression value.
     *
     * <b>Important:</b> this is a fixed constant value that is only set once during starting up the route,
     * do not use this if you want dynamic values during routing.
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
     * A functional expression of an outbound message
     */
    public T outMessage(final Function<Message, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getOut());
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
     * A functional expression of an inbound message body
     */
    public T body(final Supplier<Object> supplier) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return supplier.get();
            }
        });
    }

    /**
     * A functional expression of an inbound message body and headers
     */
    public T body(final BiFunction<Object, Map<String, Object>, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getIn().getBody(),
                    exchange.getIn().getHeaders());
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
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getIn().getBody(expectedType),
                    exchange.getIn().getHeaders());
            }
        });
    }

    /**
     * A functional expression of an outbound message body
     */
    public T outBody(final Function<Object, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getOut().getBody());
            }
        });
    }

    /**
     * A functional expression of an outbound message body and headers
     */
    public T outBody(final BiFunction<Object, Map<String, Object>, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getOut().getBody(),
                    exchange.getOut().getHeaders());
            }
        });
    }

    /**
     * A functional expression of an outbound message body converted to the expected type
     */
    public <B> T outBody(Class<B> expectedType, final Function<B, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(exchange.getOut().getBody(expectedType));
            }
        });
    }

    /**
     * A functional expression of an outbound message body converted to the expected type and headers
     */
    public <B> T outBody(Class<B> expectedType, final BiFunction<B, Map<String, Object>, Object> function) {
        return delegate.expression(new ExpressionAdapter() {
            public Object evaluate(Exchange exchange) {
                return function.apply(
                    exchange.getOut().getBody(expectedType),
                    exchange.getOut().getHeaders());
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
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html">bean language</a>
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
     * Evaluates an expression using the <a
     * href="http://camel.apache.org/bean-language.html">bean language</a>
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
     * Evaluates a <a
     * href="http://camel.apache.org/jsonpath.html">Json Path
     * expression</a>
     *
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T jsonpath(String text) {
        return delegate.jsonpath(text);
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
     * Evaluates an <a
     * href="http://camel.apache.org/xquery.html">XQuery expression</a>
     * 
     * @param text the expression to be evaluated
     * @return the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return delegate.xquery(text);
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
                        newExpression = getExpressionType().createExpression(context);
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
