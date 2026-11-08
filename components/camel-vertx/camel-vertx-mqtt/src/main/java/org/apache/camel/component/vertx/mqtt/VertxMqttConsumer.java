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

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.mqtt.messages.MqttPublishMessage;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;

/**
 * Subscribes to the topic filter of the endpoint and turns every message into an exchange.
 * <p>
 * Exchanges are processed in the order the messages arrive, on a thread of the consumer, never on the event loop of the
 * client, which also keeps the connection alive. With more than {@code maxPendingMessages} waiting the client stops
 * reading from the connection until half of them are done.
 * <p>
 * A connection or a subscription that fails is handed to the exception handler of the consumer once, and reported by
 * its health check until it succeeds: with automatic reconnect the consumer starts all the same.
 */
public class VertxMqttConsumer extends DefaultConsumer {

    private volatile VertxMqttConnection connection;
    private final Consumer<Throwable> failureListener = this::failed;
    private ExecutorService executor;
    // Under the lock of the consumer
    private int pending;
    private boolean paused;

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
        executor = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this,
                "VertxMqttConsumer[" + getEndpoint().getTopic() + "]");
        connection = getEndpoint().acquireConnection();
        connection.addFailureListener(failureListener);
        connection.subscribe(getEndpoint().getTopic(), getEndpoint().getConfiguration().getQos(), this::received);
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            // A session the broker keeps (no clean start) keeps the subscription, messages wait there meanwhile
            connection.removeFailureListener(failureListener);
            connection.unsubscribe(getEndpoint().getConfiguration().isCleanStart());
            connection = null;
            getEndpoint().releaseConnection();
        }
        if (executor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(executor);
            executor = null;
        }
        synchronized (this) {
            pending = 0;
            paused = false;
        }
        super.doStop();
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
    private void received(MqttPublishMessage message) {
        ExecutorService running = executor;
        if (running == null) {
            return;
        }
        track(1);
        running.execute(() -> process(message));
    }

    /**
     * Counts the pending messages and pauses or resumes the client. Under a lock, so that a pause and the resume that
     * follows it reach the client in that order.
     */
    private synchronized void track(int delta) {
        int maxPending = Math.max(1, getEndpoint().getConfiguration().getMaxPendingMessages());
        pending += delta;
        VertxMqttConnection running = connection;
        if (!paused && pending >= maxPending) {
            paused = true;
            if (running != null) {
                running.pause(true);
            }
        } else if (paused && pending <= maxPending / 2) {
            paused = false;
            if (running != null) {
                running.pause(false);
            }
        }
    }

    private void process(MqttPublishMessage message) {
        Exchange exchange = createExchange(false);
        Message in = exchange.getIn();
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
        getAsyncProcessor().process(exchange, doneSync -> completed(exchange, message));
    }

    private void completed(Exchange exchange, MqttPublishMessage message) {
        try {
            boolean acknowledge = getEndpoint().getConfiguration().isManualAcksEnabled()
                    && message.qosLevel() != MqttQoS.AT_MOST_ONCE && exchange.getException() == null;
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing the MQTT message on %s".formatted(message.topicName()),
                        exchange, exchange.getException());
            }
            VertxMqttConnection running = connection;
            if (acknowledge && running != null) {
                running.run(message::ack);
            }
        } finally {
            releaseExchange(exchange, false);
            track(-1);
        }
    }
}
