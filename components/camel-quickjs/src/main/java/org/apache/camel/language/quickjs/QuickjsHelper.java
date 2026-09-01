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
package org.apache.camel.language.quickjs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.util.StringHelper;

/**
 * Helpers for evaluating JavaScript with QuickJS4J using JSON-serializable Exchange bindings.
 */
final class QuickjsHelper {

    static final String MODULE_NAME = "camelQuickjs";
    static final String FUNCTION_NAME = "camelEval";

    /**
     * Evaluates {@code script} with a copy of {@code bindings} as function parameters so {@code var} declarations do
     * not leak into {@code globalThis} between Camel exchanges. Binding names must be valid JavaScript identifiers.
     * QuickJS4J host identifiers on {@code globalThis} are replaced with stubs while the user script runs and restored
     * afterwards so result delivery still works on a reused engine. Do not {@code delete} those properties: the host
     * function {@code java_invoke} is installed once per engine.
     */
    static final String EVAL_WRAPPER
            = """
                    export function camelEval(bindings, script) {
                      const names = Object.keys(bindings);
                      const values = names.map(name => bindings[name]);
                      const fn = new Function(...names, "__camel_quickjs_script",
                          '"use strict";'
                          + 'const java_invoke = () => { throw new TypeError("java_invoke is not available"); };'
                          + 'const quickjs4j_engine = undefined;'
                          + 'const previousJavaInvoke = globalThis.java_invoke;'
                          + 'const previousQuickjs4jEngine = globalThis.quickjs4j_engine;'
                          + 'const previousCamelQuickjs = globalThis.camelQuickjs;'
                          + 'try {'
                          + 'globalThis.java_invoke = java_invoke;'
                          + 'globalThis.quickjs4j_engine = undefined;'
                          + 'globalThis.camelQuickjs = undefined;'
                          + 'return eval(__camel_quickjs_script);'
                          + '} finally {'
                          + 'globalThis.java_invoke = previousJavaInvoke;'
                          + 'globalThis.quickjs4j_engine = previousQuickjs4jEngine;'
                          + 'globalThis.camelQuickjs = previousCamelQuickjs;'
                          + '}');
                      return fn(...values, script);
                    }
                    """;

    private static final ObjectMapper MAPPER = Engine.DEFAULT_OBJECT_MAPPER;

    /**
     * Strict-mode reserved words plus {@code eval}/{@code arguments}, which cannot be function parameters. Used so
     * generic {@code ScriptingLanguage.evaluate} bindings do not become a raw {@code new Function(...)} SyntaxError.
     */
    private static final Set<String> JS_RESERVED_NAMES = Set.of(
            "arguments", "await", "break", "case", "catch", "class", "const", "continue", "debugger", "default",
            "delete", "do", "else", "enum", "eval", "export", "extends", "false", "finally", "for", "function", "if",
            "implements", "import", "in", "instanceof", "interface", "let", "new", "null", "package", "private",
            "protected", "public", "return", "static", "super", "switch", "this", "throw", "true", "try", "typeof",
            "var", "void", "while", "with", "yield");

    private QuickjsHelper() {
    }

    static Engine newEngine(ByteArrayOutputStream stderr) {
        return Engine.builder()
                .withStdout(new DiscardingOutputStream())
                .withStderr(stderr)
                .addInvokables(Invokables.builder(MODULE_NAME)
                        .add(evalFunction())
                        .build())
                .build();
    }

    @SuppressWarnings("rawtypes")
    private static GuestFunction evalFunction() {
        return new GuestFunction(FUNCTION_NAME, List.of(Object.class, String.class), Object.class);
    }

