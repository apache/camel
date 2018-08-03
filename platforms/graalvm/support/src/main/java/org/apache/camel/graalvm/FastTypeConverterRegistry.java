/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.graalvm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoFactoryAvailableException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.TypeConverterLoaderException;
import org.apache.camel.TypeConverters;
import org.apache.camel.impl.converter.AnnotationTypeConverterLoader;
import org.apache.camel.impl.converter.ArrayTypeConverter;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.impl.converter.CoreFallbackConverter;
import org.apache.camel.impl.converter.EnumTypeConverter;
import org.apache.camel.impl.converter.FutureTypeConverter;
import org.apache.camel.impl.converter.OptimisedTypeConverter;
import org.apache.camel.impl.converter.ToStringTypeConverter;
import org.apache.camel.impl.converter.TypeConvertersLoader;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.camel.spi.TypeConverterAware;
import org.apache.camel.spi.TypeConverterLoader;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.MessageHelper;
import org.apache.camel.util.ObjectHelper;

public class FastTypeConverterRegistry extends ServiceSupport implements TypeConverter, TypeConverterRegistry {

    protected final PackageScanClassResolver resolver;
    protected CamelContext camelContext;
    protected Injector injector;
    protected final FactoryFinder factoryFinder;
    protected final List<TypeConverterLoader> typeConverterLoaders = new ArrayList<>();
    protected final List<FallbackTypeConverter> fallbackConverters = new CopyOnWriteArrayList<>();
    protected final Map<TypeMapping, TypeConverter> typeMappings = new HashMap<>();
    protected final OptimisedTypeConverter optimisedTypeConverter = new OptimisedTypeConverter();
    protected final AtomicBoolean loaded = new AtomicBoolean();

    public FastTypeConverterRegistry(CamelContext camelContext, PackageScanClassResolver resolver, Injector injector, FactoryFinder factoryFinder) {
        this.camelContext = camelContext;
        this.resolver = resolver;
        this.injector = injector;
        this.factoryFinder = factoryFinder;
        this.typeConverterLoaders.add(new AnnotationTypeConverterLoader(resolver));
        loadInitialConverters();
    }

    private void loadInitialConverters() {
        List<FallbackTypeConverter> fallbacks = new ArrayList<>();
        // add to string first as it will then be last in the last as to string can nearly
        // always convert something to a string so we want it only as the last resort
        // ToStringTypeConverter should NOT allow to be promoted
        addCoreFallbackTypeConverterToList(new ToStringTypeConverter(), false, fallbacks);
        // enum is okay to be promoted
        addCoreFallbackTypeConverterToList(new EnumTypeConverter(), true, fallbacks);
        // arrays is okay to be promoted
        addCoreFallbackTypeConverterToList(new ArrayTypeConverter(), true, fallbacks);
        // and future should also not allowed to be promoted
        addCoreFallbackTypeConverterToList(new FutureTypeConverter(this), false, fallbacks);
        // add sync processor to async processor converter is to be promoted
        addCoreFallbackTypeConverterToList(new AsyncProcessorTypeConverter(), true, fallbacks);
        // add core fallback converter
        addCoreFallbackTypeConverterToList(new CoreFallbackConverter(), true, fallbacks);
        // add all core fallback converters at once which is faster (profiler)
        fallbackConverters.addAll(fallbacks);
    }

    private void addCoreFallbackTypeConverterToList(TypeConverter typeConverter, boolean canPromote, List<FallbackTypeConverter> converters) {
        // add in top of fallback as the toString() fallback will nearly always be able to convert
        // the last one which is add to the FallbackTypeConverter will be called at the first place
        converters.add(0, new FallbackTypeConverter(typeConverter, canPromote));
        if (typeConverter instanceof TypeConverterAware) {
            TypeConverterAware typeConverterAware = (TypeConverterAware) typeConverter;
            typeConverterAware.setTypeConverter(this);
        }
        if (typeConverter instanceof CamelContextAware) {
            CamelContextAware camelContextAware = (CamelContextAware) typeConverter;
            if (camelContext != null) {
                camelContextAware.setCamelContext(camelContext);
            }
        }
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void doStop() throws Exception {
    }

    @Override
    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        TypeMapping key = new SimpleTypeMapping(toType, fromType);
        typeMappings.put(key, typeConverter);
    }

