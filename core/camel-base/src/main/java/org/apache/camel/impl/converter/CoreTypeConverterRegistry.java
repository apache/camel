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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;

import org.apache.camel.CamelContextAware;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterExists;
import org.apache.camel.TypeConverterExistsException;
import org.apache.camel.converter.ObjectConverter;
import org.apache.camel.spi.BulkTypeConverters;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.Injector;
import org.apache.camel.spi.TypeConverterRegistry;
import org.apache.camel.spi.TypeConvertible;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.support.TypeConverterSupport;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.impl.converter.TypeResolverHelper.tryAssignableFrom;

public abstract class CoreTypeConverterRegistry extends ServiceSupport implements TypeConverter, TypeConverterRegistry {

    protected static final TypeConverter MISS_CONVERTER = new TypeConverterSupport() {
        @Override
        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {
            return (T) MISS_VALUE;
        }
    };

    private static final Logger LOG = LoggerFactory.getLogger(CoreTypeConverterRegistry.class);

    // fallback converters
    protected final List<FallbackTypeConverter> fallbackConverters = new CopyOnWriteArrayList<>();
    // special enum converter for optional performance
    protected final TypeConverter enumTypeConverter = new EnumTypeConverter();

    private final ConverterStatistics statistics;

    protected TypeConverterExists typeConverterExists = TypeConverterExists.Ignore;
    protected LoggingLevel typeConverterExistsLoggingLevel = LoggingLevel.DEBUG;

    // Why 256: as of Camel 4, we have about 230 type converters. Therefore, set the capacity to a few more to provide
    // space for others added during runtime
    private final Map<TypeConvertible<?, ?>, TypeConverter> converters = new ConcurrentHashMap<>(256);

