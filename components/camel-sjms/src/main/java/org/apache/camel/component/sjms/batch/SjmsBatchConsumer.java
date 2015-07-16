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
package org.apache.camel.component.sjms.batch;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author jkorab
 */
public class SjmsBatchConsumer extends DefaultConsumer {
    private static final boolean TRANSACTED = true;
    private final Logger LOG = LoggerFactory.getLogger(SjmsBatchConsumer.class);

    private final SjmsBatchEndpoint sjmsBatchEndpoint;
    private final AggregationStrategy aggregationStrategy;

    private final int completionSize;
    private final int completionTimeout;
    private final int consumerCount;
    private final int pollDuration;

    private final ConnectionFactory connectionFactory;
    private final String destinationName;
    private final Processor processor;

    private static AtomicInteger batchCount = new AtomicInteger();
    private static AtomicLong messagesReceived = new AtomicLong();
    private static AtomicLong messagesProcessed = new AtomicLong();
    private ExecutorService jmsConsumerExecutors;

    public SjmsBatchConsumer(SjmsBatchEndpoint sjmsBatchEndpoint, Processor processor) {
        super(sjmsBatchEndpoint, processor);

        this.sjmsBatchEndpoint = ObjectHelper.notNull(sjmsBatchEndpoint, "batchJmsEndpoint");
        this.processor = ObjectHelper.notNull(processor, "processor");

        destinationName = ObjectHelper.notEmpty(sjmsBatchEndpoint.getDestinationName(), "destinationName");

        completionSize = sjmsBatchEndpoint.getCompletionSize();
        completionTimeout = sjmsBatchEndpoint.getCompletionTimeout();
        pollDuration = sjmsBatchEndpoint.getPollDuration();
        if (pollDuration < 0) {
            throw new IllegalArgumentException("pollDuration must be 0 or greater");
        }

        this.aggregationStrategy = ObjectHelper.notNull(sjmsBatchEndpoint.getAggregationStrategy(), "aggregationStrategy");

        consumerCount = sjmsBatchEndpoint.getConsumerCount();
        if (consumerCount <= 0) {
            throw new IllegalArgumentException("consumerCount must be greater than 0");
        }

        SjmsBatchComponent sjmsBatchComponent = (SjmsBatchComponent) sjmsBatchEndpoint.getComponent();
        connectionFactory = ObjectHelper.notNull(sjmsBatchComponent.getConnectionFactory(), "jmsBatchComponent.connectionFactory");
    }

    @Override
    public Endpoint getEndpoint() {
        return sjmsBatchEndpoint;
    }

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<CountDownLatch> consumersShutdownLatchRef = new AtomicReference<>();

    private Connection connection;

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start up a shared connection
        try {
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (JMSException ex) {
            LOG.error("Exception caught closing connection: {}", getStackTrace(ex));
            return;
        }

        if (LOG.isInfoEnabled()) {
            LOG.info("Starting " + consumerCount + " consumer(s) for " + destinationName + ":" + completionSize);
        }
        consumersShutdownLatchRef.set(new CountDownLatch(consumerCount));

        jmsConsumerExecutors = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newFixedThreadPool(this, "SjmsBatchConsumer", consumerCount);
        for (int i = 0; i < consumerCount; i++) {
            jmsConsumerExecutors.execute(new BatchConsumptionLoop());
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
                LOG.info("All consumers have shut down");
            }
        } else {
            LOG.info("Stop signalled while there are no consumers yet, so no need to wait for consumers");
        }

        try {
            LOG.debug("Shutting down JMS connection");
            connection.close();
        } catch (JMSException jex) {
            LOG.error("Exception caught closing connection: {}", getStackTrace(jex));
        }

