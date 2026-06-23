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
 * Per-route lifecycle hook that receives callbacks at each state change and at the beginning and end of every
 * {@link Exchange}, enabling dynamic control of route behaviour at runtime.
 * <p/>
 * A {@code RoutePolicy} can suspend or resume a route's consumer in response to external conditions, implement
 * throttling by counting inflight exchanges (as {@link org.apache.camel.throttling.ThrottlingInflightRoutePolicy}
 * does), enforce time-window scheduling, or perform custom instrumentation. Policies are attached to a route via the
 * DSL {@code .routePolicy()} call or globally via a {@link RoutePolicyFactory} registered on the context.
 * <p/>
 * See the <a href="https://camel.apache.org/manual/route-policy.html">Route Policy</a> documentation for usage patterns
 * and built-in implementations.
 *
 * @see RoutePolicyFactory
 * @see Route
 */
public interface RoutePolicy {

    /**
     * Callback invoked when the {@link Route} is being initialized
     *
     * @param route the route being initialized
     */
    void onInit(Route route);

    /**
     * Callback invoked when the {@link Route} is being removed from {@link org.apache.camel.CamelContext}
     *
     * @param route the route being removed
     */
    void onRemove(Route route);

    /**
     * Callback invoked when the {@link Route} is being started
     *
     * @param route the route being started
     */
    void onStart(Route route);

    /**
     * Callback invoked when the {@link Route} is being stopped
     *
     * @param route the route being stopped
     */
    void onStop(Route route);

    /**
     * Callback invoked when the {@link Route} is being suspended
     *
     * @param route the route being suspended
     */
    void onSuspend(Route route);

    /**
     * Callback invoked when the {@link Route} is being resumed
     *
     * @param route the route being resumed
     */
    void onResume(Route route);

    /**
     * Callback invoked when an {@link Exchange} is started being routed on the given {@link Route}
     *
     * @param route    the route where the exchange started from
     * @param exchange the created exchange
     */
    void onExchangeBegin(Route route, Exchange exchange);

    /**
     * Callback invoked when an {@link Exchange} is done being routed, where it started from the given {@link Route}
     * <p/>
     * Notice this callback is invoked when the <b>Exchange</b> is done and the {@link Route} is the route where the
     * {@link Exchange} was started. Most often its also the route where the exchange is done. However, it's possible to
     * route an {@link Exchange} to other routes using endpoints such as <b>direct</b> or <b>seda</b>. Bottom line is
     * that the {@link Route} parameter may not be the endpoint route and thus why we state it's the starting route.
     *
     * @param route    the route where the exchange started from
     * @param exchange the created exchange
     */
    void onExchangeDone(Route route, Exchange exchange);
}
