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
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.TrustManager;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.LongString;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultMessage;

public class RabbitMQEndpoint extends DefaultEndpoint {

    private String username = ConnectionFactory.DEFAULT_USER;
    private String password = ConnectionFactory.DEFAULT_PASS;
    private String vhost = ConnectionFactory.DEFAULT_VHOST;
    private String hostname;
    private int threadPoolSize = 10;
    private int portNumber;
    private boolean autoAck = true;
    private boolean autoDelete = true;
    private boolean durable = true;
    private boolean bridgeEndpoint;
    private String queue = String.valueOf(UUID.randomUUID().toString().hashCode());
    private String exchangeName;
    private String exchangeType = "direct";
    private String routingKey;
    private Address[] addresses;
    private int connectionTimeout = ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT;
    private int requestedChannelMax = ConnectionFactory.DEFAULT_CHANNEL_MAX;
    private int requestedFrameMax = ConnectionFactory.DEFAULT_FRAME_MAX;
    private int requestedHeartbeat = ConnectionFactory.DEFAULT_HEARTBEAT;
    private String sslProtocol;
    private TrustManager trustManager;
    private Map<String, Object> clientProperties;
    private ConnectionFactory connectionFactory;
    private Boolean automaticRecoveryEnabled;
    private Integer networkRecoveryInterval;
    private Boolean topologyRecoveryEnabled;
    //If it is true, prefetchSize, prefetchCount, prefetchGlobal will be used for basicOqs before starting RabbitMQConsumer
    private boolean prefetchEnabled;
    //Default in RabbitMq is 0.
    private int prefetchSize;
    private int prefetchCount;
    //Default value in RabbitMQ is false.
    private boolean prefetchGlobal;
    /**
     * Number of concurrent consumer threads
     */
    private int concurrentConsumers = 1;
    
    //Declares a queue and exchange in RabbitMQ, then binds both.
    private boolean declare = true;    
    //Declare dead letter exchange.
    private String deadLetterExchange;
    //Declare dead letter routhing key.
    private String deadLetterRoutingKey;
    //Declare dead letter queue to declare.
    private String deadLetterQueue;
    //Dead letter exchange type.
    private String deadLetterExchangeType = "direct";
    //Maximum number of opened channel in pool
    private int channelPoolMaxSize = 10;
    //Maximum time (in milliseconds) waiting for channel
    private long channelPoolMaxWait = 1000;

    public RabbitMQEndpoint() {
    }

    public RabbitMQEndpoint(String endpointUri, RabbitMQComponent component) throws URISyntaxException {
        super(endpointUri, component);
    }

    public RabbitMQEndpoint(String endpointUri, RabbitMQComponent component, ConnectionFactory connectionFactory) throws URISyntaxException {
        super(endpointUri, component);
        this.connectionFactory = connectionFactory;
    }

