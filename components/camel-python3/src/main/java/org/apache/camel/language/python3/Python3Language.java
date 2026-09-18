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
package org.apache.camel.language.python3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExpressionEvaluationException;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.Predicate;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.Service;
import org.apache.camel.spi.ScriptingLanguage;
import org.apache.camel.spi.annotations.Language;
import org.apache.camel.support.LRUCacheFactory;
import org.apache.camel.support.TypedLanguageSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

/**
 * Camel expression language for Python 3 via <a href="https://www.graalvm.org/python/">GraalPy</a>.
 *
 * <p>
 * Default scripts see only data bindings: {@code body}, {@code headers}, {@code properties}, and {@code exchangeId}.
 * {@code exchange}, {@code message}, and {@code context} are intentionally absent so they resolve as Python
 * {@code NameError} rather than opaque objects with no usable API. Binding them by default would also become a
 * privilege escalation if host access were later widened.
 * </p>
 *
 * <p>
 * By default, Python may index Java maps and lists (so {@code headers['foo']} works) but cannot invoke methods on host
 * objects. To allow host method calls on Exchange/Message/CamelContext, bind a language created with
 * {@link #createWithHostAccess()} before first use:
 * </p>
 *
 * <pre>
 * Python3Language python3 = Python3Language.createWithHostAccess();
 * camelContext.getRegistry().bind("python3", python3);
 * </pre>
 */
@Language("python3")
public class Python3Language extends TypedLanguageSupport implements ScriptingLanguage, Service {

    private final HostAccess hostAccess;
    /**
     * When true, also bind {@code exchange}, {@code message}, and {@code context}. Only {@link #createWithHostAccess()}
     * sets this; default mode keeps those names undefined.
     */
    private final boolean bindCamelHostObjects;
    private final Map<String, Source> sourceCache = LRUCacheFactory.newLRUSoftCache(16, 1000, true);
    private final Lock engineLock = new ReentrantLock();
    private volatile Engine engine;

    public Python3Language() {
        this(Python3Helper.defaultHostAccess(), false);
    }

    public Python3Language(HostAccess hostAccess) {
        this(hostAccess, false);
    }

    private Python3Language(HostAccess hostAccess, boolean bindCamelHostObjects) {
        this.hostAccess = hostAccess;
        this.bindCamelHostObjects = bindCamelHostObjects;
    }

    /**
     * Creates a separate language instance for trusted scripts. Uses {@link HostAccess#ALL} so Python may call public
     * methods and fields on bound host objects, and additionally exposes {@code exchange}, {@code message}, and
     * {@code context}.
     * <p>
     * This is an explicit opt-in: {@code HostAccess.ALL} is not a sandbox. It does not enable {@code allowAllAccess},
     * Java class lookup, host IO, or process creation. Use only when you trust the scripts.
     * </p>
     */
    public static Python3Language createWithHostAccess() {
        return new Python3Language(HostAccess.ALL, true);
    }

