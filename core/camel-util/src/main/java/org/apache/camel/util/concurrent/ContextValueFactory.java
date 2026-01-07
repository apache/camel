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

import java.util.function.Supplier;

/**
 * Factory for creating {@link ContextValue} instances.
 * <p>
 * This class is package-private and used internally by {@link ContextValue}. The implementation is overridden in Java
 * 21+ to use ScopedValue when appropriate.
 */
class ContextValueFactory {

    /**
     * Creates a new context value with the given name.
     * <p>
     * This base implementation always uses ThreadLocal. The Java 21+ version may use ScopedValue when virtual threads
     * are enabled.
     */
    static <T> ContextValue<T> newInstance(String name) {
        return new ThreadLocalContextValue<>(name);
    }

    /**
     * Creates a new ThreadLocal-based context value with the given name.
     * <p>
     * This always uses ThreadLocal, regardless of JDK version.
     */
    static <T> ContextValue<T> newThreadLocal(String name) {
        return new ThreadLocalContextValue<>(name);
    }

    /**
     * Creates a new ThreadLocal-based context value with the given name and initial value supplier.
     * <p>
     * This always uses ThreadLocal, regardless of JDK version.
     */
    static <T> ContextValue<T> newThreadLocal(String name, Supplier<T> supplier) {
        return new ThreadLocalContextValue<>(name, supplier);
    }

    /**
     * Executes the given operation with the context value bound to the specified value.
     */
    static <T, R> R where(ContextValue<T> key, T value, Supplier<R> operation) {
        if (key instanceof ThreadLocalContextValue<T> tlKey) {
            T oldValue = tlKey.get();
            try {
                tlKey.set(value);
                return operation.get();
            } finally {
                if (oldValue != null) {
                    tlKey.set(oldValue);
                } else {
                    tlKey.remove();
                }
            }
        }
        throw new IllegalArgumentException("Unsupported ContextValue type: " + key.getClass());
    }

    /**
     * Executes the given operation with the context value bound to the specified value.
     */
    static <T> void where(ContextValue<T> key, T value, Runnable operation) {
        where(key, value, () -> {
            operation.run();
            return null;
        });
    }

    /**
     * ThreadLocal-based implementation of ContextValue.
     */
    static class ThreadLocalContextValue<T> implements ContextValue<T> {
        private final String name;
        private final ThreadLocal<T> threadLocal;

        ThreadLocalContextValue(String name) {
            this.name = name;
            this.threadLocal = new ThreadLocal<>();
        }

        ThreadLocalContextValue(String name, Supplier<T> supplier) {
            this.name = name;
            this.threadLocal = ThreadLocal.withInitial(supplier);
        }

        @Override
        public T get() {
            return threadLocal.get();
        }

        @Override
        public T orElse(T defaultValue) {
            T value = threadLocal.get();
            return value != null ? value : defaultValue;
        }

        @Override
        public boolean isBound() {
            return threadLocal.get() != null;
        }

        @Override
        public void set(T value) {
            threadLocal.set(value);
        }

        @Override
        public void remove() {
            threadLocal.remove();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String toString() {
            return "ContextValue[" + name + "]";
        }
    }
}
