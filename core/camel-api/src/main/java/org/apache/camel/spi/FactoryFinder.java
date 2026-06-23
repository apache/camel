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

import java.util.Optional;

/**
 * Looks up and instantiates SPI factory classes declared in resource files on the classpath, following the
 * {@code META-INF/services/org/apache/camel/} service-file convention used throughout Camel.
 * <p/>
 * Each service file contains the fully-qualified class name of a factory implementation. Given a lookup key, the finder
 * appends the key to {@link #getResourcePath()}, reads the file, loads the named class via the {@link ClassResolver},
 * and instantiates it. This mechanism lets components and modules ship their implementations without explicit
 * registration; Camel discovers them automatically at startup via this discovery contract.
 * <p/>
 * {@link FactoryFinderResolver} creates and caches {@code FactoryFinder} instances per resource path, so each distinct
 * path has exactly one finder for the lifetime of the {@link org.apache.camel.CamelContext}.
 *
 * @see FactoryFinderResolver
 * @see ClassResolver
 */
public interface FactoryFinder {

    String DEFAULT_PATH = "META-INF/services/org/apache/camel/";

    /**
     * Gets the resource classpath.
     *
     * @return the resource classpath.
     */
    String getResourcePath();

    /**
     * Creates a new class instance using the key to lookup
     *
     * @param  key is the key to add to the path to find a text file containing the factory name
     * @return     a newly created instance (if exists)
     */
    Optional<Object> newInstance(String key);

    /**
     * Creates a new class instance using the key to lookup
     *
     * @param  key  is the key to add to the path to find a text file containing the factory name
     * @param  type the class type
     * @return      a newly created instance (if exists)
     */
    <T> Optional<T> newInstance(String key, Class<T> type);

    /**
     * Finds the given factory class using the key to lookup.
     *
     * @param  key is the key to add to the path to find a text file containing the factory name
     * @return     the factory class
     */
    Optional<Class<?>> findClass(String key);

    /**
     * Finds the optional factory class using the key to lookup.
     *
     * @param  key is the key to add to the path to find a text file containing the factory name
     * @return     the factory class if found, or <tt>null</tt> if no class existed
     */
    Optional<Class<?>> findOptionalClass(String key);

    /**
     * Clear the resolver state from previous scans.
     */
    void clear();

}
