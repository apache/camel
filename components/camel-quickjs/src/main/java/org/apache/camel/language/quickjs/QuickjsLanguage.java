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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.GuestException;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.Service;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.TypedLanguageSupport;
import run.endive.runtime.Memory;

/**
 * Camel expression language for JavaScript via <a href="https://github.com/roastedroot/quickjs4j">QuickJS4J</a>.
 *
 * <p>
 * Scripts see JSON-serializable data bindings: {@code body}, {@code headers}, {@code properties}, {@code exchangeId},
 * {@code variables} and {@code exception}, plus the controlled {@code camel} API ({@code camel.getHeader(name)},
 * {@code camel.setHeader(name, value)}, {@code camel.setBody(value)}, ...) that reads and writes the current
 * {@code Exchange} through host functions. Live {@code Exchange}, {@code Message}, and {@code CamelContext} objects are
 * never bound, so {@code exchange.getMessage()} is a JavaScript {@code ReferenceError} rather than Java interop.
 * </p>
 * <p>
 * Every worker thread owns one QuickJS engine, and each engine keeps a bounded cache of compiled scripts, so a route
 * expression is compiled once per thread and then only executed.
 * </p>
 */
@Language("quickjs")
public class QuickjsLanguage extends TypedLanguageSupport implements ScriptingLanguage, Service {

    /**
     * Compiled scripts kept per engine (per worker thread), least recently used first out.
     */
    static final int COMPILED_SCRIPTS_PER_ENGINE = 1000;

    /**
     * Every evaluation executes a module in the QuickJS runtime, and QuickJS keeps evaluated modules until its context
     * is freed, so an engine grows with every evaluation (~12 KB each measured with camel-quickjs 4.23). An engine is
     * therefore recycled once its WebAssembly memory exceeds {@link #getEngineMaxMemory()} or it has run
     * {@link #getEngineMaxEvaluations()} evaluations: it is closed and the thread creates a fresh one, which recompiles
     * its scripts on demand.
     */
    private volatile long engineMaxMemory = 64L * 1024 * 1024;
    private volatile int engineMaxEvaluations = 50_000;

