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

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.jspecify.annotations.Nullable;

/**
 * Pluggable strategy for autoloading a {@link org.apache.camel.Component} instance from a URI scheme the first time
 * that scheme is encountered by a {@link org.apache.camel.CamelContext}.
 * <p/>
 * When a route references an endpoint URI whose scheme is not yet registered in the context, Camel queries the active
 * {@code ComponentResolver} with the scheme name. The default implementation reads the service class name from
 * {@code META-INF/services/org/apache/camel/component/<name>} on the classpath, instantiates it, and adds it to the
 * context. Alternative resolvers (for example Spring, OSGi, or custom container integrations) may satisfy the lookup
 * from their own registries instead.
 * <p/>
 * Component discovery is lazy: only components that are actually referenced in routes are loaded.
 *
 * @see ComponentNameResolver
 * @see org.apache.camel.Component
 */
public interface ComponentResolver {

    /**
     * Attempts to resolve the component for the given URI
     *
     * @param  name      the component name to resolve
     * @param  context   the context to load the component if it can be resolved
     * @return           the component which is added to the context or null if it cannot be resolved
     * @throws Exception is thrown if the component could not be loaded
     */
    @Nullable
    Component resolveComponent(String name, CamelContext context) throws Exception;
}
