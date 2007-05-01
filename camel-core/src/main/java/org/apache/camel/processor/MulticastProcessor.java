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
import org.apache.camel.Producer;
import org.apache.camel.impl.ServiceSupport;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Implements the Multicast pattern to send a message exchange to a number of endpoints, each endpoint receiving a copy of
 * the message exchange.
 *
 * @version $Revision$
 */
public class MulticastProcessor<E extends Exchange> extends ServiceSupport implements Processor<E> {
    private Collection<Producer<E>> producers;

    /**
     * A helper method to convert a list of endpoints into a list of processors
     */
    public static <E extends Exchange> Collection<Producer<E>> toProducers(Collection<Endpoint<E>> endpoints) throws Exception {
        Collection<Producer<E>> answer = new ArrayList<Producer<E>>();
        for (Endpoint<E> endpoint : endpoints) {
            answer.add(endpoint.createProducer());
        }
        return answer;
    }

    public MulticastProcessor(Collection<Endpoint<E>> endpoints) throws Exception {
        this.producers = toProducers(endpoints);
    }

    @Override
    public String toString() {
        return "Multicast" + getEndpoints();
    }

    public void process(E exchange) throws Exception {
        for (Producer<E> producer : producers) {
            E copy = copyExchangeStrategy(producer, exchange);
            producer.process(copy);
        }
    }

    protected void doStop() throws Exception {
        for (Producer<E> producer : producers) {
            producer.stop();
        }
    }

    protected void doStart() throws Exception {
        for (Producer<E> producer : producers) {
            producer.start();
        }
    }

    /**
     * Returns the producers to multicast to
     */
    public Collection<Producer<E>> getProducers() {
        return producers;
    }

    /**
     * Returns the list of endpoints
     */
    public Collection<Endpoint<E>> getEndpoints() {
        Collection<Endpoint<E>> answer = new ArrayList<Endpoint<E>>();
        for (Producer<E> producer : producers) {
            answer.add(producer.getEndpoint());
        }
        return answer;
    }

    /**
     * Strategy method to copy the exchange before sending to another endpoint. Derived classes such as the
     * {@link Pipeline} will not clone the exchange
     *
     * @param producer the producer that will send the exchange
     * @param exchange @return the current exchange if no copying is required such as for a pipeline otherwise a new copy of the exchange is returned.
     */
    protected E copyExchangeStrategy(Producer<E> producer, E exchange) {
        return producer.createExchange(exchange);
    }
}
