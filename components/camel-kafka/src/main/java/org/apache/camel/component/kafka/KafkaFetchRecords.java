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
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import org.apache.camel.component.kafka.consumer.CommitManager;
import org.apache.camel.component.kafka.consumer.CommitManagers;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaConsumerListener;
import org.apache.camel.component.kafka.consumer.errorhandler.KafkaErrorStrategies;
import org.apache.camel.component.kafka.consumer.support.KafkaRecordProcessorFacade;
import org.apache.camel.component.kafka.consumer.support.ProcessingResult;
import org.apache.camel.component.kafka.consumer.support.classic.ClassicRebalanceListener;
import org.apache.camel.component.kafka.consumer.support.resume.ResumeRebalanceListener;
import org.apache.camel.support.BridgeExceptionHandlerToErrorHandler;
import org.apache.camel.support.task.ForegroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ReflectionHelper;
import org.apache.camel.util.TimeUtils;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.InterruptException;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaFetchRecords implements Runnable {
    /*
     This keeps track of the state the record fetcher is. Because the Kafka consumer is not thread safe, it may take
     some time between the pause or resume request is triggered and it is actually set.
     Some of the states may be set but not read. This is done deliberately to avoid the code to enter multiple times
     the branches that handle the *_REQUESTED states.
     */
    private enum State {
        RUNNING,
        PAUSE_REQUESTED,
        PAUSED,
        RESUME_REQUESTED,
    }

    private static final Logger LOG = LoggerFactory.getLogger(KafkaFetchRecords.class);

    // There are a few of volatile fields here because they are usually read from other threads,
    // like from the health check thread. They are usually only read on those contexts.

    private final KafkaConsumer kafkaConsumer;
    private org.apache.kafka.clients.consumer.Consumer consumer;
    private volatile String clientId;
    private final String topicName;
    private final Pattern topicPattern;
    private final String threadId;
    private final Properties kafkaProps;
    private PollExceptionStrategy pollExceptionStrategy;
    private final BridgeExceptionHandlerToErrorHandler bridge;
    private final ReentrantLock lock = new ReentrantLock();
    private CommitManager commitManager;
    private volatile Exception lastError;
    private final KafkaConsumerListener consumerListener;

    private volatile boolean terminated;
    private volatile long currentBackoffInterval;

    private volatile boolean reconnect; // The reconnect must be false at init (this is the policy whether to reconnect).
    private volatile boolean connected; // this is the state (connected or not)

    private volatile State state = State.RUNNING;

    KafkaFetchRecords(KafkaConsumer kafkaConsumer,
                      BridgeExceptionHandlerToErrorHandler bridge, String topicName, Pattern topicPattern, String id,
                      Properties kafkaProps, KafkaConsumerListener consumerListener) {
        this.kafkaConsumer = kafkaConsumer;
        this.bridge = bridge;
        this.topicName = topicName;
        this.topicPattern = topicPattern;
        this.consumerListener = consumerListener;
        this.threadId = topicName + "-" + "Thread " + id;
        this.kafkaProps = kafkaProps;
    }

    @Override
    public void run() {
        if (!isKafkaConsumerRunnable()) {
            return;
        }

        do {
            terminated = false;

            if (!isConnected()) {

                // task that deals with creating kafka consumer
                currentBackoffInterval = kafkaConsumer.getEndpoint().getComponent().getCreateConsumerBackoffInterval();
                ForegroundTask task = Tasks.foregroundTask()
                        .withName("Create KafkaConsumer")
                        .withBudget(Budgets.iterationBudget()
                                .withMaxIterations(
                                        kafkaConsumer.getEndpoint().getComponent().getCreateConsumerBackoffMaxAttempts())
                                .withInitialDelay(Duration.ZERO)
                                .withInterval(Duration.ofMillis(currentBackoffInterval))
                                .build())
                        .build();
                boolean success = task.run(this::createConsumerTask);
                if (!success) {
                    int max = kafkaConsumer.getEndpoint().getComponent().getCreateConsumerBackoffMaxAttempts();
                    setupCreateConsumerException(task, max);
                    // give up and terminate this consumer
                    terminated = true;
                    break;
                }

                // task that deals with subscribing kafka consumer
                currentBackoffInterval = kafkaConsumer.getEndpoint().getComponent().getSubscribeConsumerBackoffInterval();
                task = Tasks.foregroundTask()
                        .withName("Subscribe KafkaConsumer")
                        .withBudget(Budgets.iterationBudget()
                                .withMaxIterations(
                                        kafkaConsumer.getEndpoint().getComponent().getSubscribeConsumerBackoffMaxAttempts())
                                .withInitialDelay(Duration.ZERO)
                                .withInterval(Duration.ofMillis(currentBackoffInterval))
                                .build())
                        .build();
                success = task.run(this::initializeConsumerTask);
                if (!success) {
                    int max = kafkaConsumer.getEndpoint().getComponent().getCreateConsumerBackoffMaxAttempts();
                    setupInitializeErrorException(task, max);
                    // give up and terminate this consumer
                    terminated = true;
                    break;
                }

                setConnected(true);
            }

            setLastError(null);
            startPolling();
        } while ((pollExceptionStrategy.canContinue() || isReconnect()) && isKafkaConsumerRunnable());

        if (LOG.isInfoEnabled()) {
            LOG.info("Terminating KafkaConsumer thread {} receiving from {}", threadId, getPrintableTopic());
        }

        safeConsumerClose();
    }

    private void setupInitializeErrorException(ForegroundTask task, int max) {
        String time = TimeUtils.printDuration(task.elapsed(), true);
        String topic = getPrintableTopic();
        String msg = "Gave up subscribing org.apache.kafka.clients.consumer.KafkaConsumer " +
                     threadId + " to " + topic + " after " + max + " attempts (elapsed: " + time + ").";
        LOG.warn(msg);
        setLastError(new KafkaConsumerFatalException(msg, lastError));
    }

    private void setupCreateConsumerException(ForegroundTask task, int max) {
        String time = TimeUtils.printDuration(task.elapsed(), true);
        String topic = getPrintableTopic();
        String msg = "Gave up creating org.apache.kafka.clients.consumer.KafkaConsumer "
                     + threadId + " to " + topic + " after " + max + " attempts (elapsed: " + time + ").";

        setLastError(new KafkaConsumerFatalException(msg, lastError));
    }

    private boolean initializeConsumerTask() {
        try {
            initializeConsumer();
        } catch (Exception e) {
            setConnected(false);
            // ensure this is logged so users can see the problem
            LOG.warn("Error subscribing org.apache.kafka.clients.consumer.KafkaConsumer due to: {}", e.getMessage(),
                    e);
            setLastError(e);
            return false;
        }

        return true;
    }

    private boolean createConsumerTask() {
        try {
            createConsumer();
            commitManager
                    = CommitManagers.createCommitManager(consumer, kafkaConsumer, threadId, getPrintableTopic());

            if (consumerListener != null) {
                consumerListener.setConsumer(consumer);

                SeekPolicy seekPolicy = kafkaConsumer.getEndpoint().getComponent().getConfiguration().getSeekTo();
                if (seekPolicy == null) {
                    seekPolicy = SeekPolicy.BEGINNING;
                }

                consumerListener.setSeekPolicy(seekPolicy);
            }
        } catch (Exception e) {
            setConnected(false);
            // ensure this is logged so users can see the problem
            LOG.warn("Error creating org.apache.kafka.clients.consumer.KafkaConsumer due to: {}", e.getMessage(),
                    e);
            setLastError(e);
            return false;
        }

        return true;
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

            var krbLocation = kafkaConsumer.getEndpoint().getConfiguration().getKerberosConfigLocation();
            if (krbLocation != null) {
                System.setProperty("java.security.krb5.conf", krbLocation);
            }

            // init client id which we may need to get from the kafka producer via reflection
            if (clientId == null) {
                clientId = getKafkaProps().getProperty(CommonClientConfigs.CLIENT_ID_CONFIG);
                if (clientId == null) {
                    try {
                        clientId = (String) ReflectionHelper
                                .getField(consumer.getClass().getDeclaredField("clientId"), consumer);
                    } catch (Exception e) {
                        // ignore
                        clientId = "";
                    }
                }
            }

            this.pollExceptionStrategy = KafkaErrorStrategies.strategies(this, kafkaConsumer.getEndpoint(), consumer);
        } finally {
            Thread.currentThread().setContextClassLoader(threadClassLoader);
        }
    }

    private void initializeConsumer() {
        subscribe();

        // set reconnect to false as the connection and resume is done at this point
        setConnected(false);

        pollExceptionStrategy.reset();
    }

    private void subscribe() {
        ConsumerRebalanceListener listener;

        if (kafkaConsumer.getResumeStrategy() == null) {
            listener = new ClassicRebalanceListener(
                    threadId, kafkaConsumer.getEndpoint().getConfiguration(), commitManager, consumer);
        } else {
            listener = new ResumeRebalanceListener(
                    threadId, kafkaConsumer.getEndpoint().getConfiguration(),
                    commitManager, consumer, kafkaConsumer.getResumeStrategy());
        }

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
        long partitionLastOffset = -1;

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

            KafkaRecordProcessorFacade recordProcessorFacade = new KafkaRecordProcessorFacade(
                    kafkaConsumer, threadId, commitManager, consumerListener);

            Duration pollDuration = Duration.ofMillis(pollTimeoutMs);
            ProcessingResult lastResult = null;
            while (isKafkaConsumerRunnableAndNotStopped() && isConnected() && pollExceptionStrategy.canContinue()) {
                ConsumerRecords<Object, Object> allRecords = consumer.poll(pollDuration);
                if (consumerListener != null) {
                    if (!consumerListener.afterConsume(consumer)) {
                        continue;
                    }
                }

                ProcessingResult result = recordProcessorFacade.processPolledRecords(allRecords, lastResult);
                updateTaskState();
                if (result.isBreakOnErrorHit() && !this.state.equals(State.PAUSED)) {
                    LOG.debug("We hit an error ... setting flags to force reconnect");
                    // force re-connect
                    setReconnect(true);
                    setConnected(false);
                } else {
                    lastResult = result;
                }

            }

            if (!isConnected()) {
                LOG.debug("Not reconnecting, check whether to auto-commit or not ...");
                commitManager.commit();
            }

            safeUnsubscribe();
        } catch (InterruptException e) {
            kafkaConsumer.getExceptionHandler().handleException("Interrupted while consuming " + threadId + " from kafka topic",
                    e);
            commitManager.commit();

            LOG.info("Unsubscribing {} from {}", threadId, getPrintableTopic());
            safeUnsubscribe();
            Thread.currentThread().interrupt();
        } catch (WakeupException e) {
            // This is normal: it raises this exception when calling the wakeUp (which happens when we stop)

            if (LOG.isTraceEnabled()) {
                LOG.trace("The kafka consumer was woken up while polling on thread {} for {}", threadId, getPrintableTopic());
            }
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.warn("Exception {} caught by thread {} while polling {} from kafka: {}",
                        e.getClass().getName(), threadId, getPrintableTopic(), e.getMessage(), e);
            } else {
                LOG.warn("Exception {} caught by thread {} while polling {} from kafka: {}",
                        e.getClass().getName(), threadId, getPrintableTopic(), e.getMessage());
            }

            pollExceptionStrategy.handle(partitionLastOffset, e);
        } finally {
            // only close if not retry
            if (!pollExceptionStrategy.canContinue()) {
                safeUnsubscribe();
                safeConsumerClose();
            }
            lock.unlock();
        }
    }

    private void updateTaskState() {
        switch (state) {
            case PAUSE_REQUESTED:
                LOG.info("Pausing the consumer as a response to a pause request");
                consumer.pause(consumer.assignment());
                state = State.PAUSED;
                break;
            case RESUME_REQUESTED:
                LOG.info("Resuming the consumer as a response to a resume request");
                if (consumer.committed(this.consumer.assignment()) != null) {
                    consumer.committed(this.consumer.assignment()).forEach((k, v) -> {
                        if (v != null) {
                            final TopicPartition tp = (TopicPartition) k;
                            LOG.info("Resuming from the offset {} for the topic {} with partition {}",
                                    ((OffsetAndMetadata) v).offset(), tp.topic(), tp.partition());
                            consumer.seek(tp, ((OffsetAndMetadata) v).offset());
                        }
                    });
                }
                consumer.resume(consumer.assignment());
                state = State.RUNNING;
                break;
            default:
                break;
        }
    }

    private void safeConsumerClose() {
        /*
         IOHelper.close only catches IOExceptions, thus other Kafka exceptions may leak from the fetcher causing
         unspecified behavior.
         */
        try {
            LOG.debug("Closing consumer {}", threadId);
            IOHelper.close(consumer, "Kafka consumer (thread ID " + threadId + ")", LOG);
        } catch (Exception e) {
            LOG.error("Error closing the Kafka consumer: {} (this error will be ignored)", e.getMessage(), e);
        }
    }

    private void safeUnsubscribe() {
        if (consumer == null) {
            return;
        }

        final String printableTopic = getPrintableTopic();
        try {
            LOG.debug("Unsubscribing from Kafka");
            consumer.unsubscribe();
            LOG.debug("Done unsubscribing from Kafka");
        } catch (IllegalStateException e) {
            LOG.warn("The consumer is likely already closed. Skipping unsubscribing thread {} from kafka {}", threadId,
                    printableTopic);
        } catch (Exception e) {
            LOG.debug("Something went wrong while unsubscribing from Kafka: {}", e.getMessage());
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

    private boolean isKafkaConsumerRunnable() {
        return kafkaConsumer.isRunAllowed() && !kafkaConsumer.isStoppingOrStopped()
                && !kafkaConsumer.isSuspendingOrSuspended();
    }

    private boolean isKafkaConsumerRunnableAndNotStopped() {
        return kafkaConsumer.isRunAllowed() && !kafkaConsumer.isStoppingOrStopped();
    }

    private boolean isReconnect() {
        return reconnect;
    }

    public void setReconnect(boolean value) {
        reconnect = value;
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
        if (consumer == null) {
            return;
        }

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
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    void stop() {
        safeStop();
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isPaused() {
        return !consumer.paused().isEmpty();
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    private boolean isReady() {
        if (!connected) {
            return false;
        }

        boolean ready = true;
        try {
            if (consumer instanceof org.apache.kafka.clients.consumer.KafkaConsumer) {
                // need to use reflection to access the network client which has API to check if the client has ready
                // connections
                org.apache.kafka.clients.consumer.KafkaConsumer kc = (org.apache.kafka.clients.consumer.KafkaConsumer) consumer;
                ConsumerNetworkClient nc
                        = (ConsumerNetworkClient) ReflectionHelper.getField(kc.getClass().getDeclaredField("client"), kc);
                LOG.trace(
                        "Health-Check calling org.apache.kafka.clients.consumer.internals.ConsumerNetworkClient.hasReadyNode");
                ready = nc.hasReadyNodes(System.currentTimeMillis());
            }
        } catch (Exception e) {
            // ignore
            LOG.debug("Cannot check hasReadyNodes on KafkaConsumer client (ConsumerNetworkClient) due to: "
                      + e.getMessage() + ". This exception is ignored.",
                    e);
        }
        return ready;
    }

    private Properties getKafkaProps() {
        return kafkaProps;
    }

    private boolean isTerminated() {
        return terminated;
    }

    private boolean isRecoverable() {
        return (pollExceptionStrategy != null && pollExceptionStrategy.canContinue() || isReconnect())
                && isKafkaConsumerRunnable();
    }

    // concurrent access happens here
    public TaskHealthState healthState() {
        return new TaskHealthState(
                isReady(), isTerminated(), isRecoverable(), lastError, clientId,
                currentBackoffInterval, kafkaProps);
    }

    public BridgeExceptionHandlerToErrorHandler getBridge() {
        return bridge;
    }

    /*
     * This is for manually pausing the consumer. This is mostly used for directly calling pause from Java code
     * or via JMX
     */
    public void pause() {
        LOG.info("A pause request was issued and the consumer thread will pause after current processing has finished");
        state = State.PAUSE_REQUESTED;
    }

    /*
     * This is for manually resuming the consumer (not to be confused w/ the Resume API). This is
     * mostly used for directly calling resume from Java code or via JMX
     */
    public void resume() {
        LOG.info("A resume request was issued and the consumer thread will resume after current processing has finished");
        state = State.RESUME_REQUESTED;
    }

    private synchronized void setLastError(Exception lastError) {
        this.lastError = lastError;
    }
}
