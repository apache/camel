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
package org.apache.camel.component.reactor.engine;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsBackpressureStrategy;
import org.apache.camel.component.reactive.streams.ReactiveStreamsDiscardedException;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.util.ObjectHelper;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.SynchronousSink;
import reactor.util.concurrent.Queues;

final class ReactorCamelProcessor implements Closeable {
    private final String name;
    private final EmitterProcessor<Exchange> publisher;
    private final AtomicReference<FluxSink<Exchange>> camelSink;

    private final ReactorStreamsService service;
    private ReactiveStreamsProducer camelProducer;

    ReactorCamelProcessor(ReactorStreamsService service, String name) {
        this.service = service;
        this.name = name;

        this.camelProducer = null;
        this.camelSink = new AtomicReference<>();

        // TODO: The perfect emitter processor would have no buffer (0 sized)
        // The chain caches one more item than expected.
        // This implementation has (almost) full control over backpressure, but it's too chatty.
        // There's a ticket to improve chattiness of the reactive-streams internal impl.
        this.publisher = EmitterProcessor.create(1, false);
    }

    @Override
    public void close() throws IOException {
    }

    Publisher<Exchange> getPublisher() {
        return publisher;
    }

    synchronized void attach(ReactiveStreamsProducer producer) {
        Objects.requireNonNull(producer, "producer cannot be null, use the detach method");

        if (this.camelProducer != null) {
            throw new IllegalStateException("A producer is already attached to the stream '" + name + "'");
        }

        if (this.camelProducer != producer) {
            detach();

            ReactiveStreamsBackpressureStrategy strategy = producer.getEndpoint().getBackpressureStrategy();
            Flux<Exchange> flux = Flux.create(camelSink::set, FluxSink.OverflowStrategy.IGNORE);

            if (ObjectHelper.equal(strategy, ReactiveStreamsBackpressureStrategy.OLDEST)) {
                // signal item emitted for non-dropped items only
                flux = flux.onBackpressureDrop(this::onBackPressure).handle(this::onItemEmitted);
            } else if (ObjectHelper.equal(strategy, ReactiveStreamsBackpressureStrategy.LATEST)) {
                // Since there is no callback for dropped elements on backpressure "latest", item emission is signaled before dropping
                // No exception is reported back to the exchanges
                flux = flux.handle(this::onItemEmitted).onBackpressureLatest();
            } else {
                // Default strategy is BUFFER
                flux = flux.onBackpressureBuffer(Queues.SMALL_BUFFER_SIZE, this::onBackPressure).handle(this::onItemEmitted);
            }

            flux.subscribe(this.publisher);

            camelProducer = producer;
        }
    }

    synchronized void detach() {

        this.camelProducer = null;
        this.camelSink.set(null);
    }

    void send(Exchange exchange) {
        if (service.isRunAllowed()) {
            FluxSink<Exchange> sink = ObjectHelper.notNull(camelSink.get(), "FluxSink");
            sink.next(exchange);
        }
    }

    // **************************************
    // Helpers
    // **************************************

    private void onItemEmitted(Exchange exchange, SynchronousSink<Exchange> sink) {
        if (service.isRunAllowed()) {
            sink.next(exchange);
            ReactiveStreamsHelper.invokeDispatchCallback(exchange);
        }
    }

    private void onBackPressure(Exchange exchange) {
        ReactiveStreamsHelper.invokeDispatchCallback(
            exchange,
            new ReactiveStreamsDiscardedException("Discarded by back pressure strategy", exchange, name)
        );
    }
}