    /**
     * Helper for use in the Java route DSL, e.g. {@code .filter(Python3Language.python3("body == 'Hello'"))}.
     */
    public static Python3Expression python3(String script) {
        return new Python3Expression(script);
    }

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
        return createPython3Expression(expression);
    }

    @Override
    public Expression createExpression(String expression) {
        return createPython3Expression(expression);
    }

    private Python3Expression createPython3Expression(String expression) {
        return new Python3Expression(loadResource(expression), this);
    }

    @Override
    public <T> T evaluate(String script, Map<String, Object> bindings, Class<T> resultType) {
        script = loadResource(script);
        try (Context cx = Python3Helper.newContext(engine(), hostAccess)) {
            if (bindings != null) {
                Value b = cx.getBindings("python");
                bindings.forEach(b::putMember);
            }
            Value value = cx.eval(source(script));
            return convert(value, resultType, getCamelContext(), null);
        } catch (Exception e) {
            throw wrapFailure(script, null, e);
        }
    }

    Object evaluateExpression(String script, Exchange exchange) {
        try (Context cx = Python3Helper.newContext(engine(), hostAccess)) {
            Value b = cx.getBindings("python");
            // Default: data only. Do not bind exchange/message/context — they are undefined (NameError)
            // unless createWithHostAccess() opted into trusted host-object bindings.
            b.putMember("exchangeId", exchange.getExchangeId());
            b.putMember("headers", exchange.getMessage().getHeaders());
            b.putMember("properties", exchange.getAllProperties());
            b.putMember("body", exchange.getMessage().getBody());
            if (bindCamelHostObjects) {
                b.putMember("exchange", exchange);
                b.putMember("message", exchange.getMessage());
                b.putMember("context", exchange.getContext());
            }
            Value value = cx.eval(source(script));
            return convert(value, Object.class, exchange.getContext(), exchange);
        } catch (Exception e) {
            throw wrapFailure(script, exchange, e);
        }
    }

    /**
     * Resource load failures and Python parse errors are syntax problems. Runtime errors (including host-access
     * denials) are evaluation failures, matching groovy/javascript rather than wrapping everything as illegal syntax.
     */
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
            if (current instanceof PolyglotException pe && pe.isSyntaxError()) {
                return true;
            }
        }
        return false;
    }

    private Engine engine() {
        Engine existing = engine;
        if (existing != null) {
            return existing;
        }
        engineLock.lock();
        try {
            if (engine == null) {
                engine = Python3Helper.newEngine();
            }
            return engine;
        } finally {
            engineLock.unlock();
        }
    }

    private Source source(String script) {
        Source cached = sourceCache.get(script);
        if (cached != null) {
            return cached;
        }
        Source created = Source.newBuilder("python", script, "camel-python3").buildLiteral();
        sourceCache.put(script, created);
        return created;
    }

    /**
     * Converts a GraalPy {@link Value} to a Context-independent Java object while the Context is still open, then
     * applies Camel type conversion for {@code resultType}.
     */
    @SuppressWarnings("unchecked")
    static <T> T convert(Value value, Class<T> resultType, CamelContext camelContext, Exchange exchange) {
        Object obj = materialize(value, new HashMap<>());
        if (resultType == null || resultType == Object.class) {
            return (T) obj;
        }
        if (obj == null) {
            return null;
        }
        if (resultType.isInstance(obj)) {
            return resultType.cast(obj);
        }
        if (camelContext != null) {
            if (exchange != null) {
                return camelContext.getTypeConverter().convertTo(resultType, exchange, obj);
            }
            return camelContext.getTypeConverter().convertTo(resultType, obj);
        }
        return resultType.cast(obj);
    }

    /**
     * Copies guest values into ordinary Java types so the result remains usable after {@link Context#close()}.
     * <p>
     * Python lists and tuples become {@link List}, dicts become {@link Map}, and sets become {@link Set}. Nested values
     * are copied recursively. Other guest objects (including custom types) are left as {@link Value#as(Class)
     * value.as(Object.class)}.
     * <p>
     * GraalPy returns the {@code __main__} module (not {@link Value#isNull()}) for {@code None}, {@code pass}, and
     * assignment-only scripts; that is treated as Java {@code null}.
     */
    private static Object materialize(Value value, Map<Value, Object> seen) {
        if (value == null || value.isNull()) {
            return null;
        }
        Object existing = seen.get(value);
        if (existing != null) {
            return existing;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInInt()) {
                return value.asInt();
            }
            if (value.fitsInLong()) {
                return value.asLong();
            }
            if (value.fitsInDouble()) {
                return value.asDouble();
            }
            return value.as(Object.class);
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.isHostObject()) {
            return value.asHostObject();
        }
        if (isMainModule(value)) {
            return null;
        }
        if (isPythonSet(value)) {
            Set<Object> set = new LinkedHashSet<>();
            seen.put(value, set);
            materializeIterator(value.getIterator(), seen, set);
            return set;
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
                Object key = materialize(entry.getArrayElement(0), seen);
                Object val = materialize(entry.getArrayElement(1), seen);
                map.put(key, val);
            }
            return map;
        }
        if (isPythonTuple(value) && value.hasIterator()) {
            List<Object> list = new ArrayList<>();
            seen.put(value, list);
            materializeIterator(value.getIterator(), seen, list);
            return list;
        }
        return value.as(Object.class);
    }

    private static void materializeIterator(Value iterator, Map<Value, Object> seen, Collection<Object> target) {
        while (iterator.hasIteratorNextElement()) {
            target.add(materialize(iterator.getIteratorNextElement(), seen));
        }
    }

    private static boolean isPythonSet(Value value) {
        return isPythonType(value, "set") || isPythonType(value, "frozenset");
    }

    private static boolean isPythonTuple(Value value) {
        return isPythonType(value, "tuple");
    }

    private static boolean isPythonType(Value value, String simpleName) {
        Value meta = value.getMetaObject();
        if (meta == null || !meta.isMetaObject()) {
            return false;
        }
        try {
            return simpleName.equals(meta.getMetaSimpleName());
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * GraalPy {@code Context.eval} of {@code None}, {@code pass}, or a statement with no result yields the guest
     * {@code __main__} module rather than {@link Value#isNull()}. Distinguish that from a real dict/list/object by
     * requiring module members {@code __name__ == "__main__"} and {@code __spec__}, and by excluding values that are
     * numbers, strings, booleans, host objects, arrays, or hash maps.
     */
    private static boolean isMainModule(Value value) {
        if (value.hasArrayElements() || value.hasHashEntries() || !value.hasMembers() || !value.hasMember("__name__")
                || !value.hasMember("__spec__")) {
            return false;
        }
        Value name = value.getMember("__name__");
        return name != null && name.isString() && "__main__".equals(name.asString());
    }
}
