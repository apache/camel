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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Expression;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.TypedLanguageSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Camel expression language for JavaScript via <a href="https://www.graalvm.org/javascript/">GraalJS</a>.
 * <p>
 * One {@link Engine} is shared by all evaluations of a language instance so parsed and compiled scripts are reused; a
 * fresh {@link Context} is still created per evaluation so scripts stay isolated from each other. The engine is created
 * when the language is started (a language is started as soon as the {@code CamelContext} resolves it, so the GraalJS
 * engine build happens at route startup rather than on the first message) and closed when it is stopped; the
 * {@code engine()} accessor also builds it on demand for an expression used before start.
 */
@Language("js")
public class JavaScriptLanguage extends TypedLanguageSupport implements ScriptingLanguage, Service {

    // Source is not a Service, so nothing is stopped on eviction
    private final Map<String, Source> sourceCache = LRUCacheFactory.newLRUSoftCache(16, 1000, false);
    private final Lock engineLock = new ReentrantLock();
    private volatile Engine engine;

    @Override
    public void start() {
        engine();
    }

    @Override
    public void stop() {
        sourceCache.clear();
        Engine toClose;
        engineLock.lock();
        try {
            toClose = engine;
            engine = null;
        } finally {
            engineLock.unlock();
        }
        if (toClose != null) {
            toClose.close();
        }
    }

    @Override
    public Predicate createPredicate(String expression) {
        return createJavaScriptExpression(expression, Boolean.class);
    }

    @Override
    public Expression createExpression(String expression) {
        return createJavaScriptExpression(expression, Object.class);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        try (Context cx = newContext()) {
            if (bindings != null) {
                Value b = cx.getBindings("js");
                bindings.forEach(b::putMember);
            }
            Value o = cx.eval(source(script));
            Object answer = materialize(o);
            if (answer == null || resultType == Object.class || resultType.isInstance(answer)) {
                return resultType.cast(answer);
            }
            if (getCamelContext() != null) {
                try {
                    // fail loudly on an impossible conversion, as o.as(resultType) did before the result was materialized
                    return getCamelContext().getTypeConverter().mandatoryConvertTo(resultType, answer);
                } catch (NoTypeConversionAvailableException e) {
                    throw RuntimeCamelException.wrapRuntimeCamelException(e);
                }
            }
            return resultType.cast(o.as(resultType));
        }
    }

    /**
     * Copies a guest {@link Value} into ordinary Java types so the result remains usable after the per-evaluation
     * {@link Context} is closed: JS arrays become {@link List}, JS objects and {@code Map} become {@link Map}, JS
     * {@code Set} becomes {@link Set}, and {@code Date} becomes {@link java.time.Instant}. Nested values are copied
     * recursively. Primitives, strings and host objects are returned as before; other guest objects such as functions
     * are left to {@link Value#as(Class) value.as(Object.class)}.
     */
    static Object materialize(Value value) {
        return materialize(value, new HashMap<>());
    }

    private static Object materialize(Value value, Map<Value, Object> seen) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (value.isProxyObject()) {
            return value.asProxyObject();
        }
        if (value.isBoolean() || value.isNumber() || value.isString() || value.canExecute()) {
            return value.as(Object.class);
        }
        if (value.isInstant()) {
            return value.asInstant();
        }
        Object existing = seen.get(value);
        if (existing != null) {
            return existing;
        }
        if (value.hasArrayElements()) {
            int size = Math.toIntExact(value.getArraySize());
            List<Object> list = new ArrayList<>(size);
            seen.put(value, list);
            for (int i = 0; i < size; i++) {
                list.add(materialize(value.getArrayElement(i), seen));
            }
            return list;
        }
        if (value.hasHashEntries()) {
            Map<Object, Object> map = new LinkedHashMap<>();
            seen.put(value, map);
            Value entries = value.getHashEntriesIterator();
            while (entries.hasIteratorNextElement()) {
                Value entry = entries.getIteratorNextElement();
                map.put(materialize(entry.getArrayElement(0), seen), materialize(entry.getArrayElement(1), seen));
            }
            return map;
        }
        if (value.hasIterator() && isJsSet(value)) {
            Set<Object> set = new LinkedHashSet<>();
            seen.put(value, set);
            Value iterator = value.getIterator();
            while (iterator.hasIteratorNextElement()) {
                set.add(materialize(iterator.getIteratorNextElement(), seen));
            }
            return set;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new LinkedHashMap<>();
            seen.put(value, map);
            for (String key : value.getMemberKeys()) {
                map.put(key, materialize(value.getMember(key), seen));
            }
            return map;
        }
        return value.as(Object.class);
    }

    /**
     * Whether the value is a JavaScript {@code Set}. The polyglot API has no type test for it, so this is a heuristic
     * on the meta object's simple name: a script-defined {@code class Set} matches too, and a {@code WeakSet} or a
     * subclass does not (they are then materialized as a list of their iterator).
     */
    private static boolean isJsSet(Value value) {
        Value meta = value.getMetaObject();
        if (meta == null || !meta.isMetaObject()) {
            return false;
        }
        try {
            return "Set".equals(meta.getMetaSimpleName());
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * Creates a per-evaluation {@link Context} backed by the shared {@link Engine}.
     */
    Context newContext() {
        return JavaScriptHelper.newContext(engine());
    }

    /**
     * Returns the {@link Source} for the script text, building it once per distinct script so the shared engine can
     * reuse its parsed and compiled form across contexts.
     */
    Source source(String script) {
        Source cached = sourceCache.get(script);
        if (cached != null) {
            return cached;
        }
        Source created = Source.newBuilder("js", script, "Unnamed")
                .mimeType("application/javascript+module").buildLiteral();
        sourceCache.put(script, created);
        return created;
    }

    private Engine engine() {
        Engine existing = engine;
        if (existing != null) {
            return existing;
        }
        engineLock.lock();
        try {
            if (engine == null) {
                engine = JavaScriptHelper.newEngine();
            }
            return engine;
        } finally {
            engineLock.unlock();
        }
    }

    /**
     * @param  expression the expression to evaluate
     * @param  type       the type of the result
     * @return            the corresponding {@code JavaScriptExpression}
     */
    private JavaScriptExpression createJavaScriptExpression(String expression, Class<?> type) {
        return new JavaScriptExpression(loadResource(expression), type, this);
    }
}
