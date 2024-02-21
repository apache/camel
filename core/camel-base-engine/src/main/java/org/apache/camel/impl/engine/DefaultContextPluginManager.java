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
package org.apache.camel.impl.engine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PluginManager;

public class DefaultContextPluginManager implements PluginManager {

    private final Map<Class<?>, Object> extensions = new ConcurrentHashMap<>();

    @Override
    public <T> T getContextPlugin(Class<T> type) {
        // lookup by direct implementation
        Object extension = extensions.get(type);
        if (extension == null) {
            // fallback and lookup via interfaces
            for (Object e : extensions.values()) {
                if (type.isInstance(e)) {
                    return type.cast(e);
                }
            }
        }
        if (extension instanceof Supplier) {
            extension = ((Supplier) extension).get();
            addContextPlugin(type, (T) extension);
        }
        return (T) extension;
    }

    @Override
    public <T> void addContextPlugin(Class<T> type, T module) {
        if (module != null) {
            try {
                extensions.put(type, module);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    public <T> void lazyAddContextPlugin(Class<T> type, Supplier<T> module) {
        if (module != null) {
            extensions.put(type, module);
        }
    }
}
