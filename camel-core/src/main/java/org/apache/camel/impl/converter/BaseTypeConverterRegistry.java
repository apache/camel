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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.LRUSoftCache;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base implementation of a type converter registry used for
 * <a href="http://camel.apache.org/type-converter.html">type converters</a> in Camel.
 *
 * @version 
 */
public abstract class BaseTypeConverterRegistry extends ServiceSupport implements TypeConverter, TypeConverterRegistry {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    protected final ConcurrentMap<TypeMapping, TypeConverter> typeMappings = new ConcurrentHashMap<TypeMapping, TypeConverter>();
    // for misses use a soft reference cache map, as the classes may be un-deployed at runtime
    protected final LRUSoftCache<TypeMapping, TypeMapping> misses = new LRUSoftCache<TypeMapping, TypeMapping>(1000);
    protected final List<TypeConverterLoader> typeConverterLoaders = new ArrayList<TypeConverterLoader>();
    protected final List<FallbackTypeConverter> fallbackConverters = new CopyOnWriteArrayList<FallbackTypeConverter>();
    protected final PackageScanClassResolver resolver;
    protected Injector injector;
    protected final FactoryFinder factoryFinder;
    protected final PropertyEditorTypeConverter propertyEditorTypeConverter = new PropertyEditorTypeConverter();
    protected final Statistics statistics = new UtilizationStatistics();
    protected final AtomicLong attemptCounter = new AtomicLong();
    protected final AtomicLong missCounter = new AtomicLong();
    protected final AtomicLong hitCounter = new AtomicLong();
    protected final AtomicLong failedCounter = new AtomicLong();

    public BaseTypeConverterRegistry(PackageScanClassResolver resolver, Injector injector, FactoryFinder factoryFinder) {
        this.resolver = resolver;
        this.injector = injector;
        this.factoryFinder = factoryFinder;
        this.typeConverterLoaders.add(new AnnotationTypeConverterLoader(resolver));

        // add to string first as it will then be last in the last as to string can nearly
        // always convert something to a string so we want it only as the last resort
        // ToStringTypeConverter should NOT allow to be promoted
        addFallbackTypeConverter(new ToStringTypeConverter(), false);
        // do not assume property editor as it has a String converter
        addFallbackTypeConverter(propertyEditorTypeConverter, false);
        // enum is okay to be promoted
        addFallbackTypeConverter(new EnumTypeConverter(), true);
        // arrays is okay to be promoted
        addFallbackTypeConverter(new ArrayTypeConverter(), true);
        // and future should also not allowed to be promoted
        addFallbackTypeConverter(new FutureTypeConverter(this), false);
        // add sync processor to async processor converter is to be promoted
        addFallbackTypeConverter(new AsyncProcessorTypeConverter(), true);
    }

    public List<TypeConverterLoader> getTypeConverterLoaders() {
        return typeConverterLoaders;
    }

    @Override
    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        if (!isRunAllowed()) {
            throw new IllegalStateException(this + " is not started");
        }

        Object answer;
        try {
            attemptCounter.incrementAndGet();
            answer = doConvertTo(type, exchange, value, false);
        } catch (Exception e) {
            failedCounter.incrementAndGet();
            // if its a ExecutionException then we have rethrow it as its not due to failed conversion
            // this is special for FutureTypeConverter
            boolean execution = ObjectHelper.getException(ExecutionException.class, e) != null
                    || ObjectHelper.getException(CamelExecutionException.class, e) != null;
            if (execution) {
                throw ObjectHelper.wrapCamelExecutionException(exchange, e);
            }

            // error occurred during type conversion
            throw new TypeConversionException(value, type, e);
        }
        if (answer == Void.TYPE) {
            // Could not find suitable conversion
            missCounter.incrementAndGet();
            // Could not find suitable conversion
            return null;
        } else {
            hitCounter.incrementAndGet();
            return (T) answer;
        }
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return mandatoryConvertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        if (!isRunAllowed()) {
            throw new IllegalStateException(this + " is not started");
        }

