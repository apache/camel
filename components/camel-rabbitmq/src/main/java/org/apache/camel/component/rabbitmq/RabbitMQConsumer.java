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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;

public class RabbitMQConsumer extends DefaultConsumer {
    ExecutorService executor;
    Connection conn;
    Channel channel;

    private int closeTimeout = 30 * 1000;
    
    private final RabbitMQEndpoint endpoint;
    /**
     * Task in charge of starting consumer
     */
    private StartConsumerCallable startConsumerCallable;

    public RabbitMQConsumer(RabbitMQEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    public RabbitMQEndpoint getEndpoint() {
        return (RabbitMQEndpoint) super.getEndpoint();
    }

    /**
     * Open connection and channel
     */
    private void openConnectionAndChannel() throws IOException {
        log.trace("Creating connection...");
        this.conn = getEndpoint().connect(executor);
        log.debug("Created connection: {}", conn);

        log.trace("Creating channel...");
        this.channel = conn.createChannel();
        log.debug("Created channel: {}", channel);
    }

    /**
     * If needed, create Exchange and Queue, then add message listener
     */
    private void addConsumer() throws IOException {
        getEndpoint().declareExchangeAndQueue(channel);

        channel.basicConsume(endpoint.getQueue(), endpoint.isAutoAck(),
                new RabbitConsumer(this, channel));
    }

    @Override
    protected void doStart() throws Exception {
        executor = endpoint.createExecutor();
        log.debug("Using executor {}", executor);
        try {
            openConnectionAndChannel();
            addConsumer();
        } catch (Exception e) {
            // Open connection, and start message listener in background
            Integer networkRecoveryInterval = getEndpoint().getNetworkRecoveryInterval();
            final long connectionRetryInterval= networkRecoveryInterval!=null && networkRecoveryInterval >0? networkRecoveryInterval :100L;
            startConsumerCallable=new StartConsumerCallable(connectionRetryInterval);
            executor.submit(startConsumerCallable);
        }
    }

    /**
     * If needed, close Connection and Channel
     */
    private void closeConnectionAndChannel() throws IOException {
        if (startConsumerCallable!=null) {
            startConsumerCallable.stop();
        }
        if (channel != null) {
            log.debug("Closing channel: {}", channel);
            channel.close();
            channel = null;
        }
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
            mergeAmqpProperties(exchange, properties);
            log.trace("Created exchange [exchange={}]", exchange);

            try {
                consumer.getProcessor().process(exchange);

                long deliveryTag = envelope.getDeliveryTag();
                if (!consumer.endpoint.isAutoAck()) {
                    log.trace("Acknowledging receipt [delivery_tag={}]", deliveryTag);
                    channel.basicAck(deliveryTag, false);
                }

            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }

        /**
         * Will take an {@link Exchange} and add header values back to the {@link Exchange#getIn()}
         */
        private void mergeAmqpProperties(Exchange exchange, AMQP.BasicProperties properties) {

            if (properties.getType() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.TYPE, properties.getType());
            }
            if (properties.getAppId() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.APP_ID, properties.getAppId());
            }
            if (properties.getClusterId() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.CLUSTERID, properties.getClusterId());
            }
            if (properties.getContentEncoding() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.CONTENT_ENCODING, properties.getContentEncoding());
            }
            if (properties.getContentType() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.CONTENT_TYPE, properties.getContentType());
            }
            if (properties.getCorrelationId() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.CORRELATIONID, properties.getCorrelationId());
            }
            if (properties.getExpiration() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.EXPIRATION, properties.getExpiration());
            }
            if (properties.getMessageId() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.MESSAGE_ID, properties.getMessageId());
            }
            if (properties.getPriority() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.PRIORITY, properties.getPriority());
            }
            if (properties.getReplyTo() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.REPLY_TO, properties.getReplyTo());
            }
            if (properties.getTimestamp() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.TIMESTAMP, properties.getTimestamp());
            }
            if (properties.getUserId() != null) {
                exchange.getIn().setHeader(RabbitMQConstants.USERID, properties.getUserId());
            }
        }

    }

    /**
     * Task in charge of opening connection and adding listener when consumer is started
     * and broker is not avaiblable.
     */
    private class StartConsumerCallable implements Callable<Void> {
        private final long connectionRetryInterval;
        private final AtomicBoolean running=new AtomicBoolean(true);
        public StartConsumerCallable(long connectionRetryInterval) {
            this.connectionRetryInterval = connectionRetryInterval;
        }
        public void stop() {
            running.set(false);
            RabbitMQConsumer.this.startConsumerCallable=null;
        }
        @Override
        public Void call() throws Exception {
            boolean connectionFailed=true;
            // Reconnection loop
            while (running.get() && connectionFailed) {
                try {
                    openConnectionAndChannel();
                    connectionFailed=false;
                } catch (Exception e) {
                    log.debug("Connection failed, will retry in "+connectionRetryInterval+"ms", e);
                    Thread.sleep(connectionRetryInterval);
                }
            }
            if (!connectionFailed) {
                addConsumer();
            }
            stop();
            return null;
        }
    }
}
