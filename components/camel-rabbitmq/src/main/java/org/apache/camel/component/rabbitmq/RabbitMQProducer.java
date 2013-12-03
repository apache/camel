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
import java.util.concurrent.ExecutorService;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class RabbitMQProducer extends DefaultProducer {

    private int closeTimeout = 30 * 1000;
    private Connection conn;
    private Channel channel;
    private ExecutorService executorService;
   
    public RabbitMQProducer(RabbitMQEndpoint endpoint) throws IOException {
        super(endpoint);
    }

    @Override
    public RabbitMQEndpoint getEndpoint() {
        return (RabbitMQEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        this.executorService = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "CamelRabbitMQProducer[" + getEndpoint().getQueue() + "]");

        log.trace("Creating connection...");
        this.conn = getEndpoint().connect(executorService);
        log.debug("Created connection: {}", conn);

        log.trace("Creating channel...");
        this.channel = conn.createChannel();
        log.debug("Created channel: {}", channel);
    }

    @Override
    protected void doStop() throws Exception {
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
        if (executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executorService);
            executorService = null;
        }
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String exchangeName = exchange.getIn().getHeader(RabbitMQConstants.EXCHANGE_NAME, String.class);
        // If it is BridgeEndpoint we should ignore the message header of EXCHANGE_NAME
        if (exchangeName == null || getEndpoint().isBridgeEndpoint()) {
            exchangeName = getEndpoint().getExchangeName();
        }
        String key = exchange.getIn().getHeader(RabbitMQConstants.ROUTING_KEY, null, String.class);
        // we just need to make sure RoutingKey option take effect
        if (key == null) {
            key = getEndpoint().getRoutingKey() == null ? "" : getEndpoint().getRoutingKey();
        }
        if (ObjectHelper.isEmpty(key) && ObjectHelper.isEmpty(exchangeName)) {
            throw new IllegalArgumentException("ExchangeName and RoutingKey is not provided in the endpoint: " + getEndpoint());
        }
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

    public int getCloseTimeout() {
        return closeTimeout;
    }

    public void setCloseTimeout(int closeTimeout) {
        this.closeTimeout = closeTimeout;
    }
}
