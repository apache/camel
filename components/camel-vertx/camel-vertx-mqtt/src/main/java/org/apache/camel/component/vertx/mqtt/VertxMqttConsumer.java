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
package org.apache.camel.component.vertx.mqtt;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.WorkerExecutor;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.ShutdownRunningTask;
import org.apache.camel.spi.ShutdownAware;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subscribes to the topic filter of the endpoint and turns every message into an exchange.
 * <p>
 * Exchanges are processed in the order the messages arrive, on a Vert.x worker of one thread, never on the event loop
 * of the client, which also keeps the connection alive. The worker has no queue bound and never falls back to the
 * calling thread: with more than {@code maxPendingMessages} waiting the client stops reading from the connection until
 * half of them are done, which bounds the queue. Messages the client had already read when it paused still arrive, so
 * the bound is a soft one.
 * <p>
 * Stopping, and a graceful shutdown, pause the client first and wait for the pending exchanges: a message is
 * acknowledged when it arrives (unless manual acknowledgements are enabled), dropping it afterwards would lose it.
 * <p>
 * A connection or a subscription that fails is handed to the exception handler of the consumer once, and reported by
 * its health check until it succeeds: with automatic reconnect the consumer starts all the same.
 */
public class VertxMqttConsumer extends DefaultConsumer implements ShutdownAware {

    private static final Logger LOG = LoggerFactory.getLogger(VertxMqttConsumer.class);
    private static final long DRAIN_TIMEOUT = TimeUnit.SECONDS.toMillis(30);

    private volatile VertxMqttConnection connection;
    private final Consumer<Throwable> failureListener = this::failed;
    private volatile WorkerExecutor worker;
    // Under the lock of the consumer
    private int pending;
    private boolean paused;
    private boolean draining;
    // A failed exchange asked for a reconnect, made once the exchanges already received are done and acknowledged
    private MqttClient redeliveryFrom;

