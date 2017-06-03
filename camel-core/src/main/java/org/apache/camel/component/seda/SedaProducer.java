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
package org.apache.camel.component.seda;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.WaitForTaskToComplete;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.ExchangeHelper;

/**
 * @version 
 */
public class SedaProducer extends DefaultAsyncProducer {
    
    /**
     * @deprecated Better make use of the {@link SedaEndpoint#getQueue()} API which delivers the accurate reference to the queue currently being used.
     */
    @Deprecated
    protected final BlockingQueue<Exchange> queue;
    private final SedaEndpoint endpoint;
    private final WaitForTaskToComplete waitForTaskToComplete;
    private final long timeout;
    private final boolean blockWhenFull;

    /**
     * @deprecated Use {@link #SedaProducer(SedaEndpoint, WaitForTaskToComplete, long, boolean) the other constructor}.
     */
    @Deprecated
    public SedaProducer(SedaEndpoint endpoint, BlockingQueue<Exchange> queue, WaitForTaskToComplete waitForTaskToComplete, long timeout) {
        this(endpoint, waitForTaskToComplete, timeout, false);
    }

    /**
     * @deprecated Use {@link #SedaProducer(SedaEndpoint, WaitForTaskToComplete, long, boolean) the other constructor}.
     */
    @Deprecated
    public SedaProducer(SedaEndpoint endpoint, BlockingQueue<Exchange> queue, WaitForTaskToComplete waitForTaskToComplete, long timeout, boolean blockWhenFull) {
        this(endpoint, waitForTaskToComplete, timeout, blockWhenFull);
    }

    public SedaProducer(SedaEndpoint endpoint, WaitForTaskToComplete waitForTaskToComplete, long timeout, boolean blockWhenFull) {
        super(endpoint);
        this.queue = endpoint.getQueue();
        this.endpoint = endpoint;
        this.waitForTaskToComplete = waitForTaskToComplete;
        this.timeout = timeout;
        this.blockWhenFull = blockWhenFull;
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        WaitForTaskToComplete wait = waitForTaskToComplete;
        if (exchange.getProperty(Exchange.ASYNC_WAIT) != null) {
            wait = exchange.getProperty(Exchange.ASYNC_WAIT, WaitForTaskToComplete.class);
        }

        if (wait == WaitForTaskToComplete.Always
            || (wait == WaitForTaskToComplete.IfReplyExpected && ExchangeHelper.isOutCapable(exchange))) {

            // do not handover the completion as we wait for the copy to complete, and copy its result back when it done
            Exchange copy = prepareCopy(exchange, false);

            // latch that waits until we are complete
            final CountDownLatch latch = new CountDownLatch(1);

            // we should wait for the reply so install a on completion so we know when its complete
            copy.addOnCompletion(new SynchronizationAdapter() {
                @Override
                public void onDone(Exchange response) {
                    // check for timeout, which then already would have invoked the latch
                    if (latch.getCount() == 0) {
                        if (log.isTraceEnabled()) {
                            log.trace("{}. Timeout occurred so response will be ignored: {}", this, response.hasOut() ? response.getOut() : response.getIn());
                        }
                        return;
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace("{} with response: {}", this, response.hasOut() ? response.getOut() : response.getIn());
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
            });

            log.trace("Adding Exchange to queue: {}", copy);
            try {
                // do not copy as we already did the copy
                addToQueue(copy, false);
            } catch (SedaConsumerNotAvailableException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }

            if (timeout > 0) {
                if (log.isTraceEnabled()) {
                    log.trace("Waiting for task to complete using timeout (ms): {} at [{}]", timeout, endpoint.getEndpointUri());
                }
                // lets see if we can get the task done before the timeout
                boolean done = false;
                try {
                    done = latch.await(timeout, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // ignore
                }
                if (!done) {
                    exchange.setException(new ExchangeTimedOutException(exchange, timeout));
                    // remove timed out Exchange from queue
                    endpoint.getQueue().remove(copy);
                    // count down to indicate timeout
                    latch.countDown();
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("Waiting for task to complete (blocking) at [{}]", endpoint.getEndpointUri());
                }
                // no timeout then wait until its done
                try {
                    latch.await();
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        } else {
            // no wait, eg its a InOnly then just add to queue and return
            try {
                addToQueue(exchange, true);
            } catch (SedaConsumerNotAvailableException e) {
                exchange.setException(e);
                callback.done(true);
                return true;
            }
        }

        // we use OnCompletion on the Exchange to callback and wait for the Exchange to be done
        // so we should just signal the callback we are done synchronously
        callback.done(true);
        return true;
    }

    protected Exchange prepareCopy(Exchange exchange, boolean handover) {
        // use a new copy of the exchange to route async (and use same message id)

        // if handover we need to do special handover to avoid handing over
        // RestBindingMarshalOnCompletion as it should not be handed over with SEDA
        Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, handover, true,
            synchronization -> !synchronization.getClass().getName().contains("RestBindingMarshalOnCompletion"));
        // set a new from endpoint to be the seda queue
        copy.setFromEndpoint(endpoint);
        return copy;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        endpoint.onStarted(this);
    }

    @Override
    protected void doStop() throws Exception {
        endpoint.onStopped(this);
        super.doStop();
    }

    /**
     * Strategy method for adding the exchange to the queue.
     * <p>
     * Will perform a blocking "put" if blockWhenFull is true, otherwise it will
     * simply add which will throw exception if the queue is full
     * 
     * @param exchange the exchange to add to the queue
     * @param copy     whether to create a copy of the exchange to use for adding to the queue
     */
    protected void addToQueue(Exchange exchange, boolean copy) throws SedaConsumerNotAvailableException {
        BlockingQueue<Exchange> queue = null;
        QueueReference queueReference = endpoint.getQueueReference();
        if (queueReference != null) {
            queue = queueReference.getQueue();
        }
        if (queue == null) {
            throw new SedaConsumerNotAvailableException("No queue available on endpoint: " + endpoint, exchange);
        }

        boolean empty = !queueReference.hasConsumers();
        if (empty) {
            if (endpoint.isFailIfNoConsumers()) {
                throw new SedaConsumerNotAvailableException("No consumers available on endpoint: " + endpoint, exchange);
            } else if (endpoint.isDiscardIfNoConsumers()) {
                log.debug("Discard message as no active consumers on endpoint: " + endpoint);
                return;
            }
        }

        Exchange target = exchange;

        // handover the completion so its the copy which performs that, as we do not wait
        if (copy) {
            target = prepareCopy(exchange, true);
        }

        log.trace("Adding Exchange to queue: {}", target);
        if (blockWhenFull) {
            try {
                queue.put(target);
            } catch (InterruptedException e) {
                // ignore
                log.debug("Put interrupted, are we stopping? {}", isStopping() || isStopped());
            }
        } else {
            queue.add(target);
        }
    }

}
