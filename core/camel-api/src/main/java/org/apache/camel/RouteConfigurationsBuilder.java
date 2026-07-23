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

/**
 * Low-level SPI interface for objects that can contribute route configurations to a {@link CamelContext}.
 * <p/>
 * Route configurations are shared cross-cutting concerns (such as error handlers, interceptors, and on-exception
 * clauses) that can be applied to multiple {@link Route}s without repeating them inside each individual route
 * definition. They are built alongside routes using {@code RouteConfigurationBuilder} (the Java DSL subtype) or the
 * YAML / XML equivalents.
 * <p/>
 * This interface is analogous to {@link RoutesBuilder} but targets configuration artifacts rather than executable
 * routes. Implementations register the configurations in the {@link CamelContext} prior to route startup so that each
 * route can resolve them by id.
 *
 * @see   RoutesBuilder
 * @see   CamelContext
 * @since 3.12
 */
public interface RouteConfigurationsBuilder {

    /**
     * Adds the route configurations from this builder to the CamelContext.
     *
     * @param  context   the Camel context
     * @throws Exception is thrown if initialization of route configurations failed
     */
    void addRouteConfigurationsToCamelContext(CamelContext context) throws Exception;

    /**
     * Adds or updates the route configurations from this builder to the CamelContext.
     *
     * @param  context   the Camel context
     * @throws Exception is thrown if initialization of route configurations failed
     */
    void updateRouteConfigurationsToCamelContext(CamelContext context) throws Exception;

}
