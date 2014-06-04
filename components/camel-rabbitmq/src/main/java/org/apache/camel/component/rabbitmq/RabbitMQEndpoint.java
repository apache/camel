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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.TrustManager;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
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
    
    //Define will be used basicOqs
    private boolean prefetchEnabled = false;
    //Default in RabbitMq is 0.
    private Integer prefetchSize = 0;
    private Integer prefetchCount = 0;
    //Default vaule in RabbitMQ is false.
    private boolean prefetchGlobal = false;
    
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

    protected ConnectionFactory getOrCreateConnectionFactory() {
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
    
    public void setPrefetchSize(Integer prefetchSize) {
	this.prefetchSize = prefetchSize;
    }
    
    public Integer getPrefetchSize() {
	return prefetchSize;
    }
    
    public void setPrefetchCount(Integer prefetchCount) {
	this.prefetchCount = prefetchCount;
    }
    
    public Integer getPrefetchCount() {
	return prefetchCount;
    }
    
    public void setPrefetchGlobal(boolean prefetchGlobal) {
	this.prefetchGlobal = prefetchGlobal;
    }
    
    public boolean isPrefetchGlobal() {
	return prefetchGlobal;
    }
}
