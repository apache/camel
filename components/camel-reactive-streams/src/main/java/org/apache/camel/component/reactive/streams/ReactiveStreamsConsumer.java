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
package org.apache.camel.component.reactive.streams;

import java.util.concurrent.ExecutorService;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.reactive.streams.api.CamelReactiveStreamsService;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Camel reactive-streams consumer.
 */
public class ReactiveStreamsConsumer extends DefaultConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(ReactiveStreamsConsumer.class);

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
            executor = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, getEndpoint().getEndpointUri(), poolSize);
        }

        this.service.attachCamelConsumer(endpoint.getStream(), this);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        this.service.detachCamelConsumer(endpoint.getStream());

        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
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

            executorService.execute(() -> this.getAsyncProcessor().process(exchange, doneSync -> {
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }

                callback.done(doneSync);
            }));
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
