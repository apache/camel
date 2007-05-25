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
package org.apache.camel;


/**
 * An <a href="http://activemq.apache.org/camel/endpoint.html">endpoint</a> implements the 
 * <a href="http://activemq.apache.org/camel/message-endpoint.html">Message Endpoint</a>
 * pattern and represents an endpoint that can send and receive message exchanges
 *
 * @see Exchange, Message
 * @version $Revision$
 */
public interface Endpoint<E extends Exchange> {

    /**
     * Returns if the endpoint should be a CamelContext singleton.  If the endpoint is a Singleton,
     * then a single Endpoint instance will be shared by all routes with the same URI.  Because the endpoint
     * is shared, it should be treated as an immutable.
     */
    boolean isSingleton();

    /**
     * Returns the string representation of the endpoint URI
     */
    String getEndpointUri();
    
    /**
     * Create a new exchange for communicating with this endpoint
     */
    E createExchange();

    /**
     * Creates a new exchange for communicating with this exchange using the given exchange to pre-populate the values
     * of the headers and messages
     */
    E createExchange(Exchange exchange);

    /**
     * Converts the given exchange to this endpoints required type
     */
    E toExchangeType(Exchange exchange);

    /**
     * Returns the context which created the endpoint
     *
     * @return the context which created the endpoint
     */
    CamelContext getContext();

    /**
     * Creates a new producer which is used send messages into the endpoint
     *
     * @return a newly created producer
     */
    Producer<E> createProducer() throws Exception;

    /**
     * Creates a new <a href="http://activemq.apache.org/camel/event-driven-consumer.html">Event Driven Consumer</a>
     * which consumes messages from the endpoint using the given processor
     *
     * @return a newly created consumer
     */
    Consumer<E> createConsumer(Processor processor) throws Exception;

    /**
     * Creates a new <a href="http://activemq.apache.org/camel/polling-consumer.html">Polling Consumer</a>
     * so that the caller can poll message exchanges from the consumer
     * using {@link PollingConsumer#receive()}, {@link PollingConsumer#receiveNoWait()} or {@link PollingConsumer#receive(long)}
     * whenever it is ready to do so rather than using the
     * <a href="http://activemq.apache.org/camel/event-driven-consumer.html">Event Based Consumer</a>
     * returned by {@link #createConsumer(Processor)}
     *
     * @return a newly created pull consumer
     * @throws Exception if the pull consumer could not be created
     */
    PollingConsumer<E> createPollingConsumer() throws Exception;
}
