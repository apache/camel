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

import org.apache.camel.BeanScope;
import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionFactory;
import org.apache.camel.PredicateFactory;
import org.apache.camel.model.language.CSimpleExpression;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.DatasonnetExpression;
import org.apache.camel.model.language.ExchangePropertyExpression;
import org.apache.camel.model.language.GroovyExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.Hl7TerserExpression;
import org.apache.camel.model.language.JavaScriptExpression;
import org.apache.camel.model.language.JoorExpression;
import org.apache.camel.model.language.JqExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.LanguageExpression;
import org.apache.camel.model.language.MethodCallExpression;
import org.apache.camel.model.language.MvelExpression;
import org.apache.camel.model.language.OgnlExpression;
import org.apache.camel.model.language.PythonExpression;
import org.apache.camel.model.language.RefExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.language.SpELExpression;
import org.apache.camel.model.language.TokenizerExpression;
import org.apache.camel.model.language.XMLTokenizerExpression;
import org.apache.camel.model.language.XPathExpression;
import org.apache.camel.model.language.XQueryExpression;
import org.apache.camel.spi.ExpressionFactoryAware;
import org.apache.camel.spi.PredicateFactoryAware;
import org.apache.camel.support.builder.Namespaces;

/**
 * A support class for building expression clauses.
 */
public class ExpressionClauseSupport<T> implements ExpressionFactoryAware, PredicateFactoryAware {

    // Implementation detail: We must use the specific model.language.xxx
    // classes to make the DSL use these specific types
    // which ensures that the route model dumped as XML uses these types, eg
    // <header> instead of <language name="header"> etc.

    private final T result;
    private Expression expressionValue;
    private ExpressionFactory expressionType;
    private PredicateFactory predicateType;

    public ExpressionClauseSupport(T result) {
        this.result = result;
    }

    // Helper expressions
    // -------------------------------------------------------------------------

