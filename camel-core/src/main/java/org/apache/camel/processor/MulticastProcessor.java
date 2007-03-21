/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Collection;

/**
 * Implements the Multicast pattern to send a message exchange to a number of endpoints, each endpoint receiving a copy of
 * the message exchange.
 *
 * @version $Revision$
 */
public class MulticastProcessor<E extends Exchange> implements Processor<E> {
    private Collection<Endpoint<E>> endpoints;

    public MulticastProcessor(Collection<Endpoint<E>> endpoints) {
        this.endpoints = endpoints;
    }

    @Override
    public String toString() {
        return "Multicast" + endpoints;
    }

    public void onExchange(E exchange) {
        for (Endpoint<E> endpoint : endpoints) {
            E copy = copyExchangeStrategy(endpoint, exchange);
            endpoint.onExchange(copy);
        }
    }

    /**
     * Returns the endpoints to multicast to
     */
    public Collection<Endpoint<E>> getEndpoints() {
        return endpoints;
    }

    /**
     * Strategy method to copy the exchange before sending to another endpoint. Derived classes such as the
     * {@link Pipeline} will not clone the exchange
     *
     * @param endpoint the endpoint that the exchange will be sent to
     * @param exchange @return the current exchange if no copying is required such as for a pipeline otherwise a new copy of the exchange is returned.
     */
    protected E copyExchangeStrategy(Endpoint<E> endpoint, E exchange) {
        return endpoint.createExchange(exchange);
    }
}