    public Exchange createRabbitExchange(Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        Exchange exchange = new DefaultExchange(getCamelContext(), getExchangePattern());

        Message message = new DefaultMessage();
        exchange.setIn(message);

        message.setHeader(RabbitMQConstants.ROUTING_KEY, envelope.getRoutingKey());
        message.setHeader(RabbitMQConstants.EXCHANGE_NAME, envelope.getExchange());
        message.setHeader(RabbitMQConstants.DELIVERY_TAG, envelope.getDeliveryTag());

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

        message.setBody(body);

        return exchange;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Connection connect(ExecutorService executor) throws IOException {
        if (getAddresses() == null) {
            return getOrCreateConnectionFactory().newConnection(executor);
        } else {
            return getOrCreateConnectionFactory().newConnection(executor, getAddresses());
        }
    }

    /**
     * If needed, declare Exchange, declare Queue and bind them with Routing Key
     */
    public void declareExchangeAndQueue(Channel channel) throws IOException {
        HashMap<String, Object> queueArgs = null;
        if (deadLetterExchange != null) {
            queueArgs = new HashMap<String, Object>();
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_EXCHANGE, getDeadLetterExchange());
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_ROUTING_KEY, getDeadLetterRoutingKey());
            
            channel.exchangeDeclare(getDeadLetterExchange(),
                    getDeadLetterExchangeType(),
                    isDurable(),
                    isAutoDelete(),
                    new HashMap<String, Object>());
            channel.queueDeclare(getDeadLetterQueue(), isDurable(), false,
                    isAutoDelete(), null);
            channel.queueBind(
                    getDeadLetterQueue(),
                    getDeadLetterExchange(),
                    getDeadLetterRoutingKey() == null ? "" : getDeadLetterRoutingKey());
        }
        channel.exchangeDeclare(getExchangeName(),
                getExchangeType(),
                isDurable(),
                isAutoDelete(), new HashMap<String, Object>());
        if (getQueue() != null) {
            // need to make sure the queueDeclare is same with the exchange declare
            channel.queueDeclare(getQueue(), isDurable(), false,
                    isAutoDelete(), queueArgs);
            channel.queueBind(
                    getQueue(),
                    getExchangeName(),
                    getRoutingKey() == null ? "" : getRoutingKey());
        }
    }

    private ConnectionFactory getOrCreateConnectionFactory() {
        if (connectionFactory == null) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setUsername(getUsername());
            factory.setPassword(getPassword());
            factory.setVirtualHost(getVhost());
            factory.setHost(getHostname());
            factory.setPort(getPortNumber());
            if (getClientProperties() != null) {
                factory.setClientProperties(getClientProperties());
            }
            factory.setConnectionTimeout(getConnectionTimeout());
            factory.setRequestedChannelMax(getRequestedChannelMax());
            factory.setRequestedFrameMax(getRequestedFrameMax());
            factory.setRequestedHeartbeat(getRequestedHeartbeat());
            if (getSslProtocol() != null) {
                try {
                    if (getSslProtocol().equals("true")) {
                        factory.useSslProtocol();
                    } else if (getTrustManager() == null) {
                        factory.useSslProtocol(getSslProtocol());
                    } else {
                        factory.useSslProtocol(getSslProtocol(), getTrustManager());
                    }
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalArgumentException("Invalid sslProtocol " + sslProtocol, e);
                } catch (KeyManagementException e) {
                    throw new IllegalArgumentException("Invalid sslProtocol " + sslProtocol, e);
                }
            }
            if (getAutomaticRecoveryEnabled() != null) {
                factory.setAutomaticRecoveryEnabled(getAutomaticRecoveryEnabled());
            }
            if (getNetworkRecoveryInterval() != null) {
                factory.setNetworkRecoveryInterval(getNetworkRecoveryInterval());
            }
            if (getTopologyRecoveryEnabled() != null) {
                factory.setTopologyRecoveryEnabled(getTopologyRecoveryEnabled());
            }
            connectionFactory = factory;
        }
        return connectionFactory;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new RabbitMQProducer(this);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    protected ExecutorService createExecutor() {
        if (getCamelContext() != null) {
            return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "RabbitMQConsumer", getThreadPoolSize());
        } else {
            return Executors.newFixedThreadPool(getThreadPoolSize());
        }
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getVhost() {
        return vhost;
    }

    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getPortNumber() {
        return portNumber;
    }

    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public boolean isDurable() {
        return durable;
    }

    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public String getQueue() {
        return queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    public void setAddresses(String addresses) {
        Address[] addressArray = Address.parseAddresses(addresses);
        if (addressArray.length > 0) {
            this.addresses = addressArray;
        }
    }

    public Address[] getAddresses() {
        return addresses;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getRequestedChannelMax() {
        return requestedChannelMax;
    }

    public void setRequestedChannelMax(int requestedChannelMax) {
        this.requestedChannelMax = requestedChannelMax;
    }

    public int getRequestedFrameMax() {
        return requestedFrameMax;
    }

    public void setRequestedFrameMax(int requestedFrameMax) {
        this.requestedFrameMax = requestedFrameMax;
    }

    public int getRequestedHeartbeat() {
        return requestedHeartbeat;
    }

    public void setRequestedHeartbeat(int requestedHeartbeat) {
        this.requestedHeartbeat = requestedHeartbeat;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    public void setTrustManager(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public Map<String, Object> getClientProperties() {
        return clientProperties;
    }

    public void setClientProperties(Map<String, Object> clientProperties) {
        this.clientProperties = clientProperties;
    }

    public Boolean getAutomaticRecoveryEnabled() {
        return automaticRecoveryEnabled;
    }

    public void setAutomaticRecoveryEnabled(Boolean automaticRecoveryEnabled) {
        this.automaticRecoveryEnabled = automaticRecoveryEnabled;
    }

    public Integer getNetworkRecoveryInterval() {
        return networkRecoveryInterval;
    }

    public void setNetworkRecoveryInterval(Integer networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }

    public Boolean getTopologyRecoveryEnabled() {
        return topologyRecoveryEnabled;
    }

    public void setTopologyRecoveryEnabled(Boolean topologyRecoveryEnabled) {
        this.topologyRecoveryEnabled = topologyRecoveryEnabled;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    public void setPrefetchEnabled(boolean prefetchEnabled) {
        this.prefetchEnabled = prefetchEnabled;
    }

    public void setPrefetchSize(int prefetchSize) {
        this.prefetchSize = prefetchSize;
    }

    public int getPrefetchSize() {
        return prefetchSize;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchGlobal(boolean prefetchGlobal) {
        this.prefetchGlobal = prefetchGlobal;
    }

    public boolean isPrefetchGlobal() {
        return prefetchGlobal;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public boolean isDeclare() {
        return declare;
    }

    public void setDeclare(boolean declare) {
        this.declare = declare;
    }
    
    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public String getDeadLetterExchangeType() {
        return deadLetterExchangeType;
    }

    public void setDeadLetterExchangeType(String deadLetterExchangeType) {
        this.deadLetterExchangeType = deadLetterExchangeType;
    }

    /**
     * Get maximum number of opened channel in pool
     *
     * @return Maximum number of opened channel in pool
     */
    public int getChannelPoolMaxSize() {
        return channelPoolMaxSize;
    }

    /**
     * Set maximum number of opened channel in pool
     *
     * @param channelPoolMaxSize Maximum number of opened channel in pool
     */
    public void setChannelPoolMaxSize(int channelPoolMaxSize) {
        this.channelPoolMaxSize = channelPoolMaxSize;
    }

    /**
     * Get the maximum number of milliseconds to wait for a channel from the pool
     *
     * @return Maximum number of milliseconds waiting for a channel
     */
    public long getChannelPoolMaxWait() {
        return channelPoolMaxWait;
    }

    /**
     * Set the maximum number of milliseconds to wait for a channel from the pool
     *
     * @param channelPoolMaxWait Maximum number of milliseconds waiting for a channel
     */
    public void setChannelPoolMaxWait(long channelPoolMaxWait) {
        this.channelPoolMaxWait = channelPoolMaxWait;
    }
}
