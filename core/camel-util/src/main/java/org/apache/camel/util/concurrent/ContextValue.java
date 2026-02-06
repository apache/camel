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
package org.apache.camel.util.concurrent;

import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * A context value abstraction that provides thread-scoped data sharing.
 * <p>
 * This interface provides a unified API for sharing data within a thread context, with implementations that use either
 * {@link ThreadLocal} (for JDK 17+) or {@link java.lang.ScopedValue} (for JDK 21+ with virtual threads).
 * <p>
 * The implementation is chosen automatically based on the JDK version and whether virtual threads are enabled via the
 * {@code camel.threads.virtual.enabled} system property.
 * <p>
 * <b>Usage patterns:</b>
 * <ul>
 * <li><b>Read-only context passing:</b> Use {@link #where(ContextValue, Object, Runnable)} to bind a value for the
 * duration of a code block</li>
 * <li><b>Mutable state:</b> Use {@link #newThreadLocal(String)} for state that needs to be modified after
 * initialization</li>
 * </ul>
 * <p>
 * <b>Important:</b> When using {@link #newThreadLocal(String, Supplier)}, the values should be <b>lightweight
 * objects</b>. Heavy objects stored in ThreadLocal can lead to memory leaks (if threads are pooled) and increased
 * memory consumption (one instance per thread). Consider whether the object truly needs per-thread state, or if it can
 * be shared or passed as a parameter instead.
 * <p>
 * <b>Example:</b>
 *
 * <pre>{@code
 * private static final ContextValue<String> ROUTE_ID = ContextValue.newInstance("routeId");
 *
 * // Bind a value for a scope
 * ContextValue.where(ROUTE_ID, "myRoute", () -> {
 *     // Code here can access ROUTE_ID.get()
 *     processRoute();
 * });
 * }</pre>
 *
 * @param <T> the type of value stored in this context
 * @see       java.lang.ThreadLocal
 * @see       java.lang.ScopedValue
 */
public interface ContextValue<T> {

    /**
     * Returns the value of this context variable for the current thread.
     * <p>
     * For ScopedValue-based implementations (JDK 21+), this will throw {@link NoSuchElementException} if called outside
     * a binding scope. For ThreadLocal-based implementations, this returns the value set via {@link #set(Object)} or
     * {@code null} if not set.
     *
     * @return                        the current value
     * @throws NoSuchElementException if no value is bound (ScopedValue implementation only)
     */
    T get();

    /**
     * Returns the value of this context variable for the current thread, or the given default value if no value is
     * bound.
     *
     * @param  defaultValue the value to return if no value is bound
     * @return              the current value, or {@code defaultValue} if not bound
     */
    T orElse(T defaultValue);

    /**
     * Returns whether a value is currently bound for this context variable.
     *
     * @return {@code true} if a value is bound, {@code false} otherwise
     */
    boolean isBound();

    /**
     * Sets the value for this context variable (ThreadLocal-based implementations only).
     * <p>
     * This method is only supported by ThreadLocal-based implementations. For ScopedValue-based implementations, use
     * {@link #where(ContextValue, Object, Runnable)} instead.
     *
     * @param  value                         the value to set
     * @throws UnsupportedOperationException if called on a ScopedValue-based implementation
     */
    void set(T value);

    /**
     * Removes the value for this context variable (ThreadLocal-based implementations only).
     * <p>
     * This method is only supported by ThreadLocal-based implementations.
     *
     * @throws UnsupportedOperationException if called on a ScopedValue-based implementation
     */
    void remove();

    /**
     * Returns the name of this context value (for debugging purposes).
     *
     * @return the name
     */
    String name();

    /**
     * Creates a new context value with the given name.
     * <p>
     * The implementation will use ScopedValue on JDK 21+ when virtual threads are enabled, otherwise it will use
     * ThreadLocal.
     *
     * @param  <T>  the type of value
     * @param  name the name for debugging purposes
     * @return      a new context value
     */
    static <T> ContextValue<T> newInstance(String name) {
        return ContextValueFactory.newInstance(name);
    }

    /**
     * Creates a new ThreadLocal-based context value with the given name.
     * <p>
     * This always uses ThreadLocal, regardless of JDK version or virtual thread settings. Use this when you need
     * mutable state that can be modified after initialization.
     *
     * @param  <T>  the type of value
     * @param  name the name for debugging purposes
     * @return      a new ThreadLocal-based context value
     */
    static <T> ContextValue<T> newThreadLocal(String name) {
        return ContextValueFactory.newThreadLocal(name);
    }

    /**
     * Creates a new ThreadLocal-based context value with the given name and initial value supplier.
     * <p>
     * This always uses ThreadLocal regardless of JDK version or virtual thread settings. The supplier is called to
     * provide the initial value when {@link #get()} is called and no value has been set.
     *
     * @param  <T>      the type of value
     * @param  name     the name for debugging purposes
     * @param  supplier the supplier for the initial value
     * @return          a new ThreadLocal-based context value with initial value support
     */
    static <T> ContextValue<T> newThreadLocal(String name, Supplier<T> supplier) {
        return ContextValueFactory.newThreadLocal(name, supplier);
    }

    /**
     * Executes the given operation with the context value bound to the specified value.
     * <p>
     * The binding is only visible to the current thread and threads created within the operation (for ScopedValue
     * implementations).
     *
     * @param  <T>       the type of value
     * @param  <R>       the return type
     * @param  key       the context value to bind
     * @param  value     the value to bind
     * @param  operation the operation to execute
     * @return           the result of the operation
     */
    static <T, R> R where(ContextValue<T> key, T value, Supplier<R> operation) {
        return ContextValueFactory.where(key, value, operation);
    }

    /**
     * Executes the given operation with the context value bound to the specified value.
     *
     * @param <T>       the type of value
     * @param key       the context value to bind
     * @param value     the value to bind
     * @param operation the operation to execute
     */
    static <T> void where(ContextValue<T> key, T value, Runnable operation) {
        ContextValueFactory.where(key, value, operation);
    }
}
