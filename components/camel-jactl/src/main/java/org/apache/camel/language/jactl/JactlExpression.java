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
package org.apache.camel.language.jactl;

import java.util.HashMap;
import java.util.Map;

import io.jactl.JactlScript;
import org.apache.camel.Exchange;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ExpressionAdapter;

/**
 * A compiled Jactl script usable as both a Camel {@link org.apache.camel.Expression} and
 * {@link org.apache.camel.Predicate}.
 */
public class JactlExpression extends ExpressionAdapter {

    private final String text;
    private volatile JactlScript compiled;
    private volatile boolean allowContextMapAll = false;

    JactlExpression(String text, JactlScript compiled) {
        this.text = text;
        this.compiled = compiled;
    }

    JactlExpression(String text, JactlScript compiled, boolean allowContextMapAll) {
        this.text = text;
        this.compiled = compiled;
        this.allowContextMapAll = allowContextMapAll;
    }

    @Override
    public Object evaluate(Exchange exchange) {
        JactlScript script = compiled;
        if (script == null) {
            JactlLanguage language = (JactlLanguage) exchange.getContext().resolveLanguage("jactl");
            script = language.getScript(text);
            compiled = script;
            allowContextMapAll = language.allowContextMapAll;
        }
        if (allowContextMapAll) {
            Map<String, Object> globals = new HashMap<>() {
                @Override
                public Object get(Object key) {
                    return switch ((String) key) {
                        case "body" -> exchange.getIn().getBody();
                        case "header", "headers" -> exchange.getIn().getHeaders();
                        case "variable", "variables" -> exchange.getVariables();
                        case "exception" -> exchange.getException();
                        case "request" -> exchange.getIn();
                        case "response" -> ExchangeHelper.isOutCapable(exchange) ? exchange.getMessage() : null;
                        case "exchange" -> exchange;
                        case "exchangeProperty", "exchangeProperties" -> exchange.getAllProperties();
                        case "camelContext" -> exchange.getContext();
                        default -> throw new IllegalStateException("Internal error: unknown global variable: " + key);
                    };
                }
            };
            return script.eval(globals);
        } else {
            Map<String, Object> globals = new HashMap<>() {
                @Override
                public Object get(Object key) {
                    return switch ((String) key) {
                        case "body" -> exchange.getIn().getBody();
                        case "header", "headers" -> exchange.getIn().getHeaders();
                        case "variable", "variables" -> exchange.getVariables();
                        default -> throw new IllegalStateException("Internal error: unknown global variable: " + key);
                    };
                }
            };
            return script.eval(globals);
        }
    }

    @Override
    public String toString() {
        return "jactl: " + text;
    }
}
