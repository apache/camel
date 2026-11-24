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
package org.apache.camel.component.hazelcast.seda;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.hazelcast.collection.BaseQueue;
import com.hazelcast.transaction.TransactionContext;
import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.ShutdownStrategy;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.DefaultExchangeHolder;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of Hazelcast SEDA {@link Consumer} component.
 */
public class HazelcastSedaConsumer extends DefaultConsumer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(HazelcastSedaConsumer.class);
    // the longest a single poll waits (or pollTimeout when it is shorter): the consumer stops by letting its poll end,
    // so the thread sees a stop within this time. A poll that returns nothing just goes round the loop again
    private static final long MAX_POLL_WAIT = 1000L;

    private final HazelcastSedaEndpoint endpoint;
    private final AsyncProcessor processor;
    private ExecutorService executor;
    // read by the consumer threads, which can still run after doStop when the stop was interrupted
    private volatile ScheduledExecutorService taskExecutor;
    private volatile boolean transferredExchangeLogged;
    // the transacted consumer threads that poll or route a polled exchange, guarded by transactedLock: the stop waits
    // for them before it shuts the threads down, as the shutdown interrupts a thread that waits for its exchange, which
    // rolls back the transaction of an exchange that still completes (so its message is consumed again)
    private final Lock transactedLock = new ReentrantLock();
    private final Condition transactedDone = transactedLock.newCondition();
    private final Set<Thread> transactedThreads = new HashSet<>();

    public HazelcastSedaConsumer(final Endpoint endpoint, final Processor processor) {
        super(endpoint, processor);
        this.endpoint = (HazelcastSedaEndpoint) endpoint;
        this.processor = AsyncProcessorConverterHelper.convert(processor);
    }

    @Override
    protected void doStart() throws Exception {
        taskExecutor = endpoint.getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "HazelcastSedaRecoveryTask");
        int concurrentConsumers = endpoint.getConfiguration().getConcurrentConsumers();
        executor = endpoint.getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, endpoint.getEndpointUri(),
                concurrentConsumers);
        for (int i = 0; i < concurrentConsumers; i++) {
            executor.execute(this);
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (executor != null) {
            if (endpoint.getConfiguration().isTransacted()) {
                awaitTransactedExchanges();
            }
            // wait for the consumer threads to end their current poll (at most MAX_POLL_WAIT) and route what it returned:
            // a poll takes the item from the queue, so an item polled after the processor is stopped is lost
            endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(executor);
            executor = null;
        }
        // after the consumer threads, which use it for the delay after an error
        if (taskExecutor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdown(taskExecutor);
            taskExecutor = null;
        }
        super.doStop();
    }

    @Override
    protected boolean isStopOnFatalError() {
        // the consumer takes from the queue in a loop: it stops after a VirtualMachineError (CAMEL-27515)
        return true;
    }

    @Override
    public void run() {
        BaseQueue<?> queue = endpoint.getHazelcastInstance().getQueue(endpoint.getConfiguration().getQueueName());

        while (queue != null && isRunAllowed()) {
            final boolean transacted = endpoint.getConfiguration().isTransacted();
            if (transacted && !beginTransacted()) {
                // the consumer is stopping
                break;
            }
            boolean errorDelay = false;
            // the transacted consumer reads the outcome of the exchange after the routing is done, so it releases the
            // exchange itself: a pooled exchange is otherwise reset (and its exception cleared) when it is done
            final Exchange exchange = createExchange(!transacted);

            TransactionContext transactionCtx = null;
            try {
                if (transacted) {
                    // Get and begin transaction if exist
                    transactionCtx = endpoint.getHazelcastInstance().newTransactionContext();

                    if (transactionCtx != null) {
                        LOG.trace("Begin transaction: {}", transactionCtx.getTxnId());
                        transactionCtx.beginTransaction();
                        queue = transactionCtx.getQueue(endpoint.getConfiguration().getQueueName());
                    }
                }

                final Object body = queue.poll(Math.min(endpoint.getConfiguration().getPollTimeout(), MAX_POLL_WAIT),
                        TimeUnit.MILLISECONDS);

                if (body != null) {
                    if (body instanceof DefaultExchangeHolder holder) {
                        takeTransferredExchange(exchange, holder);
                    } else {
                        exchange.getIn().setBody(body);
                    }
                    try {
                        final TransactionContext txc = transactionCtx;
                        if (transacted) {
                            // a Hazelcast transaction belongs to the thread that began it, and cannot be committed
                            // or rolled back from another thread: wait for the exchange to complete, also when the
                            // route continues on another thread, and end the transaction on this thread
                            try {
                                processor.process(exchange);
                            } catch (Exception e) {
                                exchange.setException(e);
                            }
                            // when the message is back on the queue, wait before taking it again, or a message that
                            // always fails is taken and rolled back in a tight loop
                            errorDelay = onExchangeDone(exchange, txc);
                        } else {
                            // process using the asynchronous routing engine
                            processor.process(exchange, new AsyncCallback() {
                                public void done(boolean asyncDone) {
                                    onExchangeDone(exchange, null);
                                }
                            });
                        }
                    } catch (Exception e) {
                        LOG.error("Hzlq Exception caught: {}", e, e);
                        // Rollback
                        if (transactionCtx != null) {
                            LOG.trace("Rollback transaction: {}", transactionCtx.getTxnId());
                            transactionCtx.rollbackTransaction();
                        }
                    }
                } else {
                    if (transactionCtx != null) {
                        transactionCtx.commitTransaction();
                    }
                }
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Hzlq Consumer Interrupted: {}", e, e);
                }
                Thread.currentThread().interrupt();
                continue;
            } catch (VirtualMachineError e) {
                // a JVM-level failure: the consumer stops consuming until the route is restarted, and the loop ends, as
                // isRunAllowed() is false (CAMEL-27515)
                if (transactionCtx != null) {
                    try {
                        transactionCtx.rollbackTransaction();
                    } catch (Exception ignore) {
                    }
                }
                LOG.error("Fatal error consuming from: {}. The consumer stops consuming until the route is restarted.",
                        getEndpoint(), e);
                handleFatalError(e);
            } catch (Exception | Error e) {
                // also another Error, as an exception: the loop goes on (CAMEL-27474)
                // Rollback
                if (transactionCtx != null) {
                    LOG.trace("Rollback transaction: {}", transactionCtx.getTxnId());
                    try {
                        transactionCtx.rollbackTransaction();
                    } catch (Exception ignore) {
                    }
                }
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
                errorDelay = true;
            } finally {
                if (transacted) {
                    releaseExchange(exchange, false);
                    endTransacted();
                }
            }
            if (errorDelay) {
                // after endTransacted: the stop does not wait for the delay
                waitOnErrorDelay();
            }
        }
    }

    /**
     * Counts a transacted consumer thread that polls and routes, unless the consumer is stopping (checked under the
     * lock, so the stop either waits for this thread or this thread does not poll).
     */
    private boolean beginTransacted() {
        transactedLock.lock();
        try {
            if (!isRunAllowed()) {
                return false;
            }
            transactedThreads.add(Thread.currentThread());
            return true;
        } finally {
            transactedLock.unlock();
        }
    }

    private void endTransacted() {
        transactedLock.lock();
        try {
            transactedThreads.remove(Thread.currentThread());
            transactedDone.signalAll();
        } finally {
            transactedLock.unlock();
        }
    }

    /**
     * Waits until no transacted consumer thread polls or routes an exchange, at most the timeout of the shutdown
     * strategy, and not at all when the shutdown strategy has timed out and forces the consumers to stop. The shutdown
     * of the consumer threads that follows interrupts a thread that is still waiting for its exchange, and the
     * transaction of that exchange is then rolled back.
     */
    private void awaitTransactedExchanges() {
        ShutdownStrategy strategy = endpoint.getCamelContext().getShutdownStrategy();
        if (strategy.isForceShutdown() || strategy.isTimeoutOccurred()) {
            // the graceful shutdown timed out, and stops the remaining consumers now: do not wait again
            LOG.debug("Not waiting for the transacted exchanges of {}: the shutdown is forced", endpoint);
            return;
        }
        long timeoutMillis = strategy.getTimeUnit().toMillis(strategy.getTimeout());
        long remaining = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        transactedLock.lock();
        try {
            // a thread that only polls is done within MAX_POLL_WAIT (and its commit): say why the stop waits longer
            long quiet = Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(2 * MAX_POLL_WAIT));
            remaining -= quiet - awaitTransactedDone(quiet);
            if (transactedPending() > 0 && remaining > 0) {
                LOG.info("Waiting up to {} ms for {} transacted exchanges of {} to complete before the consumer stops",
                        TimeUnit.NANOSECONDS.toMillis(remaining), transactedPending(), endpoint);
                awaitTransactedDone(remaining);
            }
            if (transactedPending() > 0) {
                LOG.warn("Timeout waiting for {} transacted exchanges of {} to complete: the consumer stops now, and"
                         + " their transactions are rolled back, so their messages are consumed again",
                        transactedPending(), endpoint);
            }
        } catch (InterruptedException e) {
            // the shutdown timed out (it cancels its task): stop now, which rolls back the remaining exchanges
            LOG.debug("Interrupted while waiting for the transacted exchanges of {} to complete", endpoint);
            Thread.currentThread().interrupt();
        } finally {
            transactedLock.unlock();
        }
    }

    /**
     * Waits (holding transactedLock) until no transacted consumer thread polls or routes, at most the given time.
     *
     * @return the time left
     */
    private long awaitTransactedDone(long nanos) throws InterruptedException {
        while (transactedPending() > 0 && nanos > 0) {
            nanos = transactedDone.awaitNanos(nanos);
        }
        return Math.max(0, nanos);
    }

    /**
     * The transacted consumer threads that the stop waits for (holding transactedLock): not the thread that stops, when
     * a consumer thread stops the consumer itself (a route policy when its exchange completes), as it cannot end its
     * exchange while it waits.
     */
    private int transactedPending() {
        return transactedThreads.size() - (transactedThreads.contains(Thread.currentThread()) ? 1 : 0);
    }

    /**
     * An exchange that a producer with transferExchange=true put on the queue: the consumer restores its headers and
     * exchange properties only when it has transferExchange=true as well, and otherwise takes only its body.
     */
    private void takeTransferredExchange(Exchange exchange, DefaultExchangeHolder holder) {
        if (endpoint.getConfiguration().isTransferExchange()) {
            DefaultExchangeHolder.unmarshal(exchange, holder);
            return;
        }
        if (!transferredExchangeLogged) {
            transferredExchangeLogged = true;
            LOG.warn("The queue {} holds an exchange that a producer with transferExchange=true put on it, but this consumer"
                     + " has transferExchange=false: it takes only the body of such an exchange, without its headers and"
                     + " exchange properties. Set transferExchange=true on the consumer to restore them.",
                    endpoint.getConfiguration().getQueueName());
        }
        Exchange transferred = new DefaultExchange(endpoint.getCamelContext());
        DefaultExchangeHolder.unmarshal(transferred, holder);
        exchange.getIn().setBody(transferred.getIn().getBody());
    }

    /**
     * Waits onErrorDelay before the consumer polls again, after an error or a rolled back transaction.
     */
    private void waitOnErrorDelay() {
        final ScheduledExecutorService scheduler = taskExecutor;
        if (scheduler == null || !isRunAllowed()) {
            // the consumer is stopping, so the loop ends instead of polling again (and the scheduler may be shut down
            // already, when the stop did not wait for this thread)
            return;
        }
        // Use Camel's task API for error recovery delay instead of Thread.sleep()
        // We use initialDelay for the actual delay, and maxIterations(1) to run once
        Tasks.backgroundTask()
                .withBudget(Budgets.iterationTimeBudget()
                        .withMaxIterations(1)
                        .withInitialDelay(Duration.ofMillis(endpoint.getConfiguration().getOnErrorDelay()))
                        .withInterval(Duration.ofMillis(1))
                        .withUnlimitedDuration()
                        .build())
                .withScheduledExecutor(scheduler)
                .withName("HazelcastSedaErrorRecoveryDelay")
                .build()
                .run(getEndpoint().getCamelContext(), () -> true);
    }

    /**
     * Ends the transaction of the exchange, if any, and handles its exception.
     *
     * @return true when the transaction was rolled back, so the message is on the queue again
     */
    private boolean onExchangeDone(Exchange exchange, TransactionContext txc) {
        // a rollback only exchange (without an exception) did not complete successfully either
        boolean rollback = exchange.getException() != null || exchange.isRollbackOnly()
                || exchange.isRollbackOnlyLast();
        if (rollback && txc != null) {
            LOG.trace("Rollback transaction: {}", txc.getTxnId());
            txc.rollbackTransaction();
        }
        if (exchange.getException() != null) {
            getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
        }
        // It's OK, I commit
        if (!rollback && txc != null) {
            LOG.trace("Commit transaction: {}", txc.getTxnId());
            txc.commitTransaction();
        }
        return rollback && txc != null;
    }

}
