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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.roastedroot.quickjs4j.core.Engine;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.Predicate;
import org.apache.camel.Service;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.TypedLanguageSupport;

/**
 * Camel expression language for JavaScript via <a href="https://github.com/roastedroot/quickjs4j">QuickJS4J</a>.
 *
 * <p>
 * Scripts see only JSON-serializable data bindings: {@code body}, {@code headers}, {@code properties}, and
 * {@code exchangeId}. Live {@code Exchange}, {@code Message}, and {@code CamelContext} objects are not bound, so
 * {@code exchange.getMessage()} is a JavaScript {@code ReferenceError} rather than Java interop.
 * </p>
 */
@Language("quickjs")
public class QuickjsLanguage extends TypedLanguageSupport implements ScriptingLanguage, Service {

    private final AtomicInteger generation = new AtomicInteger();
    private final ConcurrentLinkedQueue<Engine> engines = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<EngineState> engine = new ThreadLocal<>();
    private final Lock engineLock = new ReentrantLock();

    /**
     * Helper for use in the Java route DSL, e.g. {@code .filter(QuickjsLanguage.quickjs("body == 'Hello'"))}.
     */
    public static QuickjsExpression quickjs(String script) {
        return new QuickjsExpression(script);
    }

    @Override
    public void start() {
        // Engines are created lazily per worker thread.
    }

    @Override
    public void stop() {
        // Invalidate ThreadLocal caches on other worker threads before closing so a later
        // get() cannot reuse a closed Engine (ThreadLocal.remove() only affects this thread).
        // Hold engineLock so currentEngine() cannot publish a new engine after this drain.
        engineLock.lock();
        try {
            generation.incrementAndGet();
            Engine next;
            while ((next = engines.poll()) != null) {
                try {
                    next.close();
                } catch (RuntimeException e) {
                    // Continue closing remaining engines.
                }
            }
            engine.remove();
        } finally {
            engineLock.unlock();
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        return createQuickjsExpression(expression);
    }

    @Override
    public Expression createExpression(String expression) {
        return createQuickjsExpression(expression);
    }

    private QuickjsExpression createQuickjsExpression(String expression) {
        return new QuickjsExpression(loadResource(expression), this);
    }

    /**
     * Evaluates {@code script} with optional {@code bindings} as JavaScript function parameters. Binding names must be
     * valid JavaScript identifiers; invalid names fail with {@link ExpressionEvaluationException} rather than a raw
     * JavaScript {@code SyntaxError}. Route expressions do not use this map — they always bind {@code body},
     * {@code headers}, {@code properties}, and {@code exchangeId}.
     */
    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        try {
            Object result = eval(script, QuickjsHelper.toJsonCompatibleBindings(bindings, null));
            return convert(result, resultType, getCamelContext(), null);
        } catch (Exception e) {
            throw QuickjsHelper.wrapFailure(script, null, e);
        }
    }

    Object evaluateExpression(String script, Exchange exchange) {
        try {
            Object result = eval(script, QuickjsHelper.exchangeBindings(exchange));
            return convert(result, Object.class, exchange.getContext(), exchange);
        } catch (Exception e) {
            throw QuickjsHelper.wrapFailure(script, exchange, e);
        }
    }

    private Object eval(String script, Map<String, Object> bindings) {
        EngineState state = currentEngine();
        state.stderr.reset();
        try {
            return state.engine.invokeGuestFunction(
                    QuickjsHelper.MODULE_NAME,
                    QuickjsHelper.FUNCTION_NAME,
                    List.of(bindings, script),
                    QuickjsHelper.EVAL_WRAPPER);
        } finally {
            // Drop this evaluation's WASI stderr so a reused Engine cannot accumulate it.
            state.stderr.reset();
        }
    }

    private EngineState currentEngine() {
        return currentEngine(0);
    }

    private EngineState currentEngine(int attempt) {
        if (attempt > 8) {
            throw new IllegalStateException("camel-quickjs engine was invalidated while being created");
        }
        int gen = generation.get();
        EngineState state = engine.get();
        if (state != null && state.generation == gen) {
            return state;
        }
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Engine created = QuickjsHelper.newEngine(stderr);
        if (generation.get() != gen) {
            closeUnpublished(created);
            return currentEngine(attempt + 1);
        }
        engineLock.lock();
        try {
            if (generation.get() == gen) {
                engines.add(created);
                state = new EngineState(gen, created, stderr);
                engine.set(state);
                return state;
            }
        } finally {
            engineLock.unlock();
        }
        closeUnpublished(created);
        return currentEngine(attempt + 1);
    }

    int trackedEngineCount() {
        return engines.size();
    }

    private static void closeUnpublished(Engine created) {
        try {
            created.close();
        } catch (RuntimeException e) {
            // Retry with a fresh engine; this one was never published.
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T convert(Object value, Class<T> resultType, CamelContext camelContext, Exchange exchange) {
        if (resultType == null || resultType == Object.class) {
            return (T) value;
        }
        if (value == null) {
            return null;
        }
        if (resultType.isInstance(value)) {
            return resultType.cast(value);
        }
        if (camelContext != null) {
            if (exchange != null) {
                return camelContext.getTypeConverter().convertTo(resultType, exchange, value);
            }
            return camelContext.getTypeConverter().convertTo(resultType, value);
        }
        return resultType.cast(value);
    }

    private static final class EngineState {
        private final int generation;
        private final Engine engine;
        private final ByteArrayOutputStream stderr;

        private EngineState(int generation, Engine engine, ByteArrayOutputStream stderr) {
            this.generation = generation;
            this.engine = engine;
            this.stderr = stderr;
        }
    }
}
