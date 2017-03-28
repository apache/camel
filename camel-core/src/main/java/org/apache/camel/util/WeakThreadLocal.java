/**
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
package org.apache.camel.util;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

public final class WeakThreadLocal<T> implements Supplier<T> {
    private final ThreadLocal<WeakReference<T>> cache;
    private final Supplier<T> supplier;

    public WeakThreadLocal(Supplier<T> supplier) {
        this.cache = new ThreadLocal<>();
        this.supplier = ObjectHelper.notNull(supplier, "value supplier");
    }

    @Override
    public T get() {
        T value = null;
        WeakReference<T> ref = cache.get();

        if (ref != null) {
            value = ref.get();
        }

        if (value == null) {
            value = supplier.get();
            cache.set(new WeakReference<>(value));
        }

        return value;
    }

    public static <T> WeakThreadLocal<T> withSupplier(Supplier<T> supplier) {
        return new WeakThreadLocal<>(supplier);
    }
}
