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
package org.apache.camel.component.rabbitmq.reply;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.impl.recovery.AutorecoveringConnection;
import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link ReplyManager} when using temporary queues.
 */
public class TemporaryQueueReplyManager extends ReplyManagerSupport {

    private static final Logger LOG = LoggerFactory.getLogger(TemporaryQueueReplyManager.class);

    private RabbitConsumer consumer;

    public TemporaryQueueReplyManager(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected ReplyHandler createReplyHandler(ReplyManager replyManager, Exchange exchange, AsyncCallback callback, String originalCorrelationId, String correlationId,
                                              long requestTimeout) {
        return new TemporaryQueueReplyHandler(this, exchange, callback, originalCorrelationId, correlationId, requestTimeout);
    }

    @Override
    public void updateCorrelationId(String correlationId, String newCorrelationId, long requestTimeout) {
        LOG.trace("Updated provisional correlationId [{}] to expected correlationId [{}]", correlationId, newCorrelationId);

        ReplyHandler handler = correlation.remove(correlationId);
        if (handler != null) {
            correlation.put(newCorrelationId, handler, requestTimeout);
        }
    }

    @Override
    protected void handleReplyMessage(String correlationID, AMQP.BasicProperties properties, byte[] message) {
        ReplyHandler handler = correlation.get(correlationID);
        if (handler == null && endpoint.isUseMessageIDAsCorrelationID()) {
            handler = waitForProvisionCorrelationToBeUpdated(correlationID, message);
        }
        if (handler != null) {
            correlation.remove(correlationID);
            handler.onReply(correlationID, properties, message);
        } else {
            // we could not correlate the received reply message to a matching
            // request and therefore
            // we cannot continue routing the unknown message
            // log a warn and then ignore the message
            LOG.warn("Reply received for unknown correlationID [{}]. The message will be ignored: {}", correlationID, message);
        }
    }

    @Override
    protected Connection createListenerContainer() throws Exception {

        LOG.trace("Creating connection");
        Connection conn = endpoint.connect(executorService);

        LOG.trace("Creating channel");
        Channel channel = conn.createChannel();
        // setup the basicQos
        if (endpoint.isPrefetchEnabled()) {
            channel.basicQos(endpoint.getPrefetchSize(), endpoint.getPrefetchCount(), endpoint.isPrefetchGlobal());
        }

        // Let the server pick a random name for us
        DeclareOk result = channel.queueDeclare();
        LOG.debug("Using temporary queue name: {}", result.getQueue());
        setReplyTo(result.getQueue());

        // TODO check for the RabbitMQConstants.EXCHANGE_NAME header
        channel.queueBind(getReplyTo(), endpoint.getExchangeName(), getReplyTo());

        //Add QueueRecoveryListener to notify when temporary queue name changes due to recovery
        if (conn instanceof AutorecoveringConnection) {
            ((AutorecoveringConnection) conn).addQueueRecoveryListener((oldName, newName) -> {
                LOG.debug("Temporary queue name {} was changed to {}. Updating replyTo.", oldName, newName);
                setReplyTo(newName);

                LOG.debug("Trying to rebind the new temporary queue to update routingKey");
                try {
                    channel.queueBind(newName, endpoint.getExchangeName(), newName);
                    channel.queueUnbind(newName, endpoint.getExchangeName(), oldName);
                } catch (IOException e) {
                    LOG.warn("Failed to bind or unbind a queue. This exception is ignored.", e);
                }
            });
        }

        consumer = new RabbitConsumer(this, channel);
        consumer.start();

        return conn;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        consumer.stop();
    }

    // TODO combine with class in RabbitMQConsumer
    class RabbitConsumer extends com.rabbitmq.client.DefaultConsumer {

        private final TemporaryQueueReplyManager consumer;
        private final Channel channel;
        private String tag;

        /**
         * Constructs a new instance and records its association to the
         * passed-in channel.
         *
         * @param channel the channel to which this consumer is attached
         */
        RabbitConsumer(TemporaryQueueReplyManager consumer, Channel channel) {
            super(channel);
            this.consumer = consumer;
            this.channel = channel;
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            consumer.onMessage(properties, body);
        }

        /**
         * Bind consumer to channel
         */
        private void start() throws IOException {
            tag = channel.basicConsume(getReplyTo(), true, this);
        }

        /**
         * Unbind consumer from channel
         */
        private void stop() throws IOException, TimeoutException {
            if (channel.isOpen()) {
                if (tag != null) {
                    channel.basicCancel(tag);
                }
                channel.close();
            }
        }
    }

}
