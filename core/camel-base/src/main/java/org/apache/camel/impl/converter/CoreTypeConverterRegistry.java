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
package org.apache.camel.impl.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Ordered;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.TypeConverterExistsException;
import org.apache.camel.TypeConverters;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.spi.BulkTypeConverters;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.DoubleMap;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CoreTypeConverterRegistry extends ServiceSupport implements TypeConverter, TypeConverterRegistry {

    protected static final TypeConverter MISS_CONVERTER = new TypeConverterSupport() {
        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return (T) MISS_VALUE;
        }
    };

    private static final Logger LOG = LoggerFactory.getLogger(CoreTypeConverterRegistry.class);

    // built-in core type converters that are bulked together in a few classes for optimal performance
    protected final List<BulkTypeConverters> bulkTypeConverters = new ArrayList<>();
    // custom type converters (from camel components and end users)
    protected final DoubleMap<Class<?>, Class<?>, TypeConverter> typeMappings = new DoubleMap<>(16);
    // fallback converters
    protected final List<FallbackTypeConverter> fallbackConverters = new CopyOnWriteArrayList<>();
    // special enum converter for optional performance
    protected final TypeConverter enumTypeConverter = new EnumTypeConverter();

    protected final Statistics statistics = new UtilizationStatistics();
    protected final LongAdder noopCounter = new LongAdder();
    protected final LongAdder attemptCounter = new LongAdder();
    protected final LongAdder missCounter = new LongAdder();
    protected final LongAdder hitCounter = new LongAdder();
    protected final LongAdder failedCounter = new LongAdder();

    protected TypeConverterExists typeConverterExists = TypeConverterExists.Override;
    protected LoggingLevel typeConverterExistsLoggingLevel = LoggingLevel.WARN;

    // to keep track of number of converters in the bulked classes
    private int sumBulkTypeConverters;

    public CoreTypeConverterRegistry() {
    }

    public CoreTypeConverterRegistry(TypeConverterRegistry registry) {
        if (registry instanceof CoreTypeConverterRegistry) {
            CoreTypeConverterRegistry reg = (CoreTypeConverterRegistry) registry;
            reg.getTypeMappings().forEach(typeMappings::put);
            this.bulkTypeConverters.addAll(reg.getBulkTypeConverters());
            this.fallbackConverters.addAll(reg.getFallbackConverters());
        } else {
            throw new UnsupportedOperationException();
        }
        this.typeConverterExistsLoggingLevel = registry.getTypeConverterExistsLoggingLevel();
        this.typeConverterExists = registry.getTypeConverterExists();
    }

    @Override
    public boolean allowNull() {
        return false;
    }

    @Override
    public void setInjector(Injector injector) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Injector getInjector() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CamelContext getCamelContext() {
        throw new UnsupportedOperationException();
    }

    public DoubleMap<Class<?>, Class<?>, TypeConverter> getTypeMappings() {
        return typeMappings;
    }

    public List<FallbackTypeConverter> getFallbackConverters() {
        return fallbackConverters;
    }

    public List<BulkTypeConverters> getBulkTypeConverters() {
        return bulkTypeConverters;
    }

    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        // optimize for a few common conversions
        if (value != null) {
            if (type.isInstance(value)) {
                // same instance
                return (T) value;
            }
            if (type == boolean.class) {
                // primitive boolean which must return a value so throw exception if not possible
                Object answer = ObjectConverter.toBoolean(value);
                if (answer == null) {
                    throw new TypeConversionException(
                            value, type,
                            new IllegalArgumentException("Cannot convert type: " + value.getClass().getName() + " to boolean"));
                }
                return (T) answer;
            } else if (type == Boolean.class && (value instanceof String)) {
                // String -> Boolean
                String str = (String) value;
                // must be 4 or 5 in length
                int len = str.length();
                // fast check the value as-is in lower case which is most common
                if (len == 4 && "true".equals(str)) {
                    return (T) Boolean.TRUE;
                } else if (len == 5 && "false".equals(str)) {
                    return (T) Boolean.FALSE;
                } else {
                    // do check for ignore case
                    str = str.toUpperCase();
                    if (len == 4 && "TRUE".equals(str)) {
                        return (T) Boolean.TRUE;
                    } else if (len == 5 && "FALSE".equals(str)) {
                        return (T) Boolean.FALSE;
                    }
                }
            } else if (type.isPrimitive()) {
                // okay its a wrapper -> primitive then return as-is for some common types
                Class<?> cls = value.getClass();
                if (cls == Integer.class || cls == Long.class) {
                    return (T) value;
                }
            } else if (type == String.class) {
                // okay its a primitive -> string then return as-is for some common types
                Class<?> cls = value.getClass();
                if (cls.isPrimitive()
                        || cls == Boolean.class
                        || cls == Integer.class
                        || cls == Long.class) {
                    return (T) value.toString();
                }
            } else if (type.isEnum()) {
                // okay its a conversion to enum
                try {
                    return enumTypeConverter.convertTo(type, exchange, value);
                } catch (Exception e) {
                    throw createTypeConversionException(exchange, type, value, e);
                }
            }

            // NOTE: we cannot optimize any more if value is String as it may be time pattern and other patterns
        }

        return (T) doConvertTo(type, exchange, value, false, false);
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return mandatoryConvertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        // optimize for a few common conversions
        if (value != null) {
            if (type.isInstance(value)) {
                // same instance
                return (T) value;
            }
            if (type == boolean.class) {
                // primitive boolean which must return a value so throw exception if not possible
                Object answer = ObjectConverter.toBoolean(value);
                if (answer == null) {
                    throw new TypeConversionException(
                            value, type,
                            new IllegalArgumentException("Cannot convert type: " + value.getClass().getName() + " to boolean"));
                }
                return (T) answer;
            } else if (type == Boolean.class && (value instanceof String)) {
                // String -> Boolean
                String str = (String) value;
                // must be 4 or 5 in length
                int len = str.length();
                // fast check the value as-is in lower case which is most common
                if (len == 4 && "true".equals(str)) {
                    return (T) Boolean.TRUE;
                } else if (len == 5 && "false".equals(str)) {
                    return (T) Boolean.FALSE;
                } else {
                    // do check for ignore case
                    str = str.toUpperCase();
                    if (len == 4 && "TRUE".equals(str)) {
                        return (T) Boolean.TRUE;
                    } else if (len == 5 && "FALSE".equals(str)) {
                        return (T) Boolean.FALSE;
                    }
                }
            } else if (type.isPrimitive()) {
                // okay its a wrapper -> primitive then return as-is for some common types
                Class<?> cls = value.getClass();
                if (cls == Integer.class || cls == Long.class) {
                    return (T) value;
                }
            } else if (type == String.class) {
                // okay its a primitive -> string then return as-is for some common types
                Class<?> cls = value.getClass();
                if (cls.isPrimitive()
                        || cls == Boolean.class
                        || cls == Integer.class
                        || cls == Long.class) {
                    return (T) value.toString();
                }
            } else if (type.isEnum()) {
                // okay its a conversion to enum
                try {
                    return enumTypeConverter.convertTo(type, exchange, value);
                } catch (Exception e) {
                    throw createTypeConversionException(exchange, type, value, e);
                }
            }
            // NOTE: we cannot optimize any more if value is String as it may be time pattern and other patterns
        }

        Object answer = doConvertTo(type, exchange, value, true, false);
        if (answer == null) {
            // Could not find suitable conversion
            throw new NoTypeConversionAvailableException(value, type);
        }
        return (T) answer;
    }

    public <T> T tryConvertTo(Class<T> type, Object value) {
        return tryConvertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T tryConvertTo(Class<T> type, Exchange exchange, Object value) {
        // optimize for a few common conversions
        if (value != null) {
            if (type.isInstance(value)) {
                // same instance
                return (T) value;
            }
            if (type == boolean.class) {
                // primitive boolean which must return a value so throw exception if not possible
                Object answer = ObjectConverter.toBoolean(value);
                if (answer == null) {
                    throw new TypeConversionException(
                            value, type,
                            new IllegalArgumentException("Cannot convert type: " + value.getClass().getName() + " to boolean"));
                }
                return (T) answer;
            } else if (type == Boolean.class && (value instanceof String)) {
                // String -> Boolean
                String str = (String) value;
                // must be 4 or 5 in length
                int len = str.length();
                // fast check the value as-is in lower case which is most common
                if (len == 4 && "true".equals(str)) {
                    return (T) Boolean.TRUE;
                } else if (len == 5 && "false".equals(str)) {
                    return (T) Boolean.FALSE;
                } else {
                    // do check for ignore case
                    str = str.toUpperCase();
                    if (len == 4 && "TRUE".equals(str)) {
                        return (T) Boolean.TRUE;
                    } else if (len == 5 && "FALSE".equals(str)) {
                        return (T) Boolean.FALSE;
                    }
                }
            } else if (type.isPrimitive()) {
                // okay its a wrapper -> primitive then return as-is for some common types
                Class<?> cls = value.getClass();
                if (cls == Integer.class || cls == Long.class) {
                    return (T) value;
                }
            } else if (type == String.class) {
                // okay its a primitive -> string then return as-is for some common types
                Class<?> cls = value.getClass();
                if (cls.isPrimitive()
                        || cls == Boolean.class
                        || cls == Integer.class
                        || cls == Long.class) {
                    return (T) value.toString();
                }
            } else if (type.isEnum()) {
                // okay its a conversion to enum
                try {
                    return enumTypeConverter.convertTo(type, exchange, value);
                } catch (Exception e) {
                    // we are only trying so ignore exceptions
                    return null;
                }
            }

            // NOTE: we cannot optimize any more if value is String as it may be time pattern and other patterns
        }

        return (T) doConvertTo(type, exchange, value, false, true);
    }

    protected Object doConvertTo(
            final Class<?> type, final Exchange exchange, final Object value,
            final boolean mandatory, final boolean tryConvert) {
        Object answer;
        try {
            answer = doConvertTo(type, exchange, value, tryConvert);
        } catch (Exception e) {
            if (statistics.isStatisticsEnabled()) {
                failedCounter.increment();
            }
            if (tryConvert) {
                return null;
            }

            // if its a ExecutionException then we have rethrow it as its not due to failed conversion
            // this is special for FutureTypeConverter
            boolean execution = ObjectHelper.getException(ExecutionException.class, e) != null
                    || ObjectHelper.getException(CamelExecutionException.class, e) != null;
            if (execution) {
                throw CamelExecutionException.wrapCamelExecutionException(exchange, e);
            }
            // error occurred during type conversion
            throw createTypeConversionException(exchange, type, value, e);
        }
        if (answer == TypeConverter.MISS_VALUE) {
            // Could not find suitable conversion
            if (statistics.isStatisticsEnabled()) {
                missCounter.increment();
            }
            return null;
        } else {
            if (statistics.isStatisticsEnabled()) {
                hitCounter.increment();
            }
            return answer;
        }
    }

    protected Object doConvertTo(
            final Class<?> type, final Exchange exchange, final Object value,
            final boolean tryConvert)
            throws Exception {
        boolean trace = LOG.isTraceEnabled();
        boolean statisticsEnabled = statistics.isStatisticsEnabled();

        if (trace) {
            LOG.trace("Finding type converter to convert {} -> {} with value: {}",
                    value == null ? "null" : value.getClass().getCanonicalName(),
                    type.getCanonicalName(), value);
        }

        if (value == null) {
            // no type conversion was needed
            if (statisticsEnabled) {
                noopCounter.increment();
            }
            // lets avoid NullPointerException when converting to primitives for null values
            if (type.isPrimitive()) {
                if (boolean.class == type) {
                    return Boolean.FALSE;
                }
                if (int.class == type) {
                    return 0;
                }
                if (long.class == type) {
                    return 0L;
                }
                if (byte.class == type) {
                    return (byte) 0;
                }
                if (short.class == type) {
                    return (short) 0;
                }
                if (double.class == type) {
                    return 0.0;
                }
                if (float.class == type) {
                    return 0.0f;
                }
                if (char.class == type) {
                    return '\0';
                }
            }
            return null;
        }

        // same instance type
        if (type.isInstance(value)) {
            // no type conversion was needed
            if (statisticsEnabled) {
                noopCounter.increment();
            }
            return value;
        }

        // okay we need to attempt to convert
        if (statisticsEnabled) {
            attemptCounter.increment();
        }

        // attempt bulk first which is the fastest
        for (BulkTypeConverters bulk : bulkTypeConverters) {
            if (trace) {
                LOG.trace("Using bulk converter: {} to convert [{}=>{}]", bulk.getClass().getSimpleName(), value.getClass(),
                        type);
            }
            Object rc;
            if (tryConvert) {
                rc = bulk.convertTo(value.getClass(), type, exchange, value);
            } else {
                rc = bulk.convertTo(value.getClass(), type, exchange, value);
            }
            if (rc != null) {
                return rc;
            }
        }

        // try to find a suitable type converter
        TypeConverter converter = getOrFindTypeConverter(type, value.getClass());
        if (converter != null) {
            if (trace) {
                LOG.trace("Using converter: {} to convert [{}=>{}]", converter, value.getClass(), type);
            }
            Object rc;
            if (tryConvert) {
                rc = converter.tryConvertTo(type, exchange, value);
            } else {
                rc = converter.convertTo(type, exchange, value);
            }
            if (rc != null) {
                return rc;
            } else if (converter.allowNull()) {
                return null;
            }
        }

        // not found with that type then if it was a primitive type then try again with the wrapper type
        if (type.isPrimitive()) {
            Class<?> primitiveType = ObjectHelper.convertPrimitiveTypeToWrapperType(type);
            if (primitiveType != type) {
                Class<?> fromType = value.getClass();
                TypeConverter tc = getOrFindTypeConverter(primitiveType, fromType);
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

            if (rc == TypeConverter.MISS_VALUE) {
                // it cannot be converted so give up
                return TypeConverter.MISS_VALUE;
            }

            if (rc != null) {
                // if fallback can promote then let it be promoted to a first class type converter
                if (fallback.isCanPromote()) {
                    // add it as a known type converter since we found a fallback that could do it
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(
                                "Promoting fallback type converter as a known type converter to convert from: {} to: {} for the fallback converter: {}",
                                type.getCanonicalName(), value.getClass().getCanonicalName(),
                                fallback.getFallbackTypeConverter());
                    }
                    addTypeConverter(type, value.getClass(), fallback.getFallbackTypeConverter());
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Fallback type converter {} converted type from: {} to: {}",
                            fallback.getFallbackTypeConverter(), type.getCanonicalName(), value.getClass().getCanonicalName());
                }

                // return converted value
                return rc;
            }
        }

        if (!tryConvert) {
            // Could not find suitable conversion, so remember it
            // do not register misses for try conversions
            typeMappings.put(type, value.getClass(), MISS_CONVERTER);
        }

        // Could not find suitable conversion, so return Void to indicate not found
        return TypeConverter.MISS_VALUE;
    }

    public TypeConverter getTypeConverter(Class<?> toType, Class<?> fromType) {
        return typeMappings.get(toType, fromType);
    }

    @Override
    public void addBulkTypeConverters(BulkTypeConverters bulkTypeConverters) {
        // guard against adding duplicates
        boolean exists = this.bulkTypeConverters.contains(bulkTypeConverters);
        if (!exists) {
            if (bulkTypeConverters.getOrder() == Ordered.HIGHEST) {
                this.bulkTypeConverters.add(0, bulkTypeConverters);
            } else {
                this.bulkTypeConverters.add(bulkTypeConverters);
            }
            sumBulkTypeConverters += bulkTypeConverters.size();
        }
    }

    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        LOG.trace("Adding type converter: {}", typeConverter);
        TypeConverter converter = typeMappings.get(toType, fromType);
        // only override it if its different
        // as race conditions can lead to many threads trying to promote the same fallback converter

        if (typeConverter != converter) {

            // add the converter unless we should ignore
            boolean add = true;

            // if converter is not null then a duplicate exists
            if (converter != null) {
                if (typeConverterExists == TypeConverterExists.Override) {
                    CamelLogger logger = new CamelLogger(LOG, typeConverterExistsLoggingLevel);
                    logger.log("Overriding type converter from: " + converter + " to: " + typeConverter);
                } else if (typeConverterExists == TypeConverterExists.Ignore) {
                    CamelLogger logger = new CamelLogger(LOG, typeConverterExistsLoggingLevel);
                    logger.log("Ignoring duplicate type converter from: " + converter + " to: " + typeConverter);
                    add = false;
                } else {
                    // we should fail
                    throw new TypeConverterExistsException(toType, fromType);
                }
            }

            if (add) {
                typeMappings.put(toType, fromType, typeConverter);
            }
        }
    }

    public boolean removeTypeConverter(Class<?> toType, Class<?> fromType) {
        LOG.trace("Removing type converter from: {} to: {}", fromType, toType);
        return typeMappings.remove(toType, fromType);
    }

    @Override
    public void addTypeConverters(TypeConverters typeConverters) {
        throw new UnsupportedOperationException();
    }

    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        LOG.trace("Adding fallback type converter: {} which can promote: {}", typeConverter, canPromote);

        // add in top of fallback as the toString() fallback will nearly always be able to convert
        // the last one which is add to the FallbackTypeConverter will be called at the first place
        fallbackConverters.add(0, new FallbackTypeConverter(typeConverter, canPromote));
    }

    public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
        return doLookup(toType, fromType, false);
    }

    protected TypeConverter getOrFindTypeConverter(Class<?> toType, Class<?> fromType) {
        TypeConverter converter = typeMappings.get(toType, fromType);
        if (converter == null) {
            // converter not found, try to lookup then
            converter = lookup(toType, fromType);
            if (converter != null) {
                typeMappings.put(toType, fromType, converter);
            }
        }
        return converter;
    }

    protected TypeConverter doLookup(Class<?> toType, Class<?> fromType, boolean isSuper) {

        if (fromType != null) {

            // try with base converters first
            TypeConverter converter;
            for (BulkTypeConverters base : bulkTypeConverters) {
                converter = base.lookup(toType, fromType);
                if (converter != null) {
                    return converter;
                }
            }

            // lets try if there is a direct match
            converter = getTypeConverter(toType, fromType);
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
                TypeConverter converter = typeMappings.getFirst(
                        toType::isAssignableFrom,
                        // skip Object based we do them last
                        from -> !from.equals(Object.class) && from.isAssignableFrom(fromType));
                if (converter != null) {
                    return converter;
                }

                // lets test for Object based converters as last resort
                converter = getTypeConverter(toType, Object.class);
                if (converter != null) {
                    return converter;
                }
            }
        }

        // none found
        return null;
    }

    protected TypeConversionException createTypeConversionException(
            Exchange exchange, Class<?> type, Object value, Throwable cause) {
        if (cause instanceof TypeConversionException) {
            if (((TypeConversionException) cause).getToType() == type) {
                return (TypeConversionException) cause;
            }
        }
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

    public Statistics getStatistics() {
        return statistics;
    }

    public int size() {
        return typeMappings.size() + sumBulkTypeConverters;
    }

    public LoggingLevel getTypeConverterExistsLoggingLevel() {
        return typeConverterExistsLoggingLevel;
    }

    public void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel) {
        this.typeConverterExistsLoggingLevel = typeConverterExistsLoggingLevel;
    }

    public TypeConverterExists getTypeConverterExists() {
        return typeConverterExists;
    }

    public void setTypeConverterExists(TypeConverterExists typeConverterExists) {
        this.typeConverterExists = typeConverterExists;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        // log utilization statistics when stopping, including mappings
        if (statistics.isStatisticsEnabled()) {
            String info = statistics.toString();
            AtomicInteger misses = new AtomicInteger();
            typeMappings.forEach((k1, k2, v) -> {
                if (v == MISS_CONVERTER) {
                    misses.incrementAndGet();
                }
            });
            info += String.format(" mappings[total=%s, misses=%s]", size(), misses);
            LOG.info(info);
        }

        typeMappings.clear();
        statistics.reset();
    }

    /**
     * Represents utilization statistics
     */
    private final class UtilizationStatistics implements Statistics {

        private boolean statisticsEnabled;

        @Override
        public long getNoopCounter() {
            return noopCounter.longValue();
        }

        @Override
        public long getAttemptCounter() {
            return attemptCounter.longValue();
        }

        @Override
        public long getHitCounter() {
            return hitCounter.longValue();
        }

        @Override
        public long getMissCounter() {
            return missCounter.longValue();
        }

        @Override
        public long getFailedCounter() {
            return failedCounter.longValue();
        }

        @Override
        public void reset() {
            noopCounter.reset();
            attemptCounter.reset();
            hitCounter.reset();
            missCounter.reset();
            failedCounter.reset();
        }

        @Override
        public boolean isStatisticsEnabled() {
            return statisticsEnabled;
        }

        @Override
        public void setStatisticsEnabled(boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
        }

        @Override
        public String toString() {
            return String.format("TypeConverterRegistry utilization[noop=%s, attempts=%s, hits=%s, misses=%s, failures=%s]",
                    getNoopCounter(), getAttemptCounter(), getHitCounter(), getMissCounter(), getFailedCounter());
        }
    }

    /**
     * Represents a fallback type converter
     */
    public static class FallbackTypeConverter {
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