    /**
     * Specify an {@link org.apache.camel.Expression} instance
     */
    public T expression(Expression expression) {
        if (expression instanceof ExpressionFactory || expression instanceof PredicateFactory) {
            // it can be both an expression and predicate
            if (expression instanceof ExpressionFactory) {
                setExpressionType((ExpressionFactory) expression);
            }
            if (expression instanceof PredicateFactory) {
                setPredicateType((PredicateFactory) expression);
            }
        } else {
            setExpressionValue(expression);
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
     * Specify the constant expression value. <b>Important:</b> this is a fixed constant value that is only set once
     * during starting up the route, do not use this if you want dynamic values during routing.
     */
    public T constant(Object value) {
        if (value instanceof String) {
            return expression(new ConstantExpression((String) value));
        } else {
            return expression(ExpressionBuilder.constantExpression(value));
        }
    }

    /**
     * Specify the constant expression value. <b>Important:</b> this is a fixed constant value that is only set once
     * during starting up the route, do not use this if you want dynamic values during routing.
     */
    public T constant(String value, Class<?> resultType) {
        ConstantExpression exp = new ConstantExpression(value);
        exp.setResultType(resultType);
        return expression(exp);
    }

    /**
     * Specify the constant expression value. <b>Important:</b> this is a fixed constant value that is only set once
     * during starting up the route, do not use this if you want dynamic values during routing.
     */
    public T constant(Object value, boolean trim) {
        if (value instanceof String) {
            ConstantExpression ce = new ConstantExpression((String) value);
            ce.setTrim(trim ? "true" : "false");
            return expression(ce);
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
     * An expression of an inbound message body
     */
    public T body() {
        // reuse simple as this allows the model to represent this as a known
        // JAXB type
        return expression(new SimpleExpression("${body}"));
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
        return expression(new HeaderExpression(name));
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
        return expression(new ExchangePropertyExpression(name));
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
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref the name (bean id) of the bean to lookup from the registry
     * @return     the builder to continue processing the DSL
     */
    public T method(String ref) {
        return expression(new MethodCallExpression(ref));
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  instance the existing instance of the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Object instance) {
        return expression(new MethodCallExpression(instance));
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     * <p>
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType) {
        return expression(new MethodCallExpression(beanType));
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref    the name (bean id) of the bean to lookup from the registry
     * @param  method the name of the method to invoke on the bean
     * @return        the builder to continue processing the DSL
     */
    public T method(String ref, String method) {
        return expression(new MethodCallExpression(ref, method));
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref   the name (bean id) of the bean to lookup from the registry
     * @param  scope the scope of the bean
     * @return       the builder to continue processing the DSL
     */
    public T method(String ref, BeanScope scope) {
        MethodCallExpression exp = new MethodCallExpression(ref);
        exp.setScope(scope.name());
        return expression(exp);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  ref    the name (bean id) of the bean to lookup from the registry
     * @param  method the name of the method to invoke on the bean
     * @param  scope  the scope of the bean
     * @return        the builder to continue processing the DSL
     */
    public T method(String ref, String method, BeanScope scope) {
        MethodCallExpression exp = new MethodCallExpression(ref, method);
        exp.setScope(scope.name());
        return expression(exp);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     *
     * @param  instance the existing instance of the bean
     * @param  method   the name of the method to invoke on the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Object instance, String method) {
        return expression(new MethodCallExpression(instance, method));
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     * <p>
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @param  method   the name of the method to invoke on the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, String method) {
        return expression(new MethodCallExpression(beanType, method));
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     * <p>
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @param  scope    the scope of the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, BeanScope scope) {
        MethodCallExpression exp = new MethodCallExpression(beanType);
        exp.setScope(scope.name());
        return expression(exp);
    }

    /**
     * Evaluates an expression using the <a href="http://camel.apache.org/bean-language.html>bean language</a> which
     * basically means the bean is invoked to determine the expression value.
     * <p>
     * Will lookup in registry and if there is a single instance of the same type, then the existing bean is used,
     * otherwise a new bean is created (requires a default no-arg constructor).
     *
     * @param  beanType the Class of the bean which we want to invoke
     * @param  method   the name of the method to invoke on the bean
     * @param  scope    the scope of the bean
     * @return          the builder to continue processing the DSL
     */
    public T method(Class<?> beanType, String method, BeanScope scope) {
        MethodCallExpression exp = new MethodCallExpression(beanType, method);
        exp.setScope(scope.name());
        return expression(exp);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/groovy.html">Groovy expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T groovy(String text) {
        return expression(new GroovyExpression(text));
    }

    /**
     * Evaluates a JavaScript expression.
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T js(String text) {
        return expression(new JavaScriptExpression(text));
    }

    /**
     * Evaluates an JavaScript expression
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T js(String text, Class<?> resultType) {
        JavaScriptExpression exp = new JavaScriptExpression(text);
        exp.setResultType(resultType);
        return expression(exp);
    }

    /**
     * Evaluates an JOOR expression
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T joor(String text) {
        return expression(new JoorExpression(text));
    }

    /**
     * Evaluates an JOOR expression
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T joor(String text, Class<?> resultType) {
        JoorExpression exp = new JoorExpression(text);
        exp.setResultType(resultType);
        return expression(exp);
    }

    /**
     * Evaluates <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T jq(String text) {
        return expression(new JqExpression(text));
    }

    /**
     * Evaluates <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jq(String text, Class<?> resultType) {
        JqExpression exp = new JqExpression(text);
        exp.setResultType(resultType);
        return expression(exp);
    }

    /**
     * Evaluates <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  text                 the expression to be evaluated
     * @param  headerOrPropertyName the name of the header or the property to apply the expression to
     * @return                      the builder to continue processing the DSL
     */
    public T jq(String text, String headerOrPropertyName) {
        JqExpression exp = new JqExpression(text);
        exp.setHeaderName(headerOrPropertyName);
        exp.setPropertyName(headerOrPropertyName);
        return expression(exp);
    }

    /**
     * Evaluates <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  text         the expression to be evaluated
     * @param  headerName   the name of the header to apply the expression to
     * @param  propertyName the name of the propertyName to apply the expression to
     * @return              the builder to continue processing the DSL
     */
    public T jq(String text, String headerName, String propertyName) {
        JqExpression exp = new JqExpression(text);
        exp.setHeaderName(headerName);
        exp.setPropertyName(propertyName);
        return expression(exp);
    }

    /**
     * Evaluates <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  text                 the expression to be evaluated
     * @param  resultType           the return type expected by the expression
     * @param  headerOrPropertyName the name of the header or the property to apply the expression to
     * @return                      the builder to continue processing the DSL
     */
    public T jq(String text, Class<?> resultType, String headerOrPropertyName) {
        JqExpression exp = new JqExpression(text);
        exp.setResultType(resultType);
        exp.setHeaderName(headerOrPropertyName);
        exp.setPropertyName(headerOrPropertyName);
        return expression(exp);
    }

    /**
     * Evaluates <a href="http://camel.apache.org/jq.html">JQ expression</a>
     *
     * @param  text         the expression to be evaluated
     * @param  resultType   the return type expected by the expression
     * @param  headerName   the name of the header to apply the expression to
     * @param  propertyName the name of the propertyName to apply the expression to
     * @return              the builder to continue processing the DSL
     */
    public T jq(String text, Class<?> resultType, String headerName, String propertyName) {
        JqExpression exp = new JqExpression(text);
        exp.setResultType(resultType);
        exp.setHeaderName(headerName);
        exp.setPropertyName(propertyName);
        return expression(exp);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/datasonnet.html">Datasonnet expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T datasonnet(String text) {
        return expression(new DatasonnetExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T jsonpath(String text) {
        return jsonpath(text, false);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  allowSimple        whether to allow in inlined simple exceptions in the json path expression
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, boolean allowSimple) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setAllowSimple(Boolean.toString(allowSimple));
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jsonpath(String text, Class<?> resultType) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setResultType(resultType);
        expression(expression);
        return result;
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
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setResultType(resultType);
        expression(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  allowSimple        whether to allow in inlined simple exceptions in the json path expression
     * @param  resultType         the return type expected by the expression
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, boolean allowSimple, Class<?> resultType) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setAllowSimple(Boolean.toString(allowSimple));
        expression.setResultType(resultType);
        expression(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a>
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  allowSimple        whether to allow in inlined simple exceptions in the json path expression
     * @param  resultType         the return type expected by the expression
     * @param  headerName         the name of the header to apply the expression to
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpath(String text, boolean suppressExceptions, boolean allowSimple, Class<?> resultType, String headerName) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setAllowSimple(Boolean.toString(allowSimple));
        expression.setResultType(resultType);
        expression.setHeaderName(headerName);
        expression(expression);
        return result;
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text) {
        return jsonpathWriteAsString(text, false);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, Class<?> resultType) {
        return jsonpathWriteAsString(text, false, resultType);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(Boolean.toString(true));
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        return expression(expression);
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
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(Boolean.toString(true));
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setResultType(resultType);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  allowSimple        whether to allow in inlined simple exceptions in the json path expression
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, boolean allowSimple) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(Boolean.toString(true));
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setAllowSimple(Boolean.toString(allowSimple));
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  allowSimple        whether to allow in inlined simple exceptions in the json path expression
     * @param  headerName         the name of the header to apply the expression to
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(String text, boolean suppressExceptions, boolean allowSimple, String headerName) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(Boolean.toString(true));
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setAllowSimple(Boolean.toString(allowSimple));
        expression.setHeaderName(headerName);
        return expression(expression);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/jsonpath.html">Json Path expression</a> with writeAsString enabled.
     *
     * @param  text               the expression to be evaluated
     * @param  suppressExceptions whether to suppress exceptions such as PathNotFoundException
     * @param  allowSimple        whether to allow in inlined simple exceptions in the json path expression
     * @param  headerName         the name of the header to apply the expression to
     * @param  resultType         the return type expected by the expression
     * @return                    the builder to continue processing the DSL
     */
    public T jsonpathWriteAsString(
            String text, boolean suppressExceptions, boolean allowSimple, String headerName, Class<?> resultType) {
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setWriteAsString(Boolean.toString(true));
        expression.setSuppressExceptions(Boolean.toString(suppressExceptions));
        expression.setAllowSimple(Boolean.toString(allowSimple));
        expression.setHeaderName(headerName);
        expression.setResultType(resultType);
        return expression(expression);
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
        JsonPathExpression expression = new JsonPathExpression(text);
        expression.setUnpackArray(Boolean.toString(true));
        expression.setResultType(resultType);
        return expression(expression);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/ognl.html">OGNL expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T ognl(String text) {
        return expression(new OgnlExpression(text));
    }

    /**
     * Evaluates a Python expression.
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T python(String text) {
        return expression(new PythonExpression(text));
    }

    /**
     * Evaluates Python expression
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T python(String text, Class<?> resultType) {
        PythonExpression exp = new PythonExpression(text);
        exp.setResultType(resultType);
        return expression(exp);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/mvel.html">MVEL expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T mvel(String text) {
        return expression(new MvelExpression(text));
    }

    /**
     * Evaluates a {@link Expression} by looking up existing {@link Expression} from the
     * {@link org.apache.camel.spi.Registry}
     *
     * @param  ref refers to the expression to be evaluated
     * @return     the builder to continue processing the DSL
     */
    public T ref(String ref) {
        return expression(new RefExpression(ref));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/spel.html">SpEL expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T spel(String text) {
        return expression(new SpELExpression(text));
    }

    /**
     * Evaluates a compiled simple expression
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T csimple(String text) {
        return expression(new CSimpleExpression(text));
    }

    /**
     * Evaluates a compiled simple expression
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T csimple(String text, Class<?> resultType) {
        CSimpleExpression exp = new CSimpleExpression(text);
        exp.setResultType(resultType);
        return expression(exp);
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T simple(String text) {
        return expression(new SimpleExpression(text));
    }

    /**
     * Evaluates a <a href="http://camel.apache.org/simple.html">Simple expression</a>
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the result type
     * @return            the builder to continue processing the DSL
     */
    public T simple(String text, Class<?> resultType) {
        SimpleExpression expression = new SimpleExpression(text);
        expression.setResultType(resultType);
        expression(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/hl7.html">HL7 Terser expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T hl7terser(String text) {
        return expression(new Hl7TerserExpression(text));
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token) {
        return tokenize(token, null, false);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @param  group to group by the given number
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token, int group) {
        return tokenize(token, null, false, group);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token     the token
     * @param  group     to group by the given number
     * @param  skipFirst whether to skip the very first element
     * @return           the builder to continue processing the DSL
     */
    public T tokenize(String token, int group, boolean skipFirst) {
        return tokenize(token, null, false, group, skipFirst);
    }

    /**
     * Evaluates a token expression on the message body
     *
     * @param  token the token
     * @param  regex whether the token is a regular expression or not
     * @return       the builder to continue processing the DSL
     */
    public T tokenize(String token, boolean regex) {
        return tokenize(token, null, regex);
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
        return tokenize(token, null, regex, group);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName) {
        return tokenize(token, headerName, false);
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
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(token);
        expression.setHeaderName(headerName);
        expression.setRegex(Boolean.toString(regex));
        expression(expression);
        return result;
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @param  regex      whether the token is a regular expression or not
     * @param  group      to group by number of parts
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, int group) {
        return tokenize(token, headerName, regex, group, false);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @param  regex      whether the token is a regular expression or not
     * @param  skipFirst  whether to skip the very first element
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, boolean skipFirst) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(token);
        expression.setHeaderName(headerName);
        expression.setRegex(Boolean.toString(regex));
        expression.setSkipFirst(Boolean.toString(skipFirst));
        expression(expression);
        return result;
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @param  regex      whether the token is a regular expression or not
     * @param  group      to group by number of parts
     * @param  skipFirst  whether to skip the very first element
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, int group, boolean skipFirst) {
        return tokenize(token, headerName, regex, Integer.toString(group), skipFirst);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token      the token
     * @param  headerName name of header to tokenize
     * @param  regex      whether the token is a regular expression or not
     * @param  group      to group by number of parts
     * @param  skipFirst  whether to skip the very first element
     * @return            the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, String group, boolean skipFirst) {
        return tokenize(token, headerName, regex, group, null, skipFirst);
    }

    /**
     * Evaluates a token expression on the given header
     *
     * @param  token          the token
     * @param  headerName     name of header to tokenize
     * @param  regex          whether the token is a regular expression or not
     * @param  group          to group by number of parts
     * @param  groupDelimiter delimiter to use when grouping
     * @param  skipFirst      whether to skip the very first element
     * @return                the builder to continue processing the DSL
     */
    public T tokenize(String token, String headerName, boolean regex, String group, String groupDelimiter, boolean skipFirst) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(token);
        expression.setHeaderName(headerName);
        expression.setRegex(Boolean.toString(regex));
        expression.setGroup(group);
        expression.setGroupDelimiter(groupDelimiter);
        expression.setSkipFirst(Boolean.toString(skipFirst));
        expression(expression);
        return result;
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
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(startToken);
        expression.setEndToken(endToken);
        expression.setIncludeTokens(Boolean.toString(includeTokens));
        expression(expression);
        return result;
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param  tagName                 the tag name of the child nodes to tokenize
     * @param  inheritNamespaceTagName optional parent or root tag name that contains namespace(s) to inherit
     * @param  group                   to group by the given number
     * @return                         the builder to continue processing the DSL
     */
    public T tokenizeXMLPair(String tagName, String inheritNamespaceTagName, int group) {
        return tokenizeXMLPair(tagName, inheritNamespaceTagName, Integer.toString(group));
    }

    /**
     * Evaluates a token pair expression on the message body with XML content
     *
     * @param  tagName                 the tag name of the child nodes to tokenize
     * @param  inheritNamespaceTagName optional parent or root tag name that contains namespace(s) to inherit
     * @param  group                   to group by the given number
     * @return                         the builder to continue processing the DSL
     */
    public T tokenizeXMLPair(String tagName, String inheritNamespaceTagName, String group) {
        TokenizerExpression expression = new TokenizerExpression();
        expression.setToken(tagName);
        expression.setInheritNamespaceTagName(inheritNamespaceTagName);
        expression.setXml(Boolean.toString(true));
        expression.setGroup(group);
        expression(expression);
        return result;
    }

    /**
     * Evaluates an XML token expression on the message body with XML content
     *
     * @param  path       the xpath like path notation specifying the child nodes to tokenize
     * @param  mode       one of 'i', 'w', or 'u' to inject the namespaces to the token, to wrap the token with its
     *                    ancestor contet, or to unwrap to its element child
     * @param  namespaces the namespace map to the namespace bindings
     * @param  group      to group by the given number
     * @return            the builder to continue processing the DSL
     */
    public T xtokenize(String path, char mode, Namespaces namespaces, int group) {
        XMLTokenizerExpression expression = new XMLTokenizerExpression(path);
        expression.setMode(Character.toString(mode));
        expression.setNamespaces(namespaces.getNamespaces());

        if (group > 0) {
            expression.setGroup(Integer.toString(group));
        }
        expression(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T xpath(String text) {
        return expression(new XPathExpression(text));
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
        XPathExpression expression = new XPathExpression(text);
        expression.setHeaderName(headerName);
        return expression(expression);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression(expression);
        return result;
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
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression.setHeaderName(headerName);
        expression(expression);
        return result;
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
        return xpath(text, resultType, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xpath.html">XPath expression</a> with the specified result type and
     * set of namespace prefixes and URIs on the supplied header name's contents
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  namespaces the namespace prefix and URIs to use
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xpath(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces.getNamespaces());
        expression.setHeaderName(headerName);
        expression(expression);
        return result;
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
        XPathExpression expression = new XPathExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces);
        expression(expression);
        return result;
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
        return xpath(text, namespaces.getNamespaces());
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
        XPathExpression expression = new XPathExpression(text);
        expression.setNamespaces(namespaces);
        expression(expression);
        return result;
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a>
     *
     * @param  text the expression to be evaluated
     * @return      the builder to continue processing the DSL
     */
    public T xquery(String text) {
        return expression(new XQueryExpression(text));
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a>
     *
     * @param  text       the expression to be evaluated
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, String headerName) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setHeaderName(headerName);
        return expression(expression);
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression(expression);
        return result;
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
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression.setHeaderName(headerName);
        expression(expression);
        return result;
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
        return xquery(text, resultType, namespaces.getNamespaces());
    }

    /**
     * Evaluates an <a href="http://camel.apache.org/xquery.html">XQuery expression</a> with the specified result type
     * and set of namespace prefixes and URIs
     *
     * @param  text       the expression to be evaluated
     * @param  resultType the return type expected by the expression
     * @param  namespaces the namespace prefix and URIs to use
     * @param  headerName the name of the header to apply the expression to
     * @return            the builder to continue processing the DSL
     */
    public T xquery(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces.getNamespaces());
        expression.setHeaderName(headerName);
        expression(expression);
        return result;
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
        XQueryExpression expression = new XQueryExpression(text);
        expression.setResultType(resultType);
        expression.setNamespaces(namespaces);
        expression(expression);
        return result;
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
        return xquery(text, namespaces.getNamespaces());
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
        XQueryExpression expression = new XQueryExpression(text);
        expression.setNamespaces(namespaces);
        expression(expression);
        return result;
    }

    /**
     * Evaluates a given language name with the expression text
     *
     * @param  language   the name of the language
     * @param  expression the expression in the given language
     * @return            the builder to continue processing the DSL
     */
    public T language(String language, String expression) {
        LanguageExpression exp = new LanguageExpression(language, expression);
        expression(exp);
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

    public ExpressionFactory getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(ExpressionFactory expressionType) {
        this.expressionType = expressionType;
    }

    @Override
    public ExpressionFactory getExpressionFactory() {
        return expressionType;
    }

    public PredicateFactory getPredicateType() {
        return predicateType;
    }

    public void setPredicateType(PredicateFactory predicateType) {
        this.predicateType = predicateType;
    }

    @Override
    public PredicateFactory getPredicateFactory() {
        return predicateType;
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
