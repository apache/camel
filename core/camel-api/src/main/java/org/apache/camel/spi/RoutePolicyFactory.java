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
import org.apache.camel.NamedNode;

/**
 * A factory to create {@link org.apache.camel.spi.RoutePolicy} and assign to routes automatic.
 */
public interface RoutePolicyFactory {

    /**
     * Creates a new {@link org.apache.camel.spi.RoutePolicy} which will be assigned to the given route.
     *
     * @param camelContext the camel context
     * @param routeId      the route id
     * @param route        the route definition
     * @return the created {@link org.apache.camel.spi.RoutePolicy}, or <tt>null</tt> to not use a policy for this route
     */
    RoutePolicy createRoutePolicy(CamelContext camelContext, String routeId, NamedNode route);
}
