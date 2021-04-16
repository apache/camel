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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.kafka.serde.KafkaHeaderDeserializer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaConsumer.class);

    protected ExecutorService executor;
    private final KafkaEndpoint endpoint;
    private final Processor processor;
    private final Long pollTimeoutMs;
    // This list helps working around the infinite loop of KAFKA-1894
    private final List<KafkaFetchRecords> tasks = new ArrayList<>();
    private volatile boolean stopOffsetRepo;
    private final BridgeExceptionHandlerToErrorHandler bridge = new BridgeExceptionHandlerToErrorHandler(this);
    private PollExceptionStrategy pollExceptionStrategy;

    public KafkaConsumer(KafkaEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.processor = processor;
        this.pollTimeoutMs = endpoint.getConfiguration().getPollTimeoutMs();
    }

    @Override
    protected void doBuild() throws Exception {
        super.doBuild();
        if (endpoint.getComponent().getPollExceptionStrategy() != null) {
            pollExceptionStrategy = endpoint.getComponent().getPollExceptionStrategy();
        } else {
            pollExceptionStrategy = new DefaultPollExceptionStrategy(endpoint.getConfiguration().getPollOnError());
        }
    }

    @Override
    public KafkaEndpoint getEndpoint() {
        return (KafkaEndpoint) super.getEndpoint();
    }

    Properties getProps() {
        Properties props = endpoint.getConfiguration().createConsumerProperties();
        endpoint.updateClassProperties(props);

        String brokers = endpoint.getKafkaClientFactory().getBrokers(endpoint.getConfiguration());
        if (brokers != null) {
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, brokers);
        }

        if (endpoint.getConfiguration().getGroupId() != null) {
            String groupId = endpoint.getConfiguration().getGroupId();
            props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
            LOG.debug("Kafka consumer groupId is {}", groupId);
        } else {
            String randomGroupId = UUID.randomUUID().toString();
            props.put(ConsumerConfig.GROUP_ID_CONFIG, randomGroupId);
            LOG.debug("Kafka consumer groupId is {} (generated)", randomGroupId);
        }
        return props;
    }

    @Override
    protected void doStart() throws Exception {
        LOG.info("Starting Kafka consumer on topic: {} with breakOnFirstError: {}", endpoint.getConfiguration().getTopic(),
                endpoint.getConfiguration().isBreakOnFirstError());
        super.doStart();

        // is the offset repository already started?
        StateRepository<String, String> repo = endpoint.getConfiguration().getOffsetRepository();
        if (repo instanceof ServiceSupport) {
            boolean started = ((ServiceSupport) repo).isStarted();
            // if not already started then we would do that and also stop it
            if (!started) {
                stopOffsetRepo = true;
                LOG.debug("Starting OffsetRepository: {}", repo);
                ServiceHelper.startService(endpoint.getConfiguration().getOffsetRepository());
            }
        }

        executor = endpoint.createExecutor();

        String topic = endpoint.getConfiguration().getTopic();
        Pattern pattern = null;
        if (endpoint.getConfiguration().isTopicIsPattern()) {
            pattern = Pattern.compile(topic);
        }

        for (int i = 0; i < endpoint.getConfiguration().getConsumersCount(); i++) {
            KafkaFetchRecords task = new KafkaFetchRecords(topic, pattern, i + "", getProps());
            // pre-initialize task during startup so if there is any error we
            // have it thrown asap
            task.preInit();
            executor.submit(task);
            tasks.add(task);
        }
    }

    @Override
    protected void doStop() throws Exception {
        LOG.info("Stopping Kafka consumer on topic: {}", endpoint.getConfiguration().getTopic());

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                // signal kafka consumer to stop
                for (KafkaFetchRecords task : tasks) {
                    task.stop();
                }
                int timeout = getEndpoint().getConfiguration().getShutdownTimeout();
                LOG.debug("Shutting down Kafka consumer worker threads with timeout {} millis", timeout);
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executor, timeout);
            } else {
                executor.shutdownNow();
            }
            if (!executor.isTerminated()) {
                tasks.forEach(KafkaFetchRecords::shutdown);
                executor.shutdownNow();
            }
        }
        tasks.clear();
        executor = null;

        if (stopOffsetRepo) {
            StateRepository<String, String> repo = endpoint.getConfiguration().getOffsetRepository();
            LOG.debug("Stopping OffsetRepository: {}", repo);
            ServiceHelper.stopAndShutdownService(repo);
        }

        super.doStop();
    }

    class KafkaFetchRecords implements Runnable, ConsumerRebalanceListener {

        private org.apache.kafka.clients.consumer.KafkaConsumer consumer;
        private final String topicName;
        private final Pattern topicPattern;
        private final String threadId;
        private final Properties kafkaProps;
        private final Map<String, Long> lastProcessedOffset = new ConcurrentHashMap<>();

        KafkaFetchRecords(String topicName, Pattern topicPattern, String id, Properties kafkaProps) {
            this.topicName = topicName;
            this.topicPattern = topicPattern;
            this.threadId = topicName + "-" + "Thread " + id;
            this.kafkaProps = kafkaProps;
        }

        @Override
        public void run() {
            boolean first = true;
            final AtomicBoolean reTry = new AtomicBoolean(true);
            final AtomicBoolean reConnect = new AtomicBoolean(true);

            while (reTry.get() || reConnect.get()) {
                try {
                    if (first || reConnect.get()) {
                        // re-initialize on re-connect so we have a fresh consumer
                        doInit();
                    }
                } catch (Exception e) {
                    // ensure this is logged so users can see the problem
                    LOG.warn("Error creating org.apache.kafka.clients.consumer.KafkaConsumer due {}", e.getMessage(), e);
                }

                if (!first) {
                    // skip one poll timeout before trying again
                    long delay = endpoint.getConfiguration().getPollTimeoutMs();
                    String prefix = reConnect.get() ? "Reconnecting" : "Retrying";
                    LOG.info("{} {} to topic {} after {} ms", prefix, threadId, topicName, delay);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException e) {
                        boolean stopping = endpoint.getCamelContext().isStopping();
                        if (stopping) {
                            LOG.info(
                                    "CamelContext is stopping so terminating KafkaConsumer thread: {} receiving from topic: {}",
                                    threadId, topicName);
                            return;
                        }
                    }
                }

                first = false;

                // doRun keeps running until we either shutdown or is told to re-connect
                doRun(reTry, reConnect);
            }

            LOG.info("Terminating KafkaConsumer thread: {} receiving from topic: {}", threadId, topicName);
        }

        void preInit() {
            doInit();
        }

        protected void doInit() {
            // create consumer
            ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                // Kafka uses reflection for loading authentication settings,
                // use its classloader
                Thread.currentThread()
                        .setContextClassLoader(org.apache.kafka.clients.consumer.KafkaConsumer.class.getClassLoader());
                // this may throw an exception if something is wrong with kafka
                // consumer
                this.consumer = endpoint.getKafkaClientFactory().getConsumer(kafkaProps);
            } finally {
                Thread.currentThread().setContextClassLoader(threadClassLoader);
            }
        }

        @SuppressWarnings("unchecked")
        protected void doRun(AtomicBoolean retry, AtomicBoolean reconnect) {
            if (reconnect.get()) {
                // on first run or reconnecting
                doReconnectRun();
                // set reconnect to false as its done now
                reconnect.set(false);
            }
            // polling
            doPollRun(retry, reconnect);
        }

        protected void doReconnectRun() {
            if (topicPattern != null) {
                LOG.info("Subscribing {} to topic pattern {}", threadId, topicName);
                consumer.subscribe(topicPattern, this);
            } else {
                LOG.info("Subscribing {} to topic {}", threadId, topicName);
                consumer.subscribe(Arrays.asList(topicName.split(",")), this);
            }

            StateRepository<String, String> offsetRepository = endpoint.getConfiguration().getOffsetRepository();
            if (offsetRepository != null) {
                // This poll to ensures we have an assigned partition
                // otherwise seek won't work
                ConsumerRecords poll = consumer.poll(100);

                for (TopicPartition topicPartition : (Set<TopicPartition>) consumer.assignment()) {
                    String offsetState = offsetRepository.getState(serializeOffsetKey(topicPartition));
                    if (offsetState != null && !offsetState.isEmpty()) {
                        // The state contains the last read offset so you
                        // need to seek from the next one
                        long offset = deserializeOffsetValue(offsetState) + 1;
                        LOG.debug("Resuming partition {} from offset {} from state", topicPartition.partition(), offset);
                        consumer.seek(topicPartition, offset);
                    } else {
                        // If the init poll has returned some data of a
                        // currently unknown topic/partition in the state
                        // then resume from their offset in order to avoid
                        // losing data
                        List<ConsumerRecord<Object, Object>> partitionRecords = poll.records(topicPartition);
                        if (!partitionRecords.isEmpty()) {
                            long offset = partitionRecords.get(0).offset();
                            LOG.debug("Resuming partition {} from offset {}", topicPartition.partition(), offset);
                            consumer.seek(topicPartition, offset);
                        }
                    }
                }
            } else if (endpoint.getConfiguration().getSeekTo() != null) {
                if (endpoint.getConfiguration().getSeekTo().equals("beginning")) {
                    LOG.debug("{} is seeking to the beginning on topic {}", threadId, topicName);
                    // This poll to ensures we have an assigned partition
                    // otherwise seek won't work
                    consumer.poll(Duration.ofMillis(100));
                    consumer.seekToBeginning(consumer.assignment());
                } else if (endpoint.getConfiguration().getSeekTo().equals("end")) {
                    LOG.debug("{} is seeking to the end on topic {}", threadId, topicName);
                    // This poll to ensures we have an assigned partition
                    // otherwise seek won't work
                    consumer.poll(Duration.ofMillis(100));
                    consumer.seekToEnd(consumer.assignment());
                }
            }
        }

        protected void doPollRun(AtomicBoolean retry, AtomicBoolean reconnect) {
            StateRepository<String, String> offsetRepository = endpoint.getConfiguration().getOffsetRepository();

            // allow to re-connect thread in case we use that to retry failed messages
            boolean unsubscribing = false;

            TopicPartition partition = null;
            long partitionLastOffset = -1;

            try {
                while (isRunAllowed() && !isStoppingOrStopped() && !isSuspendingOrSuspended()
                        && retry.get() && !reconnect.get()) {

                    // flag to break out processing on the first exception
                    boolean breakOnErrorHit = false;
                    LOG.trace("Polling {} from topic: {} with timeout: {}", threadId, topicName, pollTimeoutMs);
                    ConsumerRecords<Object, Object> allRecords = consumer.poll(pollTimeoutMs);

                    Iterator<TopicPartition> partitionIterator = allRecords.partitions().iterator();
                    while (partitionIterator.hasNext()) {
                        partition = partitionIterator.next();
                        partitionLastOffset = -1;

                        Iterator<ConsumerRecord<Object, Object>> recordIterator = allRecords.records(partition).iterator();
                        LOG.debug("Records count {} received for partition {}", allRecords.records(partition).size(),
                                partition);
                        if (!breakOnErrorHit && recordIterator.hasNext()) {
                            ConsumerRecord<Object, Object> record;

                            while (!breakOnErrorHit && recordIterator.hasNext()) {
                                record = recordIterator.next();
                                if (LOG.isTraceEnabled()) {
                                    LOG.trace("Partition = {}, offset = {}, key = {}, value = {}", record.partition(),
                                            record.offset(), record.key(), record.value());
                                }
                                Exchange exchange = createKafkaExchange(record);

                                propagateHeaders(record, exchange, endpoint.getConfiguration());

                                // if not auto commit then we have additional
                                // information on the exchange
                                if (!isAutoCommitEnabled()) {
                                    exchange.getIn().setHeader(KafkaConstants.LAST_RECORD_BEFORE_COMMIT,
                                            !recordIterator.hasNext());
                                }
                                if (endpoint.getConfiguration().isAllowManualCommit()) {
                                    // allow Camel users to access the Kafka
                                    // consumer API to be able to do for example
                                    // manual commits
                                    KafkaManualCommit manual = endpoint.getComponent().getKafkaManualCommitFactory()
                                            .newInstance(exchange, consumer, topicName, threadId,
                                                    offsetRepository, partition, record.offset());
                                    exchange.getIn().setHeader(KafkaConstants.MANUAL_COMMIT, manual);
                                }
                                // if commit management is on user side give additional info for the end of poll loop
                                if (!isAutoCommitEnabled() || endpoint.getConfiguration().isAllowManualCommit()) {
                                    exchange.getIn().setHeader(KafkaConstants.LAST_POLL_RECORD,
                                            !recordIterator.hasNext() && !partitionIterator.hasNext());
                                }

                                try {
                                    processor.process(exchange);
                                } catch (Exception e) {
                                    exchange.setException(e);
                                }

                                if (exchange.getException() != null) {
                                    // processing failed due to an unhandled
                                    // exception, what should we do
                                    if (endpoint.getConfiguration().isBreakOnFirstError()) {
                                        // we are failing and we should break out
                                        LOG.warn(
                                                "Error during processing {} from topic: {}. Will seek consumer to offset: {} and re-connect and start polling again.",
                                                exchange, topicName, partitionLastOffset, exchange.getException());
                                        // force commit so we resume on next poll where we failed
                                        commitOffset(offsetRepository, partition, partitionLastOffset, false, true);
                                        // continue to next partition
                                        breakOnErrorHit = true;
                                    } else {
                                        // will handle/log the exception and
                                        // then continue to next
                                        getExceptionHandler().handleException("Error during processing", exchange,
                                                exchange.getException());
                                    }
                                } else {
                                    // record was success so remember its offset
                                    partitionLastOffset = record.offset();
                                    // lastOffsetProcessed would be used by
                                    // Consumer re-balance listener to preserve
                                    // offset state upon partition revoke
                                    lastProcessedOffset.put(serializeOffsetKey(partition), partitionLastOffset);
                                }

                                // success so release the exchange
                                releaseExchange(exchange, false);
                            }

                            if (!breakOnErrorHit) {
                                // all records processed from partition so commit them
                                commitOffset(offsetRepository, partition, partitionLastOffset, false, false);
                            }
                        }
                    }

                    if (breakOnErrorHit) {
                        // force re-connect
                        reconnect.set(true);
                    }
                }

                if (!reconnect.get()) {
                    if (isAutoCommitEnabled()) {
                        if ("async".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                            LOG.info("Auto commitAsync on stop {} from topic {}", threadId, topicName);
                            consumer.commitAsync();
                        } else if ("sync".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                            LOG.info("Auto commitSync on stop {} from topic {}", threadId, topicName);
                            consumer.commitSync();
                        } else if ("none".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                            LOG.info("Auto commit on stop {} from topic {} is disabled (none)", threadId, topicName);
                        }
                    }
                }

                LOG.info("Unsubscribing {} from topic {}", threadId, topicName);
                // we are unsubscribing so do not re connect
                unsubscribing = true;
                consumer.unsubscribe();
            } catch (InterruptException e) {
                getExceptionHandler().handleException("Interrupted while consuming " + threadId + " from kafka topic", e);
                LOG.info("Unsubscribing {} from topic {}", threadId, topicName);
                consumer.unsubscribe();
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception caught while polling " + threadId + " from kafka topic " + topicName
                              + " at offset " + lastProcessedOffset + ". Deciding what to do.",
                            e);
                }
                if (unsubscribing) {
                    // some kind of error in kafka, it may happen during unsubscribing
                    getExceptionHandler().handleException("Error unsubscribing " + threadId + " from kafka topic " + topicName,
                            e);
                } else {
                    PollOnError onError = pollExceptionStrategy.handleException(e);
                    if (PollOnError.RETRY == onError) {
                        LOG.warn(
                                "{} consuming {} from topic {} causedby {}. Will attempt again polling the same message (stacktrace in DEBUG logging level)",
                                e.getClass().getName(), threadId, topicName, e.getMessage());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                    "KafkaException consuming {} from topic {} causedby {}. Will attempt again polling the same message",
                                    threadId, topicName, e.getMessage(), e);
                        }
                        // consumer retry the same message again
                        retry.set(true);
                    } else if (PollOnError.RECONNECT == onError) {
                        LOG.warn(
                                "{} consuming {} from topic {} causedby {}. Will attempt to re-connect on next run (stacktrace in DEBUG logging level)",
                                e.getClass().getName(), threadId, topicName, e.getMessage());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                    "{} consuming {} from topic {} causedby {}. Will attempt to re-connect on next run",
                                    e.getClass().getName(), threadId, topicName, e.getMessage(), e);
                        }
                        // re-connect so the consumer can try the same message again
                        reconnect.set(true);
                    } else if (PollOnError.ERROR_HANDLER == onError) {
                        // use bridge error handler to route with exception
                        bridge.handleException(e);
                        // skip this poison message and seek to next message
                        seekToNextOffset(partitionLastOffset);
                    } else if (PollOnError.DISCARD == onError) {
                        // discard message
                        LOG.warn(
                                "{} consuming {} from topic {} causedby {}. Will discard the message and continue to poll the next message (stracktrace in DEBUG logging level).",
                                e.getClass().getName(), threadId, topicName, e.getMessage());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                    "{} consuming {} from topic {} causedby {}. Will discard the message and continue to poll the next message.",
                                    e.getClass().getName(), threadId, topicName, e.getMessage(), e);
                        }
                        // skip this poison message and seek to next message
                        seekToNextOffset(partitionLastOffset);
                    } else if (PollOnError.STOP == onError) {
                        // stop and terminate consumer
                        LOG.warn(
                                "{} consuming {} from topic {} causedby {}. Will stop consumer (stacktrace in DEBUG logging level).",
                                e.getClass().getName(), threadId, topicName, e.getMessage());
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                    "{} consuming {} from topic {} causedby {}. Will stop consumer.",
                                    e.getClass().getName(), threadId, topicName, e.getMessage(), e);
                        }
                        retry.set(false);
                        reconnect.set(false);
                    }
                }
            } finally {
                // only close if not retry or re-connecting
                if (!retry.get() && !reconnect.get()) {
                    LOG.debug("Closing consumer {}", threadId);
                    IOHelper.close(consumer);
                }
            }
        }

        private void seekToNextOffset(long partitionLastOffset) {
            boolean logged = false;
            Set<TopicPartition> tps = (Set<TopicPartition>) consumer.assignment();
            if (tps != null && partitionLastOffset != -1) {
                long next = partitionLastOffset + 1;
                LOG.info("Consumer seeking to next offset {} to continue polling next message from topic: {}", next, topicName);
                for (TopicPartition tp : tps) {
                    consumer.seek(tp, next);
                }
            } else if (tps != null) {
                for (TopicPartition tp : tps) {
                    long next = consumer.position(tp) + 1;
                    if (!logged) {
                        LOG.info("Consumer seeking to next offset {} to continue polling next message from topic: {}", next,
                                topicName);
                        logged = true;
                    }
                    consumer.seek(tp, next);
                }
            }
        }

        private void commitOffset(
                StateRepository<String, String> offsetRepository, TopicPartition partition, long partitionLastOffset,
                boolean stopping, boolean forceCommit) {
            if (partitionLastOffset != -1) {
                if (!endpoint.getConfiguration().isAllowManualCommit() && offsetRepository != null) {
                    LOG.debug("Saving offset repository state {} [topic: {} partition: {} offset: {}]", threadId, topicName,
                            partition.partition(),
                            partitionLastOffset);
                    offsetRepository.setState(serializeOffsetKey(partition), serializeOffsetValue(partitionLastOffset));
                } else if (stopping) {
                    // if we are stopping then react according to the configured option
                    if ("async".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                        LOG.debug("Auto commitAsync on stop {} from topic {}", threadId, topicName);
                        consumer.commitAsync(
                                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)), null);
                    } else if ("sync".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                        LOG.debug("Auto commitSync on stop {} from topic {}", threadId, topicName);
                        consumer.commitSync(
                                Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)));
                    } else if ("none".equals(endpoint.getConfiguration().getAutoCommitOnStop())) {
                        LOG.debug("Auto commit on stop {} from topic {} is disabled (none)", threadId, topicName);
                    }
                } else if (forceCommit) {
                    LOG.debug("Forcing commitSync {} [topic: {} partition: {} offset: {}]", threadId, topicName,
                            partition.partition(), partitionLastOffset);
                    consumer.commitSync(Collections.singletonMap(partition, new OffsetAndMetadata(partitionLastOffset + 1)));
                }
            }
        }

        void stop() {
            // As advised in the KAFKA-1894 ticket, calling this wakeup method
            // breaks the infinite loop
            consumer.wakeup();
        }

        void shutdown() {
            // As advised in the KAFKA-1894 ticket, calling this wakeup method
            // breaks the infinite loop
            consumer.wakeup();
        }

        @Override
        public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            LOG.debug("onPartitionsRevoked: {} from topic {}", threadId, topicName);

            // if camel is stopping, or we are not running
            boolean stopping = getEndpoint().getCamelContext().isStopping() && !isRunAllowed();

            StateRepository<String, String> offsetRepository = endpoint.getConfiguration().getOffsetRepository();
            for (TopicPartition partition : partitions) {
                String offsetKey = serializeOffsetKey(partition);
                Long offset = lastProcessedOffset.get(offsetKey);
                if (offset == null) {
                    offset = -1L;
                }
                try {
                    // only commit offsets if the component has control
                    if (endpoint.getConfiguration().getAutoCommitEnable()) {
                        commitOffset(offsetRepository, partition, offset, stopping, false);
                    }
                } catch (Exception e) {
                    LOG.error("Error saving offset repository state {} from offsetKey {} with offset: {}", threadId, offsetKey,
                            offset);
                    throw e;
                } finally {
                    lastProcessedOffset.remove(offsetKey);
                }
            }
        }

        @Override
        public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
            LOG.debug("onPartitionsAssigned: {} from topic {}", threadId, topicName);

            StateRepository<String, String> offsetRepository = endpoint.getConfiguration().getOffsetRepository();
            if (offsetRepository != null) {
                for (TopicPartition partition : partitions) {
                    String offsetState = offsetRepository.getState(serializeOffsetKey(partition));
                    if (offsetState != null && !offsetState.isEmpty()) {
                        // The state contains the last read offset so you need
                        // to seek from the next one
                        long offset = deserializeOffsetValue(offsetState) + 1;
                        LOG.debug("Resuming partition {} from offset {} from state", partition.partition(), offset);
                        consumer.seek(partition, offset);
                    }
                }
            }
        }
    }

    @SuppressWarnings("rawtypes")
    private Exchange createKafkaExchange(ConsumerRecord record) {
        Exchange exchange = createExchange(false);

        Message message = exchange.getIn();
        message.setHeader(KafkaConstants.PARTITION, record.partition());
        message.setHeader(KafkaConstants.TOPIC, record.topic());
        message.setHeader(KafkaConstants.OFFSET, record.offset());
        message.setHeader(KafkaConstants.HEADERS, record.headers());
        message.setHeader(KafkaConstants.TIMESTAMP, record.timestamp());
        if (record.key() != null) {
            message.setHeader(KafkaConstants.KEY, record.key());
        }
        message.setBody(record.value());

        return exchange;
    }

    private void propagateHeaders(
            ConsumerRecord<Object, Object> record, Exchange exchange, KafkaConfiguration kafkaConfiguration) {
        HeaderFilterStrategy headerFilterStrategy = kafkaConfiguration.getHeaderFilterStrategy();
        KafkaHeaderDeserializer headerDeserializer = kafkaConfiguration.getHeaderDeserializer();
        StreamSupport.stream(record.headers().spliterator(), false)
                .filter(header -> shouldBeFiltered(header, exchange, headerFilterStrategy))
                .forEach(header -> exchange.getIn().setHeader(header.key(),
                        headerDeserializer.deserialize(header.key(), header.value())));
    }

    private boolean shouldBeFiltered(Header header, Exchange exchange, HeaderFilterStrategy headerFilterStrategy) {
        return !headerFilterStrategy.applyFilterToExternalHeaders(header.key(), header.value(), exchange);
    }

    private boolean isAutoCommitEnabled() {
        return endpoint.getConfiguration().getAutoCommitEnable() != null && endpoint.getConfiguration().getAutoCommitEnable();
    }

    protected String serializeOffsetKey(TopicPartition topicPartition) {
        return topicPartition.topic() + '/' + topicPartition.partition();
    }

    protected String serializeOffsetValue(long offset) {
        return String.valueOf(offset);
    }

    protected long deserializeOffsetValue(String offset) {
        return Long.parseLong(offset);
    }
}
