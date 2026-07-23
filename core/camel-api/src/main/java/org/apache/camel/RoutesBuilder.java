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
package org.apache.camel;

import java.util.Set;

/**
 * Low-level SPI interface for objects that can contribute {@link Route}s to a {@link CamelContext}.
 * <p/>
 * Implementations translate a route definition (from any DSL or model) into live {@link Route} instances and register
 * them with the context. The actual routes become active after the context starts.
 * <p/>
 * This interface is not intended to be used directly by Camel application developers. Application code should extend
 * {@code org.apache.camel.builder.RouteBuilder} (Java DSL) or use the YAML / XML DSLs instead, all of which implement
 * this interface under the hood.
 *
 * @see Route
 * @see CamelContext
 * @see RouteConfigurationsBuilder
 */
public interface RoutesBuilder {

    /**
     * Adds the routes from this Route Builder to the CamelContext.
     *
     * @param  context   the Camel context
     * @throws Exception is thrown if initialization of routes failed
     */
    void addRoutesToCamelContext(CamelContext context) throws Exception;

    /**
     * Adds the templated routes from this Route Builder to the CamelContext.
     *
     * @param  context   the Camel context
     * @throws Exception is thrown if initialization of routes failed
     */
    void addTemplatedRoutesToCamelContext(CamelContext context) throws Exception;

    /**
     * Adds or updates the routes from this Route Builder to the CamelContext.
     *
     * @param  context   the Camel context
     * @return           route ids for the routes that was updated
     * @throws Exception is thrown if initialization of routes failed
     */
    Set<String> updateRoutesToCamelContext(CamelContext context) throws Exception;

}
