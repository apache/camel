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
package org.apache.camel.component.rxjava.engine;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.FlowableEmitter;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.MulticastProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.component.reactive.streams.ReactiveStreamsBackpressureStrategy;
import org.apache.camel.component.reactive.streams.ReactiveStreamsDiscardedException;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.util.ObjectHelper;
import org.reactivestreams.Publisher;

final class RxJavaCamelProcessor implements Closeable {
    private final String name;
    private final RxJavaStreamsService service;
    private final AtomicReference<FlowableEmitter<Exchange>> camelEmitter;
    private FlowableProcessor<Exchange> publisher;
    private ReactiveStreamsProducer camelProducer;

    RxJavaCamelProcessor(RxJavaStreamsService service, String name) {
        this.service = service;
        this.name = name;
        this.camelProducer = null;
        this.camelEmitter = new AtomicReference<>();
        this.publisher = MulticastProcessor.create(1); // Buffered downstream if needed
    }

    @Override
    public void close() throws IOException {
        detach();
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
            Flowable<Exchange> flow = Flowable.create(camelEmitter::set, BackpressureStrategy.MISSING);

            if (ObjectHelper.equal(strategy, ReactiveStreamsBackpressureStrategy.OLDEST)) {
                flow.onBackpressureDrop(this::onBackPressure)
                    .doAfterNext(this::onItemEmitted)
                    .subscribe(this.publisher);
            } else if (ObjectHelper.equal(strategy, ReactiveStreamsBackpressureStrategy.LATEST)) {
                flow.doAfterNext(this::onItemEmitted)
                    .onBackpressureLatest()
                    .subscribe(this.publisher);
            } else {
                flow.doAfterNext(this::onItemEmitted)
                    .onBackpressureBuffer()
                    .subscribe(this.publisher);
            }

            camelProducer = producer;
        }
    }

    synchronized void detach() {
        this.camelProducer = null;
        this.camelEmitter.set(null);
    }

    void send(Exchange exchange) {
        if (service.isRunAllowed()) {
            FlowableEmitter<Exchange> emitter = ObjectHelper.notNull(camelEmitter.get(), "FlowableEmitter");
            emitter.onNext(exchange);
        }
    }

    // **************************************
    // Helpers
    // **************************************

    private void onItemEmitted(Exchange exchange) {
        if (service.isRunAllowed()) {
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
