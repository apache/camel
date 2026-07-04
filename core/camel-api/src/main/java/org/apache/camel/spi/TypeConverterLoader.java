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

import org.apache.camel.TypeConverterLoaderException;

/**
 * Pluggable strategy for discovering and loading {@link TypeConverter} instances into a {@link TypeConverterRegistry}
 * during {@link org.apache.camel.CamelContext} startup.
 * <p/>
 * Camel ships a default implementation that reads converter class names from
 * {@code META-INF/services/org/apache/camel/TypeConverter} resource files on the classpath, a convention that component
 * modules use to register their converters automatically without explicit wiring. Additional loaders can be supplied to
 * support alternative discovery mechanisms such as loading converters from a Spring {@code ApplicationContext} or an
 * OSGi service registry.
 *
 * @see TypeConverterRegistry
 * @see TypeConverter
 * @see org.apache.camel.Converter
 */
public interface TypeConverterLoader {

    /**
     * A pluggable strategy to load type converters into a registry from some kind of mechanism
     *
     * @param  registry                                      the registry to load the type converters into
     * @throws org.apache.camel.TypeConverterLoaderException if the type converters could not be loaded
     */
    void load(TypeConverterRegistry registry) throws TypeConverterLoaderException;
}
