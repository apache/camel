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
package org.apache.camel.language.python;

import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Message;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.support.LanguageHelper;
import org.python.core.PyCode;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

public class PythonExpression extends ExpressionSupport {

    private final String expressionString;
    private final Class<?> type;
    private final PythonInterpreter compiler;
    private final PyCode compiledExpression;
    private final Object lock = new Object();

    public PythonExpression(String expressionString, Class<?> type) {
        this.expressionString = expressionString;
        this.type = type;
        this.compiler = new PythonInterpreter();
        try {
            this.compiledExpression = compiler.compile(expressionString);
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(expressionString, e);
        }
    }

    public static PythonExpression python(String expression) {
        return new PythonExpression(expression, Object.class);
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        // the interpreter's globals are shared by every evaluation of this expression: bind, run and clean up
        // under one lock so concurrent exchanges cannot see each other's bindings
        synchronized (lock) {
            return doEvaluate(exchange, type);
        }
    }

    private <T> T doEvaluate(Exchange exchange, Class<T> type) {
        try {
            Message message = exchange.getMessage();
            Map<String, Object> headers = message.getHeaders();
            Map<String, Object> properties = exchange.getAllProperties();
            Map<String, Object> variables = exchange.getVariables();
            // the names ExchangeHelper.populateVariableMap gives Groovy, except in (a reserved word) and the deprecated
            // out and response
            compiler.set("exchange", exchange);
            compiler.set("camelContext", exchange.getContext());
            compiler.set("exchangeId", exchange.getExchangeId());
            compiler.set("message", message);
            compiler.set("request", message);
            compiler.set("body", message.getBody());
            compiler.set("header", headers);
            compiler.set("headers", headers);
            compiler.set("exchangeProperty", properties);
            compiler.set("exchangeProperties", properties);
            compiler.set("variable", variables);
            compiler.set("variables", variables);
            compiler.set("exception", LanguageHelper.exception(exchange));
            // deprecated names of camelContext and exchangeProperties
            compiler.set("context", exchange.getContext());
            compiler.set("properties", properties);

            PyObject out = compiler.eval(compiledExpression);
            Object value = toJava(out);
            if (value != null) {
                return exchange.getContext().getTypeConverter().convertTo(type, value);
            }
        } catch (Exception e) {
            // the script compiled in the constructor, so this is an error while it runs
            throw new ExpressionEvaluationException(this, exchange, e);
        } finally {
            compiler.cleanup();
        }
        return null;
    }

    /**
     * The Java value of what a script returns: None is null, a Python string, number or boolean is the Java String,
     * number or Boolean, and a Java object bound to the script (such as the body) is that object. A dict, list, tuple
     * or set is copied into a LinkedHashMap, ArrayList or LinkedHashSet of Java values, as the Jython collections are
     * live views that give their Python text from toString() and keep nested Python collections. A collection that
     * holds itself is copied once, and the copy holds itself.
     */
    static Object toJava(Object value) {
        return toJava(value, new IdentityHashMap<>());
    }

    private static Object toJava(Object value, Map<Object, Object> copies) {
        if (!(value instanceof PyObject py)) {
            return value;
        }
        Object answer = py.__tojava__(Object.class);
        if (!(answer instanceof PyObject)) {
            // None, a Python string, number or boolean, or a Java object
            return answer;
        }
        Object copied = copies.get(answer);
        if (copied != null) {
            return copied;
        }
        if (answer instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            copies.put(answer, copy);
            map.forEach((k, v) -> copy.put(toJava(k, copies), toJava(v, copies)));
            return copy;
        } else if (answer instanceof List<?> list) {
            return copyInto(answer, list, new ArrayList<>(list.size()), copies);
        } else if (answer instanceof Set<?> set) {
            return copyInto(answer, set, new LinkedHashSet<>(), copies);
        }
        return answer;
    }

    private static <C extends Collection<Object>> C copyInto(
            Object answer, Collection<?> source, C target, Map<Object, Object> copies) {
        copies.put(answer, target);
        for (Object element : source) {
            target.add(toJava(element, copies));
        }
        return target;
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    protected String assertionFailureMessage(Exchange exchange) {
        return expressionString;
    }

    @Override
    public String toString() {
        return "Python[" + expressionString + "]";
    }

}
