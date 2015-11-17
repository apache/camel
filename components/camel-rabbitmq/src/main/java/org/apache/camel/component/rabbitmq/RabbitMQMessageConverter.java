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

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMQMessageConverter {
    protected static final Logger LOG = LoggerFactory.getLogger(RabbitMQMessageConverter.class);

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
    }

    public AMQP.BasicProperties.Builder buildProperties(Exchange exchange) {
        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();

        Message msg;
        if (exchange.hasOut()) {
            msg = exchange.getOut();
        } else {
            msg = exchange.getIn();
        }

        final Object contentType = msg.removeHeader(RabbitMQConstants.CONTENT_TYPE);
        if (contentType != null) {
            properties.contentType(contentType.toString());
        }

        final Object priority = msg.removeHeader(RabbitMQConstants.PRIORITY);
        if (priority != null) {
            properties.priority(Integer.parseInt(priority.toString()));
        }

        final Object messageId = msg.removeHeader(RabbitMQConstants.MESSAGE_ID);
        if (messageId != null) {
            properties.messageId(messageId.toString());
        }

        final Object clusterId = msg.removeHeader(RabbitMQConstants.CLUSTERID);
        if (clusterId != null) {
            properties.clusterId(clusterId.toString());
        }

        final Object replyTo = msg.removeHeader(RabbitMQConstants.REPLY_TO);
        if (replyTo != null) {
            properties.replyTo(replyTo.toString());
        }

        final Object correlationId = msg.removeHeader(RabbitMQConstants.CORRELATIONID);
        if (correlationId != null) {
            properties.correlationId(correlationId.toString());
        }

        final Object deliveryMode = msg.removeHeader(RabbitMQConstants.DELIVERY_MODE);
        if (deliveryMode != null) {
            properties.deliveryMode(Integer.parseInt(deliveryMode.toString()));
        }

        final Object userId = msg.removeHeader(RabbitMQConstants.USERID);
        if (userId != null) {
            properties.userId(userId.toString());
        }

        final Object type = msg.removeHeader(RabbitMQConstants.TYPE);
        if (type != null) {
            properties.type(type.toString());
        }

        final Object contentEncoding = msg.removeHeader(RabbitMQConstants.CONTENT_ENCODING);
        if (contentEncoding != null) {
            properties.contentEncoding(contentEncoding.toString());
        }

        final Object expiration = msg.removeHeader(RabbitMQConstants.EXPIRATION);
        if (expiration != null) {
            properties.expiration(expiration.toString());
        }

        final Object appId = msg.removeHeader(RabbitMQConstants.APP_ID);
        if (appId != null) {
            properties.appId(appId.toString());
        }

        final Object timestamp = msg.removeHeader(RabbitMQConstants.TIMESTAMP);
        if (timestamp != null) {
            properties.timestamp(new Date(Long.parseLong(timestamp.toString())));
        }

        final Map<String, Object> headers = msg.getHeaders();
        Map<String, Object> filteredHeaders = new HashMap<String, Object>();

        // TODO: Add support for a HeaderFilterStrategy. See: org.apache.camel.component.jms.JmsBinding#shouldOutputHeader
        for (Map.Entry<String, Object> header : headers.entrySet()) {
            // filter header values.
            Object value = getValidRabbitMQHeaderValue(header.getValue());
            if (value != null) {
                filteredHeaders.put(header.getKey(), header.getValue());
            } else if (LOG.isDebugEnabled()) {
                if (header.getValue() == null) {
                    LOG.debug("Ignoring header: {} with null value", header.getKey());
                } else {
                    LOG.debug("Ignoring header: {} of class: {} with value: {}",
                                    new Object[] {
                                                    header.getKey(), ObjectHelper.classCanonicalName(header.getValue()), header.getValue()
                                    });
                }
            }
        }

        properties.headers(filteredHeaders);

        return properties;
    }

    /**
     * Strategy to test if the given header is valid. Without this, the
     * com.rabbitmq.client.impl.Frame.java class will throw an
     * IllegalArgumentException (invalid value in table) and close the
     * connection.
     *
     * @param headerValue the header value
     * @return the value to use, <tt>null</tt> to ignore this header
     * @see com.rabbitmq.client.impl.Frame#fieldValueSize
     */
    private Object getValidRabbitMQHeaderValue(Object headerValue) {
        if (headerValue instanceof String) {
            return headerValue;
        } else if (headerValue instanceof BigDecimal) {
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
        } else if (headerValue instanceof Timestamp) {
            return headerValue;
        } else if (headerValue instanceof Byte) {
            return headerValue;
        } else if (headerValue instanceof Double) {
            return headerValue;
        } else if (headerValue instanceof Float) {
            return headerValue;
        } else if (headerValue instanceof Long) {
            return headerValue;
        }

        return null;
    }
}
