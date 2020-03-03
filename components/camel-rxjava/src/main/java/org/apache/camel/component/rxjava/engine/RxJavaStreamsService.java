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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

import io.reactivex.Flowable;
import io.reactivex.Single;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.ReactiveStreamsCamelSubscriber;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.util.BodyConverter;
import org.apache.camel.component.reactive.streams.util.ConvertingPublisher;
import org.apache.camel.component.reactive.streams.util.ConvertingSubscriber;
import org.apache.camel.component.reactive.streams.util.UnwrapStreamProcessor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.function.Suppliers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

final class RxJavaStreamsService extends ServiceSupport implements CamelReactiveStreamsService {
    private final CamelContext context;
    private final Supplier<UnwrapStreamProcessor> unwrapStreamProcessorSupplier;
    private final ConcurrentMap<String, RxJavaCamelProcessor> publishers;
    private final ConcurrentMap<String, ReactiveStreamsCamelSubscriber> subscribers;
    private final ConcurrentMap<String, String> publishedUriToStream;
    private final ConcurrentMap<String, String> requestedUriToStream;

    RxJavaStreamsService(CamelContext context) {
        this.context = context;
        this.publishers = new ConcurrentHashMap<>();
        this.subscribers = new ConcurrentHashMap<>();
        this.publishedUriToStream = new ConcurrentHashMap<>();
        this.requestedUriToStream = new ConcurrentHashMap<>();
        this.unwrapStreamProcessorSupplier = Suppliers.memorize(UnwrapStreamProcessor::new);
    }

    @Override
    public String getId() {
        return RxJavaStreamsConstants.SERVICE_NAME;
    }

    // ******************************************
    // Lifecycle
    // ******************************************

    @Override
    public void doStart() throws Exception {
    }

    @Override
    public void doStop() throws Exception {
        for (RxJavaCamelProcessor processor : publishers.values()) {
            processor.close();
        }
        for (ReactiveStreamsCamelSubscriber subscriber : subscribers.values()) {
            subscriber.close();
        }
    }

    // ******************************************
    //
    // ******************************************

    @Override
    public Publisher<Exchange> fromStream(String name) {
        return getCamelProcessor(name).getPublisher();
    }

    @Override
    public <T> Publisher<T> fromStream(String name, Class<T> type) {
        final Publisher<Exchange> publisher = fromStream(name);

        if (Exchange.class.isAssignableFrom(type)) {
            return Publisher.class.cast(publisher);
        }

        return Flowable.fromPublisher(publisher).map(BodyConverter.forType(type)::apply);
    }

