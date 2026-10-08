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
package org.apache.camel.language.js;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.ExpressionSupport;
import org.apache.camel.support.LanguageHelper;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public class JavaScriptExpression extends ExpressionSupport {

    private final String expressionString;
    private final Class<?> type;
    private volatile JavaScriptLanguage language;

    public JavaScriptExpression(String expressionString, Class<?> type) {
        this(expressionString, type, null);
    }

    JavaScriptExpression(String expressionString, Class<?> type, JavaScriptLanguage language) {
        this.expressionString = expressionString;
        this.type = type;
        this.language = language;
    }

    public static JavaScriptExpression js(String expression) {
        return new JavaScriptExpression(expression, Object.class);
    }

    @Override
    protected String assertionFailureMessage(Exchange exchange) {
        return expressionString;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        JavaScriptLanguage lang = language(exchange);
        try (Context cx = lang.newContext()) {
            Value b = cx.getBindings("js");

            Message message = exchange.getMessage();
            Map<String, Object> headers = message.getHeaders();
            Map<String, Object> properties = exchange.getAllProperties();
            Map<String, Object> variables = exchange.getVariables();
            // the names ExchangeHelper.populateVariableMap gives Groovy, except in (a reserved word) and the deprecated
            // out and response
            b.putMember("exchange", exchange);
            b.putMember("camelContext", exchange.getContext());
            b.putMember("exchangeId", exchange.getExchangeId());
            b.putMember("message", message);
            b.putMember("request", message);
            b.putMember("body", message.getBody());
            b.putMember("header", headers);
            b.putMember("headers", headers);
            b.putMember("exchangeProperty", properties);
            b.putMember("exchangeProperties", properties);
            b.putMember("variable", variables);
            b.putMember("variables", variables);
            b.putMember("exception", LanguageHelper.exception(exchange));
            // deprecated names of camelContext and exchangeProperties
            b.putMember("context", exchange.getContext());
            b.putMember("properties", properties);

            Value o = cx.eval(lang.source(expressionString));
            Object answer = JavaScriptLanguage.materialize(o);
            if (type == Object.class) {
                return (T) answer;
            }
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, answer);
        }
    }

    /**
     * The language owning the shared engine. Expressions created through the language already have it; expressions
     * created directly (for example via {@link #js(String)}) resolve it from the exchange on first use.
     */
    private JavaScriptLanguage language(Exchange exchange) {
        JavaScriptLanguage lang = language;
        if (lang == null) {
            lang = (JavaScriptLanguage) exchange.getContext().resolveLanguage("js");
            language = lang;
        }
        return lang;
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    public String toString() {
        return "JavaScript[" + expressionString + "]";
    }

}
