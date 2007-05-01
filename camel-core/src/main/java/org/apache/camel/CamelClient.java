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
package org.apache.camel;

import org.apache.camel.Exchange;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.NoSuchEndpointException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.util.ProducerCache;
import org.apache.camel.impl.ServiceSupport;

/**
 * A Client object for working with Camel and invoking {@link Endpoint} instances with {@link Exchange} instances
 * 
 * @version $Revision$
 */
public class CamelClient<E extends Exchange> extends ServiceSupport {
    private CamelContext context;
    private ProducerCache<E> producerCache = new ProducerCache<E>();

    public CamelClient(CamelContext context) {
        this.context = context;
    }


    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param exchange the exchange to send
     */
    public E send(String endpointUri, E exchange) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        send(endpoint, exchange);
        return exchange;
    }

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpointUri the endpoint URI to send the exchange to
     * @param processor the transformer used to populate the new exchange
     */
    public E send(String endpointUri, Processor<E> processor) {
        Endpoint endpoint = resolveMandatoryEndpoint(endpointUri);
        return send(endpoint,  processor);
    }

    /**
     * Sends the exchange to the given endpoint
     *
     * @param endpoint the endpoint to send the exchange to
     * @param exchange the exchange to send
     */
    public E send(Endpoint<E> endpoint, E exchange) {
        producerCache.send(endpoint, exchange);
        return exchange;
    }

    /**
     * Sends an exchange to an endpoint using a supplied @{link Processor} to populate the exchange
     *
     * @param endpoint the endpoint to send the exchange to
     * @param processor the transformer used to populate the new exchange
     */
    public E send(Endpoint<E> endpoint, Processor<E> processor) {
        return producerCache.send(endpoint, processor);
    }

    public Producer<E> getProducer(Endpoint<E> endpoint) {
        return producerCache.getProducer(endpoint);
    }

    public CamelContext getContext() {
        return context;
    }

    protected Endpoint resolveMandatoryEndpoint(String endpointUri) {
        Endpoint endpoint = context.getEndpoint(endpointUri);
        if (endpoint == null) {
            throw new NoSuchEndpointException(endpointUri);
        }
        return endpoint;
    }

    protected void doStart() throws Exception {
        producerCache.start();
    }

    protected void doStop() throws Exception {
        producerCache.stop();
    }
}
