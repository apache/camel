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
import java.util.function.BiFunction;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.roastedroot.quickjs4j.core.Builtins;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import io.roastedroot.quickjs4j.core.GuestFunction;
import io.roastedroot.quickjs4j.core.HostFunction;
import io.roastedroot.quickjs4j.core.Invokables;
import io.roastedroot.quickjs4j.core.ScriptCache;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import run.endive.runtime.ByteArrayMemory;
import run.endive.runtime.Memory;

/**
 * Helpers for evaluating JavaScript with QuickJS4J using JSON-serializable Exchange bindings.
 */
final class QuickjsHelper {

    static final String MODULE_NAME = "camelQuickjs";
    static final String FUNCTION_NAME = "camelEval";

    /**
     * Name of the QuickJS4J builtins module (and of the JavaScript object) that exposes the controlled Camel API to
     * scripts: {@code camel.getHeader('foo')}, {@code camel.setBody(...)}, and so on.
     */
    static final String CAMEL_MODULE = "camel";

    /**
     * The exchange bindings every route expression sees, in the order they are passed to the script function.
     */
    static final List<String> EXCHANGE_BINDINGS
            = List.of("body", "headers", "properties", "exchangeId", "variables", "exception");

    private static final Logger LOG = LoggerFactory.getLogger("org.apache.camel.language.quickjs.script");

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

    /**
     * The controlled Camel API. Each entry is one host function: its JavaScript name, its parameter names (the arity is
     * fixed so the JSON argument array always has every slot) and the Java implementation, which receives the current
     * {@link Exchange} and the JSON-decoded arguments.
     */
    private static final List<CamelFunction> CAMEL_API = List.of(
            new CamelFunction(
                    "getBody", List.of(), Object.class,
                    // the same rules as the body binding: a streaming body is an error, not a silent null
                    (exchange, args) -> toJsonCompatible(exchange.getMessage().getBody(), exchange, true)),
            new CamelFunction(
                    "setBody", List.of("value"), Void.class,
                    (exchange, args) -> {
                        exchange.getMessage().setBody(args.get(0));
                        return null;
                    }),
            new CamelFunction(
                    "getHeader", List.of("name"), Object.class,
                    (exchange, args) -> toJsonCompatible(exchange.getMessage().getHeader(name(args)), exchange, false)),
            new CamelFunction(
                    "setHeader", List.of("name", "value"), Void.class,
                    (exchange, args) -> {
                        exchange.getMessage().setHeader(name(args), args.get(1));
                        return null;
                    }),
            new CamelFunction(
                    "removeHeader", List.of("name"), Object.class,
                    (exchange, args) -> exchange.getMessage().removeHeader(name(args))),
            new CamelFunction(
                    "getProperty", List.of("name"), Object.class,
                    (exchange, args) -> toJsonCompatible(exchange.getProperty(name(args)), exchange, false)),
            new CamelFunction(
                    "setProperty", List.of("name", "value"), Void.class,
                    (exchange, args) -> {
                        exchange.setProperty(name(args), args.get(1));
                        return null;
                    }),
            new CamelFunction(
                    "removeProperty", List.of("name"), Object.class,
                    (exchange, args) -> exchange.removeProperty(name(args))),
            new CamelFunction(
                    "getVariable", List.of("name"), Object.class,
                    (exchange, args) -> toJsonCompatible(exchange.getVariable(name(args)), exchange, false)),
            new CamelFunction(
                    "setVariable", List.of("name", "value"), Void.class,
                    (exchange, args) -> {
                        exchange.setVariable(name(args), args.get(1));
                        return null;
                    }),
            new CamelFunction(
                    "removeVariable", List.of("name"), Object.class,
                    (exchange, args) -> exchange.removeVariable(name(args))),
            new CamelFunction(
                    "log", List.of("level", "message"), Void.class,
                    (exchange, args) -> {
                        log(String.valueOf(args.get(0)), String.valueOf(args.get(1)));
                        return null;
                    }));

    /**
     * JavaScript that builds the {@code camel} facade once per module evaluation. It captures the real
     * {@code java_invoke} in a closure that user scripts cannot reach, and only ever dispatches to the
     * {@value #CAMEL_MODULE} builtins module with a fixed argument arity per function.
     */
    static final String CAMEL_FACADE = camelFacade();

    /**
     * Prologue and epilogue shared by every script function: QuickJS4J host identifiers on {@code globalThis} are
     * replaced with stubs while the user script runs and restored afterwards so result delivery still works on a reused
     * engine. Do not {@code delete} those properties: the host function {@code java_invoke} is installed once per
     * engine.
     */
    private static final String SANDBOX_ENTER = """
            const __camel_prev_invoke = globalThis.java_invoke;
            const __camel_prev_engine = globalThis.quickjs4j_engine;
            const __camel_prev_module = globalThis.camelQuickjs;
            const __camel_prev_camel = globalThis.camel;
            const __camel_stub_invoke = () => { throw new TypeError("java_invoke is not available"); };
            try {
              globalThis.java_invoke = __camel_stub_invoke;
              globalThis.quickjs4j_engine = undefined;
              globalThis.camelQuickjs = undefined;
              globalThis.camel = __camelFacade;
            """;

