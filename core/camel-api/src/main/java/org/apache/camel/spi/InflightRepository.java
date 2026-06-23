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

import org.apache.camel.Exchange;
import org.apache.camel.StaticService;
import org.jspecify.annotations.Nullable;

/**
 * A live registry that tracks every {@link org.apache.camel.Exchange} that is currently being processed within the
 * {@link org.apache.camel.CamelContext}.
 * <p/>
 * The routing engine calls {@link #add(org.apache.camel.Exchange)} when an exchange enters processing and
 * {@link #remove(org.apache.camel.Exchange)} when it completes (successfully or with failure). Per-route counts are
 * available via {@link #size(String)}; the full list of inflight exchanges is accessible via {@link #browse()}, which
 * returns {@link InflightExchange} snapshots with elapsed-time and duration metrics.
 * {@link org.apache.camel.spi.ShutdownStrategy} polls this repository to wait until the inflight count reaches zero
 * before completing a graceful shutdown. The JMX management layer and developer console expose the inflight data for
 * operational visibility.
 * <p/>
 * See <a href="https://camel.apache.org/manual/graceful-shutdown.html">Graceful Shutdown</a> in the Camel user manual.
 *
 * @see ShutdownStrategy
 */
public interface InflightRepository extends StaticService {

    /**
     * Information about the inflight exchange.
     */
    interface InflightExchange {

        /**
         * The exchange being inflight
         */
        Exchange getExchange();

        /**
         * The duration in millis the exchange has been inflight
         */
        long getDuration();

        /**
         * The elapsed time in millis processing the exchange at the current node
         */
        long getElapsed();

        /**
         * The id of the node from the route where the exchange currently is being processed
         * <p/>
         * Is <tt>null</tt> if message history is disabled.
         */
        @Nullable
        String getNodeId();

        /**
         * The id of the route where the exchange originates (started)
         */
        String getFromRouteId();

        /**
         * Whether the endpoint is remote where the exchange originates (started)
         */
        boolean isFromRemoteEndpoint();

        /**
         * The id of the route where the exchange currently is being processed
         * <p/>
         * Is <tt>null</tt> if message history is disabled.
         */
        @Nullable
        String getAtRouteId();

    }

    /**
     * Adds the exchange to the inflight registry to the total counter
     *
     * @param exchange the exchange
     */
    void add(Exchange exchange);

    /**
     * Removes the exchange from the inflight registry to the total counter
     *
     * @param exchange the exchange
     */
    void remove(Exchange exchange);

    /**
     * Adds the exchange to the inflight registry associated to the given route
     *
     * @param exchange the exchange
     * @param routeId  the id of the route
     */
    void add(Exchange exchange, String routeId);

    /**
     * Removes the exchange from the inflight registry removing association to the given route
     *
     * @param exchange the exchange
     * @param routeId  the id of the route
     */
    void remove(Exchange exchange, String routeId);

    /**
     * Current size of inflight exchanges.
     * <p/>
     * Will return 0 if there are no inflight exchanges.
     *
     * @return number of exchanges currently in flight.
     */
    int size();

    /**
     * Adds the route from the in flight registry.
     * <p/>
     * Is used for initializing up resources
     *
     * @param routeId the id of the route
     */
    void addRoute(String routeId);

    /**
     * Removes the route from the in flight registry.
     * <p/>
     * Is used for cleaning up resources to avoid leaking.
     *
     * @param routeId the id of the route
     */
    void removeRoute(String routeId);

    /**
     * Current size of inflight exchanges which are from the given route.
     * <p/>
     * Will return 0 if there are no inflight exchanges.
     *
     * @param  routeId the id of the route
     * @return         number of exchanges currently in flight.
     */
    int size(String routeId);

    /**
     * Whether the inflight repository should allow browsing each inflight exchange.
     *
     * This is by default disabled as there is a very slight performance overhead when enabled.
     */
    boolean isInflightBrowseEnabled();

    /**
     * Whether the inflight repository should allow browsing each inflight exchange.
     *
     * This is by default disabled as there is a very slight performance overhead when enabled.
     *
     * @param inflightBrowseEnabled whether browsing is enabled
     */
    void setInflightBrowseEnabled(boolean inflightBrowseEnabled);

    /**
     * A <i>read-only</i> browser of the {@link InflightExchange}s that are currently inflight.
     */
    Collection<InflightExchange> browse();

    /**
     * A <i>read-only</i> browser of the {@link InflightExchange}s that are currently inflight that started from the
     * given route.
     *
     * @param fromRouteId the route id, or <tt>null</tt> for all routes.
     */
    Collection<InflightExchange> browse(@Nullable String fromRouteId);

    /**
     * A <i>read-only</i> browser of the {@link InflightExchange}s that are currently inflight.
     *
     * @param limit                 maximum number of entries to return
     * @param sortByLongestDuration to sort by the longest duration. Set to <tt>true</tt> to include the exchanges that
     *                              has been inflight the longest time, set to <tt>false</tt> to sort by exchange id
     */
    Collection<InflightExchange> browse(int limit, boolean sortByLongestDuration);

    /**
     * A <i>read-only</i> browser of the {@link InflightExchange}s that are currently inflight that started from the
     * given route.
     *
     * @param fromRouteId           the route id, or <tt>null</tt> for all routes.
     * @param limit                 maximum number of entries to return
     * @param sortByLongestDuration to sort by the longest duration. Set to <tt>true</tt> to include the exchanges that
     *                              has been inflight the longest time, set to <tt>false</tt> to sort by exchange id
     */
    Collection<InflightExchange> browse(@Nullable String fromRouteId, int limit, boolean sortByLongestDuration);

    /**
     * Gets the oldest {@link InflightExchange} that are currently inflight that started from the given route.
     *
     * @param  fromRouteId the route id, or <tt>null</tt> for all routes.
     * @return             the oldest, or <tt>null</tt> if none inflight
     */
    @Nullable
    InflightExchange oldest(@Nullable String fromRouteId);

}