    @Override
    public ReactiveStreamsCamelSubscriber streamSubscriber(String name) {
        return subscribers.computeIfAbsent(name, n -> new ReactiveStreamsCamelSubscriber(name));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Subscriber<T> streamSubscriber(String name, Class<T> type) {
        final Subscriber<Exchange> subscriber = streamSubscriber(name);

        if (Exchange.class.equals(type)) {
            return Subscriber.class.cast(subscriber);
        }

        return new ConvertingSubscriber<>(subscriber, context, type);
    }

    @Override
    public Publisher<Exchange> toStream(String name, Object data) {
        return doRequest(
            name,
            ReactiveStreamsHelper.convertToExchange(context, data)
        );
    }

    @Override
    public Function<?, ? extends Publisher<Exchange>> toStream(String name) {
        return data -> toStream(name, data);
    }

    @Override
    public <T> Publisher<T> toStream(String name, Object data, Class<T> type) {
        return new ConvertingPublisher<>(toStream(name, data), type);
    }

    @Override
    public <T> Function<Object, Publisher<T>> toStream(String name, Class<T> type) {
        return data -> toStream(name, data, type);
    }

    @Override
    public Publisher<Exchange> from(String uri) {
        final String name = publishedUriToStream.computeIfAbsent(uri, camelUri -> {
            try {
                String uuid = context.getUuidGenerator().generateUuid();

                RouteBuilder.addRoutes(context, rb ->
                        rb.from(camelUri).to("reactive-streams:" + uuid));

                return uuid;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create source reactive stream from direct URI: " + uri, e);
            }
        });

        return fromStream(name);
    }

    @Override
    public <T> Publisher<T> from(String name, Class<T> type) {
        final Publisher<Exchange> publisher = from(name);

        if (Exchange.class.isAssignableFrom(type)) {
            return Publisher.class.cast(publisher);
        }

        return Flowable.fromPublisher(publisher).map(BodyConverter.forType(type)::apply);
    }

    @Override
    public Subscriber<Exchange> subscriber(String uri) {
        try {
            String uuid = context.getUuidGenerator().generateUuid();
            RouteBuilder.addRoutes(context, rb ->
                    rb.from("reactive-streams:" + uuid).to(uri));

            return streamSubscriber(uuid);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create source reactive stream towards direct URI: " + uri, e);
        }
    }

    @Override
    public <T> Subscriber<T> subscriber(String uri, Class<T> type) {
        return new ConvertingSubscriber<>(subscriber(uri), context, type);
    }

    @Override
    public Publisher<Exchange> to(String uri, Object data) {
        String streamName = requestedUriToStream.computeIfAbsent(uri, camelUri -> {
            try {
                String uuid = context.getUuidGenerator().generateUuid();
                RouteBuilder.addRoutes(context, rb ->
                        rb.from("reactive-streams:" + uuid).to(camelUri));

                return uuid;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create requested reactive stream from direct URI: " + uri, e);
            }
        });

        return toStream(streamName, data);
    }

    @Override
    public Function<Object, Publisher<Exchange>> to(String uri) {
        return data -> to(uri, data);
    }

    @Override
    public <T> Publisher<T> to(String uri, Object data, Class<T> type) {
        Publisher<Exchange> publisher = to(uri, data);

        return Flowable.fromPublisher(publisher).map(BodyConverter.forType(type)::apply);
    }

    @Override
    public <T> Function<Object, Publisher<T>> to(String uri, Class<T> type) {
        return data -> to(uri, data, type);
    }

    @Override
    public void process(String uri, Function<? super Publisher<Exchange>, ?> processor) {
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from(uri)
                        .process(exchange -> {
                            Exchange copy = exchange.copy();
                            Object result = processor.apply(Flowable.just(copy));
                            exchange.getIn().setBody(result);
                        })
                        .process(unwrapStreamProcessorSupplier.get());
                }
            });
        } catch (Exception e) {
            throw new IllegalStateException("Unable to add reactive stream processor to the direct URI: " + uri, e);
        }
    }

    @Override
    public <T> void process(String uri, Class<T> type, Function<? super Publisher<T>, ?> processor) {
        process(
            uri,
            publisher -> processor.apply(
                Flowable.fromPublisher(publisher).map(BodyConverter.forType(type)::apply)
            )
        );
    }

    // ******************************************
    // Producer
    // ******************************************

    @Override
    public void attachCamelProducer(String name, ReactiveStreamsProducer producer) {
        getCamelProcessor(name).attach(producer);
    }

    @Override
    public void detachCamelProducer(String name) {
        getCamelProcessor(name).detach();
    }

    @Override
    public void sendCamelExchange(String name, Exchange exchange) {
        getCamelProcessor(name).send(exchange);
    }

    private RxJavaCamelProcessor getCamelProcessor(String name) {
        return publishers.computeIfAbsent(name, key -> new RxJavaCamelProcessor(this, key));
    }

    // ******************************************
    // Consumer
    // ******************************************

    @Override
    public ReactiveStreamsCamelSubscriber attachCamelConsumer(String name, ReactiveStreamsConsumer consumer) {
        ReactiveStreamsCamelSubscriber subscriber = streamSubscriber(name);
        subscriber.attachConsumer(consumer);

        return subscriber;
    }

    @Override
    public void detachCamelConsumer(String name) {
        ReactiveStreamsCamelSubscriber subscriber = streamSubscriber(name);
        subscriber.detachConsumer();
    }

    // *******************************************
    // Helpers
    // *******************************************

    protected Publisher<Exchange> doRequest(String name, Exchange data) {
        ReactiveStreamsConsumer consumer = streamSubscriber(name).getConsumer();
        if (consumer == null) {
            throw new IllegalStateException("No consumers attached to the stream " + name);
        }

        Single<Exchange> source = Single.<Exchange>create(
            emitter -> data.adapt(ExtendedExchange.class).addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    emitter.onSuccess(exchange);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    Throwable throwable = exchange.getException();
                    if (throwable == null) {
                        throwable = new IllegalStateException("Unknown Exception");
                    }

                    emitter.onError(throwable);
                }
            })
        ).doOnSubscribe(
            subs -> consumer.process(data, RxJavaStreamsConstants.EMPTY_ASYNC_CALLBACK)
        );

        return source.toFlowable();
    }
}