    private static final String SANDBOX_EXIT = """
            } finally {
              globalThis.java_invoke = __camel_prev_invoke;
              globalThis.quickjs4j_engine = __camel_prev_engine;
              globalThis.camelQuickjs = __camel_prev_module;
              globalThis.camel = __camel_prev_camel;
            }
            """;

    /**
     * Generic wrapper used by {@code ScriptingLanguage.evaluate(script, bindings, resultType)}: the script text is
     * passed as an argument and evaluated with a copy of {@code bindings} as function parameters so {@code var}
     * declarations do not leak into {@code globalThis} between calls. Binding names must be valid JavaScript
     * identifiers.
     */
    static final String EVAL_WRAPPER = CAMEL_FACADE
                                       + """
                                               export function camelEval(bindings, script) {
                                                 const names = Object.keys(bindings);
                                                 const values = names.map(name => bindings[name]);
                                                 const fn = new Function(...names, "camel", "java_invoke", "quickjs4j_engine", "__camel_quickjs_script",
                                                     '"use strict"; return eval(__camel_quickjs_script);');
                                               """
                                       + indent(SANDBOX_ENTER, 2)
                                       + "    return fn(...values, __camelFacade, __camel_stub_invoke, undefined, script);\n"
                                       + indent(SANDBOX_EXIT, 2)
                                       + "}\n";

    private QuickjsHelper() {
    }

    static Engine newEngine(ByteArrayOutputStream stderr, Supplier<Exchange> currentExchange, Memory[] memory) {
        Builtins.Builder camel = Builtins.builder(CAMEL_MODULE);
        for (CamelFunction function : CAMEL_API) {
            camel.add(function.hostFunction(currentExchange));
        }
        return Engine.builder()
                .withStdout(new DiscardingOutputStream())
                .withStderr(stderr)
                .withCache(NoScriptCache.INSTANCE)
                .withMemoryFactory(limits -> {
                    // keep a handle on the WebAssembly linear memory so the language can watch it grow
                    memory[0] = new ByteArrayMemory(limits);
                    return memory[0];
                })
                .addBuiltins(camel.build())
                .addInvokables(Invokables.builder(MODULE_NAME)
                        .add(evalFunction())
                        .build())
                .build();
    }

    @SuppressWarnings("rawtypes")
    private static GuestFunction evalFunction() {
        return new GuestFunction(FUNCTION_NAME, List.of(Object.class, String.class), Object.class);
    }

    /**
     * The JavaScript library for one route expression: the script is embedded, so QuickJS compiles it once per engine
     * instead of on every evaluation. An expression is compiled as {@code return (script)}; a script that is not a
     * single expression (statements, a trailing semicolon) falls back to {@code eval} so it keeps its completion value,
     * exactly as before.
     */
    static String scriptLibrary(String script, boolean expressionForm) {
        StringBuilder sb = new StringBuilder(CAMEL_FACADE);
        sb.append("function __camelScript(").append(String.join(", ", EXCHANGE_BINDINGS))
                .append(", camel, java_invoke, quickjs4j_engine) {\n  \"use strict\";\n");
        if (expressionForm) {
            sb.append("  return (\n").append(script).append("\n  );\n}\n");
        } else {
            sb.append("  return eval(__camelSource);\n}\n");
            sb.append("const __camelSource = ").append(jsStringLiteral(script)).append(";\n");
        }
        sb.append("export function camelEval(b, s) {\n");
        sb.append(indent(SANDBOX_ENTER, 1));
        sb.append("    return __camelScript(");
        for (String name : EXCHANGE_BINDINGS) {
            sb.append("b.").append(name).append(", ");
        }
        sb.append("__camelFacade, __camel_stub_invoke, undefined);\n");
        sb.append(indent(SANDBOX_EXIT, 1));
        sb.append("}\n");
        return sb.toString();
    }

    static byte[] compileScript(Engine engine, ByteArrayOutputStream stderr, String script) {
        stderr.reset();
        try {
            return engine.compilePortableGuestFunction(scriptLibrary(script, true));
        } catch (RuntimeException e) {
            if (!isCompileFailure(e)) {
                throw e;
            }
            // not a single expression: check it is valid as statements, then keep completion-value semantics
            // through eval (a script that is invalid either way fails here, before anything runs)
            stderr.reset();
            try {
                engine.compilePortableGuestFunction("function __camelProbe() {\n" + script + "\n}\n");
            } catch (RuntimeException probe) {
                if (!isCompileFailure(probe)) {
                    throw probe;
                }
                // the script does not parse either way, unless the engine itself can no longer compile anything
                stderr.reset();
                try {
                    engine.compilePortableGuestFunction("function __camelProbe() {\n1\n}\n");
                } catch (RuntimeException broken) {
                    throw new IllegalStateException("camel-quickjs engine can no longer compile scripts", broken);
                }
                throw new ExpressionIllegalSyntaxException(script, probe);
            }
            stderr.reset();
            return engine.compilePortableGuestFunction(scriptLibrary(script, false));
        } finally {
            stderr.reset();
        }
    }

