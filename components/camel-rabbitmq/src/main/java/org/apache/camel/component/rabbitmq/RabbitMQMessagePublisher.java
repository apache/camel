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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A method object for publishing to RabbitMQ
 */
public class RabbitMQMessagePublisher {
    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQMessagePublisher.class);
    private final Exchange camelExchange;
    private final Channel channel;
    private final String routingKey;
    private final RabbitMQEndpoint endpoint;
    private final Message message;

    public RabbitMQMessagePublisher(final Exchange camelExchange, final Channel channel, final String routingKey, final RabbitMQEndpoint endpoint) {
        this.camelExchange = camelExchange;
        this.channel = channel;
        this.routingKey = routingKey;
        this.endpoint = endpoint;
        this.message = resolveMessageFrom(camelExchange);
    }

    private Message resolveMessageFrom(final Exchange camelExchange) {
        Message message = camelExchange.hasOut() ? camelExchange.getOut() : camelExchange.getIn();

        // Remove the SERIALIZE_HEADER in case it was previously set
        if (message.getHeaders() != null && message.getHeaders().containsKey(RabbitMQEndpoint.SERIALIZE_HEADER)) {
            LOG.debug("Removing the {} header", RabbitMQEndpoint.SERIALIZE_HEADER);
            message.getHeaders().remove(RabbitMQEndpoint.SERIALIZE_HEADER);
        }
        
        return message;
    }

    public void publish() throws IOException {
        AMQP.BasicProperties properties;
        byte[] body;
        try {
            // To maintain backwards compatibility try the TypeConverter (The DefaultTypeConverter seems to only work on Strings)
            body = camelExchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, camelExchange, message.getBody());

            properties = endpoint.getMessageConverter().buildProperties(camelExchange).build();
        } catch (NoTypeConversionAvailableException | TypeConversionException e) {
            if (message.getBody() instanceof Serializable) {
                // Add the header so the reply processor knows to de-serialize it
                message.getHeaders().put(RabbitMQEndpoint.SERIALIZE_HEADER, true);
                properties = endpoint.getMessageConverter().buildProperties(camelExchange).build();
                body = serializeBodyFrom(message);
            } else if (message.getBody() == null) {
                properties = endpoint.getMessageConverter().buildProperties(camelExchange).build();
                body = null;
            } else {
                LOG.warn("Could not convert {} to byte[]", message.getBody());
                throw new RuntimeCamelException(e);
            }
        }
        
        publishToRabbitMQ(properties, body);
    }

    private void publishToRabbitMQ(final AMQP.BasicProperties properties, final byte[] body) throws IOException {
        String rabbitExchange = endpoint.getExchangeName(message);

        Boolean mandatory = camelExchange.getIn().getHeader(RabbitMQConstants.MANDATORY, endpoint.isMandatory(), Boolean.class);
        Boolean immediate = camelExchange.getIn().getHeader(RabbitMQConstants.IMMEDIATE, endpoint.isImmediate(), Boolean.class);

        LOG.debug("Sending message to exchange: {} with CorrelationId = {}", rabbitExchange, properties.getCorrelationId());

        if (endpoint.isPublisherAcknowledgements()) {
            channel.confirmSelect();
        }

        channel.basicPublish(rabbitExchange, routingKey, mandatory, immediate, properties, body);

        if (endpoint.isPublisherAcknowledgements()) {
            waitForConfirmation();
        }
    }

    private void waitForConfirmation() throws IOException {
        try {
            LOG.debug("Waiting for publisher acknowledgements for {}ms", endpoint.getPublisherAcknowledgementsTimeout());
            channel.waitForConfirmsOrDie(endpoint.getPublisherAcknowledgementsTimeout());
        } catch (InterruptedException | TimeoutException e) {
            LOG.warn("Acknowledgement error for {}", camelExchange);
            throw new RuntimeCamelException(e);
        }
    }

    private byte[] serializeBodyFrom(final Message msg) throws IOException {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(msg.getBody());
            return b.toByteArray();
        } catch (NotSerializableException nse) {
            LOG.warn("Can not send object " + msg.getBody().getClass() + " via RabbitMQ because it contains non-serializable objects.");
            throw new RuntimeCamelException(nse);
        }
    }
}
