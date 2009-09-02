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

import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.Service;

/**
 * Strategy for lifecycle notifications.
 */
public interface LifecycleStrategy {

    /**
     * Notification on starting a {@link CamelContext}.
     *
     * @param context the camel context
     */
    void onContextStart(CamelContext context);

    /**
     * Notification on stopping a {@link CamelContext}.
     *
     * @param context the camel context
     */
    void onContextStop(CamelContext context);

    /**
     * Notification on adding an {@link org.apache.camel.Component}.
     *
     * @param name the unique name of this component
     * @param component the added component
     */
    void onComponentAdd(String name, Component component);

    /**
     * Notification on removing an {@link org.apache.camel.Component}.
     *
     * @param name the unique name of this component
     * @param component the removed component
     */
    void onComponentRemove(String name, Component component);

    /**
     * Notification on adding an {@link Endpoint}.
     *
     * @param endpoint the added endpoint
     */
    void onEndpointAdd(Endpoint endpoint);

    /**
     * Notification on removing an {@link Endpoint}.
     *
     * @param endpoint the removed endpoint
     */
    void onEndpointRemove(Endpoint endpoint);

    /**
     * Notification on adding a {@link Service}.
     *
     * @param context the camel context
     * @param service the added service
     */
    void onServiceAdd(CamelContext context, Service service);

    /**
     * Notification on adding a {@link Consumer} as input to a given {@link Route}
     *
     * @param route
     * @param consumer      the consumer
     */
    void onRouteConsumerAdd(Route route, Consumer consumer);

    void onRouteConsumerRemove(Route route, Consumer consumer);

    /**
     * Notification on removing a {@link Service}.
     *
     * @param context the camel context
     * @param service the removed service
     */
    void onServiceRemove(CamelContext context, Service service);

    /**
     * Notification on adding {@link Route}(s).
     *
     * @param routes the added routes
     */
    void onRoutesAdd(Collection<Route> routes);

    /**
     * Notification on removing {@link Route}(s).
     *
     * @param routes the removed routes
     */
    void onRoutesRemove(Collection<Route> routes);

    /**
     * Notification on adding {@link RouteContext}(s).
     *
     * @param routeContext the added route context
     */
    void onRouteContextCreate(RouteContext routeContext);

    // TODO: Add more stop methods and also consider always having CamelContext as parameter

}
