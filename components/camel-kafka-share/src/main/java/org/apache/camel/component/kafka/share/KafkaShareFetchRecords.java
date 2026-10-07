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
package org.apache.camel.component.kafka.share;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.kafka.KafkaClientConfiguration;
import org.apache.camel.component.kafka.KafkaConsumerFatalException;
import org.apache.camel.component.kafka.KafkaReconnectSupport;
import org.apache.camel.component.kafka.PollOnError;
import org.apache.camel.component.kafka.TaskHealthState;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessor;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.TaskRunFailureException;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.AcknowledgeType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.ShareConsumer;
import org.apache.kafka.common.KafkaException;
import org.apache.kafka.common.TopicIdPartition;
import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.AuthorizationException;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls one Kafka share consumer and routes its records. Every record of a poll is acknowledged on this thread, before
 * the next poll, as the share consumer uses explicit acknowledgement.
 */
class KafkaShareFetchRecords implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaShareFetchRecords.class);

    private final KafkaShareConsumer kafkaShareConsumer;
    private final KafkaShareConfiguration configuration;
    private final BridgeExceptionHandlerToErrorHandler bridge;
    private final List<String> topics;
    private final String threadId;
    private final Properties kafkaProps;
    private final RecordProcessor recordProcessor = new RecordProcessor();
    // held while processing the records of a poll, so that stop() waits for the records in flight
    private final ReentrantLock lock = new ReentrantLock();

    private ShareConsumer<Object, Object> consumer;
    private ScheduledExecutorService reconnectPool;

    // read from other threads, such as the health check
    private volatile String clientId;
    private volatile boolean connected;
    private volatile boolean terminated;
    private volatile Exception lastError;
    private volatile long currentBackoffInterval;

    KafkaShareFetchRecords(KafkaShareConsumer kafkaShareConsumer, BridgeExceptionHandlerToErrorHandler bridge, String id,
                           Properties kafkaProps) {
        this.kafkaShareConsumer = kafkaShareConsumer;
        this.configuration = kafkaShareConsumer.getEndpoint().getConfiguration();
        this.bridge = bridge;
        this.topics = Arrays.stream(configuration.getTopic().split(",")).map(String::trim).filter(t -> !t.isEmpty())
                .toList();
        this.threadId = configuration.getTopic() + "-Thread " + id;
        this.kafkaProps = kafkaProps;
    }

    @Override
    public void run() {
        try {
            while (isRunAllowed() && !terminated) {
                if (!connected && !connect()) {
                    // gave up creating the share consumer
                    terminated = true;
                    break;
                }
                lastError = null;
                pollAndProcess();
            }
            LOG.info("Terminating Kafka share consumer thread {} receiving from {}", threadId, topics);
        } finally {
            closeConsumer();
            if (reconnectPool != null) {
                kafkaShareConsumer.getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(reconnectPool);
                reconnectPool = null;
            }
        }
    }

    /**
     * Creates and subscribes the share consumer, retrying with the create consumer backoff of the component.
     *
     * @return false if it gave up
     */
    private boolean connect() {
        currentBackoffInterval = kafkaShareConsumer.getEndpoint().getComponent().getCreateConsumerBackoffInterval();
        int maxAttempts = kafkaShareConsumer.getEndpoint().getComponent().getCreateConsumerBackoffMaxAttempts();
        if (reconnectPool == null) {
            reconnectPool = kafkaShareConsumer.getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "KafkaShareReconnect");
        }
        BackgroundTask task = KafkaReconnectSupport.createReconnectTask(
                reconnectPool, "KafkaShareReconnect-" + configuration.getTopic(), maxAttempts, currentBackoffInterval);
        boolean success = task.run(kafkaShareConsumer.getEndpoint().getCamelContext(), this::createConsumer);
        if (!success) {
            String msg = "Gave up creating/subscribing the Kafka share consumer " + threadId + " to " + topics
                         + " after " + maxAttempts + " attempts (elapsed: "
                         + TimeUtils.printDuration(task.elapsed(), true) + ").";
            LOG.warn(msg);
            lastError = new KafkaConsumerFatalException(msg, lastError);
        }
        return success;
    }

    private boolean createConsumer() {
        if (!isRunAllowed()) {
            // stop retrying when the consumer is stopping
            return true;
        }
        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            closeConsumer();
            // Kafka uses reflection for loading authentication settings, use its classloader
            Thread.currentThread().setContextClassLoader(ShareConsumer.class.getClassLoader());

            LOG.info("{} Kafka share consumer thread ID {} for share group {}",
                    consumer == null ? "Connecting" : "Reconnecting",
                    threadId, configuration.getGroupId());
            String krbLocation = configuration.getKerberosConfigLocation();
            if (krbLocation != null) {
                System.setProperty("java.security.krb5.conf", krbLocation);
            }

            ShareConsumer<Object, Object> created
                    = kafkaShareConsumer.getEndpoint().getKafkaShareClientFactory().getShareConsumer(kafkaProps);
            consumer = created;
            if (configuration.getCommitMode() == KafkaShareCommitMode.ASYNC) {
                created.setAcknowledgementCommitCallback(this::onAcknowledgementCommit);
            }
            created.subscribe(topics);
            if (clientId == null) {
                clientId = kafkaProps.getProperty(CommonClientConfigs.CLIENT_ID_CONFIG, "");
            }
            connected = true;
            return true;
        } catch (Exception e) {
            connected = false;
            LOG.warn("Error creating/subscribing the Kafka share consumer due to: {}", e.getMessage(), e);
            lastError = e;
            if (kafkaShareConsumer.getEndpoint().isBridgeErrorHandler()) {
                kafkaShareConsumer.getExceptionHandler().handleException(e);
            }
            throw new TaskRunFailureException(e);
        } finally {
            Thread.currentThread().setContextClassLoader(threadClassLoader);
        }
    }

    private void pollAndProcess() {
        try {
            // the lock is not held while polling, so that stop() can wake up a poll that waits for records
            ConsumerRecords<Object, Object> records = consumer.poll(Duration.ofMillis(configuration.getPollTimeoutMs()));
            if (!records.isEmpty()) {
                processRecords(records);
            }
        } catch (WakeupException e) {
            // the consumer is stopping
            LOG.trace("The Kafka share consumer was woken up while polling on thread {}", threadId);
        } catch (InterruptException e) {
            kafkaShareConsumer.getExceptionHandler().handleException(
                    "Thread " + threadId + " interrupted while consuming from kafka topic", e);
            terminated = true;
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            handlePollException(e);
        }
    }

    private void processRecords(ConsumerRecords<Object, Object> records) {
        ArrayDeque<ConsumerRecord<Object, Object>> pending = new ArrayDeque<>();
        records.forEach(pending::add);
        lock.lock();
        try {
            while (!pending.isEmpty()) {
                ConsumerRecord<Object, Object> record = pending.peekFirst();
                // when stopping, the records that are not processed are released, so other consumers get them
                AcknowledgeType type = isRunAllowed() ? process(record) : AcknowledgeType.RELEASE;
                consumer.acknowledge(record, type);
                pending.removeFirst();
            }
            commit();
        } finally {
            try {
                // explicit acknowledgement: every record of the poll must be acknowledged before the next poll
                releasePending(pending);
            } finally {
                lock.unlock();
            }
        }
    }

    private AcknowledgeType process(ConsumerRecord<Object, Object> record) {
        Exchange exchange = kafkaShareConsumer.createExchange(false);
        try {
            Message message = exchange.getMessage();
            recordProcessor.setupExchangeMessage(message, record);
            recordProcessor.propagateHeaders(configuration, record, exchange);
            record.deliveryCount().ifPresent(count -> message.setHeader(KafkaShareConstants.DELIVERY_COUNT, count));

            try {
                kafkaShareConsumer.getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            AcknowledgeType type = acknowledgeType(exchange);
            if (exchange.getException() != null) {
                kafkaShareConsumer.getExceptionHandler().handleException(
                        "Error processing the record from topic " + record.topic() + " partition " + record.partition()
                                                                         + " offset " + record.offset()
                                                                         + " (acknowledged as " + type + ")",
                        exchange, exchange.getException());
            }
            return type;
        } finally {
            kafkaShareConsumer.releaseExchange(exchange, false);
        }
    }

    /**
     * The acknowledgement of a record: the one set by the route, otherwise ACCEPT when the exchange completed, and
     * onFailure when the exchange failed or was rolled back.
     */
    AcknowledgeType acknowledgeType(Exchange exchange) {
        Object value = exchange.getMessage().getHeader(KafkaShareConstants.ACKNOWLEDGE);
        if (value != null) {
            KafkaShareAcknowledgeType type = toAcknowledgeType(value);
            if (type != null) {
                return type.getAcknowledgeType();
            }
            LOG.warn("Ignoring the {} header with the invalid value {}, the valid values are ACCEPT, RELEASE and REJECT",
                    KafkaShareConstants.ACKNOWLEDGE, value);
        }
        if (exchange.isFailed() || exchange.isRollbackOnly() || exchange.isRollbackOnlyLast()) {
            return configuration.getOnFailure().getAcknowledgeType();
        }
        return AcknowledgeType.ACCEPT;
    }

    private static KafkaShareAcknowledgeType toAcknowledgeType(Object value) {
        if (value instanceof KafkaShareAcknowledgeType type) {
            return type;
        }
        String name = value instanceof AcknowledgeType type ? type.name() : value.toString().trim();
        try {
            return KafkaShareAcknowledgeType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void commit() {
        if (configuration.getCommitMode() == KafkaShareCommitMode.ASYNC) {
            consumer.commitAsync();
            return;
        }
        Map<TopicIdPartition, Optional<KafkaException>> result
                = consumer.commitSync(Duration.ofMillis(configuration.getCommitTimeoutMs()));
        result.forEach((partition, error) -> error.ifPresent(e -> kafkaShareConsumer.getExceptionHandler()
                .handleException("Error committing the acknowledgements of " + partition
                                 + ", the records will be delivered again",
                        e)));
    }

    private void onAcknowledgementCommit(Map<TopicIdPartition, Set<Long>> offsets, Exception exception) {
        if (exception != null) {
            LOG.warn("Error committing the acknowledgements of {} on thread {}, the records will be delivered again: {}",
                    offsets.keySet(), threadId, exception.getMessage());
        }
    }

    private void releasePending(ArrayDeque<ConsumerRecord<Object, Object>> pending) {
        for (ConsumerRecord<Object, Object> record : pending) {
            try {
                consumer.acknowledge(record, AcknowledgeType.RELEASE);
            } catch (Exception e) {
                LOG.debug("Cannot release the record from topic {} partition {} offset {}: {}", record.topic(),
                        record.partition(), record.offset(), e.getMessage());
            }
        }
        pending.clear();
    }

    private void handlePollException(Exception e) {
        if (LOG.isDebugEnabled()) {
            LOG.warn("Exception {} caught by thread {} while polling {} from kafka: {}", e.getClass().getName(), threadId,
                    topics, e.getMessage(), e);
        } else {
            LOG.warn("Exception {} caught by thread {} while polling {} from kafka: {}", e.getClass().getName(), threadId,
                    topics, e.getMessage());
        }
        lastError = e;

        if (e instanceof AuthenticationException || e instanceof AuthorizationException) {
            LOG.warn("Stopping the Kafka share consumer thread {} as it cannot authenticate or is not authorized", threadId);
            bridge.handleException(e);
            terminated = true;
            return;
        }

        PollOnError onError = configuration.getPollOnError();
        switch (onError) {
            case ERROR_HANDLER:
                bridge.handleException(e);
                break;
            case RECONNECT:
                LOG.warn("Reconnecting the Kafka share consumer thread {} due to the poll exception", threadId);
                connected = false;
                break;
            case STOP:
                LOG.warn("Stopping the Kafka share consumer thread {} due to the poll exception", threadId);
                terminated = true;
                break;
            default:
                // DISCARD and RETRY: poll again
                break;
        }
    }

    private void closeConsumer() {
        if (consumer == null) {
            return;
        }
        try {
            LOG.debug("Closing the Kafka share consumer {}", threadId);
            consumer.close(Duration.ofMillis(configuration.getShutdownTimeout()));
        } catch (Exception e) {
            LOG.warn("Error closing the Kafka share consumer {}: {} (this error will be ignored)", threadId, e.getMessage(),
                    e);
        }
        connected = false;
    }

    private boolean isRunAllowed() {
        return kafkaShareConsumer.isRunAllowed() && !kafkaShareConsumer.isStoppingOrStopped();
    }

    /**
     * Waits for the records in flight to be processed, up to the shutdown timeout, and wakes up the share consumer.
     */
    void stop() {
        ShareConsumer<Object, Object> current = consumer;
        if (current == null) {
            return;
        }
        long timeout = configuration.getShutdownTimeout();
        try {
            LOG.info("Waiting up to {} milliseconds for the processing to finish", timeout);
            if (!lock.tryLock(timeout, TimeUnit.MILLISECONDS)) {
                LOG.warn("The processing of the current records did not finish within {} milliseconds", timeout);
            }
            current.wakeup();
        } catch (InterruptedException e) {
            current.wakeup();
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    TaskHealthState healthState() {
        boolean recoverable = !terminated && isRunAllowed();
        return new TaskHealthState(
                connected && !terminated, terminated, recoverable, lastError, clientId, currentBackoffInterval, kafkaProps);
    }

    String getThreadId() {
        return threadId;
    }

    boolean isConnected() {
        return connected;
    }

    String getClientId() {
        return ObjectHelper.isEmpty(clientId) ? null : clientId;
    }

    /**
     * Gives access to the record to exchange mapping shared with the kafka component.
     */
    private static final class RecordProcessor extends KafkaRecordProcessor {

        @Override
        protected void setupExchangeMessage(Message message, ConsumerRecord<Object, Object> consumerRecord) {
            super.setupExchangeMessage(message, consumerRecord);
        }

        @Override
        protected void propagateHeaders(
                KafkaClientConfiguration configuration,
                ConsumerRecord<Object, Object> consumerRecord, Exchange exchange) {
            super.propagateHeaders(configuration, consumerRecord, exchange);
        }
    }
}