    @Override
    public void setInjector(Injector injector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Injector getInjector() {
        return injector;
    }

    @Override
    public boolean allowNull() {
        return false;
    }

    @Override
    public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
        return convertTo(type, null, value);
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
        T t = convertTo(type, null, value);
        if (t == null) {
            throw new NoTypeConversionAvailableException(value, type);
        } else {
            return t;
        }
    }

    @Override
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
        T t = convertTo(type, exchange, value);
        if (t == null) {
            throw new NoTypeConversionAvailableException(value, type);
        } else {
            return t;
        }
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Object value) {
        try {
            return convertTo(type, null, value);
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        try {
            return convertTo(type, exchange, value);
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
        if (!isRunAllowed()) {
            throw new IllegalStateException(this + " is not started");
        }
        Object answer;
        try {
            answer = doConvertTo(type, exchange, value, false);
        } catch (Exception e) {
            // if its a ExecutionException then we have rethrow it as its not due to failed conversion
            // this is special for FutureTypeConverter
            boolean execution = ObjectHelper.getException(ExecutionException.class, e) != null
                    || ObjectHelper.getException(CamelExecutionException.class, e) != null;
            if (execution) {
                throw ObjectHelper.wrapCamelExecutionException(exchange, e);
            }
            // error occurred during type conversion
            if (e instanceof TypeConversionException) {
                throw (TypeConversionException) e;
            } else {
                throw createTypeConversionException(exchange, type, value, e);
            }
        }
        if (answer == Void.TYPE) {
            return null;
        } else {
            return (T) answer;
        }
    }

    protected TypeConversionException createTypeConversionException(Exchange exchange, Class<?> type, Object value, Throwable cause) {
        Object body;
        // extract the body for logging which allows to limit the message body in the exception/stacktrace
        // and also can be used to turn off logging sensitive message data
        if (exchange != null) {
            body = MessageHelper.extractValueForLogging(value, exchange.getIn());
        } else {
            body = value;
        }
        return new TypeConversionException(body, type, cause);
    }

    protected Object doConvertTo(final Class<?> type, final Exchange exchange, final Object value, final boolean tryConvert) throws Exception {
        if (value == null) {
            // no type conversion was needed
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return Boolean.FALSE;
            }
            return null;
        }

        // same instance type
        if (type.isInstance(value)) {
            // no type conversion was needed
            return value;
        }

        // special for NaN numbers, which we can only convert for floating numbers
        if ((value instanceof Float && value.equals(Float.NaN)) || (value instanceof Double && value.equals(Double.NaN))) {
            // no type conversion was needed
            if (Float.class.isAssignableFrom(type)) {
                return Float.NaN;
            } else if (Double.class.isAssignableFrom(type)) {
                return Double.NaN;
            } else {
                // we cannot convert the NaN
                return Void.TYPE;
            }
        }

        // use the optimised core converter first
        Object result = optimisedTypeConverter.convertTo(type, exchange, value);
        if (result != null) {
            return result;
        }

        // check if we have tried it before and if its a miss
        TypeMapping key = new SimpleTypeMapping(type, value.getClass());

        // try to find a suitable type converter
        TypeConverter converter = getOrFindTypeConverter(key);
        if (converter != null) {
            Object rc;
            if (tryConvert) {
                rc = converter.tryConvertTo(type, exchange, value);
            } else {
                rc = converter.convertTo(type, exchange, value);
            }
            if (rc == null && converter.allowNull()) {
                return null;
            } else if (rc != null) {
                return rc;
            }
        }

        // not found with that type then if it was a primitive type then try again with the wrapper type
        if (type.isPrimitive()) {
            Class<?> primitiveType = ObjectHelper.convertPrimitiveTypeToWrapperType(type);
            if (primitiveType != type) {
                Class<?> fromType = value.getClass();
                TypeConverter tc = getOrFindTypeConverter(new SimpleTypeMapping(primitiveType, fromType));
                if (tc != null) {
                    // add the type as a known type converter as we can convert from primitive to object converter
                    addTypeConverter(type, fromType, tc);
                    Object rc;
                    if (tryConvert) {
                        rc = tc.tryConvertTo(primitiveType, exchange, value);
                    } else {
                        rc = tc.convertTo(primitiveType, exchange, value);
                    }
                    if (rc == null && tc.allowNull()) {
                        return null;
                    } else if (rc != null) {
                        return rc;
                    }
                }
            }
        }

        // fallback converters
        for (FallbackTypeConverter fallback : fallbackConverters) {
            TypeConverter tc = fallback.getFallbackTypeConverter();
            Object rc;
            if (tryConvert) {
                rc = tc.tryConvertTo(type, exchange, value);
            } else {
                rc = tc.convertTo(type, exchange, value);
            }
            if (rc == null && tc.allowNull()) {
                return null;
            }

            if (Void.TYPE.equals(rc)) {
                // it cannot be converted so give up
                return Void.TYPE;
            }

            if (rc != null) {
                // if fallback can promote then let it be promoted to a first class type converter
                if (fallback.isCanPromote()) {
                    // add it as a known type converter since we found a fallback that could do it
                    addTypeConverter(type, value.getClass(), fallback.getFallbackTypeConverter());
                }

                // return converted value
                return rc;
            }
        }
        // Could not find suitable conversion, so return Void to indicate not found
        return Void.TYPE;
    }

    protected <T> TypeConverter getOrFindTypeConverter(TypeMapping key) {
        TypeConverter converter = typeMappings.get(key);
        if (converter == null) {
            // converter not found, try to lookup then
            converter = lookup(key.getToType(), key.getFromType());
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
        TypeConverter answer = doLookup2(toType, fromType, isSuper);
        if (answer == null && !loaded.get()) {
            // okay we could not convert, so try again, but load the converters up front
            ensureLoaded();
            answer = doLookup2(toType, fromType, isSuper);
        }
        return answer;
    }

    private synchronized void ensureLoaded() {
        if (loaded.compareAndSet(false, true)) {
            try {
                loadTypeConverters();
            } catch (Exception e) {
                throw ObjectHelper.wrapRuntimeCamelException(e);
            }
        }
    }

    protected TypeConverter doLookup2(Class<?> toType, Class<?> fromType, boolean isSuper) {
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
                converter = doLookup2(toType, fromSuperClass, true);
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
        for (TypeConverterLoader typeConverterLoader : typeConverterLoaders) {
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

    public List<Class<?>[]> listAllTypeConvertersFromTo() {
        List<Class<?>[]> answer = new ArrayList<Class<?>[]>(typeMappings.size());
        for (TypeMapping mapping : typeMappings.keySet()) {
            answer.add(new Class<?>[]{mapping.getFromType(), mapping.getToType()});
        }
        return answer;
    }

    public TypeConverter getTypeConverter(Class<?> toType, Class<?> fromType) {
        TypeMapping key = new SimpleTypeMapping(toType, fromType);
        return typeMappings.get(key);
    }

    @Override
    public void addTypeConverters(TypeConverters typeConverters) {
        try {
            // scan the class for @Converter and load them into this registry
            TypeConvertersLoader loader = new TypeConvertersLoader(typeConverters);
            loader.load(this);
        } catch (TypeConverterLoaderException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @Override
    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        // add in top of fallback as the toString() fallback will nearly always be able to convert
        // the last one which is add to the FallbackTypeConverter will be called at the first place
        fallbackConverters.add(0, new FallbackTypeConverter(typeConverter, canPromote));
        if (typeConverter instanceof TypeConverterAware) {
            TypeConverterAware typeConverterAware = (TypeConverterAware) typeConverter;
            typeConverterAware.setTypeConverter(this);
        }
        if (typeConverter instanceof CamelContextAware) {
            CamelContextAware camelContextAware = (CamelContextAware) typeConverter;
            if (camelContext != null) {
                camelContextAware.setCamelContext(camelContext);
            }
        }
    }

    @Override
    public boolean removeTypeConverter(Class<?> toType, Class<?> fromType) {
        throw new UnsupportedOperationException();
    }

    @Override
    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel) {
        throw new UnsupportedOperationException();
    }

    @Override
    public TypeConverterExists getTypeConverterExists() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        return typeMappings.size();
    }

    @Override
    public Statistics getStatistics() {
        return null;
    }

    protected interface TypeMapping {

        Class<?> getFromType();

        Class<?> getToType();

    }
    
    protected static final class LazyTypeMapping implements TypeMapping {

        private final String toTypeStr;
        private final String fromTypeStr;
        private Class<?> toType;
        private Class<?> fromType;

        public LazyTypeMapping(String toTypeStr, String fromTypeStr) {
            this.toTypeStr = toTypeStr;
            this.fromTypeStr = fromTypeStr;
        }

        @Override
        public Class<?> getFromType() {
            if (fromType == null) {
                fromType = doLoad(fromTypeStr);
            }
            return fromType;
        }

        @Override
        public Class<?> getToType() {
            if (toType == null) {
                toType = doLoad(toTypeStr);
            }
            return toType;
        }

        private Class<?> doLoad(String str) {
            try {
                switch (str) {
                    case "byte":
                        return byte.class;
                    case "char":
                        return char.class;
                    case "boolean":
                        return boolean.class;
                    case "long":
                        return long.class;
                    case "int":
                        return int.class;
                    default:
                        return Class.forName(str);
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeCamelException("Unable to load class", e);
            }
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof LazyTypeMapping) {
                LazyTypeMapping that = (LazyTypeMapping) object;
                return Objects.equals(this.fromType.toString(), that.fromTypeStr)
                        && Objects.equals(this.toType.toString(), that.toTypeStr);
            }
            if (object instanceof TypeMapping) {
                TypeMapping that = (TypeMapping) object;
                return this.getFromType() == that.getFromType() && this.getToType() == that.getToType();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return this.fromTypeStr.hashCode() * 31 + this.toTypeStr.hashCode();
        }

        @Override
        public String toString() {
            return "[" + fromType + "=>" + toType + "]";
        }

    }

    protected static final class SimpleTypeMapping implements TypeMapping {
        private final Class<?> toType;
        private final Class<?> fromType;
        private final int hashCode;

        SimpleTypeMapping(Class<?> toType, Class<?> fromType) {
            this.toType = toType;
            this.fromType = fromType;

            // pre calculate hashcode
            int hash = toType.hashCode();
            if (fromType != null) {
                hash *= 37 + fromType.hashCode();
            }
            hashCode = hash;
        }

        public Class<?> getFromType() {
            return fromType;
        }

        public Class<?> getToType() {
            return toType;
        }

        @Override
        public boolean equals(Object object) {
            if (object instanceof SimpleTypeMapping) {
                SimpleTypeMapping that = (SimpleTypeMapping) object;
                return this.fromType == that.fromType && this.toType == that.toType;
            }
            if (object instanceof LazyTypeMapping) {
                LazyTypeMapping that = (LazyTypeMapping) object;
                return Objects.equals(this.fromType.toString(), that.fromTypeStr)
                        && Objects.equals(this.toType.toString(), that.toTypeStr);
            }
            if (object instanceof TypeMapping) {
                TypeMapping that = (TypeMapping) object;
                return this.fromType == that.getFromType() && this.toType == that.getToType();
            }
            return false;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public String toString() {
            return "[" + fromType + "=>" + toType + "]";
        }

        public boolean isApplicable(Class<?> fromClass) {
            return fromType.isAssignableFrom(fromClass);
        }
    }

    static class FallbackTypeConverter {
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

    static class DelayingTypeConverter implements TypeConverter {
        final String signature;
        TypeConverter converter;

        public DelayingTypeConverter(String signature) {
            this.signature = signature;
        }

        protected TypeConverter getConverter() {
            if (converter == null) {
                converter = createConverter();
            }
            return converter;
        }

        private TypeConverter createConverter() {
            // TODO
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean allowNull() {
            return getConverter().allowNull();
        }

        @Override
        public <T> T convertTo(Class<T> type, Object value) throws TypeConversionException {
            return getConverter().convertTo(type, value);
        }

        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return getConverter().convertTo(type, exchange, value);
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
            return getConverter().mandatoryConvertTo(type, value);
        }

        @Override
        public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException, NoTypeConversionAvailableException {
            return getConverter().mandatoryConvertTo(type, exchange, value);
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Object value) {
            return getConverter().tryConvertTo(type, value);
        }

        @Override
        public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
            return getConverter().tryConvertTo(type, exchange, value);
        }
    }

}
