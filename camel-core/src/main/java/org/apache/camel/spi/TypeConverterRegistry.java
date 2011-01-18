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
package org.apache.camel.spi;

import org.apache.camel.TypeConverter;

/**
 * Registry for type converters.
 *
 * @version $Revision$
 */
public interface TypeConverterRegistry {

    /**
     * Registers a new type converter
     *
     * @param toType        the type to convert to
     * @param fromType      the type to convert from
     * @param typeConverter the type converter to use
     */
    void addTypeConverter(Class<?> toType, Class<?> fromType, TypeConverter typeConverter);

    /**
     * Registers a new fallback type converter
     *
     * @param typeConverter the type converter to use
     * @param canPromote  whether or not the fallback type converter can be promoted to a first class type converter
     */
    void addFallbackTypeConverter(TypeConverter typeConverter, boolean canPromote);

    /**
     * Performs a lookup for a given type converter.
     *
     * @param toType        the type to convert to
     * @param fromType      the type to convert from
     * @return the type converter or <tt>null</tt> if not found.
     */
    TypeConverter lookup(Class<?> toType, Class<?> fromType);

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
}
