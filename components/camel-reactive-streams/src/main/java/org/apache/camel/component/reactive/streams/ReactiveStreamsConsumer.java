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
package org.apache.camel.component.reactive.streams;

import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel reactive-streams consumer.
 */
public class ReactiveStreamsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(ReactiveStreamsConsumer.class);

    /**
     * The consumer whose thread pool runs the exchange that the current thread is routing.
     */
    private static final ThreadLocal<ReactiveStreamsConsumer> ROUTING = new ThreadLocal<>();

    private final ReactiveStreamsEndpoint endpoint;
    private final CamelReactiveStreamsService service;
    private ExecutorService executor;

    public ReactiveStreamsConsumer(ReactiveStreamsEndpoint endpoint, Processor processor, CamelReactiveStreamsService service) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.service = ObjectHelper.notNull(service, "service");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        int poolSize = endpoint.getConcurrentConsumers();
        if (executor == null) {
            executor = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this,
                    getEndpoint().getEndpointUri(), poolSize);
        }

        this.service.attachCamelConsumer(endpoint.getStream(), this);
    }

    @Override
    protected void doStop() throws Exception {
        this.service.detachCamelConsumer(endpoint.getStream());

        if (executor != null) {
            // the queued exchanges were already taken from the stream (they cannot be requested again),
            // so let them complete before the processor is stopped; dropping them would also leave
            // them counted as inflight by the subscriber, which would then request less or nothing
            if (ROUTING.get() == this) {
                // stopped by an exchange of this consumer (for example a route policy): the pool cannot be
                // awaited from one of its own threads, the queued exchanges run after this one
                endpoint.getCamelContext().getExecutorServiceManager().shutdown(executor);
            } else {
                endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            }
            executor = null;
        }

        super.doStop();
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onNext");
        return doSend(exchange, callback);
    }

    public void onComplete() {
        if (endpoint.isForwardOnComplete()) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onComplete");

            doSend(exchange, done -> {
            });
        }
    }

    public void onError(Throwable error) {
        if (endpoint.isForwardOnError()) {
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setHeader(ReactiveStreamsConstants.REACTIVE_STREAMS_EVENT_TYPE, "onError");
            exchange.getIn().setBody(error);

            doSend(exchange, done -> {
            });
        }
    }

    private boolean doSend(Exchange exchange, AsyncCallback callback) {
        ExecutorService executorService = this.executor;
        if (executorService != null && this.isRunAllowed()) {

            executorService.execute(() -> {
                ROUTING.set(this);
                try {
                    this.getAsyncProcessor().process(exchange, doneSync -> {
                        if (exchange.getException() != null) {
                            getExceptionHandler().handleException("Error processing exchange", exchange,
                                    exchange.getException());
                        }

                        callback.done(doneSync);
                    });
                } finally {
                    ROUTING.remove();
                }
            });
            return false;

        } else {
            LOG.warn("Consumer not ready to process exchanges. The exchange {} will be discarded", exchange);
            callback.done(true);
            return true;
        }
    }

    @Override
    public ReactiveStreamsEndpoint getEndpoint() {
        return endpoint;
    }

}
