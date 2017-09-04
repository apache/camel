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
package org.apache.camel.component.reactive.streams.engine;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenDataException;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.reactive.streams.ReactiveStreamsCamelSubscriber;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConstants;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsHelper;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.util.ConvertingPublisher;
import org.apache.camel.component.reactive.streams.util.ConvertingSubscriber;
import org.apache.camel.component.reactive.streams.util.MonoPublisher;
import org.apache.camel.component.reactive.streams.util.UnwrapStreamProcessor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.function.Suppliers;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * The default implementation of the reactive streams service.
 */
@ManagedResource(description = "Managed CamelReactiveStreamsService")
public class DefaultCamelReactiveStreamsService extends ServiceSupport implements CamelReactiveStreamsService {

    private final CamelContext context;
    private final ReactiveStreamsEngineConfiguration configuration;
    private final Supplier<UnwrapStreamProcessor> unwrapStreamProcessorSupplier;

    private ExecutorService workerPool;

    private final ConcurrentMap<String, CamelPublisher> publishers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReactiveStreamsCamelSubscriber> subscribers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> publishedUriToStream = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> requestedUriToStream = new ConcurrentHashMap<>();

    public DefaultCamelReactiveStreamsService(CamelContext context, ReactiveStreamsEngineConfiguration configuration) {
        this.context = context;
        this.configuration = configuration;
        this.unwrapStreamProcessorSupplier = Suppliers.memorize(UnwrapStreamProcessor::new);

        // must initialize the worker pool as early as possible
        init();
    }

    @Override
    public String getId() {
        return ReactiveStreamsConstants.DEFAULT_SERVICE_NAME;
    }

