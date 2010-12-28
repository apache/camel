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
package org.apache.camel.component.routebox.strategy;

import java.net.URI;
import java.util.List;

import org.apache.camel.Exchange;

/**
 * A strategy for identifying the route consumer in the routebox where the exchange should to be dispatched
 */
public interface RouteboxDispatchStrategy {

    /**
     * Receives an incoming exchange and consumer list and identifies the inner route consumer for dispatching the exchange
     *
     * @param destinations the list of possible real-time inner route consumers available
     *        to where the exchange can be dispatched in the routebox
     * @param exchange the incoming exchange
     * @return a selected consumer to whom the exchange can be directed
     * @throws Exception is thrown if error
     */
    URI selectDestinationUri(List<URI> destinations, Exchange exchange) throws Exception;
} 