    protected CoreTypeConverterRegistry(boolean statisticsEnabled) {
        if (statisticsEnabled) {
            statistics = new TypeConverterStatistics();
        } else {
            statistics = new NoopTypeConverterStatistics();
        }
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

    public <T> T convertTo(Class<T> type, Object value) {
        return convertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    private <T> T fastConvertTo(Class<T> type, Exchange exchange, Object value) {
        if (value == null) {
            return null;
        }

        if (type.equals(value.getClass())) {
            // same instance
            return (T) value;
        }

        if (type == boolean.class) {
            // primitive boolean which must return a value so throw exception if not possible
            Object answer = ObjectConverter.toBoolean(value);
            requireNonNullBoolean(type, value, answer);
            return (T) answer;
        } else if (type == Boolean.class && value instanceof String str) {
            // String -> Boolean
            Boolean parsedBoolean = customParseBoolean(str);
            if (parsedBoolean != null) {
                return (T) parsedBoolean;
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
        return null;
    }

    @SuppressWarnings("unchecked")
    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) {
        // optimize for a few common conversions

        if (value != null) {
            T ret = fastConvertTo(type, exchange, value);
            if (ret != null) {
                return ret;
            }

            // NOTE: we cannot optimize any more if value is String as it may be time pattern and other patterns
        }

        return (T) doConvertToAndStat(type, exchange, value, false);
    }

    private static Boolean customParseBoolean(String str) {
        if ("true".equalsIgnoreCase(str)) {
            return Boolean.TRUE;
        }

        if ("false".equalsIgnoreCase(str)) {
            return Boolean.FALSE;
        }

        return null;
    }

    public <T> T mandatoryConvertTo(Class<T> type, Object value) throws NoTypeConversionAvailableException {
        return mandatoryConvertTo(type, null, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T mandatoryConvertTo(Class<T> type, Exchange exchange, Object value) throws NoTypeConversionAvailableException {
        // optimize for a few common conversions
        if (value != null) {
            T ret = fastConvertTo(type, exchange, value);
            if (ret != null) {
                return ret;
            }
        }

        Object answer = doConvertToAndStat(type, exchange, value, false);
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
                requireNonNullBoolean(type, value, answer);
                return (T) answer;
            } else if (type == Boolean.class && value instanceof String str) {
                // String -> Boolean
                Boolean parsedBoolean = customParseBoolean(str);
                if (parsedBoolean != null) {
                    return (T) parsedBoolean;
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

        return (T) doConvertToAndStat(type, exchange, value, true);
    }

    private static <T> void requireNonNullBoolean(Class<T> type, Object value, Object answer) {
        if (answer == null) {
            throw new TypeConversionException(
                    value, type,
                    new IllegalArgumentException("Cannot convert type: " + value.getClass().getName() + " to boolean"));
        }
    }

    protected Object doConvertToAndStat(
            final Class<?> type, final Exchange exchange, final Object value,
            final boolean tryConvert) {

        Object answer = null;
        try {
            answer = doConvertTo(type, exchange, value, tryConvert);
        } catch (Exception e) {
            // only record if not try
            if (!tryConvert) {
                statistics.incrementFailed();
            }

            if (tryConvert) {
                return null;
            }

            wrapConversionException(type, exchange, value, e);
        }
        if (answer == TypeConverter.MISS_VALUE) {
            if (!tryConvert) {
                // Could not find suitable conversion
                statistics.incrementMiss();
            }

            return null;
        } else {
            if (!tryConvert) {
                statistics.incrementHit();
            }

            return answer;
        }
    }

    private void wrapConversionException(Class<?> type, Exchange exchange, Object value, Exception e) {
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

    private static Object nullToPrimitiveType(final Class<?> type) {
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

        return null;
    }

    protected Object doConvertTo(
            final Class<?> type, final Exchange exchange, final Object value,
            final boolean tryConvert) {

        if (value == null) {
            // no type conversion was needed
            if (!tryConvert) {
                statistics.incrementNoop();
            }

            // lets avoid NullPointerException when converting to primitives for null values
            if (type.isPrimitive()) {
                return nullToPrimitiveType(type);
            }
            return null;
        }

        // same instance type
        if (type.isInstance(value)) {
            // no type conversion was needed

            if (!tryConvert) {
                statistics.incrementNoop();
            }

            return value;
        }

        if (!tryConvert) {
            statistics.incrementAttempt();
        }

        // attempt bulk first which is the fastest (also taking into account primitives)
        final Class<?> aClass = type.isPrimitive() ? ObjectHelper.convertPrimitiveTypeToWrapperType(type) : type;
        final TypeConvertible<?, ?> typeConvertible = new TypeConvertible<>(value.getClass(), aClass);

        final Object ret = tryCachedConverters(type, exchange, value, typeConvertible);
        if (ret != null) {
            return ret;
        }

        // fallback converters
        final Object fallBackRet = tryFallback(type, exchange, value, tryConvert, typeConvertible);
        if (fallBackRet != null) {
            return fallBackRet;
        }

        final TypeConverter assignableConverter = tryAssignableFrom(typeConvertible, converters);
        if (assignableConverter != null) {
            converters.put(typeConvertible, assignableConverter);
            return assignableConverter.convertTo(type, exchange, value);
        }

        // This is the last resort: if nothing else works, try to find something that converts from an Object to the target type
        final TypeConverter objConverter = converters.get(new TypeConvertible<>(Object.class, type));
        if (objConverter != null) {
            converters.put(typeConvertible, objConverter);
            return objConverter.convertTo(type, exchange, value);
        }

        converters.put(typeConvertible, MISS_CONVERTER);

        // Could not find suitable conversion, so return Void to indicate not found
        return TypeConverter.MISS_VALUE;
    }

    private Object tryCachedConverters(Class<?> type, Exchange exchange, Object value, TypeConvertible<?, ?> typeConvertible) {
        final TypeConverter typeConverter = converters.get(typeConvertible);
        if (typeConverter != null) {
            final Object ret = typeConverter.convertTo(type, exchange, value);
            if (ret != null) {
                return ret;
            }
        }

        final TypeConverter superConverterTc = TypeResolverHelper.tryMatch(typeConvertible, converters);
        if (superConverterTc != null) {
            final Object ret = superConverterTc.convertTo(type, exchange, value);
            if (ret != null) {
                converters.put(typeConvertible, superConverterTc);
                return ret;
            }
        }

        return null;
    }

    private Object tryFallback(
            final Class<?> type, final Exchange exchange, final Object value, boolean tryConvert,
            TypeConvertible<?, ?> typeConvertible) {
        for (FallbackTypeConverter fallback : fallbackConverters) {
            TypeConverter tc = fallback.getFallbackTypeConverter();

            Object rc = doConvert(type, exchange, value, tryConvert, tc);
            if (rc == null && tc.allowNull()) {
                return null;
            }

            if (rc == TypeConverter.MISS_VALUE) {
                // it cannot be converted so give up
                return TypeConverter.MISS_VALUE;
            }

            if (rc != null) {
                converters.put(typeConvertible, tc);
                // if fallback can promote then let it be promoted to a first class type converter
                if (fallback.isCanPromote()) {
                    // add it as a known type converter since we found a fallback that could do it
                    addOrReplaceTypeConverter(tc, typeConvertible);
                }
                // return converted value
                return rc;
            }
        }

        return null;
    }

    private static Object doConvert(
            Class<?> type, Exchange exchange, Object value, boolean tryConvert, TypeConverter converter) {

        if (tryConvert) {
            return converter.tryConvertTo(type, exchange, value);
        } else {
            return converter.convertTo(type, exchange, value);
        }
    }

    public TypeConverter getTypeConverter(Class<?> toType, Class<?> fromType) {
        return converters.get(new TypeConvertible<>(fromType, toType));
    }

    @Override
    public void addConverter(TypeConvertible<?, ?> typeConvertible, TypeConverter typeConverter) {
        converters.put(typeConvertible, typeConverter);
    }

    @Override
    public void addBulkTypeConverters(BulkTypeConverters bulkTypeConverters) {
        // NO-OP
    }

    public void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter) {
        LOG.trace("Adding type converter: {}", typeConverter);
        final TypeConvertible<?, ?> typeConvertible = new TypeConvertible<>(fromType, toType);

        addOrReplaceTypeConverter(typeConverter, typeConvertible);
    }

    private void addOrReplaceTypeConverter(TypeConverter typeConverter, TypeConvertible<?, ?> typeConvertible) {
        TypeConverter converter = converters.get(typeConvertible);

        if (converter == MISS_CONVERTER) {
            // we have previously attempted to convert but missed, so add this converter
            converters.put(typeConvertible, typeConverter);
            return;
        }

        // only override it if its different
        // as race conditions can lead to many threads trying to promote the same fallback converter
        if (typeConverter != converter) {

            // add the converter unless we should ignore
            boolean add = true;

            // if converter is not null then a duplicate exists
            if (converter != null) {
                add = onTypeConverterExists(typeConverter, typeConvertible, converter);
            }

            if (add) {
                converters.put(typeConvertible, typeConverter);
            }
        }
    }

    private boolean onTypeConverterExists(
            TypeConverter typeConverter, TypeConvertible<?, ?> typeConvertible, TypeConverter converter) {
        if (typeConverterExists == TypeConverterExists.Override) {
            CamelLogger logger = new CamelLogger(LOG, typeConverterExistsLoggingLevel);
            logger.log("Overriding type converter from: " + converter + " to: " + typeConverter);

            return true;
        } else if (typeConverterExists == TypeConverterExists.Ignore) {
            CamelLogger logger = new CamelLogger(LOG, typeConverterExistsLoggingLevel);
            logger.log("Ignoring duplicate type converter from: " + converter + " to: " + typeConverter);
            return false;
        }

        // we should fail
        throw new TypeConverterExistsException(typeConvertible.getTo(), typeConvertible.getFrom());
    }

    public boolean removeTypeConverter(Class<?> toType, Class<?> fromType) {
        LOG.trace("Removing type converter from: {} to: {}", fromType, toType);
        final TypeConverter removed = converters.remove(new TypeConvertible<>(fromType, toType));
        return removed != null;
    }

    @Override
    public void addTypeConverters(Object typeConverters) {
        throw new UnsupportedOperationException();
    }

    public void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote) {
        LOG.trace("Adding fallback type converter: {} which can promote: {}", typeConverter, canPromote);

        // add in top of fallback as the toString() fallback will nearly always be able to convert
        // the last one which is add to the FallbackTypeConverter will be called at the first place
        fallbackConverters.add(0, new FallbackTypeConverter(typeConverter, canPromote));
    }

    public TypeConverter lookup(Class<?> toType, Class<?> fromType) {
        return doLookup(toType, fromType);
    }

    @Deprecated(since = "4.0.0")
    protected TypeConverter getOrFindTypeConverter(Class<?> toType, Class<?> fromType) {
        TypeConvertible<?, ?> typeConvertible = new TypeConvertible<>(fromType, toType);

        TypeConverter converter = converters.get(typeConvertible);
        if (converter == null) {
            // converter not found, try to lookup then
            converter = lookup(toType, fromType);
            if (converter != null) {
                converters.put(typeConvertible, converter);
            }
        }
        return converter;
    }

    protected TypeConverter doLookup(Class<?> toType, Class<?> fromType) {
        return TypeResolverHelper.doLookup(toType, fromType, converters);
    }

    protected TypeConversionException createTypeConversionException(
            Exchange exchange, Class<?> type, Object value, Throwable cause) {
        if (cause instanceof TypeConversionException tce) {
            if (tce.getToType() == type) {
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
        return converters.size();
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
    protected void doBuild() throws Exception {
        super.doBuild();
        CamelContextAware.trySetCamelContext(enumTypeConverter, getCamelContext());
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // log utilization statistics when stopping, including mappings
        statistics.logMappingStatisticsMessage(converters, MISS_CONVERTER);

        statistics.reset();
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