    private void init() {
        if (this.workerPool == null) {
            this.workerPool = context.getExecutorServiceManager().newThreadPool(
                this,
                configuration.getThreadPoolName(),
                configuration.getThreadPoolMinSize(),
                configuration.getThreadPoolMaxSize()
            );
        }
    }


    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        if (this.workerPool != null) {
            context.getExecutorServiceManager().shutdownNow(this.workerPool);
            this.workerPool = null;
        }
    }

    @Override
    public Publisher<Exchange> fromStream(String name) {
        return new UnwrappingPublisher(getPayloadPublisher(name));
    }

    @SuppressWarnings("unchecked")
    public <T> Publisher<T> fromStream(String name, Class<T> cls) {
        if (Exchange.class.equals(cls)) {
            return (Publisher<T>) fromStream(name);
        }

        return new ConvertingPublisher<T>(fromStream(name), cls);
    }

    @Override
    public ReactiveStreamsCamelSubscriber streamSubscriber(String name) {
        return subscribers.computeIfAbsent(name, n -> new ReactiveStreamsCamelSubscriber(name));
    }

    @SuppressWarnings("unchecked")
    public <T> Subscriber<T> streamSubscriber(String name, Class<T> type) {
        if (Exchange.class.equals(type)) {
            return (Subscriber<T>) streamSubscriber(name);
        }

        return new ConvertingSubscriber<T>(streamSubscriber(name), context);
    }

    @Override
    public void sendCamelExchange(String name, Exchange exchange) {
        getPayloadPublisher(name).publish(exchange);
    }

    @Override
    public Publisher<Exchange> toStream(String name, Object data) {
        Exchange exchange = ReactiveStreamsHelper.convertToExchange(context, data);
        return doRequest(name, exchange);
    }

    @Override
    public Function<?, ? extends Publisher<Exchange>> toStream(String name) {
        return data -> toStream(name, data);
    }

    @Override
    public <T> Publisher<T> toStream(String name, Object data, Class<T> type) {
        return new ConvertingPublisher<>(toStream(name, data), type);
    }

    protected Publisher<Exchange> doRequest(String name, Exchange data) {
        ReactiveStreamsConsumer consumer = streamSubscriber(name).getConsumer();
        if (consumer == null) {
            throw new IllegalStateException("No consumers attached to the stream " + name);
        }

        DelayedMonoPublisher<Exchange> publisher = new DelayedMonoPublisher<>(this.workerPool);

        data.addOnCompletion(new Synchronization() {
            @Override
            public void onComplete(Exchange exchange) {
                publisher.setData(exchange);
            }

            @Override
            public void onFailure(Exchange exchange) {
                Throwable throwable = exchange.getException();
                if (throwable == null) {
                    throwable = new IllegalStateException("Unknown Exception");
                }
                publisher.setException(throwable);
            }
        });

        consumer.process(data, doneSync -> {
        });

        return publisher;
    }

    @Override
    public <T> Function<Object, Publisher<T>> toStream(String name, Class<T> type) {
        return data -> toStream(name, data, type);
    }

    private CamelPublisher getPayloadPublisher(String name) {
        publishers.computeIfAbsent(name, n -> new CamelPublisher(this.workerPool, this.context, n));
        return publishers.get(name);
    }

    @Override
    public Publisher<Exchange> from(String uri) {
        publishedUriToStream.computeIfAbsent(uri, u -> {
            try {
                String uuid = context.getUuidGenerator().generateUuid();
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from(u)
                            .to("reactive-streams:" + uuid);
                    }
                }.addRoutesToCamelContext(context);

                return uuid;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create source reactive stream from direct URI: " + uri, e);
            }
        });
        return fromStream(publishedUriToStream.get(uri));
    }

    @Override
    public <T> Publisher<T> from(String uri, Class<T> type) {
        return new ConvertingPublisher<T>(from(uri), type);
    }

    @Override
    public Subscriber<Exchange> subscriber(String uri) {
        try {
            String uuid = context.getUuidGenerator().generateUuid();
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("reactive-streams:" + uuid)
                        .to(uri);
                }
            }.addRoutesToCamelContext(context);

            return streamSubscriber(uuid);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create source reactive stream towards direct URI: " + uri, e);
        }
    }

    @Override
    public <T> Subscriber<T> subscriber(String uri, Class<T> type) {
        return new ConvertingSubscriber<T>(subscriber(uri), context);
    }

    @Override
    public Publisher<Exchange> to(String uri, Object data) {
        requestedUriToStream.computeIfAbsent(uri, u -> {
            try {
                String uuid = context.getUuidGenerator().generateUuid();
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("reactive-streams:" + uuid)
                            .to(u);
                    }
                }.addRoutesToCamelContext(context);

                return uuid;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to create requested reactive stream from direct URI: " + uri, e);
            }
        });
        return toStream(requestedUriToStream.get(uri), data);
    }

    @Override
    public Function<Object, Publisher<Exchange>> to(String uri) {
        return data -> to(uri, data);
    }

    @Override
    public <T> Publisher<T> to(String uri, Object data, Class<T> type) {
        return new ConvertingPublisher<>(to(uri, data), type);
    }

    @Override
    public <T> Function<Object, Publisher<T>> to(String uri, Class<T> type) {
        return data -> to(uri, data, type);
    }

    @Override
    public void process(String uri, Function<? super Publisher<Exchange>, ?> processor) {
        try {
            new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from(uri)
                        .process(exchange -> {
                            Exchange copy = exchange.copy();
                            Object result = processor.apply(new MonoPublisher<>(copy));
                            exchange.getIn().setBody(result);
                        })
                        .process(unwrapStreamProcessorSupplier.get());
                }
            }.addRoutesToCamelContext(context);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to add reactive stream processor to the direct URI: " + uri, e);
        }
    }

    @Override
    public <T> void process(String uri, Class<T> type, Function<? super Publisher<T>, ?> processor) {
        process(uri, exPub -> processor.apply(new ConvertingPublisher<T>(exPub, type)));
    }

    @Override
    public ReactiveStreamsCamelSubscriber attachCamelConsumer(String name, ReactiveStreamsConsumer consumer) {
        ReactiveStreamsCamelSubscriber subscriber = streamSubscriber(name);
        subscriber.attachConsumer(consumer);
        return subscriber;
    }

    @Override
    public void detachCamelConsumer(String name) {
        streamSubscriber(name).detachConsumer();
    }

    @Override
    public void attachCamelProducer(String name, ReactiveStreamsProducer producer) {
        getPayloadPublisher(name).attachProducer(producer);
    }

    @Override
    public void detachCamelProducer(String name) {
        getPayloadPublisher(name).detachProducer();
    }

    @ManagedOperation(description = "Information about Camel Reactive subscribers")
    public TabularData camelSubscribers() {
        try {
            final TabularData answer = new TabularDataSupport(subscribersTabularType());

            subscribers.forEach((k, v) -> {
                try {
                    String name = k;
                    long inflight = v.getInflightCount();
                    long requested = v.getRequested();

                    CompositeType ct = subscribersCompositeType();
                    CompositeData data = new CompositeDataSupport(ct,
                        new String[] {"name", "inflight", "requested"},
                        new Object[] {name, inflight, requested});
                    answer.put(data);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            });

            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    @ManagedOperation(description = "Information about Camel Reactive publishers")
    public TabularData camelPublishers() {
        try {
            final TabularData answer = new TabularDataSupport(publishersTabularType());

            publishers.forEach((k, v) -> {
                try {
                    String name = k;
                    List<CamelSubscription> subscriptions = v.getSubscriptions();
                    int subscribers = subscriptions.size();

                    TabularData subscriptionData = new TabularDataSupport(subscriptionsTabularType());
                    CompositeType subCt = subscriptionsCompositeType();
                    for (CamelSubscription sub : subscriptions) {
                        String id = sub.getId();
                        long bufferSize = sub.getBufferSize();
                        String backpressure = sub.getBackpressureStrategy() != null ? sub.getBackpressureStrategy().name() : "";
                        CompositeData subData = new CompositeDataSupport(subCt, new String[]{"name", "buffer size", "back pressure"}, new Object[]{id, bufferSize, backpressure});

                        subscriptionData.put(subData);
                    }


                    CompositeType ct = publishersCompositeType();
                    CompositeData data = new CompositeDataSupport(ct,
                        new String[] {"name", "subscribers", "subscriptions"},
                        new Object[] {name, subscribers, subscriptionData});
                    answer.put(data);
                } catch (Exception e) {
                    throw ObjectHelper.wrapRuntimeCamelException(e);
                }
            });

            return answer;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    private static TabularType subscribersTabularType() throws OpenDataException {
        CompositeType ct = subscribersCompositeType();
        return new TabularType("subscribers", "Information about Camel Reactive subscribers", ct, new String[]{"name"});
    }

    private static CompositeType subscribersCompositeType() throws OpenDataException {
        return new CompositeType("subscriptions", "Subscriptions",
                new String[] {"name", "inflight", "requested"},
                new String[] {"Name", "Inflight", "Requested"},
                new OpenType[] {SimpleType.STRING, SimpleType.LONG, SimpleType.LONG});
    }

    private static CompositeType publishersCompositeType() throws OpenDataException {
        return new CompositeType("publishers", "Publishers",
            new String[] {"name", "subscribers", "subscriptions"},
            new String[] {"Name", "Subscribers", "Subscriptions"},
            new OpenType[] {SimpleType.STRING, SimpleType.INTEGER, subscriptionsTabularType()});
    }

    private static TabularType subscriptionsTabularType() throws OpenDataException {
        CompositeType ct = subscriptionsCompositeType();
        return new TabularType("subscriptions", "Information about External Reactive subscribers", ct, new String[]{"name"});
    }

    private static CompositeType subscriptionsCompositeType() throws OpenDataException {
        return new CompositeType("subscriptions", "Subscriptions",
                new String[] {"name", "buffer size", "back pressure"},
                new String[] {"Name", "Buffer Size", "Back Pressure"},
                new OpenType[] {SimpleType.STRING, SimpleType.LONG, SimpleType.STRING});
    }

    private static TabularType publishersTabularType() throws OpenDataException {
        CompositeType ct = publishersCompositeType();
        return new TabularType("publishers", "Information about Camel Reactive publishers", ct, new String[]{"name"});
    }

}
