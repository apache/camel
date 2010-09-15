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
import java.util.concurrent.ExecutionException;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConverter;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Default implementation of a type converter registry used for
 * <a href="http://camel.apache.org/type-converter.html">type converters</a> in Camel.
 *
 * @version $Revision$
 */
public class DefaultTypeConverter extends ServiceSupport implements TypeConverter, TypeConverterRegistry {
    private static final transient Log LOG = LogFactory.getLog(DefaultTypeConverter.class);
    private final Map<TypeMapping, TypeConverter> typeMappings = new ConcurrentHashMap<TypeMapping, TypeConverter>();
    private final Map<TypeMapping, TypeMapping> misses = new ConcurrentHashMap<TypeMapping, TypeMapping>();
    private final List<TypeConverterLoader> typeConverterLoaders = new ArrayList<TypeConverterLoader>();
    private final List<FallbackTypeConverter> fallbackConverters = new ArrayList<FallbackTypeConverter>();
    private Injector injector;
    private final FactoryFinder factoryFinder;
    private final PropertyEditorTypeConverter propertyEditorTypeConverter = new PropertyEditorTypeConverter();

    public DefaultTypeConverter(PackageScanClassResolver resolver, Injector injector, FactoryFinder factoryFinder) {
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

    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        if (!isRunAllowed()) {
            throw new IllegalStateException(this + " is not started");
        }

        Object answer;
        try {
            answer = doConvertTo(type, exchange, value);
        } catch (Exception e) {
            // if its a ExecutionException then we have rethrow it as its not due to failed conversion
            boolean execution = ObjectHelper.getException(ExecutionException.class, e) != null
                    || ObjectHelper.getException(CamelExecutionException.class, e) != null;
            if (execution) {
                throw ObjectHelper.wrapCamelExecutionException(exchange, e);
            }

            // we cannot convert so return null
            if (LOG.isDebugEnabled()) {
                LOG.debug(NoTypeConversionAvailableException.createMessage(value, type)
                        + " Caused by: " + e.getMessage() + ". Will ignore this and continue.");
            }
            return null;
        }
        if (answer == Void.TYPE) {
            // Could not find suitable conversion
            return null;
        } else {
            return (T) answer;
        }
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return mandatoryConvertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        if (!isRunAllowed()) {
            throw new IllegalStateException(this + " is not started");
        }

        Object answer;
        try {
            answer = doConvertTo(type, exchange, value);
        } catch (Exception e) {
            throw new NoTypeConversionAvailableException(value, type, e);
        }
        if (answer == Void.TYPE || value == null) {
            // Could not find suitable conversion
            throw new NoTypeConversionAvailableException(value, type);
        } else {
            return (T) answer;
        }
    }

