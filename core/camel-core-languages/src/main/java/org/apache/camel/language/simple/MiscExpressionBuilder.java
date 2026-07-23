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
package org.apache.camel.language.simple;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.Language;
import org.apache.camel.spi.SimpleFunctionRegistry;
import org.apache.camel.spi.UuidGenerator;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ClassicUuidGenerator;
import org.apache.camel.support.DefaultUuidGenerator;
import org.apache.camel.support.ExpressionAdapter;
import org.apache.camel.support.LanguageHelper;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.RandomUuidGenerator;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.ShortUuidGenerator;
import org.apache.camel.support.SimpleUuidGenerator;
import org.apache.camel.support.builder.ExpressionBuilder;
import org.apache.camel.support.builder.PredicateBuilder;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.SkipIterator;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsonable;

/**
 * Expression builder for miscellaneous functions used by the simple language.
 */
public final class MiscExpressionBuilder {

    private MiscExpressionBuilder() {
    }

    /**
     * Returns the message history (including exchange details or not)
     */
    public static Expression messageHistoryExpression(final boolean detailed) {
        return new ExpressionAdapter() {

            private ExchangeFormatter formatter;

            @Override
            public void init(CamelContext context) {
                if (detailed) {
                    // use the exchange formatter to log exchange details
                    formatter = getOrCreateExchangeFormatter(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return MessageHelper.dumpMessageHistoryStacktrace(exchange, formatter, false);
            }

            private ExchangeFormatter getOrCreateExchangeFormatter(CamelContext camelContext) {
                return LanguageHelper.getOrCreateExchangeFormatter(camelContext, formatter);
            }

            @Override
            public String toString() {
                return "messageHistory(" + detailed + ")";
            }
        };
    }

    /**
     * Returns an iterator to collate (iterate) the given expression
     */
    public static Expression collateExpression(final String expression, final int group) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                // first use simple then create the group expression
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                exp = ExpressionBuilder.groupIteratorExpression(exp, null, Integer.toString(group), false);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "collate(" + expression + "," + group + ")";
            }
        };
    }

    /**
     * Returns an iterator to collate (iterate) the given expression
     */
    public static Expression collateExpression(final String expression, final String group) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression num;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                num = context.resolveLanguage("simple").createExpression(group);
                num.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Integer n = num.evaluate(exchange, Integer.class);
                Expression grouped = ExpressionBuilder.groupIteratorExpression(exp, null, Integer.toString(n), false);
                grouped.init(exchange.getContext());
                return grouped.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "collate(" + expression + "," + group + ")";
            }
        };
    }

    /**
     * Returns an iterator to skip (iterate) the given expression
     */
    public static Expression skipExpression(final String expression, final int number) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return skipIteratorExpression(exp, number).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "skip(" + expression + "," + number + ")";
            }
        };
    }

    /**
     * Returns an iterator to skip (iterate) the given expression
     */
    public static Expression skipExpression(final String expression, final String number) {
        return new ExpressionAdapter() {
            private Expression exp;
            private Expression num;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                num = context.resolveLanguage("simple").createExpression(number);
                num.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Integer n = num.evaluate(exchange, Integer.class);
                if (n == null) {
                    throw new IllegalArgumentException("skip number expression evaluated to null: " + number);
                }
                return skipIteratorExpression(exp, n).evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                return "skip(" + expression + "," + number + ")";
            }
        };
    }

    /**
     * Whether the expression is empty or having a list/map which has no elements.
     */
    public static Expression isEmptyExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Object body;
                if (exp != null) {
                    body = exp.evaluate(exchange, Object.class);
                } else {
                    body = exchange.getMessage().getBody();
                }
                // this may be an object that we can iterate
                Iterable<?> it = org.apache.camel.support.ObjectHelper.createIterable(body);
                for (Object o : it) {
                    if (o != null) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "isEmpty(" + expression + ")";
                } else {
                    return "isEmpty()";
                }
            }
        };
    }

    /**
     * Whether the expression is an alphabetic String
     */
    public static Expression isAlphaExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String body;
                if (exp != null) {
                    body = exp.evaluate(exchange, String.class);
                } else {
                    body = exchange.getMessage().getBody(String.class);
                }
                if (body == null || body.isBlank()) {
                    return false;
                }
                for (int i = 0; i < body.length(); i++) {
                    char ch = body.charAt(i);
                    if (!Character.isLetter(ch)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "isAlpha(" + expression + ")";
                } else {
                    return "isAlpha()";
                }
            }
        };
    }

    /**
     * Whether the expression is a number (integral or floating)
     */
    public static Expression isNumericExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String body;
                if (exp != null) {
                    body = exp.evaluate(exchange, String.class);
                } else {
                    body = exchange.getMessage().getBody(String.class);
                }
                if (body == null || body.isBlank()) {
                    return false;
                }
                for (int i = 0; i < body.length(); i++) {
                    char ch = body.charAt(i);
                    if (!Character.isDigit(ch)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "isNumeric(" + expression + ")";
                } else {
                    return "isNumeric()";
                }
            }
        };
    }

    /**
     * Whether the expression is an alphabetic or numeric String
     */
    public static Expression isAlphaNumericExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String body;
                if (exp != null) {
                    body = exp.evaluate(exchange, String.class);
                } else {
                    body = exchange.getMessage().getBody(String.class);
                }
                if (body == null || body.isBlank()) {
                    return false;
                }
                for (int i = 0; i < body.length(); i++) {
                    char ch = body.charAt(i);
                    if (!Character.isLetterOrDigit(ch)) {
                        return false;
                    }
                }
                return true;
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "isAlphaNumeric(" + expression + ")";
                } else {
                    return "isAlphaNumeric()";
                }
            }
        };
    }

    /**
     * Returns the opposite result of the predicate
     */
    public static Expression isNotPredicate(final String predicate) {
        return new ExpressionAdapter() {
            private Predicate pred;

            @Override
            public void init(CamelContext context) {
                pred = PredicateBuilder.not(context.resolveLanguage("simple").createPredicate(predicate));
                pred.init(context);
            }

            public Object evaluate(Exchange exchange) {
                return pred.matches(exchange);
            }

            @Override
            public String toString() {
                if (predicate != null) {
                    return "not(" + predicate + ")";
                } else {
                    return "not()";
                }
            }
        };
    }

    /**
     * What kind of type is the expression
     */
    public static Expression kindOfTypeExpression(final String expression) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (expression != null) {
                    exp = context.resolveLanguage("simple").createExpression(expression);
                    exp.init(context);
                }
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Object value;
                if (exp != null) {
                    value = exp.evaluate(exchange, Object.class);
                } else {
                    value = exchange.getMessage().getBody();
                }
                if (value != null) {
                    Class<?> type = value.getClass();
                    if (ObjectHelper.isNumericType(type)) {
                        return "number";
                    } else if (boolean.class == type || Boolean.class == type) {
                        return "boolean";
                    } else if (value instanceof CharSequence) {
                        return "string";
                    } else if (ObjectHelper.isPrimitiveArrayType(type) || value instanceof Collection
                            || value instanceof Map<?, ?>) {
                        return "array";
                    } else {
                        return "object";
                    }
                }
                return "null";
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "kindOfType(" + expression + ")";
                } else {
                    return "kindOfType()";
                }
            }
        };
    }

    /**
     * Throws an exception with the given message.
     */
    public static Expression throwExceptionExpression(String msg, String type) {
        return new ExpressionAdapter() {
            private Class<?> clazz;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                if (type == null) {
                    clazz = IllegalArgumentException.class;
                } else {
                    try {
                        clazz = context.getClassResolver().resolveMandatoryClass(type);
                    } catch (ClassNotFoundException e) {
                        throw CamelExecutionException.wrapRuntimeException(e);
                    }
                }
                exp = context.resolveLanguage("simple").createExpression(msg);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String text = exp.evaluate(exchange, String.class);
                try {
                    // create a new exception of that type, and provide the message
                    Constructor<?> constructor = clazz.getConstructor(String.class);
                    Exception cause = (Exception) constructor.newInstance(text);
                    if (cause instanceof RuntimeException re) {
                        throw re;
                    }
                    throw new RuntimeCamelException(cause);
                } catch (RuntimeException re) {
                    throw re;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public String toString() {
                return "throwException(" + msg + ")";
            }
        };
    }

    /**
     * Assert the expression
     */
    public static Expression assertExpression(String expression, String message) {
        return new ExpressionAdapter() {
            private Predicate pred;

            @Override
            public void init(CamelContext context) {
                pred = context.resolveLanguage("simple").createPredicate(expression);
                pred.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                if (!pred.matches(exchange)) {
                    throw new SimpleAssertionException(message);
                }
                return null;
            }

            @Override
            public String toString() {
                return "assert(" + pred + ", " + message + ")";
            }
        };
    }

    /**
     * Converts the result of the expression to the given type
     */
    public static Expression convertToExpression(final String expression, final String type) {
        return new ExpressionAdapter() {
            private Class<?> clazz;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                try {
                    clazz = context.getClassResolver().resolveMandatoryClass(type);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapRuntimeException(e);
                }
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                return exp.evaluate(exchange, clazz);
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "convertTo(" + expression + ", " + type + ")";
                } else {
                    return "convertTo(" + type + ")";
                }
            }
        };
    }

    /**
     * Converts the result of the expression to the given type and invoking methods on the converted object defined in a
     * simple OGNL notation
     */
    public static Expression convertToOgnlExpression(final String expression, final String type, final String ognl) {
        return new ExpressionAdapter() {
            private Class<?> clazz;
            private Expression exp;
            private Language bean;

            @Override
            public void init(CamelContext context) {
                try {
                    clazz = context.getClassResolver().resolveMandatoryClass(type);
                } catch (ClassNotFoundException e) {
                    throw CamelExecutionException.wrapRuntimeException(e);
                }
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
                bean = context.resolveLanguage("bean");
            }

            @Override
            public Object evaluate(Exchange exchange) {
                Object body = exp.evaluate(exchange, clazz);
                if (body == null) {
                    return null;
                }
                Expression ognlExp = bean.createExpression(null, new Object[] { null, body, ognl });
                ognlExp.init(exchange.getContext());
                return ognlExp.evaluate(exchange, Object.class);
            }

            @Override
            public String toString() {
                if (expression != null) {
                    return "convertToOgnl(" + expression + ", " + type + ")." + ognl;
                } else {
                    return "convertToOgnl(" + type + ")." + ognl;
                }
            }
        };
    }

    /**
     * A ternary condition expression
     */
    public static Expression iifExpression(final String predicate, final String trueValue, final String falseValue) {
        return new ExpressionAdapter() {
            private Predicate pred;
            private Expression expTrue;
            private Expression expFalse;

            @Override
            public void init(CamelContext context) {
                pred = context.resolveLanguage("simple").createPredicate(predicate);
                pred.init(context);
                expTrue = context.resolveLanguage("simple").createExpression(trueValue);
                expTrue.init(context);
                expFalse = context.resolveLanguage("simple").createExpression(falseValue);
                expFalse.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                if (pred.matches(exchange)) {
                    return expTrue.evaluate(exchange, Object.class);
                } else {
                    return expFalse.evaluate(exchange, Object.class);
                }
            }

            @Override
            public String toString() {
                return "iif(" + predicate + "," + trueValue + "," + falseValue + ")";
            }
        };
    }

    /**
     * Hashes the value using the given algorithm
     */
    public static Expression hashExpression(final String expression, final String algorithm) {
        return new ExpressionAdapter() {
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                InputStream is = exp.evaluate(exchange, InputStream.class);
                if (is != null) {
                    try {
                        // calculate the hash in chunks in case the payload is big
                        MessageDigest digest = MessageDigest.getInstance(algorithm);
                        DigestInputStream dis = new DigestInputStream(is, digest);
                        IOHelper.copy(dis, new OutputStream() {
                            @Override
                            public void write(int b) throws IOException {
                                // ignore
                            }
                        });
                        return StringHelper.bytesToHex(digest.digest());
                    } catch (Exception e) {
                        throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
                    } finally {
                        // reset cached streams so they can be read again
                        MessageHelper.resetStreamCache(exchange.getMessage());
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "hash(" + expression + "," + algorithm + ")";
            }
        };
    }

    /**
     * Returns a random number between min and max (exclusive)
     */
    public static Expression randomExpression(final String min, final String max) {
        return new ExpressionAdapter() {
            private Expression exp1;
            private Expression exp2;

            @Override
            public Object evaluate(Exchange exchange) {
                int num1 = exp1.evaluate(exchange, Integer.class);
                int num2 = exp2.evaluate(exchange, Integer.class);
                Random random = new Random(); // NOSONAR
                return random.nextInt(num2 - num1) + num1;
            }

            @Override
            public void init(CamelContext context) {
                exp1 = ExpressionBuilder.simpleExpression(min);
                exp1.init(context);
                exp2 = ExpressionBuilder.simpleExpression(max);
                exp2.init(context);
            }

            @Override
            public String toString() {
                return "random(" + min + "," + max + ")";
            }
        };
    }

    /**
     * Returns a random number between 0 and max (exclusive)
     */
    public static Expression randomExpression(final int max) {
        return randomExpression(0, max);
    }

    /**
     * Returns a random number between min and max (exclusive)
     */
    public static Expression randomExpression(final int min, final int max) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                Random random = new Random(); // NOSONAR
                return random.nextInt(max - min) + min;
            }

            @Override
            public String toString() {
                return "random(" + min + "," + max + ")";
            }
        };
    }

    /**
     * Returns a new empty object of the given type
     */
    public static Expression newEmptyExpression(final String type) {

        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                if ("map".equalsIgnoreCase(type)) {
                    return new LinkedHashMap<>();
                } else if ("string".equalsIgnoreCase(type)) {
                    return "";
                } else if ("list".equalsIgnoreCase(type)) {
                    return new ArrayList<>();
                } else if ("set".equalsIgnoreCase(type)) {
                    return new LinkedHashSet<>();
                }
                throw new IllegalArgumentException("function empty(%s) has unknown type".formatted(type));
            }

            @Override
            public String toString() {
                return "empty(%s)".formatted(type);
            }
        };
    }

    /**
     * Returns an uuid string based on the given generator (default, classic, short, simple)
     */
    public static Expression uuidExpression(final String generator) {
        return new ExpressionAdapter() {

            UuidGenerator uuid;

            @Override
            public Object evaluate(Exchange exchange) {
                return uuid.generateUuid();
            }

            @Override
            public void init(CamelContext context) {
                if ("classic".equalsIgnoreCase(generator)) {
                    uuid = new ClassicUuidGenerator();
                } else if ("short".equals(generator)) {
                    uuid = new ShortUuidGenerator();
                } else if ("simple".equals(generator)) {
                    uuid = new SimpleUuidGenerator();
                } else if ("random".equals(generator)) {
                    uuid = new RandomUuidGenerator();
                } else if (generator == null || "default".equals(generator)) {
                    uuid = new DefaultUuidGenerator();
                } else {
                    // lookup custom generator
                    uuid = CamelContextHelper.mandatoryLookup(context, generator, UuidGenerator.class);
                }
            }

            @Override
            public String toString() {
                if (generator != null) {
                    return "uuid(" + generator + ")";
                } else {
                    return "uuid";
                }
            }
        };
    }

    public static Expression skipIteratorExpression(final Expression expression, final int skip) {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                // evaluate expression as iterator
                Iterator<?> it = expression.evaluate(exchange, Iterator.class);
                ObjectHelper.notNull(it,
                        "expression: " + expression + " evaluated on " + exchange + " must return an java.util.Iterator");
                return new SkipIterator(it, skip);
            }

            @Override
            public String toString() {
                return "skip " + expression + " " + skip + " times";
            }
        };
    }

    /**
     * Returns the expression for the {@code null} value
     */
    public static Expression nullExpression() {
        return new ExpressionAdapter() {
            @Override
            public Object evaluate(Exchange exchange) {
                return null;
            }

            @Override
            public String toString() {
                return "null";
            }
        };
    }

    /**
     * Returns an expression that caches the evaluation of another expression and returns the cached value, to avoid
     * re-evaluating the expression.
     *
     * @param  expression the target expression to cache
     * @return            the cached value
     */
    public static Expression cacheExpression(final Expression expression) {
        return new ExpressionAdapter() {
            private final AtomicReference<Object> cache = new AtomicReference<>();

            @Override
            public Object evaluate(Exchange exchange) {
                Object answer = cache.get();
                if (answer == null) {
                    answer = expression.evaluate(exchange, Object.class);
                    cache.set(answer);
                }
                return answer;
            }

            @Override
            public String toString() {
                return expression.toString();
            }
        };
    }

    /**
     * Loads the given resource from classpath
     */
    public static Expression loadExpression(final String expression) {
        return new ExpressionAdapter() {

            private Expression exp;

            @Override
            public void init(CamelContext context) {
                exp = context.resolveLanguage("simple").createExpression(expression);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                String name = exp.evaluate(exchange, String.class);
                if (name != null) {
                    name = name.trim();
                    String part = StringHelper.after(name, ":", name);
                    boolean optional = part.endsWith("?optional=true");
                    if (optional) {
                        part = part.substring(0, part.length() - 14);
                    }
                    if (part.endsWith("?optional=false")) {
                        part = part.substring(0, part.length() - 15);
                    }
                    try {
                        InputStream is;
                        if (!optional) {
                            is = ResourceHelper.resolveMandatoryResourceAsInputStream(exchange.getContext(), part);
                        } else {
                            is = ResourceHelper.resolveResourceAsInputStream(exchange.getContext(), part);
                        }
                        if (is == null) {
                            return null;
                        }
                        return IOHelper.loadText(is, false);
                    } catch (Exception e) {
                        throw RuntimeCamelException.wrapRuntimeException(e);
                    }
                }
                return null;
            }

            @Override
            public String toString() {
                return "load(" + expression + ")";
            }
        };
    }

    /**
     * Returns an expression for a type value
     *
     * @param  name the type name
     * @return      an expression object which will return the type value
     */
    public static Expression typeExpression(final String name) {
        return new ExpressionAdapter() {
            private ClassResolver classResolver;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                classResolver = context.getClassResolver();
                exp = ExpressionBuilder.simpleExpression(name);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                // it may refer to a class type
                String text = exp.evaluate(exchange, String.class);
                Class<?> type = classResolver.resolveClass(text);
                if (type != null) {
                    return type;
                }

                int pos = text.lastIndexOf('.');
                if (pos > 0) {
                    String before = text.substring(0, pos);
                    String after = text.substring(pos + 1);
                    type = classResolver.resolveClass(before);
                    if (type != null) {
                        // special for enum constants
                        if (type.isEnum()) {
                            Class<Enum<?>> enumClass = (Class<Enum<?>>) type;
                            for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                                if (enumValue.name().equalsIgnoreCase(after)) {
                                    return type.cast(enumValue);
                                }
                            }
                            throw CamelExecutionException.wrapCamelExecutionException(exchange,
                                    new ClassNotFoundException("Cannot find enum: " + after + " on type: " + type));
                        } else {
                            // we assume it is a field constant
                            Object answer = ObjectHelper.lookupConstantFieldValue(type, after);
                            if (answer != null) {
                                return answer;
                            }
                        }
                    }
                }

                throw CamelExecutionException.wrapCamelExecutionException(exchange,
                        new ClassNotFoundException("Cannot find type: " + text));
            }

            @Override
            public String toString() {
                return "type:" + name;
            }
        };
    }

    /**
     * Executes a custom simple function
     */
    public static Expression customFunction(final String name, final String parameter) {
        return new ExpressionAdapter() {
            private Expression func;
            private Expression exp;

            @Override
            public void init(CamelContext context) {
                super.init(context);
                SimpleFunctionRegistry registry
                        = context.getCamelContextExtension().getContextPlugin(SimpleFunctionRegistry.class);
                func = registry.getFunction(name);
                if (func == null) {
                    throw new IllegalArgumentException("No custom simple function with name: " + name);
                }
                exp = ExpressionBuilder.simpleExpression(parameter);
                exp.init(context);
            }

            @Override
            public Object evaluate(Exchange exchange) {
                final Object originalBody = exchange.getMessage().getBody();
                try {
                    Object input = exp.evaluate(exchange, Object.class);
                    exchange.getMessage().setBody(input);
                    return func.evaluate(exchange, Object.class);
                } finally {
                    exchange.getMessage().setBody(originalBody);
                }
            }

            public String toString() {
                return "function(" + name + ")";
            }
        };
    }

    /**
     * Evaluates the simple jsonpath with the source input
     */
    public static Expression simpleJsonPathExpression(String source, String path) {
        return new ExpressionAdapter() {
            private Expression input;

            @Override
            public Object evaluate(Exchange exchange) {
                Jsonable j = input.evaluate(exchange, Jsonable.class);
                if (j instanceof JsonObject jo) {
                    return jo.path(path);
                } else if (j instanceof JsonArray ja) {
                    // wrap array in pseudo root to leverage json-path here
                    JsonObject jo = new JsonObject();
                    jo.put("_root_", ja);
                    return jo.path("_root_." + path);
                }
                return null;
            }

            @Override
            public void init(CamelContext context) {
                super.init(context);
                input = ExpressionBuilder.singleInputExpression(source);
                input.init(context);
            }

            @Override
            public String toString() {
                return "simpleJsonpath[" + path + "]";
            }
        };
    }
}
