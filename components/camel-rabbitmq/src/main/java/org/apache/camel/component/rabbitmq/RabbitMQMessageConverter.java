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
package org.apache.camel.component.rabbitmq;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.LongString;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQMessageConverter {
    protected static final Logger LOG = LoggerFactory.getLogger(RabbitMQMessageConverter.class);

    private boolean allowNullHeaders;
    private boolean allowCustomHeaders;
    private Map<String, Object> additionalHeaders;
    private Map<String, Object> additionalProperties;
    private final HeaderFilterStrategy headerFilterStrategy = new RabbitMQHeaderFilterStrategy();

    /**
     * Will take an {@link Exchange} and add header values back to the {@link Exchange#getIn()}
     */
    public void mergeAmqpProperties(Exchange exchange, AMQP.BasicProperties properties) {

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
        if (properties.getDeliveryMode() != null) {
            exchange.getIn().setHeader(RabbitMQConstants.DELIVERY_MODE, properties.getDeliveryMode());
        }
    }

    public AMQP.BasicProperties.Builder buildProperties(Exchange exchange) {
        Message msg = exchange.getMessage();

        AMQP.BasicProperties.Builder properties = buildBasicAmqpProperties(exchange.getProperties(), msg);

        final Map<String, Object> headers = properties.build().getHeaders();
        // Add additional headers (if any)
        if (additionalHeaders != null) {
            headers.putAll(additionalHeaders);
        }
        Map<String, Object> filteredHeaders = new HashMap<>();

        for (Map.Entry<String, Object> header : headers.entrySet()) {
            // filter header values.
            Object value = getValidRabbitMQHeaderValue(header.getKey(), header.getValue());

            // additionally filter out the OVERRIDE header so it does not
            // propagate
            if ((value != null || isAllowNullHeaders()) && !header.getKey()
                    .equals(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME)) {
                boolean filteredHeader;
                if (!allowCustomHeaders) {
                    filteredHeader = headerFilterStrategy
                            .applyFilterToCamelHeaders(header.getKey(), header.getValue(), exchange);
                    if (filteredHeader) {
                        filteredHeaders.put(header.getKey(), header.getValue());
                    }
                } else {
                    filteredHeaders.put(header.getKey(), header.getValue());
                }
            } else if (LOG.isDebugEnabled()) {
                if (header.getValue() == null) {
                    LOG.debug("Ignoring header: {} with null value", header.getKey());
                } else if (header.getKey().equals(RabbitMQConstants.EXCHANGE_OVERRIDE_NAME)) {
                    LOG.debug("Preventing header propagation: {} with value {}:", header.getKey(),
                            header.getValue());
                } else {
                    LOG.debug("Ignoring header: {} of class: {} with value: {}", header.getKey(),
                            ObjectHelper.classCanonicalName(header.getValue()), header.getValue());
                }
            }
        }

        properties.headers(filteredHeaders);

        return properties;
    }

    private AMQP.BasicProperties.Builder buildBasicAmqpProperties(
            Map<String, Object> exchangeProperties, Message msg) {
        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();

        final Map<String, Object> headers = new HashMap<>(msg.getHeaders()); // We don't want to mutate the message headers
        final Object contentType = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.CONTENT_TYPE);
        if (contentType != null) {
            properties.contentType(contentType.toString());
        }

        final Object priority = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.PRIORITY);
        if (priority != null) {
            properties.priority(Integer.parseInt(priority.toString()));
        }

        final Object messageId = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.MESSAGE_ID);
        if (messageId != null) {
            properties.messageId(messageId.toString());
        }

        final Object clusterId = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.CLUSTERID);
        if (clusterId != null) {
            properties.clusterId(clusterId.toString());
        }

        final Object replyTo = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.REPLY_TO);
        if (replyTo != null) {
            properties.replyTo(replyTo.toString());
        }

        final Object correlationId = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.CORRELATIONID);
        if (correlationId != null) {
            properties.correlationId(correlationId.toString());
        }

        final Object deliveryMode = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.DELIVERY_MODE);
        if (deliveryMode != null) {
            properties.deliveryMode(Integer.parseInt(deliveryMode.toString()));
        }

        final Object userId = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.USERID);
        if (userId != null) {
            properties.userId(userId.toString());
        }

        final Object type = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.TYPE);
        if (type != null) {
            properties.type(type.toString());
        }

        final Object contentEncoding = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.CONTENT_ENCODING);
        if (contentEncoding != null) {
            properties.contentEncoding(contentEncoding.toString());
        }

        final Object expiration = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.EXPIRATION);
        if (expiration != null) {
            properties.expiration(expiration.toString());
        }

        final Object appId = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.APP_ID);
        if (appId != null) {
            properties.appId(appId.toString());
        }

        final Object timestamp = getBasicAmqpProperty(exchangeProperties, headers, RabbitMQConstants.TIMESTAMP);
        if (timestamp != null) {
            properties.timestamp(convertTimestamp(timestamp));
        }

        String ignoredProperties = exchangeProperties.keySet().stream()
                .filter(key -> !RabbitMQConstants.BASIC_AMQP_PROPERTIES.contains(key))
                .collect(Collectors.joining(", "));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Added AMQP basic properties: {}", properties.build());
            LOG.debug("Ignoring non-AMQP basic properties: {}", ignoredProperties);
        }

        properties.headers(headers);

        return properties;
    }

    private Object getBasicAmqpProperty(
            Map<String, Object> exchangeProperties, Map<String, Object> headers,
            String propertyKey) {
        boolean hasAdditionalProps = additionalProperties != null && !additionalProperties
                .isEmpty();
        Object object = headers.remove(propertyKey);

        if (exchangeProperties.containsKey(propertyKey)) {
            object = exchangeProperties.get(propertyKey);
        }
        if (object == null && hasAdditionalProps) {
            object = additionalProperties.get(propertyKey);
        }
        return object;
    }

    private Date convertTimestamp(Object timestamp) {
        if (timestamp instanceof Date) {
            return (Date) timestamp;
        }
        return new Date(Long.parseLong(timestamp.toString()));
    }

    /**
     * Strategy to test if the given header is valid. Without this, the com.rabbitmq.client.impl.Frame.java class will
     * throw an IllegalArgumentException (invalid value in table) and close the connection.
     *
     * @param  headerValue the header value
     * @return             the value to use, <tt>null</tt> to ignore this header
     * @see                com.rabbitmq.client.impl.Frame#fieldValueSize
     */
    private Object getValidRabbitMQHeaderValue(String headerKey, Object headerValue) {
        // accept all x- headers
        if (headerKey.startsWith("x-") || headerKey.startsWith("X-")) {
            return headerKey;
        }

        if (headerValue instanceof String) {
            return headerValue;
        } else if (headerValue instanceof Number) {
            return headerValue;
        } else if (headerValue instanceof Boolean) {
            return headerValue;
        } else if (headerValue instanceof Date) {
            return headerValue;
        } else if (headerValue instanceof byte[]) {
            return headerValue;
        } else if (headerValue instanceof LongString) {
            return headerValue;
        } else if (headerValue instanceof Map) {
            return headerValue;
        } else if (headerValue instanceof List) {
            return headerValue;
        }

        return null;
    }

    public void populateRabbitExchange(
            Exchange camelExchange, Envelope envelope, AMQP.BasicProperties properties, byte[] body, final boolean out,
            final boolean allowMessageBodySerialization) {
        Message message = resolveMessageFrom(camelExchange, out);
        populateMessageHeaders(message, envelope, properties);
        populateMessageBody(message, camelExchange, properties, body, allowMessageBodySerialization);
    }

    private Message resolveMessageFrom(final Exchange camelExchange, final boolean out) {
        Message message;
        if (out) {
            // use OUT message
            message = camelExchange.getOut();
        } else {
            if (camelExchange.getIn() != null) {
                // Use the existing message so we keep the headers
                message = camelExchange.getIn();
            } else {
                message = new DefaultMessage(camelExchange.getContext());
                camelExchange.setIn(message);
            }
        }
        return message;
    }

    private void populateMessageHeaders(final Message message, final Envelope envelope, final AMQP.BasicProperties properties) {
        populateRoutingInfoHeaders(message, envelope);
        populateMessageHeadersFromRabbitMQHeaders(message, properties);
    }

    private void populateRoutingInfoHeaders(final Message message, final Envelope envelope) {
        if (envelope != null) {
            message.setHeader(RabbitMQConstants.ROUTING_KEY, envelope.getRoutingKey());
            message.setHeader(RabbitMQConstants.EXCHANGE_NAME, envelope.getExchange());
            message.setHeader(RabbitMQConstants.DELIVERY_TAG, envelope.getDeliveryTag());
            message.setHeader(RabbitMQConstants.REDELIVERY_TAG, envelope.isRedeliver());
        }
    }

    private void populateMessageHeadersFromRabbitMQHeaders(final Message message, final AMQP.BasicProperties properties) {
        Map<String, Object> headers = properties.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                // Convert LongStrings to String.
                if (entry.getValue() instanceof LongString) {
                    message.setHeader(entry.getKey(), entry.getValue().toString());
                } else {
                    message.setHeader(entry.getKey(), entry.getValue());
                }
            }
        }
        if (properties.getTimestamp() != null) {
            message.setHeader(RabbitMQConstants.MESSAGE_TIMESTAMP, properties.getTimestamp().getTime());
        }
    }

    private void populateMessageBody(
            final Message message, final Exchange camelExchange, final AMQP.BasicProperties properties, final byte[] body,
            final boolean allowMessageBodySerialization) {
        if (allowMessageBodySerialization && hasSerializeHeader(properties)) {
            deserializeBody(camelExchange, message, body);
        } else {
            // Set the body as a byte[] and let the type converter deal with it
            message.setBody(body);
        }
    }

    private void deserializeBody(final Exchange camelExchange, final Message message, final byte[] body) {
        Object messageBody = null;
        try (InputStream b = new ByteArrayInputStream(body); ObjectInputStream o = new ObjectInputStream(b)) {
            messageBody = o.readObject();
        } catch (IOException | ClassNotFoundException e) {
            LOG.warn("Could not deserialize the object");
            camelExchange.setException(e);
        }
        if (messageBody instanceof Throwable) {
            LOG.debug("Reply was an Exception. Setting the Exception on the Exchange");
            camelExchange.setException((Throwable) messageBody);
        } else {
            message.setBody(messageBody);
        }
    }

    private boolean hasSerializeHeader(AMQP.BasicProperties properties) {
        return hasHeaders(properties) && Boolean.TRUE.equals(isSerializeHeaderEnabled(properties));
    }

    private boolean hasHeaders(final AMQP.BasicProperties properties) {
        return properties != null && properties.getHeaders() != null;
    }

    private Object isSerializeHeaderEnabled(final AMQP.BasicProperties properties) {
        return properties.getHeaders().get(RabbitMQEndpoint.SERIALIZE_HEADER);
    }

    public boolean isAllowNullHeaders() {
        return allowNullHeaders;
    }

    public void setAllowNullHeaders(boolean allowNullHeaders) {
        this.allowNullHeaders = allowNullHeaders;
    }

    public boolean isAllowCustomHeaders() {
        return allowCustomHeaders;
    }

    public void setAllowCustomHeaders(boolean allowCustomHeaders) {
        this.allowCustomHeaders = allowCustomHeaders;
    }

    public void setAdditionalHeaders(Map<String, Object> additionalHeaders) {
        this.additionalHeaders = additionalHeaders;
    }

    public Map<String, Object> getAdditionalHeaders() {
        return additionalHeaders;
    }

    public void setAdditionalProperties(
            Map<String, Object> additionalProperties) {
        this.additionalProperties = additionalProperties;
    }

    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }
}