    public VertxMqttConsumer(VertxMqttEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public VertxMqttEndpoint getEndpoint() {
        return (VertxMqttEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (getHealthCheck() == null) {
            setHealthCheck(new VertxMqttConsumerHealthCheck(this, getRouteId()));
        }
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        synchronized (this) {
            pending = 0;
            paused = false;
            draining = false;
            redeliveryFrom = null;
        }
        // A pool of its own for every consumer: a shared worker executor of the same name shares its threads
        // A route may take long: no blocked thread warnings of Vert.x for the worker
        worker = getEndpoint().getComponent().getVertx().createSharedWorkerExecutor(
                "VertxMqttConsumer[%s]@%x".formatted(getEndpoint().getTopic(), System.identityHashCode(this)), 1,
                Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        connection = getEndpoint().acquireConnection();
        connection.addFailureListener(failureListener);
        connection.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos(), this::received);
    }

    @Override
    protected void doStop() throws Exception {
        final VertxMqttConnection running = connection;
        if (running != null) {
            // Stop reading first and finish what was read: the messages were acknowledged when they arrived
            stopReading();
            drain();
            running.removeFailureListener(failureListener);
            // A session the broker keeps (no clean start) keeps the subscription, messages wait there meanwhile
            running.unsubscribe(getEndpoint().getConfiguration().isCleanStart());
            connection = null;
            getEndpoint().releaseConnection();
        }
        final WorkerExecutor stopping = worker;
        worker = null;
        if (stopping != null) {
            stopping.close();
        }
        super.doStop();
    }

    @Override
    public boolean deferShutdown(ShutdownRunningTask shutdownRunningTask) {
        return false;
    }

    @Override
    public synchronized int getPendingExchangesSize() {
        return pending;
    }

    @Override
    public void prepareShutdown(boolean suspendOnly, boolean forced) {
        stopReading();
    }

    private synchronized void stopReading() {
        draining = true;
        if (!paused) {
            paused = true;
            final VertxMqttConnection running = connection;
            if (running != null) {
                running.pause(true);
            }
        }
    }

    /**
     * Waits for the pending exchanges, the messages already read included, for a while.
     */
    private synchronized void drain() throws InterruptedException {
        final long until = System.currentTimeMillis() + DRAIN_TIMEOUT;
        long left = DRAIN_TIMEOUT;
        while (pending > 0 && left > 0) {
            wait(left);
            left = until - System.currentTimeMillis();
        }
        if (pending > 0) {
            LOG.warn("Stopping the consumer of {} with {} messages still being processed", getEndpoint().getTopic(),
                    pending);
        }
    }

    /**
     * @return the connection while the consumer is started, for its health check
     */
    VertxMqttConnection connection() {
        return connection;
    }

    /**
     * On the context of the connection.
     */
    private void failed(Throwable cause) {
        getExceptionHandler().handleException(cause.getMessage(), cause);
    }

    /**
     * On the context of the connection.
     */
    private void received(MqttPublishMessage message, MqttClient receiver) {
        final WorkerExecutor running = worker;
        if (running == null) {
            LOG.debug("Message on {} after the consumer stopped", message.topicName());
            return;
        }
        track(1);
        // Ordered: the tasks of one context run one after the other, in the order they were submitted
        running.executeBlocking(() -> {
            process(message, receiver);
            return null;
        }, true).onFailure(e -> {
            LOG.warn("Cannot process the MQTT message on {}", message.topicName(), e);
            track(-1);
        });
    }

    /**
     * Counts the pending messages and pauses or resumes the client. Under a lock, so that a pause and the resume that
     * follows it reach the client in that order.
     */
    private synchronized void track(int delta) {
        final int maxPending = Math.max(1, getEndpoint().getConfiguration().getMaxPendingMessages());
        pending += delta;
        final VertxMqttConnection running = connection;
        if (!paused && pending >= maxPending) {
            paused = true;
            if (running != null) {
                running.pause(true);
            }
        } else if (paused && !draining && redeliveryFrom == null && pending <= maxPending / 2) {
            paused = false;
            if (running != null) {
                running.pause(false);
            }
        }
        if (pending == 0) {
            if (redeliveryFrom != null && running != null && !draining) {
                // Everything received before is done: close for the redelivery, and read again on the next connection
                running.redeliver(redeliveryFrom);
                redeliveryFrom = null;
                paused = false;
                running.pause(false);
            }
            notifyAll();
        }
    }

    private void process(MqttPublishMessage message, MqttClient receiver) {
        final Exchange exchange = createExchange(false);
        final Message in = exchange.getIn();
        in.setBody(message.payload().getBytes());
        in.setHeader(VertxMqttConstants.TOPIC, message.topicName());
        in.setHeader(VertxMqttConstants.QOS, message.qosLevel().value());
        in.setHeader(VertxMqttConstants.RETAINED, message.isRetain());
        in.setHeader(VertxMqttConstants.DUPLICATE, message.isDup());
        if (message.qosLevel() != MqttQoS.AT_MOST_ONCE) {
            in.setHeader(VertxMqttConstants.MESSAGE_ID, message.messageId());
        }
        if (getEndpoint().getConfiguration().getMqttVersion() == VertxMqttVersion.MQTT_5_0) {
            VertxMqttProperties.toHeaders(message.properties(), in);
        }
        getAsyncProcessor().process(exchange, doneSync -> completed(exchange, message, receiver));
    }

    /**
     * Stops reading and remembers the redelivery, made by {@link #track(int)} once the pending exchanges are done:
     * closing at once would refuse the acknowledgements of the exchanges that succeed meanwhile, and the broker would
     * deliver those again as well.
     */
    private synchronized void requestRedelivery(VertxMqttConnection running, MqttClient receiver) {
        if (redeliveryFrom == null) {
            redeliveryFrom = receiver;
            if (!paused) {
                paused = true;
                running.pause(true);
            }
        }
    }

    private void completed(Exchange exchange, MqttPublishMessage message, MqttClient receiver) {
        try {
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing the MQTT message on %s".formatted(message.topicName()),
                        exchange, exchange.getException());
            }
            final VertxMqttConnection running = connection;
            final VertxMqttConfiguration configuration = getEndpoint().getConfiguration();
            if (running != null && exchange.getException() == null) {
                running.processed();
            }
            if (running != null && configuration.isManualAcksEnabled() && message.qosLevel() != MqttQoS.AT_MOST_ONCE) {
                if (exchange.getException() == null) {
                    running.acknowledge(message, receiver);
                } else if (message.qosLevel() == MqttQoS.AT_LEAST_ONCE && running.canRedeliver()) {
                    // Left unacknowledged, the message would hold one of the in-flight slots of the broker until the
                    // next connect: a reconnect has the broker deliver it again from the session it keeps
                    requestRedelivery(running, receiver);
                } else {
                    // Without a session to deliver it from again, or at quality of service 2, whose PUBREC went out
                    // when the message arrived, it is not delivered again: acknowledged, so that the slot is free
                    running.acknowledge(message, receiver);
                }
            }
        } finally {
            releaseExchange(exchange, false);
            track(-1);
        }
    }
}
