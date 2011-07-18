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
package org.apache.camel.karaf.commands;

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.model.RouteDefinition;

/**
 * CamelController interface defines the expected behaviors to manipulate Camel resources (context, route, etc).
 */
public interface CamelController {

    /**
     * Get the list of Camel context.
     *
     * @return the list of Camel contexts.
     */
    List<CamelContext> getCamelContexts();

    /**
     * Get a Camel context identified by the given name.
     *
     * @param name the Camel context name.
     * @return the Camel context or null if not found.
     */
    CamelContext getCamelContext(String name);

    /**
     * Get all routes. If Camel context name is null, all routes from all contexts are listed.
     *
     * @param camelContextName the Camel context name. If null, all contexts are considered.
     * @return the list of the Camel routes.
     */
    List<Route> getRoutes(String camelContextName);

    /**
     * Return the route with the given route ID.
     *
     * @param routeId the route ID.
     * @param camelContextName the Camel context name.
     * @return the route.
     */
    Route getRoute(String routeId, String camelContextName);

    /**
     * Return the definition of a route identified by a ID and a Camel context.
     *
     * @param routeId the route ID.
     * @param camelContextName the Camel context.
     * @return the <code>RouteDefinition</code>.
     */
    RouteDefinition getRouteDefinition(String routeId, String camelContextName);

}
