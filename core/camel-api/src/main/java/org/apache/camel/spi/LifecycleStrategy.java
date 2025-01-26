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

import java.util.Collection;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.VetoCamelContextStartException;

/**
 * Strategy for lifecycle notifications.
 */
public interface LifecycleStrategy {

    /**
     * Notification on initializing a {@link CamelContext}.
     *
     * @param  context                        the camel context
     * @throws VetoCamelContextStartException can be thrown to veto starting {@link CamelContext}. Any other runtime
     *                                        exceptions will be logged at <tt>WARN</tt> level by Camel will continue
     *                                        starting itself.
     */
    default void onContextInitializing(CamelContext context) throws VetoCamelContextStartException {
    }

    /**
     * Notification on initialized {@link CamelContext}.
     *
     * @param  context                        the camel context
     * @throws VetoCamelContextStartException can be thrown to veto starting {@link CamelContext}. Any other runtime
     *                                        exceptions will be logged at <tt>WARN</tt> level by Camel will continue
     *                                        starting itself.
     */
    default void onContextInitialized(CamelContext context) throws VetoCamelContextStartException {
    }

    /**
     * Notification on starting a {@link CamelContext}.
     *
     * @param  context                        the camel context
     * @throws VetoCamelContextStartException can be thrown to veto starting {@link CamelContext}. Any other runtime
     *                                        exceptions will be logged at <tt>WARN</tt> level by Camel will continue
     *                                        starting itself.
     */
    default void onContextStarting(CamelContext context) throws VetoCamelContextStartException {
    }

    /**
     * Notification on started {@link CamelContext}.
     *
     * @param context the camel context
     */
    default void onContextStarted(CamelContext context) {
    }

    /**
     * Notification on stopping a {@link CamelContext}.
     *
     * @param context the camel context
     */
    default void onContextStopping(CamelContext context) {
    }

    /**
     * Notification on stopped {@link CamelContext}.
     *
     * @param context the camel context
     */
    default void onContextStopped(CamelContext context) {
    }

    /**
     * Notification on adding an {@link Component}.
     *
     * @param name      the unique name of this component
     * @param component the added component
     */
    void onComponentAdd(String name, Component component);

    /**
     * Notification on removing an {@link Component}.
     *
     * @param name      the unique name of this component
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
     * Notification on {@link DataFormat} being resolved from the {@link Registry}
     *
     * @param name       the unique name of the {@link DataFormat}
     * @param dataFormat the resolved {@link DataFormat}
     */
    default void onDataFormatCreated(String name, DataFormat dataFormat) {
    }

    /**
     * Notification on a {@link Language} instance being resolved.
     *
     * @param name     the unique name of the {@link Language}
     * @param language the created {@link Language}
     */
    default void onLanguageCreated(String name, Language language) {
    }

    /**
     * Notification on adding a {@link Service}.
     *
     * @param context the camel context
     * @param service the added service
     * @param route   the route the service belongs to if any possible to determine
     */
    void onServiceAdd(CamelContext context, Service service, Route route);

    /**
     * Notification on removing a {@link Service}.
     *
     * @param context the camel context
     * @param service the removed service
     * @param route   the route the service belongs to if any possible to determine
     */
    void onServiceRemove(CamelContext context, Service service, Route route);

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
     * Notification on creating {@link Route}(s).
     *
     * @param route the created route context
     */
    void onRouteContextCreate(Route route);

    /**
     * Notification on adding a thread pool.
     *
     * @param camelContext        the camel context
     * @param threadPool          the thread pool
     * @param id                  id of the thread pool (can be null in special cases)
     * @param sourceId            id of the source creating the thread pool (can be null in special cases)
     * @param routeId             id of the route for the source (is null if no source)
     * @param threadPoolProfileId id of the thread pool profile, if used for creating this thread pool (can be null)
     */
    void onThreadPoolAdd(
            CamelContext camelContext, ThreadPoolExecutor threadPool, String id,
            String sourceId, String routeId, String threadPoolProfileId);

    /**
     * Notification on removing a thread pool.
     *
     * @param camelContext the camel context
     * @param threadPool   the thread pool
     */
    void onThreadPoolRemove(CamelContext camelContext, ThreadPoolExecutor threadPool);

}
