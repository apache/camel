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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.reactive.streams.ReactiveStreamsComponent;
import org.apache.camel.component.reactive.streams.ReactiveStreamsConsumer;
import org.apache.camel.component.reactive.streams.ReactiveStreamsProducer;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.component.reactive.streams.api.DispatchCallback;
import org.apache.camel.component.reactive.streams.util.ConvertingPublisher;
import org.apache.camel.component.reactive.streams.util.ConvertingSubscriber;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.spi.Synchronization;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

/**
 * The default implementation of the reactive streams service.
 */
public class CamelReactiveStreamsServiceImpl implements CamelReactiveStreamsService {

    private CamelContext context;

    private ExecutorService workerPool;

    private Map<String, CamelPublisher> publishers = new HashMap<>();

    private final Map<String, CamelSubscriber> subscribers = new HashMap<>();

    public CamelReactiveStreamsServiceImpl() {
    }

    @Override
    public void start() throws Exception {
        ReactiveStreamsComponent component = context.getComponent("reactive-streams", ReactiveStreamsComponent.class);
        ReactiveStreamsEngineConfiguration config = component.getInternalEngineConfiguration();
        this.workerPool = context.getExecutorServiceManager().newThreadPool(this, config.getThreadPoolName(), config.getThreadPoolMinSize(), config.getThreadPoolMaxSize());
    }

    @Override
    public void stop() throws Exception {
        if (this.workerPool != null) {
            context.getExecutorServiceManager().shutdownNow(this.workerPool);
        }
    }

    @Override
    public Publisher<Exchange> getPublisher(String name) {
        return new UnwrappingPublisher<>(getPayloadPublisher(name));
    }

    @SuppressWarnings("unchecked")
    public <T> Publisher<T> getPublisher(String name, Class<T> cls) {
        if (Exchange.class.equals(cls)) {
            return (Publisher<T>) getPublisher(name);
        }

        return new ConvertingPublisher<T>(getPublisher(name), cls);
    }

    @Override
    public CamelSubscriber getSubscriber(String name) {
        synchronized (this) {
            if (!subscribers.containsKey(name)) {
                CamelSubscriber sub = new CamelSubscriber(name);
                subscribers.put(name, sub);
            }
            return subscribers.get(name);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> Subscriber<T> getSubscriber(String name, Class<T> type) {
        if (Exchange.class.equals(type)) {
            return (Subscriber<T>) getSubscriber(name);
        }

        return new ConvertingSubscriber<T>(getSubscriber(name), getCamelContext());
    }

    @Override
    public void process(String name, Exchange exchange, DispatchCallback<Exchange> callback) {
        StreamPayload<Exchange> payload = new StreamPayload<>(exchange, callback);
        getPayloadPublisher(name).publish(payload);
    }

    @Override
    public Publisher<Exchange> request(String name, Object data) {
        Exchange exchange;
        if (data instanceof Exchange) {
            exchange = (Exchange) data;
        } else {
            exchange = new DefaultExchange(context);
            exchange.setPattern(ExchangePattern.InOut);
            exchange.getIn().setBody(data);
        }

        return doRequest(name, exchange);
    }

    @Override
    public <T> Publisher<T> request(String name, Object data, Class<T> type) {
        return new ConvertingPublisher<>(request(name, data), type);
    }

    protected Publisher<Exchange> doRequest(String name, Exchange data) {
        ReactiveStreamsConsumer consumer = getSubscriber(name).getConsumer();
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

    private CamelPublisher getPayloadPublisher(String name) {
        synchronized (this) {
            if (!publishers.containsKey(name)) {
                CamelPublisher publisher = new CamelPublisher(this.workerPool, this.context, name);
                publishers.put(name, publisher);
            }

            return publishers.get(name);
        }
    }

    @Override
    public void attachCamelConsumer(String name, ReactiveStreamsConsumer consumer) {
        getSubscriber(name).attachConsumer(consumer);
    }

    @Override
    public void detachCamelConsumer(String name) {
        getSubscriber(name).detachConsumer();
    }

    @Override
    public void attachCamelProducer(String name, ReactiveStreamsProducer producer) {
        getPayloadPublisher(name).attachProducer(producer);
    }

    @Override
    public void detachCamelProducer(String name) {
        getPayloadPublisher(name).detachProducer();
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.context = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return this.context;
    }

}
