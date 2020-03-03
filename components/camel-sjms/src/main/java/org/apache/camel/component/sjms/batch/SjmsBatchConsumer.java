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
package org.apache.camel.component.sjms.batch;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;

import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SjmsBatchConsumer extends DefaultConsumer {

    public static final String SJMS_BATCH_TIMEOUT_CHECKER = "SJmsBatchTimeoutChecker";

    private static final boolean TRANSACTED = true;
    private static final Logger LOG = LoggerFactory.getLogger(SjmsBatchConsumer.class);

    // global counters, maybe they should be on component instead?
    private static final AtomicInteger BATCH_COUNT = new AtomicInteger();
    private static final AtomicLong MESSAGE_RECEIVED = new AtomicLong();
    private static final AtomicLong MESSAGE_PROCESSED = new AtomicLong();

    private ScheduledExecutorService timeoutCheckerExecutorService;
    private boolean shutdownTimeoutCheckerExecutorService;

    private final SjmsBatchEndpoint sjmsBatchEndpoint;
    private final AggregationStrategy aggregationStrategy;
    private final int completionSize;
    private final int completionInterval;
    private final int completionTimeout;
    private final Predicate completionPredicate;
    private final boolean eagerCheckCompletion;
    private final int consumerCount;
    private final int pollDuration;
    private final ConnectionFactory connectionFactory;
    private final String destinationName;
    private ExecutorService jmsConsumerExecutors;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<CountDownLatch> consumersShutdownLatchRef = new AtomicReference<>();
    private volatile Connection connection;

    public SjmsBatchConsumer(SjmsBatchEndpoint sjmsBatchEndpoint, Processor processor) {
        super(sjmsBatchEndpoint, processor);

        this.sjmsBatchEndpoint = ObjectHelper.notNull(sjmsBatchEndpoint, "batchJmsEndpoint");

        destinationName = StringHelper.notEmpty(sjmsBatchEndpoint.getDestinationName(), "destinationName");

        completionSize = sjmsBatchEndpoint.getCompletionSize();
        completionInterval = sjmsBatchEndpoint.getCompletionInterval();
        completionTimeout = sjmsBatchEndpoint.getCompletionTimeout();
        if (completionInterval > 0 && completionTimeout != SjmsBatchEndpoint.DEFAULT_COMPLETION_TIMEOUT) {
            throw new IllegalArgumentException("Only one of completionInterval or completionTimeout can be used, not both.");
        }
        if (sjmsBatchEndpoint.isSendEmptyMessageWhenIdle() && completionTimeout <= 0 && completionInterval <= 0) {
            throw new IllegalArgumentException("SendEmptyMessageWhenIdle can only be enabled if either completionInterval or completionTimeout is also set");
        }
        completionPredicate = sjmsBatchEndpoint.getCompletionPredicate();
        eagerCheckCompletion = sjmsBatchEndpoint.isEagerCheckCompletion();

        pollDuration = sjmsBatchEndpoint.getPollDuration();
        if (pollDuration < 0) {
            throw new IllegalArgumentException("pollDuration must be 0 or greater");
        }

        this.aggregationStrategy = ObjectHelper.notNull(sjmsBatchEndpoint.getAggregationStrategy(), "aggregationStrategy");

        consumerCount = sjmsBatchEndpoint.getConsumerCount();
        if (consumerCount <= 0) {
            throw new IllegalArgumentException("consumerCount must be greater than 0");
        }

        SjmsBatchComponent sjmsBatchComponent = sjmsBatchEndpoint.getComponent();
        connectionFactory = ObjectHelper.notNull(sjmsBatchComponent.getConnectionFactory(), "jmsBatchComponent.connectionFactory");
    }

    @Override
    public SjmsBatchEndpoint getEndpoint() {
        return sjmsBatchEndpoint;
    }

    public ScheduledExecutorService getTimeoutCheckerExecutorService() {
        return timeoutCheckerExecutorService;
    }

    public void setTimeoutCheckerExecutorService(ScheduledExecutorService timeoutCheckerExecutorService) {
        this.timeoutCheckerExecutorService = timeoutCheckerExecutorService;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        boolean recovery = getEndpoint().isAsyncStartListener();
        StartConsumerTask task = new StartConsumerTask(recovery, getEndpoint().getRecoveryInterval(), getEndpoint().getKeepAliveDelay());

        if (recovery) {
            // use a background thread to keep starting the consumer until
            getEndpoint().getComponent().getAsyncStartStopExecutorService().submit(task);
        } else {
            task.run();
        }
    }

    /**
     * Task to startup the consumer either synchronously or using asynchronous with recovery
     */
    protected class StartConsumerTask implements Runnable {

        private boolean recoveryEnabled;
        private int recoveryInterval;
        private int keepAliveDelay;
        private long attempt;

        public StartConsumerTask(boolean recoveryEnabled, int recoveryInterval, int keepAliveDelay) {
            this.recoveryEnabled = recoveryEnabled;
            this.recoveryInterval = recoveryInterval;
            this.keepAliveDelay = keepAliveDelay;
        }

        @Override
        public void run() {
            jmsConsumerExecutors = getEndpoint().getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "SjmsBatchConsumer", consumerCount);
            consumersShutdownLatchRef.set(new CountDownLatch(consumerCount));

            if (completionInterval > 0) {
                LOG.info("Using CompletionInterval to run every {} millis.", completionInterval);
                if (timeoutCheckerExecutorService == null) {
                    setTimeoutCheckerExecutorService(getEndpoint().getCamelContext().getExecutorServiceManager().newScheduledThreadPool(this, SJMS_BATCH_TIMEOUT_CHECKER, 1));
                    shutdownTimeoutCheckerExecutorService = true;
                }
            }

            // keep loop until we can connect
            while (isRunAllowed() && !running.get()) {
                Connection localConnection = null;
                try {
                    attempt++;

                    LOG.debug("Attempt #{}. Starting {} consumer(s) for {}:{}", attempt, consumerCount, destinationName, completionSize);

                    // start up a shared connection
                    localConnection = connectionFactory.createConnection();
                    localConnection.start();

                    // its success so prepare for exit
                    connection = localConnection;

                    final List<AtomicBoolean> triggers = new ArrayList<>();
                    for (int i = 0; i < consumerCount; i++) {
                        BatchConsumptionLoop loop = new BatchConsumptionLoop();
                        loop.setKeepAliveDelay(keepAliveDelay);
                        triggers.add(loop.getCompletionTimeoutTrigger());
                        jmsConsumerExecutors.submit(loop);
                    }

                    if (completionInterval > 0) {
                        // trigger completion based on interval
                        timeoutCheckerExecutorService.scheduleAtFixedRate(new CompletionIntervalTask(triggers), completionInterval, completionInterval, TimeUnit.MILLISECONDS);
                    }

                    if (attempt > 1) {
                        LOG.info("Successfully refreshed connection after {} attempts.", attempt);
                    }

                    LOG.info("Started {} consumer(s) for {}:{}", consumerCount, destinationName, completionSize);
                    running.set(true);
                    return;
                } catch (Throwable e) {
                    // we failed so close the local connection as we create a new on next attempt
                    try {
                        if (localConnection != null) {
                            localConnection.close();
                        }
                    } catch (Throwable t) {
                        // ignore
                    }

                    if (recoveryEnabled) {
                        getExceptionHandler().handleException("Error starting consumer after " + attempt + " attempts. Will try again in " + recoveryInterval + " millis.", e);
                    } else {
                        throw RuntimeCamelException.wrapRuntimeCamelException(e);
                    }
                }

                // sleeping before next attempt
                try {
                    LOG.debug("Attempt #{}. Sleeping {} before next attempt to recover", attempt, recoveryInterval);
                    Thread.sleep(recoveryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        running.set(false);

        CountDownLatch consumersShutdownLatch = consumersShutdownLatchRef.get();
        if (consumersShutdownLatch != null) {
            LOG.info("Stop signalled, waiting on consumers to shut down");
            if (consumersShutdownLatch.await(60, TimeUnit.SECONDS)) {
                LOG.warn("Timeout waiting on consumer threads to signal completion - shutting down");
            } else {
                LOG.info("All consumers have been shutdown");
            }
        } else {
            LOG.info("Stop signalled while there are no consumers yet, so no need to wait for consumers");
        }

        try {
            LOG.debug("Shutting down JMS connection");
            connection.close();
        } catch (Exception e) {
            // ignore
        }

        getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(jmsConsumerExecutors);
        jmsConsumerExecutors = null;

        if (shutdownTimeoutCheckerExecutorService) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(timeoutCheckerExecutorService);
            timeoutCheckerExecutorService = null;
        }
    }

    /**
     * Background task that triggers completion based on interval.
     */
    private final class CompletionIntervalTask implements Runnable {

        private final List<AtomicBoolean> triggers;

        CompletionIntervalTask(List<AtomicBoolean> triggers) {
            this.triggers = triggers;
        }

        @Override
        public void run() {
            // only run if CamelContext has been fully started
            if (!getEndpoint().getCamelContext().getStatus().isStarted()) {
                LOG.trace("Completion interval task cannot start due CamelContext({}) has not been started yet", getEndpoint().getCamelContext().getName());
                return;
            }

            // signal
            for (AtomicBoolean trigger : triggers) {
                trigger.set(true);
            }
        }
    }

    private class BatchConsumptionLoop implements Runnable {

        private final AtomicBoolean completionTimeoutTrigger = new AtomicBoolean();
        private final BatchConsumptionTask task = new BatchConsumptionTask(completionTimeoutTrigger);
        private int keepAliveDelay;

        public AtomicBoolean getCompletionTimeoutTrigger() {
            return completionTimeoutTrigger;
        }
        public void setKeepAliveDelay(int i) {
            keepAliveDelay = i;
        }

        @Override
        public void run() {
            try {
                // This loop is intended to keep the consumer up and running as long as it's supposed to be, but allow it to bail if signaled.
                // I'm using a do/while loop because the first time through we want to attempt it regardless of any other conditions... we
                // only want to try AGAIN if the keepAlive is set.
                do {
                    // a batch corresponds to a single session that will be committed or rolled back by a background thread
                    final Session session = connection.createSession(TRANSACTED, Session.CLIENT_ACKNOWLEDGE);
                    try {
                        // only batch consumption from queues is supported - it makes no sense to transactionally consume
                        // from a topic as you don't car about message loss, users can just use a regular aggregator instead
                        Queue queue = session.createQueue(destinationName);
                        MessageConsumer consumer = session.createConsumer(queue);

                        try {
                            task.consumeBatchesOnLoop(session, consumer);
                        } finally {
                            closeJmsConsumer(consumer);
                        }
                    } catch (javax.jms.IllegalStateException ex) {
                        // from consumeBatchesOnLoop
                        // if keepAliveDelay was not specified (defaults to -1) just rethrow to break the loop. This preserves original default behavior
                        if (keepAliveDelay < 0) {
                            throw ex;
                        }
                        // this will log the exception and the parent loop will create a new session
                        getExceptionHandler().handleException("Exception caught consuming from " + destinationName, ex);
                        //sleep to avoid log spamming
                        if (keepAliveDelay > 0) {
                            Thread.sleep(keepAliveDelay);
                        }
                    } finally {
                        closeJmsSession(session);
                    }
                }while (running.get() || isStarting());
            } catch (Throwable ex) {
                // from consumeBatchesOnLoop
                // catch anything besides the IllegalStateException and exit the application
                getExceptionHandler().handleException("Exception caught consuming from " + destinationName, ex);
            } finally {
                // indicate that we have shut down
                CountDownLatch consumersShutdownLatch = consumersShutdownLatchRef.get();
                consumersShutdownLatch.countDown();
            }
        }

        private void closeJmsConsumer(MessageConsumer consumer) {
            try {
                consumer.close();
            } catch (JMSException ex2) {
                // only include stacktrace in debug logging
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception caught closing consumer", ex2);
                }
                LOG.warn("Exception caught closing consumer: {}. This exception is ignored.", ex2.getMessage());
            }
        }

        private void closeJmsSession(Session session) {
            try {
                session.close();
            } catch (JMSException ex2) {
                // only include stacktrace in debug logging
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception caught closing session", ex2);
                }
                LOG.warn("Exception caught closing session: {}. This exception is ignored.", ex2.getMessage());
            }
        }

        private final class BatchConsumptionTask {

            // state
            private final AtomicBoolean timeoutInterval;
            private final AtomicBoolean timeout = new AtomicBoolean();
            private int messageCount;
            private long timeElapsed;
            private long startTime;
            private Exchange aggregatedExchange;

            BatchConsumptionTask(AtomicBoolean timeoutInterval) {
                this.timeoutInterval = timeoutInterval;
            }

            private void consumeBatchesOnLoop(final Session session, final MessageConsumer consumer) throws JMSException {
                final boolean usingTimeout = completionTimeout > 0;

                LOG.trace("BatchConsumptionTask +++ start +++");

                while (running.get()) {

                    LOG.trace("BatchConsumptionTask running");

                    if (timeout.compareAndSet(true, false) || timeoutInterval.compareAndSet(true, false)) {
                        // trigger timeout
                        LOG.trace("Completion batch due timeout");
                        String completedBy = completionInterval > 0 ? "interval" : "timeout";
                        completionBatch(session, completedBy);
                        reset();
                        continue;
                    }

                    if (completionSize > 0 && messageCount >= completionSize) {
                        // trigger completion size
                        LOG.trace("Completion batch due size");
                        completionBatch(session, "size");
                        reset();
                        continue;
                    }

                    // check periodically to see whether we should be shutting down
                    long waitTime = (usingTimeout && (timeElapsed > 0))
                            ? getReceiveWaitTime(timeElapsed)
                            : pollDuration;


                    Message message = consumer.receive(waitTime);

                    if (running.get()) {
                        // no interruptions received
                        if (message == null) {
                            // timed out, no message received
                            LOG.trace("No message received");
                        } else {
                            messageCount++;
                            LOG.debug("#{} messages received", messageCount);

                            if (usingTimeout && startTime == 0) {
                                // this is the first message start counting down the period for this batch
                                startTime = new Date().getTime();
                            }

                            final Exchange exchange = getEndpoint().createExchange(message, session);
                            aggregatedExchange = aggregationStrategy.aggregate(aggregatedExchange, exchange);
                            aggregatedExchange.setProperty(Exchange.BATCH_SIZE, messageCount);

                            // is the batch complete by predicate?
                            if (completionPredicate != null) {
                                try {
                                    boolean complete;
                                    if (eagerCheckCompletion) {
                                        complete = completionPredicate.matches(exchange);
                                    } else {
                                        complete = completionPredicate.matches(aggregatedExchange);
                                    }
                                    if (complete) {
                                        // trigger completion predicate
                                        LOG.trace("Completion batch due predicate");
                                        completionBatch(session, "predicate");
                                        reset();
                                    }
                                } catch (Exception e) {
                                    LOG.warn("Error during evaluation of completion predicate " + e.getMessage() + ". This exception is ignored.", e);
                                }
                            }
                        }

                        if (usingTimeout && startTime > 0) {
                            // a batch has been started, check whether it should be timed out
                            long currentTime = new Date().getTime();
                            timeElapsed = currentTime - startTime;

                            if (timeElapsed > completionTimeout) {
                                // batch finished by timeout
                                timeout.set(true);
                            } else {
                                LOG.trace("This batch has more time until the timeout, elapsed: {} timeout: {}", timeElapsed, completionTimeout);
                            }
                        }

                    } else {
                        LOG.info("Shutdown signal received - rolling back batch");
                        session.rollback();
                    }
                }

                LOG.trace("BatchConsumptionTask +++ end +++");
            }

            private void reset() {
                messageCount = 0;
                timeElapsed = 0;
                startTime = 0;
                aggregatedExchange = null;
            }

            private void completionBatch(final Session session, String completedBy) {
                // batch
                if (aggregatedExchange == null && getEndpoint().isSendEmptyMessageWhenIdle()) {
                    processEmptyMessage();
                } else if (aggregatedExchange != null) {
                    processBatch(aggregatedExchange, session, completedBy);
                }
            }

        }

        /**
         * Determine the time that a call to {@link MessageConsumer#receive()} should wait given the time that has elapsed for this batch.
         *
         * @param timeElapsed The time that has elapsed.
         * @return The shorter of the time remaining or poll duration.
         */
        private long getReceiveWaitTime(long timeElapsed) {
            long timeRemaining = getTimeRemaining(timeElapsed);

            // wait for the shorter of the time remaining or the poll duration
            if (timeRemaining <= 0) { // ensure that the thread doesn't wait indefinitely
                timeRemaining = 1;
            }
            final long waitTime = Math.min(timeRemaining, pollDuration);

            LOG.trace("Waiting for {}", waitTime);
            return waitTime;
        }

        private long getTimeRemaining(long timeElapsed) {
            long timeRemaining = completionTimeout - timeElapsed;
            if (LOG.isDebugEnabled() && timeElapsed > 0) {
                LOG.debug("Time remaining this batch: {}", timeRemaining);
            }
            return timeRemaining;
        }

        /**
         * No messages in batch so send an empty message instead.
         */
        private void processEmptyMessage() {
            Exchange exchange = getEndpoint().createExchange();
            LOG.debug("Sending empty message as there were no messages from polling: {}", getEndpoint());
            try {
                getProcessor().process(exchange);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }

        /**
         * Send an message with the batches messages.
         */
        private void processBatch(Exchange exchange, Session session, String completedBy) {
            int id = BATCH_COUNT.getAndIncrement();
            int batchSize = exchange.getProperty(Exchange.BATCH_SIZE, Integer.class);
            if (LOG.isDebugEnabled()) {
                long total = MESSAGE_RECEIVED.get() + batchSize;
                LOG.debug("Processing batch[" + id + "]:size=" + batchSize + ":total=" + total);
            }

            if ("timeout".equals(completedBy)) {
                aggregationStrategy.timeout(exchange, id, batchSize, completionTimeout);
            }
            exchange.setProperty(Exchange.AGGREGATED_COMPLETED_BY, completedBy);

            // invoke the on completion callback
            aggregationStrategy.onCompletion(exchange);

            SessionCompletion sessionCompletion = new SessionCompletion(session);
            exchange.adapt(ExtendedExchange.class).addOnCompletion(sessionCompletion);
            try {
                getProcessor().process(exchange);
                long total = MESSAGE_PROCESSED.addAndGet(batchSize);
                LOG.debug("Completed processing[{}]:total={}", id, total);
            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }

    }
}