        Object answer;
        try {
            attemptCounter.incrementAndGet();
            answer = doConvertTo(type, exchange, value, false);
        } catch (Exception e) {
            failedCounter.incrementAndGet();
            // error occurred during type conversion
            throw new TypeConversionException(value, type, e);
        }
        if (answer == Void.TYPE || value == null) {
            // Could not find suitable conversion
            missCounter.incrementAndGet();
            // Could not find suitable conversion
            throw new NoTypeConversionAvailableException(value, type);
        } else {
            hitCounter.incrementAndGet();
            return (T) answer;
        }
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Object value) {
        return tryConvertTo(type, null, value);
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        if (!isRunAllowed()) {
            return null;
        }

        Object answer;
        try {
            attemptCounter.incrementAndGet();
            answer = doConvertTo(type, exchange, value, true);
        } catch (Exception e) {
            failedCounter.incrementAndGet();
            return null;
        }
        if (answer == Void.TYPE) {
            missCounter.incrementAndGet();
            // Could not find suitable conversion
            return null;
        } else {
            hitCounter.incrementAndGet();
            return (T) answer;
        }
    }

    protected Object doConvertTo(final Class<?> type, final Exchange exchange, final Object value, final boolean tryConvert) {
        if (log.isTraceEnabled()) {
            log.trace("Converting {} -> {} with value: {}",
                    new Object[]{value == null ? "null" : value.getClass().getCanonicalName(), 
                        type.getCanonicalName(), value});
        }

        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class.isAssignableFrom(type)) {
                return Boolean.FALSE;
            }
            return null;
        }

        // same instance type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        // check if we have tried it before and if its a miss
        TypeMapping key = new TypeMapping(type, value.getClass());
        if (misses.containsKey(key)) {
            // we have tried before but we cannot convert this one
            return Void.TYPE;
        }
        
        // special for NaN numbers, which we can only convert for floating numbers
        if (ObjectHelper.isNaN(value)) {
            if (Float.class.isAssignableFrom(type)) {
                return Float.NaN;
            } else if (Double.class.isAssignableFrom(type)) {
                return Double.NaN;
            } else {
                // we cannot convert the NaN
                return Void.TYPE;
            }
        }

        // try to find a suitable type converter
        TypeConverter converter = getOrFindTypeConverter(type, value);
        if (converter != null) {
            log.trace("Using converter: {} to convert {}", converter, key);
            Object rc = converter.convertTo(type, exchange, value);
            if (rc != null) {
                return rc;
            }
        }

        // fallback converters
        for (FallbackTypeConverter fallback : fallbackConverters) {
            Object rc = fallback.getFallbackTypeConverter().convertTo(type, exchange, value);

            if (Void.TYPE.equals(rc)) {
                // it cannot be converted so give up
                return Void.TYPE;
            }

            if (rc != null) {
                // if fallback can promote then let it be promoted to a first class type converter
                if (fallback.isCanPromote()) {
                    // add it as a known type converter since we found a fallback that could do it
                    if (log.isDebugEnabled()) {
                        log.debug("Promoting fallback type converter as a known type converter to convert from: {} to: {} for the fallback converter: {}",
                                new Object[]{type.getCanonicalName(), value.getClass().getCanonicalName(), fallback.getFallbackTypeConverter()});
                    }
                    addTypeConverter(type, value.getClass(), fallback.getFallbackTypeConverter());
                }

                if (log.isTraceEnabled()) {
                    log.trace("Fallback type converter {} converted type from: {} to: {}",
                            new Object[]{fallback.getFallbackTypeConverter(),
                                type.getCanonicalName(), value.getClass().getCanonicalName()});
                }

                // return converted value
                return rc;
            }
        }

        // not found with that type then if it was a primitive type then try again with the wrapper type
        if (type.isPrimitive()) {
            Class<?> primitiveType = ObjectHelper.convertPrimitiveTypeToWrapperType(type);
            if (primitiveType != type) {
                return convertTo(primitiveType, exchange, value);
            }
        }

        if (!tryConvert) {
            // Could not find suitable conversion, so remember it
            // do not register misses for try conversions
            misses.put(key, key);
        }

        // Could not find suitable conversion, so return Void to indicate not found
        return Void.TYPE;
    }

    @Override
    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        log.trace("Adding type converter: {}", typeConverter);
        TypeMapping key = new TypeMapping(toType, fromType);
        TypeConverter converter = typeMappings.get(key);
        // only override it if its different
        // as race conditions can lead to many threads trying to promote the same fallback converter
        if (typeConverter != converter) {
            if (converter != null) {
                log.warn("Overriding type converter from: " + converter + " to: " + typeConverter);
            }
            typeMappings.put(key, typeConverter);
            // remove any previous misses, as we added the new type converter
            misses.remove(key);
        }
    }

    @Override
    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        log.trace("Adding fallback type converter: {} which can promote: {}", typeConverter, canPromote);

        // add in top of fallback as the toString() fallback will nearly always be able to convert
        fallbackConverters.add(0, new FallbackTypeConverter(typeConverter, canPromote));
        if (typeConverter instanceof TypeConverterAware) {
            TypeConverterAware typeConverterAware = (TypeConverterAware) typeConverter;
            typeConverterAware.setTypeConverter(this);
        }
    }

    public TypeConverter getTypeConverter(Class<?> toType, Class<?> fromType) {
        TypeMapping key = new TypeMapping(toType, fromType);
        return typeMappings.get(key);
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Override
    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    public Set<Class<?>> getFromClassMappings() {
        Set<Class<?>> answer = new HashSet<Class<?>>();
        for (TypeMapping mapping : typeMappings.keySet()) {
            answer.add(mapping.getFromType());
        }
        return answer;
    }

    public Map<Class<?>, TypeConverter> getToClassMappings(Class<?> fromClass) {
        Map<Class<?>, TypeConverter> answer = new HashMap<Class<?>, TypeConverter>();
        for (Map.Entry<TypeMapping, TypeConverter> entry : typeMappings.entrySet()) {
            TypeMapping mapping = entry.getKey();
            if (mapping.isApplicable(fromClass)) {
                answer.put(mapping.getToType(), entry.getValue());
            }
        }
        return answer;
    }

    public Map<TypeMapping, TypeConverter> getTypeMappings() {
        return typeMappings;
    }

    protected <T> TypeConverter getOrFindTypeConverter(Class<?> toType, Object value) {
        Class<?> fromType = null;
        if (value != null) {
            fromType = value.getClass();
        }
        TypeMapping key = new TypeMapping(toType, fromType);
        TypeConverter converter = typeMappings.get(key);
        if (converter == null) {
            // converter not found, try to lookup then
            converter = lookup(toType, fromType);
            if (converter != null) {
                typeMappings.putIfAbsent(key, converter);
            }
        }
        return converter;
    }

    @Override
    public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
        return doLookup(toType, fromType, false);
    }

    protected TypeConverter doLookup(Class<?> toType, Class<?> fromType, boolean isSuper) {

        if (fromType != null) {
            // lets try if there is a direct match
            TypeConverter converter = getTypeConverter(toType, fromType);
            if (converter != null) {
                return converter;
            }

            // try the interfaces
            for (Class<?> type : fromType.getInterfaces()) {
                converter = getTypeConverter(toType, type);
                if (converter != null) {
                    return converter;
                }
            }

            // try super then
            Class<?> fromSuperClass = fromType.getSuperclass();
            if (fromSuperClass != null && !fromSuperClass.equals(Object.class)) {
                converter = doLookup(toType, fromSuperClass, true);
                if (converter != null) {
                    return converter;
                }
            }
        }

        // only do these tests as fallback and only on the target type (eg not on its super)
        if (!isSuper) {
            if (fromType != null && !fromType.equals(Object.class)) {

                // lets try classes derived from this toType
                Set<Map.Entry<TypeMapping, TypeConverter>> entries = typeMappings.entrySet();
                for (Map.Entry<TypeMapping, TypeConverter> entry : entries) {
                    TypeMapping key = entry.getKey();
                    Class<?> aToType = key.getToType();
                    if (toType.isAssignableFrom(aToType)) {
                        Class<?> aFromType = key.getFromType();
                        // skip Object based we do them last
                        if (!aFromType.equals(Object.class) && aFromType.isAssignableFrom(fromType)) {
                            return entry.getValue();
                        }
                    }
                }

                // lets test for Object based converters as last resort
                TypeConverter converter = getTypeConverter(toType, Object.class);
                if (converter != null) {
                    return converter;
                }
            }
        }

        // none found
        return null;
    }

    /**
     * Loads the core type converters which is mandatory to use Camel
     */
    public void loadCoreTypeConverters() throws Exception {
        // load all the type converters from camel-core
        CoreTypeConverterLoader core = new CoreTypeConverterLoader();
        core.load(this);
    }

    /**
     * Checks if the registry is loaded and if not lazily load it
     */
    protected void loadTypeConverters() throws Exception {
        for (TypeConverterLoader typeConverterLoader : getTypeConverterLoaders()) {
            typeConverterLoader.load(this);
        }

        // lets try load any other fallback converters
        try {
            loadFallbackTypeConverters();
        } catch (NoFactoryAvailableException e) {
            // ignore its fine to have none
        }
    }

    protected void loadFallbackTypeConverters() throws IOException, ClassNotFoundException {
        List<TypeConverter> converters = factoryFinder.newInstances("FallbackTypeConverter", getInjector(), TypeConverter.class);
        for (TypeConverter converter : converters) {
            addFallbackTypeConverter(converter, false);
        }
    }

    @Override
    public Statistics getStatistics() {
        return statistics;
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // log utilization statistics when stopping, including mappings
        String info = statistics.toString();
        info += String.format(" mappings[total=%s, misses=%s]", typeMappings.size(), misses.size());
        log.info(info);

        typeMappings.clear();
        misses.clear();
        propertyEditorTypeConverter.clear();
        statistics.reset();
    }

    /**
     * Represents utilization statistics
     */
    private final class UtilizationStatistics implements Statistics {

        @Override
        public long getAttemptCounter() {
            return attemptCounter.get();
        }

        @Override
        public long getHitCounter() {
            return hitCounter.get();
        }

        @Override
        public long getMissCounter() {
            return missCounter.get();
        }

        @Override
        public long getFailedCounter() {
            return failedCounter.get();
        }

        @Override
        public void reset() {
            attemptCounter.set(0);
            hitCounter.set(0);
            missCounter.set(0);
            failedCounter.set(0);
        }

        @Override
        public String toString() {
            return String.format("TypeConverterRegistry utilization[attempts=%s, hits=%s, misses=%s, failures=%s]",
                    getAttemptCounter(), getHitCounter(), getMissCounter(), getFailedCounter());
        }
    }

    /**
     * Represents a mapping from one type (which can be null) to another
     */
    protected static class TypeMapping {
        private final Class<?> toType;
        private final Class<?> fromType;

        TypeMapping(Class<?> toType, Class<?> fromType) {
            this.toType = toType;
            this.fromType = fromType;
        }

        public Class<?> getFromType() {
            return fromType;
        }

        public Class<?> getToType() {
            return toType;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof TypeMapping) {
                TypeMapping that = (TypeMapping) object;
                return ObjectHelper.equal(this.fromType, that.fromType)
                        && ObjectHelper.equal(this.toType, that.toType);
            }
            return false;
        }

        @Override
        public int hashCode() {
            int answer = toType.hashCode();
            if (fromType != null) {
                answer *= 37 + fromType.hashCode();
            }
            return answer;
        }

        @Override
        public String toString() {
            return "[" + fromType + "=>" + toType + "]";
        }

        public boolean isApplicable(Class<?> fromClass) {
            return fromType.isAssignableFrom(fromClass);
        }
    }

    /**
     * Represents a fallback type converter
     */
    protected static class FallbackTypeConverter {
        private final boolean canPromote;
        private final TypeConverter fallbackTypeConverter;

        FallbackTypeConverter(TypeConverter fallbackTypeConverter, boolean canPromote) {
            this.canPromote = canPromote;
            this.fallbackTypeConverter = fallbackTypeConverter;
        }

        public boolean isCanPromote() {
            return canPromote;
        }

        public TypeConverter getFallbackTypeConverter() {
            return fallbackTypeConverter;
        }
    }
}
