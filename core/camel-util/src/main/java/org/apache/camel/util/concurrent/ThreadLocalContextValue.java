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
 * ThreadLocal-based implementation of {@link ContextValue}.
 * <p>
 * This is the default implementation used on JDK 17-24, and also used on JDK 25+ when virtual threads are not enabled
 * or when a ThreadLocal is explicitly requested via {@link ContextValue#newThreadLocal(String)}.
 */
class ThreadLocalContextValue<T> implements ContextValue<T> {
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
