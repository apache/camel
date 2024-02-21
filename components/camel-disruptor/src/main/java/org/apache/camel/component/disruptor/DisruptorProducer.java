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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.lmax.disruptor.InsufficientCapacityException;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.support.DefaultAsyncProducer;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Producer for the Disruptor component.
 */
public class DisruptorProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DisruptorProducer.class);

    private final WaitForTaskToComplete waitForTaskToComplete;
    private final long timeout;

    private final DisruptorEndpoint endpoint;
    private boolean blockWhenFull;

    public DisruptorProducer(
                             final DisruptorEndpoint endpoint,
                             final WaitForTaskToComplete waitForTaskToComplete,
                             final long timeout, boolean blockWhenFull) {
        super(endpoint);
        this.waitForTaskToComplete = waitForTaskToComplete;
        this.timeout = timeout;
        this.endpoint = endpoint;
        this.blockWhenFull = blockWhenFull;
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
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        WaitForTaskToComplete wait = waitForTaskToComplete;
        if (exchange.getProperty(Exchange.ASYNC_WAIT) != null) {
            wait = exchange.getProperty(Exchange.ASYNC_WAIT, WaitForTaskToComplete.class);
        }

        if (wait == WaitForTaskToComplete.Always
                || wait == WaitForTaskToComplete.IfReplyExpected && ExchangeHelper.isOutCapable(exchange)) {

            // do not handover the completion as we wait for the copy to complete, and copy its result back when it done
            final Exchange copy = prepareCopy(exchange, false);

            // latch that waits until we are complete
            final CountDownLatch latch = new CountDownLatch(1);

            // we should wait for the reply so install a on completion so we know when its complete
            copy.getExchangeExtension().addOnCompletion(newOnCompletion(exchange, latch));

            doPublish(copy);

            if (timeout > 0) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Waiting for task to complete using timeout (ms): {} at [{}]", timeout,
                            endpoint.getEndpointUri());
                }
                // lets see if we can get the task done before the timeout
                boolean done = false;
                try {
                    done = latch.await(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    LOG.info("Interrupted while waiting for the task to complete");
                    Thread.currentThread().interrupt();
                }
                if (!done) {
                    // Remove timed out Exchange from disruptor endpoint.

                    // We can't actually remove a published exchange from an active Disruptor.
                    // Instead we prevent processing of the exchange by setting a Property on the exchange and the value
                    // would be an AtomicBoolean. This is set by the Producer and the Consumer would look up that Property and
                    // check the AtomicBoolean. If the AtomicBoolean says that we are good to proceed, it will process the
                    // exchange. If false, it will simply disregard the exchange.
                    // But since the Property map is a Concurrent one, maybe we don't need the AtomicBoolean. Check with Simon.
                    // Also check the TimeoutHandler of the new Disruptor 3.0.0, consider making the switch to the latest version.
                    exchange.setProperty(DisruptorEndpoint.DISRUPTOR_IGNORE_EXCHANGE, true);

                    exchange.setException(new ExchangeTimedOutException(exchange, timeout));

                    // count down to indicate timeout
                    latch.countDown();
                }
            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Waiting for task to complete (blocking) at [{}]", endpoint.getEndpointUri());
                }
                // no timeout then wait until its done
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    LOG.info("Interrupted while waiting for the task to complete");
                    Thread.currentThread().interrupt();
                }
            }
        } else {
            // no wait, eg its a InOnly then just publish to the ringbuffer and return
            // handover the completion so its the copy which performs that, as we do not wait
            final Exchange copy = prepareCopy(exchange, true);
            doPublish(copy);
        }

        // we use OnCompletion on the Exchange to callback and wait for the Exchange to be done
        // so we should just signal the callback we are done synchronously
        callback.done(true);
        return true;
    }

    private SynchronizationAdapter newOnCompletion(Exchange exchange, CountDownLatch latch) {
        return new SynchronizationAdapter() {
            @Override
            public void onDone(final Exchange response) {
                // check for timeout, which then already would have invoked the latch
                if (latch.getCount() == 0) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("{}. Timeout occurred so response will be ignored: {}", this, response.getMessage());
                    }
                } else {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("{} with response: {}", this, response.getMessage());
                    }
                    try {
                        ExchangeHelper.copyResults(exchange, response);
                    } finally {
                        // always ensure latch is triggered
                        latch.countDown();
                    }
                }
            }

            @Override
            public boolean allowHandover() {
                // do not allow handover as we want to seda producer to have its completion triggered
                // at this point in the routing (at this leg), instead of at the very last (this ensure timeout is honored)
                return false;
            }

            @Override
            public String toString() {
                return "onDone at endpoint: " + endpoint;
            }
        };
    }

    private void doPublish(Exchange exchange) {
        LOG.trace("Publishing Exchange to disruptor ringbuffer: {}", exchange);

        try {
            if (blockWhenFull) {
                endpoint.publish(exchange);
            } else {
                endpoint.tryPublish(exchange);
            }
        } catch (DisruptorNotStartedException e) {
            throw new IllegalStateException("Disruptor was not started", e);
        } catch (InsufficientCapacityException e) {
            throw new IllegalStateException("Disruptors ringbuffer was full", e);
        }
    }

    private Exchange prepareCopy(final Exchange exchange, final boolean handover) {
        // use a new copy of the exchange to route async
        final Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, handover);
        // set a new from endpoint to be the disruptor
        copy.getExchangeExtension().setFromEndpoint(endpoint);
        return copy;
    }
}
