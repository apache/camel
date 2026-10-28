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
package org.apache.camel.support;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.AsyncProducer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ShutdownableService;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.SendHook;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeSource;

/**
 * Helper for the places in Camel that send an {@link Exchange} to the producer of an {@link Endpoint}: the
 * {@code SendProcessor}, the {@link org.apache.camel.spi.ProducerCache} (used by {@code toD}, {@code enrich},
 * {@code wireTap}, {@code routingSlip} and the {@link org.apache.camel.ProducerTemplate}), the recipient list and the
 * deferred producers.
 * <p/>
 * It fires the {@link CamelEvent.ExchangeSendingEvent} before the send and the {@link CamelEvent.ExchangeSentEvent},
 * with the time taken, after it ({@link #notifySending(Exchange, Endpoint)} and
 * {@link #notifySent(Exchange, Endpoint, StopWatch)}), and calls the producer with the {@link SendHook} of the
 * {@link CamelContext}, if one is installed, around it
 * ({@link #process(Exchange, Endpoint, AsyncProcessor, AsyncCallback)}).
 * {@link #send(Exchange, Endpoint, AsyncProcessor, AsyncCallback)} does both.
 * <p/>
 * The events and the hook are apart because some places fire the events once per send and call the producer inside an
 * error handler that can call it again: the recipient list and the routing slip. The hook runs where the producer is
 * called, so it runs inside that error handler, for every attempt.
 *
 * @since 4.24
 */
public final class SendHelper {

    private SendHelper() {
    }

    /**
     * Fires the {@link CamelEvent.ExchangeSendingEvent}.
     *
     * @param  exchange the exchange that is sent
     * @param  endpoint the endpoint it is sent to
     * @return          a started {@link StopWatch} when an {@link CamelEvent.ExchangeSentEvent} must be fired when the
     *                  send is done (see {@link #notifySent(Exchange, Endpoint, StopWatch)}), or {@code null}
     */
    public static StopWatch notifySending(Exchange exchange, Endpoint endpoint) {
        if (EventHelper.notifyExchangeSending(exchange.getContext(), exchange, endpoint)) {
            return new StopWatch();
        }
        return null;
    }

    /**
     * Fires the {@link CamelEvent.ExchangeSentEvent} with the time taken since
     * {@link #notifySending(Exchange, Endpoint)}, when it returned a {@link StopWatch}.
     *
     * @param exchange the exchange that was sent
     * @param endpoint the endpoint it was sent to
     * @param watch    the watch {@link #notifySending(Exchange, Endpoint)} returned, or {@code null} to fire nothing
     */
    public static void notifySent(Exchange exchange, Endpoint endpoint, StopWatch watch) {
        if (watch != null) {
            long timeTaken = watch.taken();
            EventHelper.notifyExchangeSent(exchange.getContext(), exchange, endpoint, timeTaken);
        }
    }

    /**
     * Sends the exchange to the producer: fires the {@link CamelEvent.ExchangeSendingEvent}, calls the producer with
     * the {@link SendHook} around it, and fires the {@link CamelEvent.ExchangeSentEvent} before the callback is called.
     * <p/>
     * An exception the producer throws, instead of calling the callback, is thrown to the caller, without the sent
     * event, as when the producer is called directly.
     *
     * @param  exchange the exchange
     * @param  endpoint the endpoint of the producer
     * @param  producer the producer
     * @param  callback the callback, called exactly once when the send is done
     * @return          whether the send completed synchronously (the same value the callback was called with)
     */
    public static boolean send(Exchange exchange, Endpoint endpoint, AsyncProcessor producer, AsyncCallback callback) {
        final StopWatch watch = notifySending(exchange, endpoint);
        if (watch == null) {
            return process(exchange, endpoint, producer, callback);
        }
        return process(exchange, endpoint, producer, doneSync -> {
            try {
                notifySent(exchange, endpoint, watch);
            } finally {
                callback.done(doneSync);
            }
        });
    }

    /**
     * Calls the producer with the {@link SendHook} of the {@link CamelContext} around it. Without a hook this is
     * {@code producer.process(exchange, callback)}.
     * <p/>
     * With a hook, see {@link SendHook} for what the hook can do. An exception the producer throws, instead of calling
     * the callback, is set on the exchange, the hook's {@link SendHook#afterSend(Exchange, Endpoint)} is called, and
     * the exception is thrown to the caller, as without a hook.
     *
     * @param  exchange the exchange
     * @param  endpoint the endpoint of the producer
     * @param  producer the producer
     * @param  callback the callback, called exactly once when the send is done
     * @return          whether the send completed synchronously (the same value the callback was called with)
     */
    public static boolean process(Exchange exchange, Endpoint endpoint, AsyncProcessor producer, AsyncCallback callback) {
        final SendHook hook = exchange.getContext().getCamelContextExtension().getSendHook();
        if (hook == null) {
            return producer.process(exchange, callback);
        }
        return new HookedSend(hook, exchange, endpoint, producer, callback).send();
    }

    /**
     * Returns the producer as it is when no {@link SendHook} is installed, or else a producer that calls it with the
     * hook around it (see {@link #newSendHookProducer(AsyncProducer)}).
     *
     * @param  context  the camel context
     * @param  producer the producer
     * @return          the producer, or a producer with the hook around it
     */
    public static AsyncProducer withSendHook(CamelContext context, AsyncProducer producer) {
        if (context.getCamelContextExtension().getSendHook() == null) {
            return producer;
        }
        return newSendHookProducer(producer);
    }

