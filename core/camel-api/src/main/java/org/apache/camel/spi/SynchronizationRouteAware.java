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

import org.apache.camel.Exchange;
import org.apache.camel.Route;

/**
 * An extended {@link org.apache.camel.spi.Synchronization} which is route aware.
 */
public interface SynchronizationRouteAware extends Synchronization {

    /**
     * Invoked before the {@link org.apache.camel.Exchange} is being routed by the given route.
     * <p/>
     * Notice if the exchange is being routed through multiple routes, there will be callbacks for each route.
     * <p/>
     * <b>Important:</b> this callback may not invoked if the {@link org.apache.camel.spi.SynchronizationRouteAware} implementation
     * is being added to the {@link org.apache.camel.spi.UnitOfWork} after the routing has started.
     *
     * @param route     the route
     * @param exchange  the exchange
     */
    void onBeforeRoute(Route route, Exchange exchange);

    /**
     * Invoked after the {@link org.apache.camel.Exchange} has been routed by the given route.
     * <p/>
     * Notice if the exchange is being routed through multiple routes, there will be callbacks for each route.
     * <p/>
     * This invocation happens before these callbacks:
     * <ul>
     *     <li>The consumer of the route writes any response back to the caller (if in InOut mode)</li>
     *     <li>The UoW is done calling either {@link #onComplete(org.apache.camel.Exchange)} or {@link #onFailure(org.apache.camel.Exchange)}</li>
     * </ul>
     * This allows custom logic to be executed after all routing is done, but before the {@link org.apache.camel.Consumer} prepares and writes
     * any data back to the caller (if in InOut mode).
     *
     * @param route     the route
     * @param exchange  the exchange
     */
    void onAfterRoute(Route route, Exchange exchange);

}