    @SuppressWarnings("unchecked")
    public Object doConvertTo(final Class type, final Exchange exchange, final Object value) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Converting " + (value == null ? "null" : value.getClass().getCanonicalName())
                    + " -> " + type.getCanonicalName() + " with value: " + value);
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

        // try to find a suitable type converter
        TypeConverter converter = getOrFindTypeConverter(type, value);
        if (converter != null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Using converter: " + converter + " to convert " + key);
            }
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
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Promoting fallback type converter as a known type converter to convert from: "
                                + type.getCanonicalName() + " to: " + value.getClass().getCanonicalName()
                                + " for the fallback converter: " + fallback.getFallbackTypeConverter());
                    }
                    addTypeConverter(type, value.getClass(), fallback.getFallbackTypeConverter());
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Fallback type converter " + fallback.getFallbackTypeConverter() + " converted type from: "
                                + type.getCanonicalName() + " to: " + value.getClass().getCanonicalName());
                }

                // return converted value
                return rc;
            }
        }

        // not found with that type then if it was a primitive type then try again with the wrapper type
        if (type.isPrimitive()) {
            Class primitiveType = ObjectHelper.convertPrimitiveTypeToWrapperType(type);
            if (primitiveType != type) {
                return convertTo(primitiveType, exchange, value);
            }
        }

        // Could not find suitable conversion, so remember it
        synchronized (misses) {
            misses.put(key, key);
        }

        // Could not find suitable conversion, so return Void to indicate not found
        return Void.TYPE;
    }

    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding type converter: " + typeConverter);
        }
        TypeMapping key = new TypeMapping(toType, fromType);
        synchronized (typeMappings) {
            TypeConverter converter = typeMappings.get(key);
            // only override it if its different
            // as race conditions can lead to many threads trying to promote the same fallback converter
            if (typeConverter != converter) {
                if (converter != null) {
                    LOG.warn("Overriding type converter from: " + converter + " to: " + typeConverter);
                }
                typeMappings.put(key, typeConverter);
            }
        }
    }

    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Adding fallback type converter: " + typeConverter + " which can promote: " + canPromote);
        }

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

    public Injector getInjector() {
        return injector;
    }

    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    public Set<Class<?>> getFromClassMappings() {
        Set<Class<?>> answer = new HashSet<Class<?>>();
        synchronized (typeMappings) {
            for (TypeMapping mapping : typeMappings.keySet()) {
                answer.add(mapping.getFromType());
            }
        }
        return answer;
    }

    public Map<Class<?>, TypeConverter> getToClassMappings(Class<?> fromClass) {
        Map<Class<?>, TypeConverter> answer = new HashMap<Class<?>, TypeConverter>();
        synchronized (typeMappings) {
            for (Map.Entry<TypeMapping, TypeConverter> entry : typeMappings.entrySet()) {
                TypeMapping mapping = entry.getKey();
                if (mapping.isApplicable(fromClass)) {
                    answer.put(mapping.getToType(), entry.getValue());
                }
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
        TypeConverter converter;
        synchronized (typeMappings) {
            converter = typeMappings.get(key);
            if (converter == null) {
                converter = lookup(toType, fromType);
                if (converter != null) {
                    typeMappings.put(key, converter);
                }
            }
        }
        return converter;
    }

    public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
        return doLookup(toType, fromType, false);
    }

    private TypeConverter doLookup(Class<?> toType, Class<?> fromType, boolean isSuper) {

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
     * Checks if the registry is loaded and if not lazily load it
     */
    protected void loadTypeConverters() throws Exception {
        StopWatch watch = new StopWatch();

        LOG.debug("Loading type converters ...");
        for (TypeConverterLoader typeConverterLoader : getTypeConverterLoaders()) {
            typeConverterLoader.load(this);
        }

        // lets try load any other fallback converters
        try {
            loadFallbackTypeConverters();
        } catch (NoFactoryAvailableException e) {
            // ignore its fine to have none
        }
        LOG.debug("Loading type converters done");

        // report how long time it took to load
        if (LOG.isInfoEnabled()) {
            LOG.info("Loaded " + typeMappings.size() + " type converters in " + TimeUtils.printDuration(watch.stop()));
        }
    }

    protected void loadFallbackTypeConverters() throws IOException, ClassNotFoundException {
        List<TypeConverter> converters = factoryFinder.newInstances("FallbackTypeConverter", getInjector(), TypeConverter.class);
        for (TypeConverter converter : converters) {
            addFallbackTypeConverter(converter, false);
        }
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startService(propertyEditorTypeConverter);
        loadTypeConverters();
    }

    @Override
    protected void doStop() throws Exception {
        typeMappings.clear();
        misses.clear();
        // let property editor type converter stop and cleanup resources
        ServiceHelper.stopService(propertyEditorTypeConverter);
    }

    /**
     * Represents a mapping from one type (which can be null) to another
     */
    protected static class TypeMapping {
        Class<?> toType;
        Class<?> fromType;

        public TypeMapping(Class<?> toType, Class<?> fromType) {
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
        private boolean canPromote;
        private TypeConverter fallbackTypeConverter;

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
