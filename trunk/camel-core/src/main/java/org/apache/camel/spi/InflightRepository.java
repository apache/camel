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
import org.apache.camel.Service;

/**
 * A repository which tracks in flight {@link Exchange}s.
 *
 * @version 
 */
public interface InflightRepository extends Service {

    /**
     * Adds the exchange to the inflight registry
     *
     * @param exchange  the exchange
     */
    void add(Exchange exchange);

    /**
     * Removes the exchange from the inflight registry
     *
     * @param exchange  the exchange
     */
    void remove(Exchange exchange);

    /**
     * Current size of inflight exchanges.
     * <p/>
     * Will return 0 if there are no inflight exchanges.
     *
     * @return number of exchanges currently in flight.
     */
    int size();

    /**
     * Current size of inflight exchanges which are from the given endpoint.
     * <p/>
     * Will return 0 if there are no inflight exchanges.
     *
     * @param endpoint the endpoint where the {@link Exchange} are from.
     * @return number of exchanges currently in flight.
     */
    int size(Endpoint endpoint);

}