    /**
     * Creates a producer that calls the given producer with the {@link SendHook} around it, as
     * {@link #process(Exchange, Endpoint, AsyncProcessor, AsyncCallback)} does. It looks up the hook on every send, so
     * it can be kept (for example as the output of an error handler) when the hook is removed later. It passes start,
     * stop, shutdown and close on to the given producer.
     *
     * @param  producer the producer
     * @return          a producer with the hook around it
     */
    public static AsyncProducer newSendHookProducer(AsyncProducer producer) {
        return new SendHookProducer(producer);
    }

    /**
     * One send with a hook. The hook is called before the producer, the producer is called right away, after a delay
     * (that this thread waits for), or not at all, and then the hook is called after it and the callback is called,
     * each exactly once.
     */
    private static final class HookedSend implements AsyncCallback {

        private final SendHook hook;
        private final Exchange exchange;
        private final Endpoint endpoint;
        private final AsyncProcessor producer;
        private final AsyncCallback callback;

        private HookedSend(SendHook hook, Exchange exchange, Endpoint endpoint, AsyncProcessor producer,
                           AsyncCallback callback) {
            this.hook = hook;
            this.exchange = exchange;
            this.endpoint = endpoint;
            this.producer = producer;
            this.callback = callback;
        }

        boolean send() {
            // an exchange can be sent with an exception on it already, which does not stop the send
            final Exception existing = exchange.getException();
            final long delay;
            try {
                delay = hook.beforeSend(exchange, endpoint);
            } catch (Exception e) {
                // the hook failed: the producer is not called, and there is no after
                exchange.setException(e);
                callback.done(true);
                return true;
            }

            final Exception exception = exchange.getException();
            if (exception != null && exception != existing || delay == SendHook.SKIP) {
                // the hook stopped (or skipped) the send, so the producer is not called
                return completeWithoutProducer();
            }
            if (delay < 0) {
                // the other negative values are reserved, so a hook that returns one fails the send
                exchange.setException(new IllegalArgumentException(
                        "SendHook.beforeSend returned " + delay + ": negative values other than SKIP are reserved"));
                return completeWithoutProducer();
            }

            if (delay > 0) {
                // wait on this thread, as for a slow endpoint (through TimeSource, so a virtual clock can run it)
                try {
                    TimeSource.sleep(delay);
                } catch (InterruptedException e) {
                    // the send fails and the thread keeps its interrupted status; the exception marks the exchange as
                    // interrupted, so Camel stops routing it, as when the wait of the redelivery error handler is
                    // interrupted
                    Thread.currentThread().interrupt();
                    exchange.setException(e);
                    return completeWithoutProducer();
                } catch (RuntimeException e) {
                    // the clock failed: as for a producer that throws
                    exchange.setException(e);
                    afterSend();
                    throw e;
                }
            }

            try {
                return producer.process(exchange, this);
            } catch (RuntimeException e) {
                // the caller handles a producer that throws as it does without a hook
                exchange.setException(e);
                afterSend();
                throw e;
            }
        }

        private boolean completeWithoutProducer() {
            afterSend();
            callback.done(true);
            return true;
        }

        /**
         * The producer is done.
         */
        @Override
        public void done(boolean doneSync) {
            afterSend();
            callback.done(doneSync);
        }

        private void afterSend() {
            try {
                hook.afterSend(exchange, endpoint);
            } catch (Exception e) {
                Exception existing = exchange.getException();
                if (existing == null) {
                    exchange.setException(e);
                } else if (existing != e) {
                    existing.addSuppressed(e);
                }
            }
        }
    }

    /**
     * A producer that calls another producer with the {@link SendHook} around it. It passes its lifecycle on to that
     * producer, so as the output of an error handler it is shut down with the error handler as the producer would be.
     * (It is not {@link org.apache.camel.SuspendableService}: an error handler does not suspend its output.)
     */
    private static final class SendHookProducer implements AsyncProducer, ShutdownableService {

        private final AsyncProducer producer;

        private SendHookProducer(AsyncProducer producer) {
            this.producer = producer;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            return SendHelper.process(exchange, producer.getEndpoint(), producer, callback);
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            PluginHelper.getAsyncProcessorAwaitManager(exchange.getContext()).process(this, exchange);
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            AsyncCallbackToCompletableFutureAdapter<Exchange> callback
                    = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
            process(exchange, callback);
            return callback.getFuture();
        }

        @Override
        public Endpoint getEndpoint() {
            return producer.getEndpoint();
        }

        @Override
        public boolean isSingleton() {
            return producer.isSingleton();
        }

        @Override
        public void build() {
            producer.build();
        }

        @Override
        public void init() {
            producer.init();
        }

        @Override
        public void start() {
            producer.start();
        }

        @Override
        public void stop() {
            producer.stop();
        }

        @Override
        public void shutdown() {
            // as for the producer itself: stopped, and shut down if it can be
            ServiceHelper.stopAndShutdownService(producer);
        }

        @Override
        public void close() throws IOException {
            producer.close();
        }

        @Override
        public String toString() {
            return producer.toString();
        }
    }
}
