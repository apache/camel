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
package org.apache.camel.component.reactive.streams.api;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * The interface to which any implementation of the reactive-streams engine should comply.
 */
public interface CamelReactiveStreamsService extends CamelContextAware, Service {

    /*
     * Main API methods.
     */

    /**
     * Returns the publisher associated to the given stream name.
     * A publisher can be used to push Camel exchanges to reactive-streams subscribers.
     *
     * @param name the stream name
     * @return the stream publisher
     */
    Publisher<Exchange> getPublisher(String name);

    /**
     * Returns the publisher associated to the given stream name.
     * A publisher can be used to push Camel exchange to external reactive-streams subscribers.
     *
     * The publisher converts automatically exchanges to the given type.
     *
     * @param name the stream name
     * @param type the type of the emitted items
     * @param <T> the type of items emitted by the publisher
     * @return the publisher associated to the stream
     */
    <T> Publisher<T> getPublisher(String name, Class<T> type);

    /**
     * Returns the subscriber associated to the given stream name.
     * A subscriber can be used to push items coming from external reactive-streams publishers to Camel routes.
     *
     * @param name the stream name
     * @return the subscriber associated with the stream
     */
    Subscriber<Exchange> getSubscriber(String name);

    /**
     * Returns the subscriber associated to the given stream name.
     * A subscriber can be used to push items coming from external reactive-streams publishers to Camel routes.
     *
     * The subscriber converts automatically items of the given type to exchanges before pushing them.
     *
     * @param name the stream name
     * @param type the publisher converts automatically exchanges to the given type.
     * @param <T> the type of items accepted by the subscriber
     * @return the subscriber associated with the stream
     */
    <T> Subscriber<T> getSubscriber(String name, Class<T> type);

    /*
     * Methods for Camel producers.
     */

    /**
     * Used by Camel to associate the publisher of the stream with the given name to a specific Camel producer.
     * This method is used to bind a Camel route to a reactive stream.
     *
     * @param name the stream name
     * @param producer the producer of the route
     * @throws IllegalStateException if another producer is already associated with the given stream name
     */
    void attachCamelProducer(String name, ReactiveStreamsProducer producer);

    /**
     * Used by Camel to detach the existing producer from the given stream.
     *
     * @param name the stream name
     */
    void detachCamelProducer(String name);

    /**
     * Used by Camel to send the exchange to all active subscriptions on the given stream.
     * The callback is used to signal that the exchange has been delivered to the subscribers.
     *
     * @param name the stream name
     * @param exchange the exchange to be forwarded to the external subscribers
     * @param callback the callback that signals the delivery of the exchange
     */
    void process(String name, Exchange exchange, DispatchCallback<Exchange> callback);

    /*
     * Methods for Camel consumers.
     */

    /**
     * Used by Camel to associate the subscriber of the stream with the given name to a specific Camel consumer.
     * This method is used to bind a Camel route to a reactive stream.
     *
     * @param name the stream name
     * @param consumer the consumer of the route
     * @throws IllegalStateException if another consumer is already associated with the given stream name
     */
    void attachCamelConsumer(String name, ReactiveStreamsConsumer consumer);

    /**
     * Used by Camel to detach the existing consumer from the given stream.
     *
     * @param name the stream name
     */
    void detachCamelConsumer(String name);

}
