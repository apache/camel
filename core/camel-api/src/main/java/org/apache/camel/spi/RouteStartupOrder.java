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

import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Route;
import org.apache.camel.Service;

/**
 * Captures the resolved startup order and associated services for a {@link Route}, used by
 * {@link org.apache.camel.CamelContext} and {@link ShutdownStrategy} to control start and shutdown sequencing.
 * <p/>
 * At startup, the context builds an ordered list of {@code RouteStartupOrder} instances from each route's configured
 * {@code startupOrder} attribute and passes it to the {@link ShutdownStrategy} at shutdown time (in reverse order, by
 * default). This allows routes with dependencies to be stopped before the routes they depend on.
 * <p/>
 * See <a href="https://camel.apache.org/manual/configuring-route-startup-ordering-and-autostartup.html"> Configuring
 * Route Startup Ordering and Auto Startup</a> for details.
 *
 * @see ShutdownStrategy
 * @see RouteController
 */
public interface RouteStartupOrder {

    /**
     * Get the order this route should be started.
     * <p/>
     * See more at <a href="https://camel.apache.org/configuring-route-startup-ordering-and-autostartup.html">
     * configuring route startup ordering</a>.
     *
     * @return the order
     */
    int getStartupOrder();

    /**
     * Gets the route
     *
     * @return the route
     */
    Route getRoute();

    /**
     * Gets the input to this route
     *
     * @return the consumer.
     */
    Consumer getInput();

    /**
     * Gets the services to this route.
     *
     * @return the services.
     */
    List<Service> getServices();

}
