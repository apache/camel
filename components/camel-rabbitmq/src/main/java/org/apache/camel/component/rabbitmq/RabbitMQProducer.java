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
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class RabbitMQProducer extends DefaultProducer {

    private final Connection conn;
    private final Channel channel;

    public RabbitMQProducer(RabbitMQEndpoint endpoint) throws IOException {
        super(endpoint);
        this.conn = endpoint.connect(Executors.newSingleThreadExecutor());
        this.channel = conn.createChannel();
    }

    @Override
    public RabbitMQEndpoint getEndpoint() {
        return (RabbitMQEndpoint) super.getEndpoint();
    }

    public void shutdown() throws IOException {
        conn.close();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String exchangeName = exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_NAME, String.class);
        if (exchangeName == null) {
            exchangeName = getEndpoint().getExchangeName();
        }
        if (ObjectHelper.isEmpty(exchangeName)) {
            throw new IllegalArgumentException("ExchangeName is not provided in header " + RabbitMQConstants.EXCHANGE_NAME);
        }

        String key = exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY, "", String.class);
        byte[] messageBodyBytes = exchange.getIn().getMandatoryBody(byte[].class);
        AMQP.BasicProperties.Builder properties = buildProperties(exchange);

        channel.basicPublish(exchangeName, key, properties.build(), messageBodyBytes);
    }

    AMQP.BasicProperties.Builder buildProperties(Exchange exchange) {
        AMQP.BasicProperties.Builder properties = new AMQP.BasicProperties.Builder();

        final Object contentType = exchange.getIn().getHeader(RabbitMQConstants.CONTENT_TYPE);
        if (contentType != null) {
            properties.contentType(contentType.toString());
        }
        
        final Object priority = exchange.getIn().getHeader(RabbitMQConstants.PRIORITY);
        if (priority != null) {
            properties.priority(Integer.parseInt(priority.toString()));
        }

        final Object messageId = exchange.getIn().getHeader(RabbitMQConstants.MESSAGE_ID);
        if (messageId != null) {
            properties.messageId(messageId.toString());
        }

        final Object clusterId = exchange.getIn().getHeader(RabbitMQConstants.CLUSTERID);
        if (clusterId != null) {
            properties.clusterId(clusterId.toString());
        }

        final Object replyTo = exchange.getIn().getHeader(RabbitMQConstants.REPLY_TO);
        if (replyTo != null) {
            properties.replyTo(replyTo.toString());
        }

        final Object correlationId = exchange.getIn().getHeader(RabbitMQConstants.CORRELATIONID);
        if (correlationId != null) {
            properties.correlationId(correlationId.toString());
        }

        final Object deliveryMode = exchange.getIn().getHeader(RabbitMQConstants.DELIVERY_MODE);
        if (deliveryMode != null) {
            properties.deliveryMode(Integer.parseInt(deliveryMode.toString()));
        }

        final Object userId = exchange.getIn().getHeader(RabbitMQConstants.USERID);
        if (userId != null) {
            properties.userId(userId.toString());
        }

        final Object type = exchange.getIn().getHeader(RabbitMQConstants.TYPE);
        if (type != null) {
            properties.type(type.toString());
        }

        final Object contentEncoding = exchange.getIn().getHeader(RabbitMQConstants.CONTENT_ENCODING);
        if (contentEncoding != null) {
            properties.contentEncoding(contentEncoding.toString());
        }

        final Object expiration = exchange.getIn().getHeader(RabbitMQConstants.EXPIRATION);
        if (expiration != null) {
            properties.expiration(expiration.toString());
        }

        final Object appId = exchange.getIn().getHeader(RabbitMQConstants.APP_ID);
        if (appId != null) {
            properties.appId(appId.toString());
        }

        final Object timestamp = exchange.getIn().getHeader(RabbitMQConstants.TIMESTAMP);
        if (timestamp != null) {
            properties.timestamp(new Date(Long.parseLong(timestamp.toString())));
        }

        final Map<String, Object> headers = exchange.getIn().getHeaders();
        Map<String, Object> filteredHeaders = new HashMap<String, Object>();

        // TODO: Add support for a HeaderFilterStrategy. See: org.apache.camel.component.jms.JmsBinding#shouldOutputHeader
        for (Map.Entry<String, Object> header : headers.entrySet()) {

            // filter header values.
            Object value = getValidRabbitMQHeaderValue(header.getValue());
            if (value != null) {
                filteredHeaders.put(header.getKey(), header.getValue());
            } else if (log.isDebugEnabled()) {
                log.debug("Ignoring header: {} of class: {} with value: {}",
                    new Object[]{header.getKey(), header.getValue().getClass().getName(), header.getValue()});
            }
        }

        properties.headers(filteredHeaders);

        return properties;
    }

    /**
     * Strategy to test if the given header is valid
     *
     * @param headerValue  the header value
     * @return  the value to use, <tt>null</tt> to ignore this header
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
        }
        return null;
    }
}