        getEndpoint().getCamelContext().getExecutorServiceManager()
                .shutdown(jmsConsumerExecutors);
    }

    private String getStackTrace(Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private class BatchConsumptionLoop implements Runnable {
        @Override
        public void run() {
            try {
                // a batch corresponds to a single session that will be committed or rolled back by a background thread
                final Session session = connection.createSession(TRANSACTED, Session.CLIENT_ACKNOWLEDGE);
                try {
                    // destinationName only creates queues; there is no additional value to be gained
                    // by transactionally consuming topic messages in batches
                    Queue queue = session.createQueue(destinationName);
                    MessageConsumer consumer = session.createConsumer(queue);
                    try {
                        consumeBatchesOnLoop(session, consumer);
                    } finally {
                        try {
                            consumer.close();
                        } catch (JMSException ex2) {
                            log.error("Exception caught closing consumer: {}", ex2.getMessage());

                        }
                    }
                } finally {
                    try {
                        session.close();
                    } catch (JMSException ex1) {
                        log.error("Exception caught closing session: {}", ex1.getMessage());
                    }
                }
            } catch (JMSException ex) {
                // from loop
                LOG.error("Exception caught consuming from {}: {}", destinationName, getStackTrace(ex));
            } finally {
                // indicate that we have shut down
                CountDownLatch consumersShutdownLatch = consumersShutdownLatchRef.get();
                consumersShutdownLatch.countDown();
            }
        }

        private void consumeBatchesOnLoop(Session session, MessageConsumer consumer) throws JMSException {
            final boolean usingTimeout = completionTimeout > 0;

            batchConsumption:
            while (running.get()) {
                int messageCount = 0;

                // reset the clock counters
                long timeElapsed = 0;
                long startTime = 0;
                Exchange aggregatedExchange = null;

                batch:
                while ((completionSize <= 0) || (messageCount < completionSize)) {
                    // check periodically to see whether we should be shutting down
                    long waitTime = (usingTimeout && (timeElapsed > 0))
                            ? getReceiveWaitTime(timeElapsed)
                            : pollDuration;
                    Message message = consumer.receive(waitTime);

                    if (running.get()) { // no interruptions received
                        if (message == null) {
                            // timed out, no message received
                            LOG.trace("No message received");
                        } else {
                            if ((usingTimeout) && (messageCount == 0)) { // this is the first message
                                startTime = new Date().getTime(); // start counting down the period for this batch
                            }
                            messageCount++;
                            LOG.debug("Message received: {}", messageCount);
                            if ((message instanceof ObjectMessage)
                                    || (message instanceof TextMessage)) {
                                Exchange exchange = JmsMessageHelper.createExchange(message, getEndpoint());
                                aggregatedExchange = aggregationStrategy.aggregate(aggregatedExchange, exchange);
                                aggregatedExchange.setProperty(SjmsBatchEndpoint.PROPERTY_BATCH_SIZE, messageCount);
                            } else {
                                throw new IllegalArgumentException("Unexpected message type: "
                                        + message.getClass().toString());
                            }
                        }

                        if ((usingTimeout) && (startTime > 0)) {
                            // a batch has been started, check whether it should be timed out
                            long currentTime = new Date().getTime();
                            timeElapsed = currentTime - startTime;

                            if (timeElapsed > completionTimeout) {
                                // batch finished by timeout
                                break batch;
                            }
                        }

                    } else {
                        LOG.info("Shutdown signal received - rolling batch back");
                        session.rollback();
                        break batchConsumption;
                    }
                } // batch
                assert (aggregatedExchange != null);
                process(aggregatedExchange, session);
            }
        }

        /**
         * Determine the time that a call to {@link MessageConsumer#receive()} should wait given the time that has elapsed for this batch.
         * @param timeElapsed The time that has elapsed.
         * @return The shorter of the time remaining or poll duration.
         */
        private long getReceiveWaitTime(long timeElapsed) {
            long timeRemaining = getTimeRemaining(timeElapsed);

            // wait for the shorter of the time remaining or the poll duration
            if (timeRemaining <= 0) { // ensure that the thread doesn't wait indefinitely
                timeRemaining = 1;
            }
            final long waitTime = (timeRemaining > pollDuration) ? pollDuration : timeRemaining;

            LOG.debug("waiting for {}", waitTime);
            return waitTime;
        }

        private long getTimeRemaining(long timeElapsed) {
            long timeRemaining = completionTimeout - timeElapsed;
            if (LOG.isDebugEnabled() && (timeElapsed > 0)) {
                LOG.debug("Time remaining this batch: {}", timeRemaining);
            }
            return timeRemaining;
        }

        private void process(Exchange exchange, Session session) {
            assert (exchange != null);
            int id = batchCount.getAndIncrement();
            int batchSize = exchange.getProperty(SjmsBatchEndpoint.PROPERTY_BATCH_SIZE, Integer.class);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Processing batch:size={}:total={}", batchSize, messagesReceived.addAndGet(batchSize));
            }

            Synchronization committing = new SessionCompletion(session);
            exchange.addOnCompletion(committing);
            try {
                processor.process(exchange);
                LOG.debug("Completed processing[{}]:total={}", id, messagesProcessed.addAndGet(batchSize));
            } catch (Exception e) {
                LOG.error("Error processing exchange: {}", e.getMessage());
            }
        }

    }
}
