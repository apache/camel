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

/**
 * Factory for creating and caching {@link FactoryFinder} instances, each scoped to a specific resource-path prefix on
 * the classpath.
 * <p/>
 * The {@link org.apache.camel.CamelContext} holds one {@code FactoryFinderResolver} and uses it to obtain finders for
 * the standard {@link FactoryFinder#DEFAULT_PATH} as well as for bootstrap-phase lookups that must complete before the
 * context is fully started. Separating bootstrap and runtime finders allows implementations to apply different caching
 * or isolation strategies during the two lifecycle phases.
 *
 * @see FactoryFinder
 * @see ClassResolver
 */
public interface FactoryFinderResolver {

    /**
     * Creates a new default factory finder using a default resource path.
     *
     * @param  classResolver the class resolver to use
     * @return               a factory finder.
     */
    default FactoryFinder resolveDefaultFactoryFinder(ClassResolver classResolver) {
        return resolveFactoryFinder(classResolver, FactoryFinder.DEFAULT_PATH);
    }

    /**
     * Creates a new bootstrap factory finder using a default resource path.
     *
     * @param  classResolver the class resolver to use
     * @return               a factory finder.
     */
    default FactoryFinder resolveBootstrapFactoryFinder(ClassResolver classResolver) {
        return resolveBootstrapFactoryFinder(classResolver, FactoryFinder.DEFAULT_PATH);
    }

    /**
     * Creates a new factory finder.
     *
     * @param  classResolver the class resolver to use
     * @param  resourcePath  the resource path as base to lookup files within
     * @return               a factory finder.
     */
    FactoryFinder resolveFactoryFinder(ClassResolver classResolver, String resourcePath);

    /**
     * Creates a new factory finder.
     *
     * @param  classResolver the class resolver to use
     * @param  resourcePath  the resource path as base to lookup files within
     * @return               a factory finder.
     */
    FactoryFinder resolveBootstrapFactoryFinder(ClassResolver classResolver, String resourcePath);

}
