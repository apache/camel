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
package org.apache.camel.spi;

import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import org.apache.camel.CamelContextAware;
import org.apache.camel.LoggingLevel;
import org.apache.camel.StaticService;
import org.apache.camel.TypeConverter;
import org.apache.camel.TypeConverterExists;

/**
 * Registry for type converters.
 * <p/>
 * The utilization {@link Statistics} is by default disabled, as it has a slight performance impact under very high
 * concurrent load. The statistics can be enabled using
 * {@link org.apache.camel.CamelContext#setTypeConverterStatisticsEnabled(Boolean)} (boolean)} method.
 */
public interface TypeConverterRegistry extends StaticService, CamelContextAware {

    /**
     * Utilization statistics of the registry.
     */
    interface Statistics {

        /**
         * Number of noop attempts (no type conversion was needed)
         */
        long getNoopCounter();

        /**
         * Number of type conversion attempts
         */
        long getAttemptCounter();

        /**
         * Number of successful conversions
         */
        long getHitCounter();

        /**
         * Number of attempts which cannot be converted as no suitable type converter exists
         */
        long getMissCounter();

        /**
         * Number of failed attempts during type conversion
         */
        long getFailedCounter();

        /**
         * Reset the counters
         */
        void reset();

        default void computeIfEnabled(LongSupplier supplier, LongConsumer consumer) {
            consumer.accept(supplier.getAsLong());
        }
    }

    /**
     * Registers a new set of type converters that are bulked together into a single {@link BulkTypeConverters} class.
     */
    void addBulkTypeConverters(BulkTypeConverters bulkTypeConverters);

    /**
     * Registers a new type converter.
     * <p/>
     * This method may throw {@link org.apache.camel.TypeConverterExistsException} if configured to fail if an existing
     * type converter already exists
     *
     * @param toType        the type to convert to
     * @param fromType      the type to convert from
     * @param typeConverter the type converter to use
     */
    void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter);

    /**
     * Removes the type converter
     *
     * @param  toType   the type to convert to
     * @param  fromType the type to convert from
     * @return          <tt>true</tt> if removed, <tt>false</tt> if the type converter didn't exist
     */
    boolean removeTypeConverter(Class<?> toType, Class<?> fromType);

    /**
     * Registers all the type converters from the instance, each converter must be implemented as a method and annotated
     * with {@link org.apache.camel.Converter}.
     *
     * @param typeConverters instance which implements the type converters
     */
    void addTypeConverters(Object typeConverters);

    /**
     * Registers a new fallback type converter
     *
     * @param typeConverter the type converter to use
     * @param canPromote    whether or not the fallback type converter can be promoted to a first class type converter
     */
    void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote);

    /**
     * Performs a lookup for a given type converter.
     *
     * @param  toType   the type to convert to
     * @param  fromType the type to convert from
     * @return          the type converter or <tt>null</tt> if not found.
     */
    TypeConverter lookup(Class<?> toType, Class<?> fromType);

    /**
     * Lookup the type converters that can convert to a given type
     *
     * @param  toType the type to convert to
     * @return        the type converters that can convert from
     */
    Map<Class<?>, TypeConverter> lookup(Class<?> toType);

    /**
     * Sets the injector to be used for creating new instances during type conversions.
     *
     * @param injector the injector
     */
    void setInjector(Injector injector);

    /**
     * Gets the injector
     *
     * @return the injector
     */
    Injector getInjector();

    /**
     * Gets the utilization statistics of this type converter registry
     *
     * @return the utilization statistics
     */
    Statistics getStatistics();

    /**
     * Number of type converters in the registry.
     *
     * @return number of type converters in the registry.
     */
    int size();

    /**
     * The logging level to use when logging that a type converter already exists when attempting to add a duplicate
     * type converter.
     * <p/>
     * The default logging level is <tt>DEBUG</tt>
     */
    LoggingLevel getTypeConverterExistsLoggingLevel();

    /**
     * The logging level to use when logging that a type converter already exists when attempting to add a duplicate
     * type converter.
     * <p/>
     * The default logging level is <tt>DEBUG</tt>
     */
    void setTypeConverterExistsLoggingLevel(LoggingLevel typeConverterExistsLoggingLevel);

    /**
     * What should happen when attempting to add a duplicate type converter.
     * <p/>
     * The default behavior is to ignore the duplicate.
     */
    TypeConverterExists getTypeConverterExists();

    /**
     * What should happen when attempting to add a duplicate type converter.
     * <p/>
     * The default behavior is to ignore the duplicate.
     */
    void setTypeConverterExists(TypeConverterExists typeConverterExists);

    /**
     * Adds a type convertible pair to the registry
     *
     * @param typeConvertible A type convertible pair
     * @param typeConverter   The type converter to associate with the type convertible pair
     */
    void addConverter(TypeConvertible<?, ?> typeConvertible, TypeConverter typeConverter);

}
