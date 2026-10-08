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

import java.util.Map;

import org.apache.camel.Exchange;
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
            if (out != null) {
                String value = out.toString();
                return exchange.getContext().getTypeConverter().convertTo(type, value);
            }
        } catch (Exception e) {
            throw new ExpressionIllegalSyntaxException(expressionString, e);
        } finally {
            compiler.cleanup();
        }
        return null;
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
