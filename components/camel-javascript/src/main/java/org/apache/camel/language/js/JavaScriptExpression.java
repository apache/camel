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

import org.apache.camel.Exchange;
import org.apache.camel.support.ExpressionSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

import static org.graalvm.polyglot.Source.newBuilder;

public class JavaScriptExpression extends ExpressionSupport {

    private final String expressionString;
    private final Class<?> type;

    public JavaScriptExpression(String expressionString, Class<?> type) {
        this.expressionString = expressionString;
        this.type = type;
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
        try (Context cx = JavaScriptHelper.newContext()) {
            Value b = cx.getBindings("js");

            b.putMember("exchange", exchange);
            b.putMember("context", exchange.getContext());
            b.putMember("exchangeId", exchange.getExchangeId());
            b.putMember("message", exchange.getMessage());
            b.putMember("headers", exchange.getMessage().getHeaders());
            b.putMember("properties", exchange.getAllProperties());
            b.putMember("body", exchange.getMessage().getBody());

            Source source = newBuilder("js", expressionString, "Unnamed")
                    .mimeType("application/javascript+module").buildLiteral();
            Value o = cx.eval(source);
            Object answer = o != null ? o.as(Object.class) : null;
            if (type == Object.class) {
                return (T) answer;
            }
            return exchange.getContext().getTypeConverter().convertTo(type, exchange, answer);
        }
    }

    public Class<?> getType() {
        return type;
    }

    @Override
    public String toString() {
        return "JavaScript[" + expressionString + "]";
    }

}
