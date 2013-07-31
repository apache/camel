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

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.StaticService;

/**
 * A repository which tracks in flight {@link Exchange}s.
 *
 * @version 
 */
public interface InflightRepository extends StaticService {

    /**
     * Adds the exchange to the inflight registry to the total counter
     *
     * @param exchange  the exchange
     */
    void add(Exchange exchange);

    /**
     * Removes the exchange from the inflight registry to the total counter
     *
     * @param exchange  the exchange
     */
    void remove(Exchange exchange);

    /**
     * Adds the exchange to the inflight registry associated to the given route
     *
     * @param exchange  the exchange
     * @param routeId the id of the route
     */
    void add(Exchange exchange, String routeId);

    /**
     * Removes the exchange from the inflight registry removing association to the given route
     *
     * @param exchange  the exchange
     * @param routeId the id of the route
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
     * Will always return 0 due method is deprecated.
     * @deprecated will be removed in a future Camel release.
     */
    @Deprecated
    int size(Endpoint endpoint);

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
     * @param routeId the id of the route
     * @return number of exchanges currently in flight.
     */
    int size(String routeId);

}