    private final AtomicInteger generation = new AtomicInteger();
    private final ConcurrentLinkedQueue<Engine> engines = new ConcurrentLinkedQueue<>();
    private final ThreadLocal<EngineState> engine = new ThreadLocal<>();
    private final ThreadLocal<Exchange> currentExchange = new ThreadLocal<>();
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
     * {@code headers}, {@code properties}, {@code exchangeId}, {@code variables} and {@code exception}. There is no
     * current exchange, so the {@code camel} API is not usable from this entry point.
     */
    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        Map<String, Object> jsonBindings = QuickjsHelper.toJsonCompatibleBindings(bindings, null);
        EngineState state = currentEngine();
        state.stderr.reset();
        Object result;
        boolean discarded = false;
        try {
            result = state.engine.invokeGuestFunction(
                    QuickjsHelper.MODULE_NAME,
                    QuickjsHelper.FUNCTION_NAME,
                    List.of(jsonBindings, script),
                    QuickjsHelper.EVAL_WRAPPER);
        } catch (Exception e) {
            discarded = discardIfPoisoned(state, e);
            throw QuickjsHelper.wrapFailure(script, null, e);
        } finally {
            state.stderr.reset();
            if (!discarded && state.exhausted(engineMaxMemory, engineMaxEvaluations)) {
                discard(state);
            }
        }
        return convert(result, resultType, getCamelContext(), null);
    }

    Object evaluateExpression(String script, Exchange exchange) {
        Exchange previous = currentExchange.get();
        currentExchange.set(exchange);
        try {
            Map<String, Object> bindings = QuickjsHelper.exchangeBindings(exchange);
            EngineState state = currentEngine();
            Object result;
            boolean discarded = false;
            try {
                byte[] compiled = state.compiled(script);
                state.stderr.reset();
                result = state.engine.invokePrecompiledGuestFunction(
                        QuickjsHelper.MODULE_NAME,
                        QuickjsHelper.FUNCTION_NAME,
                        List.of(bindings, ""),
                        compiled);
            } catch (Exception e) {
                discarded = discardIfPoisoned(state, e);
                throw QuickjsHelper.wrapFailure(script, exchange, e);
            } finally {
                // Drop this evaluation's WASI stderr so a reused Engine cannot accumulate it.
                state.stderr.reset();
                // a script that throws has still evaluated (and QuickJS kept) its module: count it as well
                if (!discarded && state.exhausted(engineMaxMemory, engineMaxEvaluations)) {
                    discard(state);
                }
            }
            return convert(result, Object.class, exchange.getContext(), exchange);
        } finally {
            if (previous != null) {
                currentExchange.set(previous);
            } else {
                currentExchange.remove();
            }
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
        Memory[] memory = new Memory[1];
        Engine created = QuickjsHelper.newEngine(stderr, currentExchange::get, memory);
        if (generation.get() != gen) {
            closeUnpublished(created);
            return currentEngine(attempt + 1);
        }
        engineLock.lock();
        try {
            if (generation.get() == gen) {
                engines.add(created);
                state = new EngineState(gen, created, stderr, memory[0]);
                engine.set(state);
                return state;
            }
        } finally {
            engineLock.unlock();
        }
        closeUnpublished(created);
        return currentEngine(attempt + 1);
    }

    /**
     * A JavaScript exception ({@link GuestException}) and a script that does not parse
     * ({@link ExpressionIllegalSyntaxException}) leave the QuickJS runtime usable. Anything else (a host function that
     * threw, a stack overflow, a compile that fails for another reason) is a trap inside the WebAssembly instance,
     * after which the next compile panics with "RefCell already borrowed": the engine is dropped and this thread gets a
     * fresh one on its next evaluation.
     */
    private boolean discardIfPoisoned(EngineState state, Exception e) {
        if (e instanceof GuestException || e instanceof ExpressionIllegalSyntaxException) {
            return false;
        }
        discard(state);
        return true;
    }

    /**
     * Closes the calling thread's engine; the next evaluation on this thread creates a fresh one.
     */
    private void discard(EngineState state) {
        if (engine.get() == state) {
            engine.remove();
        }
        // stop() may have polled this engine off the queue already and closed it: only the remover closes
        if (engines.remove(state.engine)) {
            closeUnpublished(state.engine);
        }
    }

    int trackedEngineCount() {
        return engines.size();
    }

    /**
     * WebAssembly memory of the calling thread's engine, in bytes (for tests).
     */
    long engineMemory() {
        EngineState state = engine.get();
        return state == null ? 0 : state.memoryBytes();
    }

    public long getEngineMaxMemory() {
        return engineMaxMemory;
    }

    /**
     * Recycle a worker thread's engine once its WebAssembly memory exceeds this many bytes (default 64 MB).
     */
    public void setEngineMaxMemory(long engineMaxMemory) {
        this.engineMaxMemory = engineMaxMemory;
    }

    public int getEngineMaxEvaluations() {
        return engineMaxEvaluations;
    }

    /**
     * Recycle a worker thread's engine after this many evaluations (default 50,000).
     */
    public void setEngineMaxEvaluations(int engineMaxEvaluations) {
        this.engineMaxEvaluations = engineMaxEvaluations;
    }

    /**
     * Number of compiled scripts cached by the engine of the calling thread (for tests).
     */
    int compiledScriptCount() {
        EngineState state = engine.get();
        return state == null ? 0 : state.compiled.size();
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

    /**
     * One engine and its compiled scripts; only ever used by the thread that created it.
     */
    private static final class EngineState {
        private final int generation;
        private final Engine engine;
        private final ByteArrayOutputStream stderr;
        private final Memory memory;
        private int evaluations;
        private final Map<String, byte[]> compiled = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, byte[]> eldest) {
                return size() > COMPILED_SCRIPTS_PER_ENGINE;
            }
        };

        private EngineState(int generation, Engine engine, ByteArrayOutputStream stderr, Memory memory) {
            this.generation = generation;
            this.engine = engine;
            this.stderr = stderr;
            this.memory = memory;
        }

        long memoryBytes() {
            return memory == null ? 0 : (long) memory.pages() * Memory.PAGE_SIZE;
        }

        boolean exhausted(long maxMemory, int maxEvaluations) {
            evaluations++;
            return evaluations >= maxEvaluations || memoryBytes() > maxMemory;
        }

        byte[] compiled(String script) {
            byte[] code = compiled.get(script);
            if (code == null) {
                code = QuickjsHelper.compileScript(engine, stderr, script);
                compiled.put(script, code);
            }
            return code;
        }
    }
}
