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
package org.apache.camel.component.kafka;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.component.kafka.consumer.support.PartitionAssignmentListener;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.util.IOHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor.serializeOffsetKey;

class KafkaFetchRecords implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(KafkaFetchRecords.class);

    private final KafkaConsumer kafkaConsumer;
    private org.apache.kafka.clients.consumer.Consumer consumer;
    private final String topicName;
    private final Pattern topicPattern;
    private final String threadId;
    private final Properties kafkaProps;
    private final Map<String, Long> lastProcessedOffset = new HashMap<>();
    private final PollExceptionStrategy pollExceptionStrategy;
    private final BridgeExceptionHandlerToErrorHandler bridge;
    private final ReentrantLock lock = new ReentrantLock();
    private final AtomicBoolean stopping = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<KafkaAsyncManualCommit> asyncCommits = new ConcurrentLinkedQueue<>();

    private boolean retry = true;
    private boolean reconnect; // must be false at init (this is the policy whether to reconnect)
    private boolean connected; // this is the state (connected or not)

    KafkaFetchRecords(KafkaConsumer kafkaConsumer, PollExceptionStrategy pollExceptionStrategy,
                      BridgeExceptionHandlerToErrorHandler bridge, String topicName, Pattern topicPattern, String id,
                      Properties kafkaProps) {
        this.kafkaConsumer = kafkaConsumer;
        this.pollExceptionStrategy = pollExceptionStrategy;
        this.bridge = bridge;
        this.topicName = topicName;
        this.topicPattern = topicPattern;
        this.threadId = topicName + "-" + "Thread " + id;
        this.kafkaProps = kafkaProps;
    }

    @Override
    public void run() {
        if (!isKafkaConsumerRunnable()) {
            return;
        }

        do {
            try {
                if (!isConnected()) {
                    createConsumer();

                    initializeConsumer();
                    setConnected(true);
                }
            } catch (Exception e) {
                setConnected(false);
                // ensure this is logged so users can see the problem
                LOG.warn("Error creating org.apache.kafka.clients.consumer.KafkaConsumer due {}", e.getMessage(), e);
                continue;
            }

            startPolling();
        } while ((isRetrying() || isReconnect()) && isKafkaConsumerRunnable());

        if (LOG.isInfoEnabled()) {
            LOG.info("Terminating KafkaConsumer thread: {} receiving from {}", threadId, getPrintableTopic());
        }

        safeUnsubscribe();
        IOHelper.close(consumer);
    }

    protected void createConsumer() {
        // create consumer
        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            // Kafka uses reflection for loading authentication settings, use its classloader
            Thread.currentThread()
                    .setContextClassLoader(org.apache.kafka.clients.consumer.KafkaConsumer.class.getClassLoader());

            // The Kafka consumer should be null at the first try. For every other reconnection event, it will not
            long delay = kafkaConsumer.getEndpoint().getConfiguration().getPollTimeoutMs();
            final String prefix = this.consumer == null ? "Connecting" : "Reconnecting";
            LOG.info("{} Kafka consumer thread ID {} with poll timeout of {} ms", prefix, threadId, delay);

            // this may throw an exception if something is wrong with kafka consumer
            this.consumer = kafkaConsumer.getEndpoint().getKafkaClientFactory().getConsumer(kafkaProps);
        } finally {
            Thread.currentThread().setContextClassLoader(threadClassLoader);
        }
    }

    private void initializeConsumer() {
        subscribe();

        // set reconnect to false as the connection and resume is done at this point
        setConnected(false);

        // set retry to true to continue polling
        setRetry(true);
    }

    private void subscribe() {
        PartitionAssignmentListener listener = new PartitionAssignmentListener(
                threadId, kafkaConsumer.getEndpoint().getConfiguration(), consumer, lastProcessedOffset,
                this::isRunnable);

        if (LOG.isInfoEnabled()) {
            LOG.info("Subscribing {} to {}", threadId, getPrintableTopic());
        }

        if (topicPattern != null) {
            consumer.subscribe(topicPattern, listener);
        } else {
            consumer.subscribe(Arrays.asList(topicName.split(",")), listener);
        }
    }

    protected void startPolling() {
        KafkaRecordProcessor.ProcessResult lastResult = null;
        try {
            /*
             * We lock the processing of the record to avoid raising a WakeUpException as a result to a call
             * to stop() or shutdown().
             */
            lock.lock();

            long pollTimeoutMs = kafkaConsumer.getEndpoint().getConfiguration().getPollTimeoutMs();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Polling {} from {} with timeout: {}", threadId, getPrintableTopic(), pollTimeoutMs);
            }

            KafkaRecordProcessor kafkaRecordProcessor = buildKafkaRecordProcessor();

            Duration pollDuration = Duration.ofMillis(pollTimeoutMs);
            while (isKafkaConsumerRunnable() && isRetrying() && isConnected()) {
                ConsumerRecords<Object, Object> allRecords = consumer.poll(pollDuration);

                processAsyncCommits();

                lastResult = processPolledRecords(allRecords, kafkaRecordProcessor, lastResult);
            }

            if (!isConnected()) {
                LOG.debug("Not reconnecting, check whether to auto-commit or not ...");
                commit();
            }

            safeUnsubscribe();
        } catch (InterruptException e) {
            kafkaConsumer.getExceptionHandler().handleException("Interrupted while consuming " + threadId + " from kafka topic",
                    e);
            commit();

            LOG.info("Unsubscribing {} from {}", threadId, getPrintableTopic());
            safeUnsubscribe();
            Thread.currentThread().interrupt();
        } catch (WakeupException e) {
            // This is normal: it raises this exception when calling the wakeUp (which happens when we stop)

            if (LOG.isTraceEnabled()) {
                LOG.trace("The kafka consumer was woken up while polling on thread {} for {}", threadId, getPrintableTopic());
            }

            safeUnsubscribe();
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.warn("Exception {} caught while polling {} from kafka {} at offset {}: {}",
                        e.getClass().getName(), threadId, getPrintableTopic(), lastProcessedOffset, e.getMessage(), e);
            } else {
                LOG.warn("Exception {} caught while polling {} from kafka {} at offset {}: {}",
                        e.getClass().getName(), threadId, getPrintableTopic(), lastProcessedOffset, e.getMessage());
            }

            handleAccordingToStrategy(lastResult.getPartitionLastOffset(), e);
        } finally {
            lock.unlock();

            // only close if not retry
            if (!isRetrying()) {
                LOG.debug("Closing consumer {}", threadId);
                safeUnsubscribe();
                IOHelper.close(consumer);
            }
        }
    }

    private void processAsyncCommits() {
        while (!asyncCommits.isEmpty()) {
            asyncCommits.poll().processAsyncCommit();
        }
    }

    private void handleAccordingToStrategy(long partitionLastOffset, Exception e) {
        PollOnError onError = pollExceptionStrategy.handleException(e);
        if (PollOnError.RETRY == onError) {
            handlePollRetry();
        } else if (PollOnError.RECONNECT == onError) {
            handlePollReconnect();
        } else if (PollOnError.ERROR_HANDLER == onError) {
            handlePollErrorHandler(partitionLastOffset, e);
        } else if (PollOnError.DISCARD == onError) {
            handlePollDiscard(partitionLastOffset);
        } else if (PollOnError.STOP == onError) {
            handlePollStop();
        }
    }

    private void safeUnsubscribe() {
        final String printableTopic = getPrintableTopic();

        try {
            consumer.unsubscribe();
        } catch (IllegalStateException e) {
            LOG.warn("The consumer is likely already closed. Skipping the unsubscription from {}", printableTopic);
        } catch (Exception e) {
            kafkaConsumer.getExceptionHandler().handleException(
                    "Error unsubscribing thread " + threadId + " from kafka " + printableTopic, e);
        }
    }

    /*
     * This is only used for presenting log messages that take into consideration that it might be subscribed to a topic
     * or a topic pattern.
     */
    private String getPrintableTopic() {
        if (topicPattern != null) {
            return "topic pattern " + topicPattern;
        } else {
            return "topic " + topicName;
        }
    }

    private void commit() {
        processAsyncCommits();
        if (isAutoCommitEnabled()) {
            if ("async".equals(kafkaConsumer.getEndpoint().getConfiguration().getAutoCommitOnStop())) {
                LOG.info("Auto commitAsync on stop {} from {}", threadId, getPrintableTopic());
                consumer.commitAsync();
            } else if ("sync".equals(kafkaConsumer.getEndpoint().getConfiguration().getAutoCommitOnStop())) {
                LOG.info("Auto commitSync on stop {} from {}", threadId, getPrintableTopic());
                consumer.commitSync();
            } else if ("none".equals(kafkaConsumer.getEndpoint().getConfiguration().getAutoCommitOnStop())) {
                LOG.info("Auto commit on stop {} from {} is disabled (none)", threadId, getPrintableTopic());
            }
        }
    }

    private void handlePollStop() {
        // stop and terminate consumer
        LOG.warn("Requesting the consumer to stop based on polling exception strategy");

        setRetry(false);
        setConnected(false);
    }

    private void handlePollDiscard(long partitionLastOffset) {
        LOG.warn("Requesting the consumer to discard the message and continue to the next based on polling exception strategy");

        // skip this poison message and seek to next message
        seekToNextOffset(partitionLastOffset);
    }

    private void handlePollErrorHandler(long partitionLastOffset, Exception e) {
        LOG.warn("Deferring processing to the exception handler based on polling exception strategy");

        // use bridge error handler to route with exception
        bridge.handleException(e);
        // skip this poison message and seek to next message
        seekToNextOffset(partitionLastOffset);
    }

    private void handlePollReconnect() {
        LOG.warn("Requesting the consumer to re-connect on the next run based on polling exception strategy");

        // re-connect so the consumer can try the same message again
        setReconnect(true);
        setConnected(false);

        // to close the current consumer
        setRetry(false);
    }

    private void handlePollRetry() {
        LOG.warn("Requesting the consumer to retry polling the same message based on polling exception strategy");

        // consumer retry the same message again
        setRetry(true);
    }

    private boolean isKafkaConsumerRunnable() {
        return kafkaConsumer.isRunAllowed() && !kafkaConsumer.isStoppingOrStopped()
                && !kafkaConsumer.isSuspendingOrSuspended();
    }

    private boolean isRunnable() {
        return kafkaConsumer.getEndpoint().getCamelContext().isStopping() && !kafkaConsumer.isRunAllowed();
    }

    private KafkaRecordProcessor.ProcessResult processPolledRecords(
            ConsumerRecords<Object, Object> allRecords, KafkaRecordProcessor kafkaRecordProcessor,
            KafkaRecordProcessor.ProcessResult resultFromPreviousPoll) {
        logRecords(allRecords);

        Set<TopicPartition> partitions = allRecords.partitions();
        Iterator<TopicPartition> partitionIterator = partitions.iterator();

        KafkaRecordProcessor.ProcessResult lastResult
                = resultFromPreviousPoll == null ? KafkaRecordProcessor.ProcessResult.newUnprocessed() : resultFromPreviousPoll;

        while (partitionIterator.hasNext() && !isStopping()) {
            TopicPartition partition = partitionIterator.next();

            List<ConsumerRecord<Object, Object>> partitionRecords = allRecords.records(partition);
            Iterator<ConsumerRecord<Object, Object>> recordIterator = partitionRecords.iterator();

            logRecordsInPartition(partitionRecords, partition);

            while (!lastResult.isBreakOnErrorHit() && recordIterator.hasNext() && !isStopping()) {
                ConsumerRecord<Object, Object> record = recordIterator.next();

                lastResult = processRecord(partition, partitionIterator.hasNext(), recordIterator.hasNext(), lastResult,
                        kafkaRecordProcessor, record);
            }

            if (!lastResult.isBreakOnErrorHit()) {
                LOG.debug("Committing offset on successful execution");
                // all records processed from partition so commit them
                kafkaRecordProcessor.commitOffset(partition, lastResult.getPartitionLastOffset(), false, false);
            }
        }

        if (lastResult.isBreakOnErrorHit()) {
            LOG.debug("We hit an error ... setting flags to force reconnect");
            // force re-connect
            setReconnect(true);
            setConnected(false);
            setRetry(false); // to close the current consumer
        }

        return lastResult;
    }

    private void logRecordsInPartition(List<ConsumerRecord<Object, Object>> partitionRecords, TopicPartition partition) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Records count {} received for partition {}", partitionRecords.size(),
                    partition);
        }
    }

    private void logRecords(ConsumerRecords<Object, Object> allRecords) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Last poll on thread {} resulted on {} records to process", threadId, allRecords.count());
        }
    }

    private KafkaRecordProcessor.ProcessResult processRecord(
            TopicPartition partition,
            boolean partitionHasNext,
            boolean recordHasNext,
            final KafkaRecordProcessor.ProcessResult lastResult,
            KafkaRecordProcessor kafkaRecordProcessor,
            ConsumerRecord<Object, Object> record) {

        logRecord(record);

        Exchange exchange = kafkaConsumer.createExchange(false);

        KafkaRecordProcessor.ProcessResult currentResult
                = kafkaRecordProcessor.processExchange(exchange, partition, partitionHasNext,
                        recordHasNext, record, lastResult, kafkaConsumer.getExceptionHandler());

        if (!currentResult.isBreakOnErrorHit()) {
            lastProcessedOffset.put(serializeOffsetKey(partition), currentResult.getPartitionLastOffset());
        }

        // success so release the exchange
        kafkaConsumer.releaseExchange(exchange, false);

        return currentResult;
    }

    private void logRecord(ConsumerRecord<Object, Object> record) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Partition = {}, offset = {}, key = {}, value = {}", record.partition(),
                    record.offset(), record.key(), record.value());
        }
    }

    private KafkaRecordProcessor buildKafkaRecordProcessor() {
        return new KafkaRecordProcessor(
                isAutoCommitEnabled(),
                kafkaConsumer.getEndpoint().getConfiguration(),
                kafkaConsumer.getProcessor(),
                consumer,
                kafkaConsumer.getEndpoint().getKafkaManualCommitFactory(), threadId, asyncCommits);
    }

    private void seekToNextOffset(long partitionLastOffset) {
        boolean logged = false;
        Set<TopicPartition> tps = consumer.assignment();
        if (tps != null && partitionLastOffset != -1) {
            long next = partitionLastOffset + 1;

            if (LOG.isInfoEnabled()) {
                LOG.info("Consumer seeking to next offset {} to continue polling next message from {}", next,
                        getPrintableTopic());
            }

            for (TopicPartition tp : tps) {
                consumer.seek(tp, next);
            }
        } else if (tps != null) {
            for (TopicPartition tp : tps) {
                long next = consumer.position(tp) + 1;
                if (!logged) {
                    LOG.info("Consumer seeking to next offset {} to continue polling next message from {}", next,
                            getPrintableTopic());
                    logged = true;
                }
                consumer.seek(tp, next);
            }
        }
    }

    private boolean isRetrying() {
        return retry;
    }

    private void setRetry(boolean value) {
        retry = value;
    }

    private boolean isReconnect() {
        return reconnect;
    }

    private void setReconnect(boolean value) {
        reconnect = value;
    }

    private void setStopping(boolean value) {
        stopping.set(value);
    }

    private boolean isStopping() {
        return stopping.get();
    }

    /*
     * This wraps a safe stop procedure that should help ensure a clean termination procedure for consumer code.
     * This means that it should wait for the last process call to finish cleanly, including the commit of the
     * record being processed at the current moment.
     *
     * Note: keep in mind that the KafkaConsumer is not thread-safe, so no other call to the consumer instance
     * should be made here besides the wakeUp.
     */
    private void safeStop() {
        setStopping(true);
        long timeout = kafkaConsumer.getEndpoint().getConfiguration().getShutdownTimeout();
        try {
            /*
             Try to wait for the processing to finish before giving up and waking up the Kafka consumer regardless
             of whether the processing have finished or not.
             */
            LOG.info("Waiting up to {} milliseconds for the processing to finish", timeout);
            if (!lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
                LOG.warn("The processing of the current record did not finish within {} seconds", timeout);
            }

            // As advised in the KAFKA-1894 ticket, calling this wakeup method breaks the infinite loop
            consumer.wakeup();
        } catch (InterruptedException e) {
            consumer.wakeup();
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    void stop() {
        safeStop();
    }

    void shutdown() {
        safeStop();
    }

    private boolean isAutoCommitEnabled() {
        return kafkaConsumer.getEndpoint().getConfiguration().getAutoCommitEnable() != null
                && kafkaConsumer.getEndpoint().getConfiguration().getAutoCommitEnable();
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }
}