    /**
     * Whether the exception is QuickJS refusing to compile a library (which QuickJS4J reports as "Failed to compile JS
     * code" without naming the error class), or a runtime SyntaxError.
     */
    static boolean isCompileFailure(Throwable thrown) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && message.contains("Failed to compile JS code")) {
                return true;
            }
        }
        return isSyntaxError(thrown);
    }

    static Map<String, Object> exchangeBindings(Exchange exchange) {
        Map<String, Object> bindings = new LinkedHashMap<>();
        bindings.put("body", toJsonCompatible(exchange.getMessage().getBody(), exchange, true));
        bindings.put("headers", toJsonCompatibleMap(exchange.getMessage().getHeaders(), exchange, false));
        bindings.put("properties", toJsonCompatibleMap(exchange.getAllProperties(), exchange, false));
        bindings.put("exchangeId", exchange.getExchangeId());
        bindings.put("variables",
                exchange.hasVariables() ? toJsonCompatibleMap(exchange.getVariables(), exchange, false) : Map.of());
        bindings.put("exception", exceptionBinding(exchange));
        return bindings;
    }

    private static Map<String, Object> exceptionBinding(Exchange exchange) {
        Throwable t = exchange.getException();
        if (t == null) {
            t = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
        }
        if (t == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", t.getClass().getName());
        map.put("message", t.getMessage());
        return map;
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
            if (message != null && message.contains("SyntaxError")) {
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

    private static String name(List<Object> args) {
        Object name = args.get(0);
        if (name == null) {
            throw new IllegalArgumentException("camel-quickjs: a name is required");
        }
        return String.valueOf(name);
    }

    private static void log(String level, String message) {
        switch (level.toLowerCase()) {
            case "trace" -> LOG.trace(message);
            case "debug" -> LOG.debug(message);
            case "warn" -> LOG.warn(message);
            case "error" -> LOG.error(message);
            default -> LOG.info(message);
        }
    }

    private static String camelFacade() {
        StringBuilder sb = new StringBuilder();
        sb.append("const __camelFacade = (() => {\n");
        sb.append("  const invoke = globalThis.java_invoke;\n");
        sb.append("  const call = (name, args) => JSON.parse(invoke(\"").append(CAMEL_MODULE)
                .append("\", name, JSON.stringify(args)));\n");
        sb.append("  return Object.freeze({\n");
        for (CamelFunction function : CAMEL_API) {
            String params = String.join(", ", function.params());
            sb.append("    ").append(function.name()).append(": (").append(params).append(") => call(\"")
                    .append(function.name()).append("\", [").append(params).append("]),\n");
        }
        sb.append("  });\n})();\n");
        return sb.toString();
    }

    static String jsStringLiteral(String s) {
        try {
            return MAPPER.writeValueAsString(s);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot encode script as a JavaScript string", e);
        }
    }

    private static String indent(String block, int levels) {
        String pad = "  ".repeat(levels);
        StringBuilder sb = new StringBuilder();
        for (String line : block.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            sb.append(pad).append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * One function of the controlled Camel API.
     */
    private record CamelFunction(String name, List<String> params, Class<?> returnType,
            BiFunction<Exchange, List<Object>, Object> body) {

        @SuppressWarnings("rawtypes")
        HostFunction hostFunction(Supplier<Exchange> currentExchange) {
            List<Class> paramTypes = new ArrayList<>(params.size());
            for (String param : params) {
                paramTypes.add("value".equals(param) ? Object.class : String.class);
            }
            return new HostFunction(name, paramTypes, returnType, args -> {
                Exchange exchange = currentExchange.get();
                if (exchange == null) {
                    throw new IllegalStateException(
                            "camel." + name + " is only available while evaluating a route expression");
                }
                return body.apply(exchange, args);
            });
        }
    }

    /**
     * QuickJS4J caches every compiled library by a SHA-256 of its source in an unbounded map; camel-quickjs keeps its
     * own bounded per-engine cache of compiled scripts, so the engine's cache is disabled.
     */
    private static final class NoScriptCache implements ScriptCache {
        static final NoScriptCache INSTANCE = new NoScriptCache();

        @Override
        public boolean exists(byte[] code) {
            return false;
        }

        @Override
        public void set(byte[] code, byte[] compiled) {
            // not cached here
        }

        @Override
        public byte[] get(byte[] code) {
            return null;
        }
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
