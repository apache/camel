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
                "direct",
                endpoint.isDurable(),
                endpoint.isAutoDelete(),
                new HashMap<String, Object>());
        
        // need to make sure the queueDeclare is same with the exchange declare
        channel.queueDeclare(endpoint.getQueue(), endpoint.isDurable(), false, endpoint.isAutoDelete(), null);
        channel.queueBind(endpoint.getQueue(), endpoint.getExchangeName(),
                endpoint.getRoutingKey() == null ? "" : endpoint.getRoutingKey());

        channel.basicConsume(endpoint.getQueue(), endpoint.isAutoAck(), new RabbitConsumer(this, channel));
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
         * Constructs a new instance and records its association to the passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        public RabbitConsumer(RabbitMQConsumer consumer, Channel channel) {
            super(channel);
            this.consumer = consumer;
            this.channel = channel;
        }

        @Override
        public void handleDelivery(String consumerTag,
                                   Envelope envelope,
                                   AMQP.BasicProperties properties,
                                   byte[] body) throws IOException {

            Exchange exchange = consumer.endpoint.createRabbitExchange(envelope, body);
            log.trace("Created exchange [exchange={}]", new Object[]{exchange});

            try {
                consumer.getProcessor().process(exchange);

                long deliveryTag = envelope.getDeliveryTag();
                log.trace("Acknowleding receipt [delivery_tag={}]", deliveryTag);
                channel.basicAck(deliveryTag, false);

            } catch (Exception e) {
                getExceptionHandler().handleException("Error processing exchange", exchange, e);
            }
        }
    }
}

