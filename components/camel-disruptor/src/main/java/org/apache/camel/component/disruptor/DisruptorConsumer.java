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
package org.apache.camel.component.disruptor;

import java.util.HashSet;
import java.util.Set;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.Suspendable;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.support.service.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Consumer for the Disruptor component.
 */
public class DisruptorConsumer extends ServiceSupport implements Consumer, Suspendable, ShutdownAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(DisruptorConsumer.class);

    private static final AsyncCallback NOOP_ASYNC_CALLBACK = new AsyncCallback() {
        @Override
        public void done(boolean doneSync) {
            //Noop
        }
    };

    private final DisruptorEndpoint endpoint;
    private final AsyncProcessor processor;
    private ExceptionHandler exceptionHandler;

    public DisruptorConsumer(final DisruptorEndpoint endpoint, final Processor processor) {
        this.endpoint = endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    public AsyncProcessor getProcessor() {
        return processor;
    }

    public ExceptionHandler getExceptionHandler() {
        if (exceptionHandler == null) {
            exceptionHandler = new LoggingExceptionHandler(endpoint.getCamelContext(), getClass());
        }
        return exceptionHandler;
    }

    public void setExceptionHandler(final ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    @Override
    public DisruptorEndpoint getEndpoint() {
        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        getEndpoint().onStarted(this);
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().onStopped(this);
    }

    @Override
    protected void doSuspend() throws Exception {
        getEndpoint().onStopped(this);
    }

    @Override
    protected void doResume() throws Exception {
        getEndpoint().onStarted(this);
    }

    Set<LifecycleAwareExchangeEventHandler> createEventHandlers(final int concurrentConsumers) {
        final Set<LifecycleAwareExchangeEventHandler> eventHandlers = new HashSet<>();

        for (int i = 0; i < concurrentConsumers; ++i) {
            eventHandlers.add(new ConsumerEventHandler(i, concurrentConsumers));
        }

        return eventHandlers;
    }

    @Override
    public boolean deferShutdown(final ShutdownRunningTask shutdownRunningTask) {
        // deny stopping on shutdown as we want disruptor consumers to run in case some other queues
        // depend on this consumer to run, so it can complete its exchanges
        return true;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        // nothing
    }

    @Override
    public int getPendingExchangesSize() {
        return getEndpoint().getDisruptor().getPendingExchangeCount();
    }

    @Override
    public String toString() {
        return "DisruptorConsumer[" + endpoint + "]";
    }

    private Exchange prepareExchange(final Exchange exchange) {
        // send a new copied exchange with new camel context
        // don't copy handovers as they are handled by the Disruptor Event Handlers
        final Exchange newExchange = ExchangeHelper.copyExchangeWithProperties(exchange, endpoint.getCamelContext());
        // set the from endpoint
        newExchange.getExchangeExtension().setFromEndpoint(endpoint);
        return newExchange;
    }

    private void process(final SynchronizedExchange synchronizedExchange) {
        try {
            Exchange exchange = synchronizedExchange.getExchange();

            final boolean ignore = exchange.hasProperties() && exchange
                    .getProperties().containsKey(DisruptorEndpoint.DISRUPTOR_IGNORE_EXCHANGE);
            if (ignore) {
                // Property was set and it was set to true, so don't process Exchange.
                LOGGER.trace("Ignoring exchange {}", exchange);
                return;
            }

            // send a new copied exchange with new camel context
            final Exchange result = prepareExchange(exchange);

            // We need to be notified when the exchange processing is complete to synchronize the original exchange
            // This is however the last part of the processing of this exchange and as such can't be done
            // in the AsyncCallback as that is called *AFTER* processing is considered to be done
            // (see org.apache.camel.processor.CamelInternalProcessor.InternalCallback#done).
            // To solve this problem, a new synchronization is set on the exchange that is to be
            // processed
            result.getExchangeExtension().addOnCompletion(new Synchronization() {
                @Override
                public void onComplete(Exchange exchange) {
                    synchronizedExchange.consumed(result);
                }

                @Override
                public void onFailure(Exchange exchange) {
                    synchronizedExchange.consumed(result);
                }
            });

            // As the necessary post-processing of the exchange is done by the registered Synchronization,
            // we can suffice with a no-op AsyncCallback
            processor.process(result, NOOP_ASYNC_CALLBACK);

        } catch (Exception e) {
            Exchange exchange = synchronizedExchange.getExchange();

            if (exchange != null) {
                getExceptionHandler().handleException("Error processing exchange",
                        exchange, e);
            } else {
                getExceptionHandler().handleException(e);
            }
        }
    }

    @Override
    public Exchange createExchange(boolean autoRelease) {
        // noop
        return null;
    }

    @Override
    public void releaseExchange(Exchange exchange, boolean autoRelease) {
        // noop
    }

    /**
     * Implementation of the {@link LifecycleAwareExchangeEventHandler} interface that passes all Exchanges to the
     * {@link Processor} registered at this {@link DisruptorConsumer}.
     */
    private class ConsumerEventHandler extends AbstractLifecycleAwareExchangeEventHandler {

        private final int ordinal;

        private final int concurrentConsumers;

        ConsumerEventHandler(final int ordinal, final int concurrentConsumers) {
            this.ordinal = ordinal;
            this.concurrentConsumers = concurrentConsumers;
        }

        @Override
        public void onEvent(final ExchangeEvent event, final long sequence, final boolean endOfBatch) throws Exception {
            // Consumer threads are managed at the endpoint to achieve the optimal performance.
            // However, both multiple consumers (pub-sub style multicasting) as well as 'worker-pool' consumers dividing
            // exchanges amongst them are scheduled on their own threads and are provided with all exchanges.
            // To prevent duplicate exchange processing by worker-pool event handlers, they are all given an ordinal,
            // which can be used to determine whether he should process the exchange, or leave it for his brethren.
            //see http://code.google.com/p/disruptor/wiki/FrequentlyAskedQuestions#How_do_you_arrange_a_Disruptor_with_multiple_consumers_so_that_e
            if (sequence % concurrentConsumers == ordinal) {
                process(event.getSynchronizedExchange());
            }
        }

    }
}