    static Map<String, Object> exchangeBindings(Exchange exchange) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("body", toJsonCompatible(exchange.getMessage().getBody(), exchange, true));
        bindings.put("headers", toJsonCompatibleMap(exchange.getMessage().getHeaders(), exchange, false));
        bindings.put("properties", toJsonCompatibleMap(exchange.getAllProperties(), exchange, false));
        bindings.put("exchangeId", exchange.getExchangeId());
        return bindings;
    }

    static Map<String, Object> toJsonCompatibleBindings(Map<String, Object> bindings, Exchange exchange) {
        if (bindings == null || bindings.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : bindings.entrySet()) {
            String name = entry.getKey();
            if (!isJavaScriptIdentifier(name)) {
                throw new ExpressionEvaluationException(
                        null, exchange, new IllegalArgumentException(
                                "camel-quickjs binding name is not a valid JavaScript identifier: " + name));
            }
            copy.put(name, toJsonCompatible(entry.getValue(), exchange, true));
        }
        return copy;
    }

    static boolean isJavaScriptIdentifier(String name) {
        return StringHelper.isJavaIdentifier(name) && !JS_RESERVED_NAMES.contains(name);
    }

    /**
     * Converts a Java value into JSON-serializable data (primitives, {@link String}, {@link List}, {@link Map}). Live
     * Camel host objects are rejected when {@code failOnUnsupported} is true. Other Java types are snapshotted through
     * Jackson and are not callable from JavaScript.
     */
    static Object toJsonCompatible(Object value, Exchange exchange, boolean failOnUnsupported) {
        if (value == null || value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (isForbiddenHostObject(value)) {
            if (failOnUnsupported) {
                throw new ExpressionEvaluationException(null, exchange, forbiddenType(value));
            }
            return null;
        }
        if (isStreamingValue(value)) {
            if (failOnUnsupported) {
                throw new ExpressionEvaluationException(null, exchange, streamingType(value));
            }
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            return toJsonCompatibleMap(map, exchange, failOnUnsupported);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object element : collection) {
                Object converted = toJsonCompatible(element, exchange, failOnUnsupported);
                if (converted != null || element == null) {
                    copy.add(converted);
                }
            }
            return copy;
        }
        if (value instanceof byte[] || value instanceof char[]) {
            // Jackson JSON for byte[] is Base64; do not explode binary into a number array.
            return jsonSnapshot(value, exchange, failOnUnsupported);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(value, i);
                Object converted = toJsonCompatible(element, exchange, failOnUnsupported);
                if (converted != null || element == null) {
                    copy.add(converted);
                }
            }
            return copy;
        }
        return jsonSnapshot(value, exchange, failOnUnsupported);
    }

    private static Object jsonSnapshot(Object value, Exchange exchange, boolean failOnUnsupported) {
        try {
            JsonNode node = MAPPER.valueToTree(value);
            return MAPPER.treeToValue(node, Object.class);
        } catch (Exception e) {
            if (!failOnUnsupported) {
                return null;
            }
            throw new ExpressionEvaluationException(
                    null, exchange, new IllegalArgumentException(
                            "Value of type " + value.getClass().getName()
                                                                 + " is not JSON-serializable for camel-quickjs",
                            e));
        }
    }

    static Map<String, Object> toJsonCompatibleMap(Map<?, ?> map, Exchange exchange, boolean failOnUnsupported) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            try {
                Object converted = toJsonCompatible(entry.getValue(), exchange, failOnUnsupported);
                if (converted != null || entry.getValue() == null) {
                    copy.put(String.valueOf(entry.getKey()), converted);
                }
            } catch (ExpressionEvaluationException e) {
                if (failOnUnsupported) {
                    throw e;
                }
            }
        }
        return copy;
    }

    static boolean isForbiddenHostObject(Object value) {
        return value instanceof Exchange
                || value instanceof Message
                || value instanceof CamelContext
                || value instanceof Class
                || value instanceof ClassLoader;
    }

    static boolean isStreamingValue(Object value) {
        return value instanceof InputStream || value instanceof Reader || value instanceof StreamCache;
    }

    static RuntimeCamelException wrapFailure(String script, Exchange exchange, Exception e) {
        if (e instanceof ExpressionIllegalSyntaxException ise) {
            return ise;
        }
        if (e instanceof ExpressionEvaluationException eee) {
            return eee;
        }
        if (isSyntaxError(e)) {
            return new ExpressionIllegalSyntaxException(script, e);
        }
        return new ExpressionEvaluationException(null, exchange, e);
    }

    static boolean isSyntaxError(Throwable thrown) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (current instanceof ExpressionIllegalSyntaxException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("SyntaxError") || message.contains("Failed to compile JS code"))) {
                return true;
            }
            if (current instanceof GuestException && message != null && message.contains("SyntaxError")) {
                return true;
            }
        }
        return false;
    }

    private static IllegalArgumentException forbiddenType(Object value) {
        return new IllegalArgumentException(
                "Type " + value.getClass().getName() + " cannot be exposed to camel-quickjs");
    }

    private static IllegalArgumentException streamingType(Object value) {
        return new IllegalArgumentException(
                "Streaming type " + value.getClass().getName()
                                            + " cannot be exposed to camel-quickjs without consuming the message body");
    }

    /**
     * Drops {@code console.log} / WASI output so a reused {@link Engine} does not accumulate stdout.
     */
    private static final class DiscardingOutputStream extends ByteArrayOutputStream {
        @Override
        public synchronized void write(int b) {
            // discard
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            // discard
        }
    }
}
