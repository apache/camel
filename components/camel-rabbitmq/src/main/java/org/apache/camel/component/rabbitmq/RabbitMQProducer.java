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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.camel.component.rabbitmq.pool.PoolableChannelFactory;
import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

public class RabbitMQProducer extends DefaultProducer {

    private int closeTimeout = 30 * 1000;
    private Connection conn;
    /**
     * Maximum number of opened channel in pool
     */
    private int channelPoolMaxSize = 10;
    /**
    * Maximum time (in milliseconds) waiting for channel
    */
    private long channelPoolMaxWait = 1000;
    private ObjectPool<Channel> channelPool;
    private ExecutorService executorService;
    public RabbitMQProducer(RabbitMQEndpoint endpoint) throws IOException {
        super(endpoint);
    }

    @Override
    public RabbitMQEndpoint getEndpoint() {
        return (RabbitMQEndpoint) super.getEndpoint();
    }
    /**
     * Channel callback (similar to Spring JDBC ConnectionCallback)
     */
    private static interface ChannelCallback<T> {
        public T doWithChannel(Channel channel) throws Exception;
    }
    /**
     * Do something with a pooled channel (similar to Spring JDBC TransactionTemplate#execute)
     */
    private <T> T execute(ChannelCallback<T> callback) throws Exception {
        Channel channel = channelPool.borrowObject();
        try {
            return callback.doWithChannel(channel);
        } finally {
            channelPool.returnObject(channel);
        }
    }
    /**
     * Open connection and initialize channel pool
     */
    private void openConnectionAndChannelPool() throws Exception {
        log.trace("Creating connection...");
        this.conn = getEndpoint().connect(executorService);
        log.debug("Created connection: {}", conn);

        log.trace("Creating channel pool...");
        channelPool = new GenericObjectPool<>(new PoolableChannelFactory(this.conn), getChannelPoolMaxSize(), GenericObjectPool.WHEN_EXHAUSTED_BLOCK, getChannelPoolMaxWait());
        if (getEndpoint().isDeclare()) {
            execute(new ChannelCallback<Void>() {
                @Override
                public Void doWithChannel(Channel channel) throws Exception {
                    getEndpoint().declareExchangeAndQueue(channel);
                    return null;
                }
            });
        }
    }

    @Override
    protected void doStart() throws Exception {
        this.executorService = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "CamelRabbitMQProducer[" + getEndpoint().getQueue() + "]");

        try {
            openConnectionAndChannelPool();
        } catch (IOException e) {
            log.warn("Failed to create connection", e);
        }
    }

    /**
     * If needed, close Connection and Channel
     */
    private void closeConnectionAndChannel() throws Exception {
        channelPool.close();
        if (conn != null) {
            log.debug("Closing connection: {} with timeout: {} ms.", conn, closeTimeout);
            conn.close(closeTimeout);
            conn = null;
        }
    }

    @Override
    protected void doStop() throws Exception {
        closeConnectionAndChannel();
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
        // we just need to make sure RoutingKey option take effect if it is not BridgeEndpoint
        if (key == null || getEndpoint().isBridgeEndpoint()) {
            key = getEndpoint().getRoutingKey() == null ? "" : getEndpoint().getRoutingKey();
        }
        if (ObjectHelper.isEmpty(key) && ObjectHelper.isEmpty(exchangeName)) {
            throw new IllegalArgumentException("ExchangeName and RoutingKey is not provided in the endpoint: " + getEndpoint());
        }
        byte[] messageBodyBytes = exchange.getIn().getMandatoryBody(byte[].class);
        AMQP.BasicProperties properties = buildProperties(exchange).build();

        basicPublish(exchangeName, key, properties, messageBodyBytes);
    }

    /**
     * Send a message borrowing a channel from the pool
     * @param exchange Target exchange
     * @param routingKey Routing key
     * @param properties Header properties
     * @param body Body content
     */
    private void basicPublish(final String exchange, final String routingKey, final AMQP.BasicProperties properties, final byte[] body) throws Exception {
        if (channelPool==null) {
            // Open connection and channel lazily
            openConnectionAndChannelPool();
        }
        execute(new ChannelCallback<Void>() {
            @Override
            public Void doWithChannel(Channel channel) throws Exception {
                channel.basicPublish(exchange, routingKey, properties, body);
                return null;
            }
        });
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
                if (header.getValue() == null) {
                    log.debug("Ignoring header: {} with null value", header.getKey());
                } else {
                    log.debug("Ignoring header: {} of class: {} with value: {}",
                            new Object[]{header.getKey(), ObjectHelper.classCanonicalName(header.getValue()), header.getValue()});
                }
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

    /**
     * Get maximum number of opened channel in pool
     * @return Maximum number of opened channel in pool
     */
    public int getChannelPoolMaxSize() {
            return channelPoolMaxSize;
    }

    /**
     * Set maximum number of opened channel in pool
     * @param channelPoolMaxSize Maximum number of opened channel in pool
     */
    public void setChannelPoolMaxSize(int channelPoolMaxSize) {
            this.channelPoolMaxSize = channelPoolMaxSize;
    }

    /**
     * Get the maximum number of milliseconds to wait for a channel from the pool
     * @return Maximum number of milliseconds waiting for a channel
     */
    public long getChannelPoolMaxWait() {
            return channelPoolMaxWait;
    }

    /**
     * Set the maximum number of milliseconds to wait for a channel from the pool
     * @param channelPoolMaxWait Maximum number of milliseconds waiting for a channel
     */
    public void setChannelPoolMaxWait(long channelPoolMaxWait) {
            this.channelPoolMaxWait = channelPoolMaxWait;
    }
}
