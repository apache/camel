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
package org.apache.camel.component.rabbitmq;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;


public class RabbitMQConsumer extends DefaultConsumer {
    private ExecutorService executor;
    private Connection conn;
    private int closeTimeout = 30 * 1000;
    private final RabbitMQEndpoint endpoint;

    /**
     * Task in charge of starting consumer
     */
    private StartConsumerCallable startConsumerCallable;

    /**
     * Running consumers
     */
    private final List<RabbitConsumer> consumers = new ArrayList<RabbitConsumer>();

    public RabbitMQConsumer(RabbitMQEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public RabbitMQEndpoint getEndpoint() {
        return (RabbitMQEndpoint) super.getEndpoint();
    }

    /**
     * Open connection
     */
    private void openConnection() throws IOException, TimeoutException {
        log.trace("Creating connection...");
        this.conn = getEndpoint().connect(executor);
        log.debug("Created connection: {}", conn);
    }

    /**
     * Open channel
     */
    private Channel openChannel() throws IOException {
        log.trace("Creating channel...");
        Channel channel = conn.createChannel();
        log.debug("Created channel: {}", channel);
        // setup the basicQos
        if (endpoint.isPrefetchEnabled()) {
            channel.basicQos(endpoint.getPrefetchSize(), endpoint.getPrefetchCount(),
                            endpoint.isPrefetchGlobal());
        }
        return channel;
    }

    /**
     * Add a consumer thread for given channel
     */
    private void startConsumers() throws IOException {
        // First channel used to declare Exchange and Queue
        Channel channel = openChannel();
        if (getEndpoint().isDeclare()) {
            endpoint.declareExchangeAndQueue(channel);
        }
        startConsumer(channel);
        // Other channels
        for (int i = 1; i < endpoint.getConcurrentConsumers(); i++) {
            channel = openChannel();
            startConsumer(channel);
        }
    }

    /**
     * Add a consumer thread for given channel
     */
    private void startConsumer(Channel channel) throws IOException {
        RabbitConsumer consumer = new RabbitConsumer(this, channel);
        consumer.start();
        this.consumers.add(consumer);
    }

    @Override
    protected void doStart() throws Exception {
        executor = endpoint.createExecutor();
        log.debug("Using executor {}", executor);
        try {
            openConnection();
            startConsumers();
        } catch (Exception e) {
            log.info("Connection failed, will start background thread to retry!", e);
            // Open connection, and start message listener in background
            Integer networkRecoveryInterval = getEndpoint().getNetworkRecoveryInterval();
            final long connectionRetryInterval = networkRecoveryInterval != null && networkRecoveryInterval > 0 ? networkRecoveryInterval : 100L;
            startConsumerCallable = new StartConsumerCallable(connectionRetryInterval);
            executor.submit(startConsumerCallable);
        }
    }

    /**
     * If needed, close Connection and Channels 
     */
    private void closeConnectionAndChannel() throws IOException, TimeoutException {
        if (startConsumerCallable != null) {
            startConsumerCallable.stop();
        }
        for (RabbitConsumer consumer : this.consumers) {
            try {
                consumer.stop();
            } catch (TimeoutException e) {
                log.error("Timeout occured");
                throw e;
            }
        }
        this.consumers.clear();
        if (conn != null) {
            log.debug("Closing connection: {} with timeout: {} ms.", conn, closeTimeout);
            conn.close(closeTimeout);
            conn = null;
        }
    }

    @Override
    protected void doStop() throws Exception {
        closeConnectionAndChannel();

        if (executor != null) {
            if (endpoint != null && endpoint.getCamelContext() != null) {
                endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
            executor = null;
        }
    }

    class RabbitConsumer extends com.rabbitmq.client.DefaultConsumer {

        private final RabbitMQConsumer consumer;
        private final Channel channel;
        private String tag;

        /**
         * Constructs a new instance and records its association to the
         * passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        public RabbitConsumer(RabbitMQConsumer consumer, Channel channel) {
            super(channel);
            this.consumer = consumer;
            this.channel = channel;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                                   AMQP.BasicProperties properties, byte[] body) throws IOException {

            Exchange exchange = consumer.endpoint.createRabbitExchange(envelope, properties, body);
            endpoint.getMessageConverter().mergeAmqpProperties(exchange, properties);

            boolean sendReply = properties.getReplyTo() != null;
            if (sendReply && !exchange.getPattern().isOutCapable()) {
                log.debug("In an inOut capable route");
                exchange.setPattern(ExchangePattern.InOut);
            }

            log.trace("Created exchange [exchange={}]", exchange);
            long deliveryTag = envelope.getDeliveryTag();
            try {
                consumer.getProcessor().process(exchange);
            } catch (Exception e) {
                exchange.setException(e);
            }

            // obtain the message after processing
            Message msg;
            if (exchange.hasOut()) {
                msg = exchange.getOut();
            } else {
                msg = exchange.getIn();
            }

            if (!exchange.isFailed()) {
                // processing success
                if (sendReply && exchange.getPattern().isOutCapable()) {
                    try {
                        endpoint.publishExchangeToChannel(exchange, channel, properties.getReplyTo());
                    } catch (RuntimeCamelException e) {
                        getExceptionHandler().handleException("Error processing exchange", exchange, e);
                    }
                }
                if (!consumer.endpoint.isAutoAck()) {
                    log.trace("Acknowledging receipt [delivery_tag={}]", deliveryTag);
                    channel.basicAck(deliveryTag, false);
                }
            } else if (endpoint.isTransferException() && exchange.getPattern().isOutCapable()) {
                // the inOut exchange failed so put the exception in the body and send back
                msg.setBody(exchange.getException());
                exchange.setOut(msg);
                try {
                    endpoint.publishExchangeToChannel(exchange, channel, properties.getReplyTo());
                } catch (RuntimeCamelException e) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, e);
                }
            } else {
                boolean isRequeueHeaderSet = msg.getHeader(RabbitMQConstants.REQUEUE, false, boolean.class);
                // processing failed, then reject and handle the exception
                if (deliveryTag != 0 && !consumer.endpoint.isAutoAck()) {
                    log.trace("Rejecting receipt [delivery_tag={}] with requeue={}", deliveryTag, isRequeueHeaderSet);
                    if (isRequeueHeaderSet) {
                        channel.basicReject(deliveryTag, true);
                    } else {
                        channel.basicReject(deliveryTag, false);
                    }
                }
                if (exchange.getException() != null) {
                    getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
                }
            }
        }

        /**
         * Bind consumer to channel
         */
        public void start() throws IOException {
            tag = channel.basicConsume(endpoint.getQueue(), endpoint.isAutoAck(), this);
        }

        /**
         * Unbind consumer from channel
         */
        public void stop() throws IOException, TimeoutException {
            if (tag != null) {
                channel.basicCancel(tag);
            }
            try {
                channel.close();
            } catch (TimeoutException e) {
                log.error("Timeout occured");
                throw e;
            }
        }
    }

    /**
     * Task in charge of opening connection and adding listener when consumer is started
     * and broker is not available.
     */
    private class StartConsumerCallable implements Callable<Void> {
        private final long connectionRetryInterval;
        private final AtomicBoolean running = new AtomicBoolean(true);

        public StartConsumerCallable(long connectionRetryInterval) {
            this.connectionRetryInterval = connectionRetryInterval;
        }

        public void stop() {
            running.set(false);
            RabbitMQConsumer.this.startConsumerCallable = null;
        }

        @Override
        public Void call() throws Exception {
            boolean connectionFailed = true;
            // Reconnection loop
            while (running.get() && connectionFailed) {
                try {
                    openConnection();
                    connectionFailed = false;
                } catch (Exception e) {
                    log.info("Connection failed, will retry in {}" + connectionRetryInterval + "ms", e);
                    Thread.sleep(connectionRetryInterval);
                }
            }
            if (!connectionFailed) {
                startConsumers();
            }
            stop();
            return null;
        }
    }
}
