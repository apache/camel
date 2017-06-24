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
package org.apache.camel.impl.converter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.util.ObjectHelper;

/**
 * Lazy implementation of a type converter registry used for
 * <a href="http://camel.apache.org/type-converter.html">type converters</a> in Camel.
 * <p/>
 * This implementation will lazy load type converters on-demand.
 *
 * @version
 * @deprecated will be removed in a future Camel release.
 */
@Deprecated
public class LazyLoadingTypeConverter extends BaseTypeConverterRegistry {
    private final AtomicBoolean loaded = new AtomicBoolean();

    public LazyLoadingTypeConverter(PackageScanClassResolver resolver, Injector injector, FactoryFinder factoryFinder) {
        super(resolver, injector, factoryFinder);
    }

    @Override
    public boolean allowNull() {
        return false;
    }

    @Override
    protected Object doConvertTo(final Class<?> type, final Exchange exchange, final Object value, boolean tryConvert) throws Exception {
        Object answer = super.doConvertTo(type, exchange, value, tryConvert);
        if (answer == null && !loaded.get()) {
            // okay we could not convert, so try again, but load the converters up front
            ensureLoaded();
            answer = super.doConvertTo(type, exchange, value, tryConvert);
        }
        return answer;
    }

    @Override
    public TypeConverter getTypeConverter(Class<?> toType, Class<?> fromType) {
        TypeConverter answer = super.getTypeConverter(toType, fromType);
        if (answer == null && !loaded.get()) {
            // okay we could not convert, so try again, but load the converters up front
            ensureLoaded();
            answer = super.getTypeConverter(toType, fromType);
        }
        return answer;
    }

    @Override
    public Set<Class<?>> getFromClassMappings() {
        if (!loaded.get()) {
            ensureLoaded();
        }
        return super.getFromClassMappings();
    }

    @Override
    public Map<Class<?>, TypeConverter> getToClassMappings(Class<?> fromClass) {
        if (!loaded.get()) {
            ensureLoaded();
        }
        return super.getToClassMappings(fromClass);
    }

    @Override
    public Map<TypeMapping, TypeConverter> getTypeMappings() {
        if (!loaded.get()) {
            ensureLoaded();
        }
        return super.getTypeMappings();
    }

    @Override
    protected TypeConverter doLookup(Class<?> toType, Class<?> fromType, boolean isSuper) {
        TypeConverter answer = super.doLookup(toType, fromType, isSuper);
        if (answer == null && !loaded.get()) {
            // okay we could not convert, so try again, but load the converters up front
            ensureLoaded();
            answer = super.doLookup(toType, fromType, isSuper);
        }
        return answer;
    }

    private synchronized void ensureLoaded() {
        if (loaded.compareAndSet(false, true)) {
            try {
                super.loadTypeConverters();
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // must load core type converters
        loadCoreTypeConverters();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // reset loaded flag
        loaded.set(false);
    }
}

