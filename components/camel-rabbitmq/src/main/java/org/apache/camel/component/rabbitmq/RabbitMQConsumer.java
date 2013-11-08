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
import java.util.HashMap;
import java.util.concurrent.ExecutorService;

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

    private final RabbitMQEndpoint endpoint;

    public RabbitMQConsumer(RabbitMQEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.info("Starting RabbitMQ consumer");

        executor = endpoint.createExecutor();
        log.debug("Using executor {}", executor);

        conn = endpoint.connect(executor);
        log.debug("Using conn {}", conn);

        channel = conn.createChannel();
        log.debug("Using channel {}", channel);

        channel.exchangeDeclare(endpoint.getExchangeName(),
                endpoint.getExchangeType(),
                endpoint.isDurable(),
                endpoint.isAutoDelete(),
                new HashMap<String, Object>());

        // need to make sure the queueDeclare is same with the exchange declare
        channel.queueDeclare(endpoint.getQueue(), endpoint.isDurable(), false,
                endpoint.isAutoDelete(), null);
        channel.queueBind(
                endpoint.getQueue(),
                endpoint.getExchangeName(),
                endpoint.getRoutingKey() == null ? "" : endpoint
                        .getRoutingKey());

        channel.basicConsume(endpoint.getQueue(), endpoint.isAutoAck(),
                new RabbitConsumer(this, channel));
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        log.info("Stopping RabbitMQ consumer");
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
                // ignored
            }
        }

        channel = null;
        conn = null;

        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;
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

}
