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

import java.util.function.Function;

import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.component.reactive.streams.ReactiveStreamsCamelSubscriber;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.spi.HasId;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * The interface to which any implementation of the reactive-streams engine should comply.
 */
public interface CamelReactiveStreamsService extends Service, HasId {

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
    Publisher<Exchange> fromStream(String name);

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
    <T> Publisher<T> fromStream(String name, Class<T> type);

    /**
     * Returns the subscriber associated to the given stream name.
     * A subscriber can be used to push items coming from external reactive-streams publishers to Camel routes.
     *
     * @param name the stream name
     * @return the subscriber associated with the stream
     */
    Subscriber<Exchange> streamSubscriber(String name);

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
    <T> Subscriber<T> streamSubscriber(String name, Class<T> type);

    /**
     * Pushes the given data into the specified Camel stream and returns a Publisher (mono) holding
     * the resulting exchange or an error.
     *
     * @param name the stream name
     * @param data the data to push
     * @return a publisher with the resulting exchange
     */
    Publisher<Exchange> toStream(String name, Object data);

    /**
     * Returns a function that pushes data into the specified Camel stream and
     * returns a Publisher (mono) holding the resulting exchange or an error.
     *
     * This is a curryied version of {@link CamelReactiveStreamsService#toStream(String, Object)}.
     *
     * @param name the stream name
     * @return a function that returns a publisher with the resulting exchange
     */
    Function<?, ? extends Publisher<Exchange>> toStream(String name);

    /**
     * Pushes the given data into the specified Camel stream and returns a Publisher (mono) holding
     * the exchange output or an error.
     *
     * @param name the stream name
     * @param data the data to push
     * @param type  the type to which the output should be converted
     * @param <T> the generic type of the resulting Publisher
     * @return a publisher with the resulting data
     */
    <T> Publisher<T> toStream(String name, Object data, Class<T> type);

    /**
     * Returns a function that pushes data into the specified Camel stream and
     * returns a Publisher (mono) holding the exchange output or an error.
     *
     * This is a curryied version of {@link CamelReactiveStreamsService#toStream(String, Object, Class)}.
     *
     * @param name the stream name
     * @param type  the type to which the output should be converted
     * @param <T> the generic type of the resulting Publisher
     * @return a function that returns a publisher with the resulting data
     */
    <T> Function<Object, Publisher<T>> toStream(String name, Class<T> type);

    /*
     * Direct client API methods
     */

    /**
     * Creates a new stream from the endpoint URI (used as Camel Consumer) and returns
     * the associated {@code Publisher}.
     *
     * If a stream has already been created, the existing {@link Publisher} is returned.
     *
     * @param uri the consumer uri
     * @return the publisher associated to the uri
     */
    Publisher<Exchange> from(String uri);

    /**
     * Creates a new stream of the given type from the endpoint URI (used as Camel Consumer) and returns
     * the associated {@code Publisher}.
     *
     * If a stream has already been created, the existing {@link Publisher} is returned.
     *
     * @param uri the consumer uri
     * @param type the type of items emitted by the publisher
     * @param <T> the type to which Camel should convert exchanges to
     * @return the publisher associated to the uri
     */
    <T> Publisher<T> from(String uri, Class<T> type);

    /**
     * Creates a new route that pushes data to the endpoint URI and returns
     * the associated {@code Subscriber}.
     *
     * This method always create a new stream.
     *
     * @param uri the target uri
     * @return the subscriber associated to the uri
     */
    Subscriber<Exchange> subscriber(String uri);

    /**
     * Creates a new route that pushes data to the endpoint URI and returns
     * the associated {@code Subscriber}.
     *
     * This method always create a new stream.
     *
     * @param uri the target uri
     * @param type the type of items that the subscriber can receive
     * @param <T> the type from which Camel should convert data to exchanges
     * @return the subscriber associated to the uri
     */
    <T> Subscriber<T> subscriber(String uri, Class<T> type);

    /**
     * Creates a new route that uses the endpoint URI as producer, pushes the given data to the route
     * and returns a {@code Publisher} that will eventually return the resulting exchange or an error.
     *
     * @param uri the producer uri
     * @param data the data to push
     * @return a publisher with the resulting exchange
     */
    Publisher<Exchange> to(String uri, Object data);

    /**
     * Creates a new route that uses the endpoint URI as producer, and returns a
     * function that pushes the data into the route and returns the
     * {@code Publisher} that holds the resulting exchange or the error.
     *
     *
     * This is a curryied version of {@link CamelReactiveStreamsService#to(String, Object)}.
     *
     * @param uri the producer uri
     * @return a function that returns a publisher with the resulting exchange
     */
    Function<Object, Publisher<Exchange>> to(String uri);

    /**
     * Creates a new route that uses the endpoint URI as producer, pushes the given data to the route
     * and returns a {@code Publisher} that will eventually return the exchange output or an error.
     *
     * @param uri the producer uri
     * @param data the data to push
     * @param type  the type to which the output should be converted
     * @param <T> the generic type of the resulting Publisher
     * @return a publisher with the resulting data
     */
    <T> Publisher<T> to(String uri, Object data, Class<T> type);

    /**
     * Creates a new route that uses the endpoint URI as producer, and returns a
     * function that pushes the data into the route and returns the
     * {@code Publisher} that holds the exchange output or an error.
     *
     * This is a curryied version of {@link CamelReactiveStreamsService#to(String, Object, Class)}.
     *
     * @param uri the producer uri
     * @param type  the type to which the output should be converted
     * @param <T> the generic type of the resulting Publisher
     * @return a function that returns a publisher with the resulting data
     */
    <T> Function<Object, Publisher<T>> to(String uri, Class<T> type);

    /**
     * Adds a processing step at the specified endpoint uri (usually a "direct:name") that delegates
     * to the given reactive processor.
     *
     * The processor receives a {@link Publisher} of exchanges and returns an object.
     * If the output of the processor is a {@link Publisher}, it will be unwrapped before
     * delivering the result to the source route.
     *
     * @param uri the uri where the processor should be attached
     * @param processor the reactive processor
     */
    void process(String uri, Function<? super Publisher<Exchange>, ?> processor);

    /**
     * Adds a processing step at the specified endpoint uri (usually a "direct:name") that delegates
     * to the given reactive processor.
     *
     * The processor receives a {@link Publisher} of items of the given type and returns an object.
     * If the output of the processor is a {@link Publisher}, it will be unwrapped before
     * delivering the result to the source route.
     *
     * @param uri the uri where the processor should be attached
     * @param type  the type to which the body of the exchange should be converted
     * @param <T> the generic type of the Publisher that should be processed
     * @param processor the reactive processor
     */
    <T> void process(String uri, Class<T> type, Function<? super Publisher<T>, ?> processor);

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
     */
    void sendCamelExchange(String name, Exchange exchange);

    /*
     * Methods for Camel consumers.
     */

    /**
     * Used by Camel to associate the subscriber of the stream with the given name to a specific Camel consumer.
     * This method is used to bind a Camel route to a reactive stream.
     *
     * @param name the stream name
     * @param consumer the consumer of the route
     * @return the associated subscriber
     * @throws IllegalStateException if another consumer is already associated with the given stream name
     */
    ReactiveStreamsCamelSubscriber attachCamelConsumer(String name, ReactiveStreamsConsumer consumer);

    /**
     * Used by Camel to detach the existing consumer from the given stream.
     *
     * @param name the stream name
     */
    void detachCamelConsumer(String name);

}
