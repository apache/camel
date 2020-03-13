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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.TrustManager;

import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.ExceptionHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("rabbitmq")
public class RabbitMQComponent extends DefaultComponent {

    public static final String ARG_PREFIX = "arg.";
    public static final String EXCHANGE_ARG_PREFIX = "exchange.";
    public static final String QUEUE_ARG_PREFIX = "queue.";
    public static final String BINDING_ARG_PREFIX = "binding.";

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQComponent.class);

    @Metadata(label = "common")
    private String hostname;
    @Metadata(label = "common", defaultValue = "5672")
    private int portNumber;
    @Metadata(label = "security", defaultValue = ConnectionFactory.DEFAULT_USER, secret = true)
    private String username = ConnectionFactory.DEFAULT_USER;
    @Metadata(label = "security", defaultValue = ConnectionFactory.DEFAULT_PASS, secret = true)
    private String password = ConnectionFactory.DEFAULT_PASS;
    @Metadata(label = "common", defaultValue = ConnectionFactory.DEFAULT_VHOST)
    private String vhost = ConnectionFactory.DEFAULT_VHOST;
    @Metadata(label = "common")
    private String addresses;
    @Metadata(label = "common")
    private ConnectionFactory connectionFactory;
    @Metadata(label = "consumer", defaultValue = "true")
    private boolean autoAck = true;
    @Metadata(label = "common", defaultValue = "true")
    private boolean autoDelete = true;
    @Metadata(label = "common", defaultValue = "true")
    private boolean durable = true;
    @Metadata(label = "consumer")
    private boolean exclusiveConsumer;
    @Metadata(label = "common")
    private boolean exclusive;
    @Metadata(label = "common")
    private boolean passive;
    @Metadata(label = "common", defaultValue = "true")
    private boolean declare = true;
    @Metadata(label = "common")
    private boolean skipQueueDeclare;
    @Metadata(label = "common")
    private boolean skipQueueBind;
    @Metadata(label = "common")
    private boolean skipExchangeDeclare;
    @Metadata(label = "common")
    private String deadLetterExchange;
    @Metadata(label = "common")
    private String deadLetterRoutingKey;
    @Metadata(label = "common")
    private String deadLetterQueue;
    @Metadata(label = "common", defaultValue = "direct", enums = "direct,fanout,headers,topic")
    private String deadLetterExchangeType = "direct";
    @Metadata(label = "producer")
    private boolean allowNullHeaders;
    @Metadata(label = "security")
    private String sslProtocol;
    @Metadata(label = "security")
    private TrustManager trustManager;
    @Metadata(label = "consumer,advanced", defaultValue = "10")
    private int threadPoolSize = 10;
    @Metadata(label = "advanced", defaultValue = "true")
    private boolean autoDetectConnectionFactory = true;
    @Metadata(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT)
    private int connectionTimeout = ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT;
    @Metadata(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_CHANNEL_MAX)
    private int requestedChannelMax = ConnectionFactory.DEFAULT_CHANNEL_MAX;
    @Metadata(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_FRAME_MAX)
    private int requestedFrameMax = ConnectionFactory.DEFAULT_FRAME_MAX;
    @Metadata(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_HEARTBEAT)
    private int requestedHeartbeat = ConnectionFactory.DEFAULT_HEARTBEAT;
    @Metadata(label = "advanced")
    private Boolean automaticRecoveryEnabled;
    @Metadata(label = "advanced", defaultValue = "5000")
    private Integer networkRecoveryInterval = 5000;
    @Metadata(label = "advanced")
    private Boolean topologyRecoveryEnabled;
    @Metadata(label = "consumer")
    private boolean prefetchEnabled;
    @Metadata(label = "consumer")
    private int prefetchSize;
    @Metadata(label = "consumer")
    private int prefetchCount;
    @Metadata(label = "consumer")
    private boolean prefetchGlobal;
    @Metadata(label = "producer", defaultValue = "10")
    private int channelPoolMaxSize = 10;
    @Metadata(label = "producer", defaultValue = "1000")
    private long channelPoolMaxWait = 1000;
    @Metadata(label = "advanced", defaultValue = "20000")
    private long requestTimeout = 20000;
    @Metadata(label = "advanced", defaultValue = "1000")
    private long requestTimeoutCheckerInterval = 1000;
    @Metadata(label = "advanced")
    private boolean transferException;
    @Metadata(label = "producer")
    private boolean mandatory;
    @Metadata(label = "producer")
    private boolean immediate;
    @Metadata(label = "producer")
    private boolean publisherAcknowledgements;
    @Metadata(label = "producer")
    private long publisherAcknowledgementsTimeout;
    @Metadata(label = "producer")
    private boolean guaranteedDeliveries;
    @Metadata(label = "advanced")
    private Map<String, Object> args;
    @Metadata(label = "advanced")
    private Map<String, Object> clientProperties;
    @Metadata(label = "advanced")
    private ExceptionHandler connectionFactoryExceptionHandler;

    public RabbitMQComponent() {
    }

    public RabbitMQComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected RabbitMQEndpoint createEndpoint(String uri, String remaining, Map<String, Object> params) throws Exception {

        String host = getHostname();
        int port = getPortNumber();
        String exchangeName = remaining;

        if (remaining.contains(":") || remaining.contains("/")) {
            LOG.warn("The old syntax rabbitmq://hostname:port/exchangeName is deprecated. You should configure the hostname on the component or ConnectionFactory");
            try {
                URI u = new URI("http://" + remaining);
                host = u.getHost();
                port = u.getPort();
                if (u.getPath().trim().length() > 1) {
                    exchangeName = u.getPath().substring(1);
                } else {
                    exchangeName = "";
                }
            } catch (Throwable e) {
                // ignore
            }
        }

        // ConnectionFactory reference
        ConnectionFactory connectionFactory = resolveAndRemoveReferenceParameter(params, "connectionFactory", ConnectionFactory.class, getConnectionFactory());

        // try to lookup if there is a single instance in the registry of the
        // ConnectionFactory
        if (connectionFactory == null && isAutoDetectConnectionFactory()) {
            Map<String, ConnectionFactory> map = getCamelContext().getRegistry().findByTypeWithName(ConnectionFactory.class);
            if (map != null && map.size() == 1) {
                Map.Entry<String, ConnectionFactory> entry = map.entrySet().iterator().next();
                connectionFactory = entry.getValue();
                String name = entry.getKey();
                if (name == null) {
                    name = "anonymous";
                }
                LOG.info("Auto-detected single instance: {} of type ConnectionFactory in Registry to be used as ConnectionFactory when creating endpoint: {}", name, uri);
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> clientProperties = resolveAndRemoveReferenceParameter(params, "clientProperties", Map.class, getClientProperties());
        TrustManager trustManager = resolveAndRemoveReferenceParameter(params, "trustManager", TrustManager.class, getTrustManager());
        RabbitMQEndpoint endpoint;
        if (connectionFactory == null) {
            endpoint = new RabbitMQEndpoint(uri, this);
        } else {
            endpoint = new RabbitMQEndpoint(uri, this, connectionFactory);
        }
        endpoint.setHostname(host);
        endpoint.setPortNumber(port);
        endpoint.setUsername(getUsername());
        endpoint.setPassword(getPassword());
        endpoint.setVhost(getVhost());
        endpoint.setAddresses(getAddresses());
        endpoint.setThreadPoolSize(getThreadPoolSize());
        endpoint.setExchangeName(exchangeName);
        endpoint.setClientProperties(clientProperties);
        endpoint.setSslProtocol(getSslProtocol());
        endpoint.setTrustManager(trustManager);
        endpoint.setConnectionTimeout(getConnectionTimeout());
        endpoint.setRequestedChannelMax(getRequestedChannelMax());
        endpoint.setRequestedFrameMax(getRequestedFrameMax());
        endpoint.setRequestedHeartbeat(getRequestedHeartbeat());
        endpoint.setAutomaticRecoveryEnabled(getAutomaticRecoveryEnabled());
        endpoint.setNetworkRecoveryInterval(getNetworkRecoveryInterval());
        endpoint.setTopologyRecoveryEnabled(getTopologyRecoveryEnabled());
        endpoint.setPrefetchEnabled(isPrefetchEnabled());
        endpoint.setPrefetchSize(getPrefetchSize());
        endpoint.setPrefetchCount(getPrefetchCount());
        endpoint.setPrefetchGlobal(isPrefetchGlobal());
        endpoint.setChannelPoolMaxSize(getChannelPoolMaxSize());
        endpoint.setChannelPoolMaxWait(getChannelPoolMaxWait());
        endpoint.setRequestTimeout(getRequestTimeout());
        endpoint.setRequestTimeoutCheckerInterval(getRequestTimeoutCheckerInterval());
        endpoint.setTransferException(isTransferException());
        endpoint.setPublisherAcknowledgements(isPublisherAcknowledgements());
        endpoint.setPublisherAcknowledgementsTimeout(getPublisherAcknowledgementsTimeout());
        endpoint.setGuaranteedDeliveries(isGuaranteedDeliveries());
        endpoint.setMandatory(isMandatory());
        endpoint.setImmediate(isImmediate());
        endpoint.setAutoAck(isAutoAck());
        endpoint.setAutoDelete(isAutoDelete());
        endpoint.setDurable(isDurable());
        endpoint.setExclusive(isExclusive());
        endpoint.setExclusiveConsumer(isExclusiveConsumer());
        endpoint.setPassive(isPassive());
        endpoint.setSkipExchangeDeclare(isSkipExchangeDeclare());
        endpoint.setSkipQueueBind(isSkipQueueBind());
        endpoint.setSkipQueueDeclare(isSkipQueueDeclare());
        endpoint.setDeclare(isDeclare());
        endpoint.setDeadLetterExchange(getDeadLetterExchange());
        endpoint.setDeadLetterExchangeType(getDeadLetterExchangeType());
        endpoint.setDeadLetterQueue(getDeadLetterQueue());
        endpoint.setDeadLetterRoutingKey(getDeadLetterRoutingKey());
        endpoint.setAllowNullHeaders(isAllowNullHeaders());
        endpoint.setConnectionFactoryExceptionHandler(getConnectionFactoryExceptionHandler());
        setProperties(endpoint, params);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating RabbitMQEndpoint with host {}:{} and exchangeName: {}",
                      new Object[] {endpoint.getHostname(), endpoint.getPortNumber(), endpoint.getExchangeName()});
        }

        Map<String, Object> localArgs = new HashMap<>();
        if (getArgs() != null) {
            // copy over the component configured args
            localArgs.putAll(getArgs());
        }
        localArgs.putAll(PropertiesHelper.extractProperties(params, ARG_PREFIX));
        Map<String, Object> existing = endpoint.getArgs();
        if (existing != null) {
            existing.putAll(localArgs);
        } else {
            endpoint.setArgs(localArgs);
        }

        // Change null headers processing for message converter
        endpoint.getMessageConverter().setAllowNullHeaders(endpoint.isAllowNullHeaders());
        endpoint.getMessageConverter().setAllowCustomHeaders(endpoint.isAllowCustomHeaders());
        return endpoint;
    }

    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the running RabbitMQ instance or cluster.
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getPortNumber() {
        return portNumber;
    }

    /**
     * Port number for the host with the running rabbitmq instance or cluster.
     */
    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username in case of authenticated access
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for authenticated access
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getVhost() {
        return vhost;
    }

    /**
     * The vhost for the channel
     */
    public void setVhost(String vhost) {
        this.vhost = vhost;
    }

    /**
     * If this option is set, camel-rabbitmq will try to create connection based
     * on the setting of option addresses. The addresses value is a string which
     * looks like "server1:12345, server2:12345"
     */
    public void setAddresses(String addresses) {
        this.addresses = addresses;
    }

    public String getAddresses() {
        return addresses;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * To use a custom RabbitMQ connection factory. When this option is set, all
     * connection options (connectionTimeout, requestedChannelMax...) set on URI
     * are not used
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    /**
     * The consumer uses a Thread Pool Executor with a fixed number of threads.
     * This setting allows you to set that number of threads.
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public boolean isAutoDetectConnectionFactory() {
        return autoDetectConnectionFactory;
    }

    /**
     * Whether to auto-detect looking up RabbitMQ connection factory from the
     * registry. When enabled and a single instance of the connection factory is
     * found then it will be used. An explicit connection factory can be
     * configured on the component or endpoint level which takes precedence.
     */
    public void setAutoDetectConnectionFactory(boolean autoDetectConnectionFactory) {
        this.autoDetectConnectionFactory = autoDetectConnectionFactory;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    /**
     * Connection timeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    public int getRequestedChannelMax() {
        return requestedChannelMax;
    }

    /**
     * Connection requested channel max (max number of channels offered)
     */
    public void setRequestedChannelMax(int requestedChannelMax) {
        this.requestedChannelMax = requestedChannelMax;
    }

    public int getRequestedFrameMax() {
        return requestedFrameMax;
    }

    /**
     * Connection requested frame max (max size of frame offered)
     */
    public void setRequestedFrameMax(int requestedFrameMax) {
        this.requestedFrameMax = requestedFrameMax;
    }

    public int getRequestedHeartbeat() {
        return requestedHeartbeat;
    }

    /**
     * Connection requested heartbeat (heart-beat in seconds offered)
     */
    public void setRequestedHeartbeat(int requestedHeartbeat) {
        this.requestedHeartbeat = requestedHeartbeat;
    }

    public Boolean getAutomaticRecoveryEnabled() {
        return automaticRecoveryEnabled;
    }

    /**
     * Enables connection automatic recovery (uses connection implementation
     * that performs automatic recovery when connection shutdown is not
     * initiated by the application)
     */
    public void setAutomaticRecoveryEnabled(Boolean automaticRecoveryEnabled) {
        this.automaticRecoveryEnabled = automaticRecoveryEnabled;
    }

    public Integer getNetworkRecoveryInterval() {
        return networkRecoveryInterval;
    }

    /**
     * Network recovery interval in milliseconds (interval used when recovering
     * from network failure)
     */
    public void setNetworkRecoveryInterval(Integer networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }

    public Boolean getTopologyRecoveryEnabled() {
        return topologyRecoveryEnabled;
    }

    /**
     * Enables connection topology recovery (should topology recovery be
     * performed?)
     */
    public void setTopologyRecoveryEnabled(Boolean topologyRecoveryEnabled) {
        this.topologyRecoveryEnabled = topologyRecoveryEnabled;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    /**
     * Enables the quality of service on the RabbitMQConsumer side. You need to
     * specify the option of prefetchSize, prefetchCount, prefetchGlobal at the
     * same time
     */
    public void setPrefetchEnabled(boolean prefetchEnabled) {
        this.prefetchEnabled = prefetchEnabled;
    }

    /**
     * The maximum amount of content (measured in octets) that the server will
     * deliver, 0 if unlimited. You need to specify the option of prefetchSize,
     * prefetchCount, prefetchGlobal at the same time
     */
    public void setPrefetchSize(int prefetchSize) {
        this.prefetchSize = prefetchSize;
    }

    public int getPrefetchSize() {
        return prefetchSize;
    }

    /**
     * The maximum number of messages that the server will deliver, 0 if
     * unlimited. You need to specify the option of prefetchSize, prefetchCount,
     * prefetchGlobal at the same time
     */
    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    /**
     * If the settings should be applied to the entire channel rather than each
     * consumer You need to specify the option of prefetchSize, prefetchCount,
     * prefetchGlobal at the same time
     */
    public void setPrefetchGlobal(boolean prefetchGlobal) {
        this.prefetchGlobal = prefetchGlobal;
    }

    public boolean isPrefetchGlobal() {
        return prefetchGlobal;
    }

    /**
     * Get maximum number of opened channel in pool
     */
    public int getChannelPoolMaxSize() {
        return channelPoolMaxSize;
    }

    public void setChannelPoolMaxSize(int channelPoolMaxSize) {
        this.channelPoolMaxSize = channelPoolMaxSize;
    }

    public long getChannelPoolMaxWait() {
        return channelPoolMaxWait;
    }

    /**
     * Set the maximum number of milliseconds to wait for a channel from the
     * pool
     */
    public void setChannelPoolMaxWait(long channelPoolMaxWait) {
        this.channelPoolMaxWait = channelPoolMaxWait;
    }

    /**
     * Set timeout for waiting for a reply when using the InOut Exchange Pattern
     * (in milliseconds)
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Set requestTimeoutCheckerInterval for inOut exchange
     */
    public void setRequestTimeoutCheckerInterval(long requestTimeoutCheckerInterval) {
        this.requestTimeoutCheckerInterval = requestTimeoutCheckerInterval;
    }

    public long getRequestTimeoutCheckerInterval() {
        return requestTimeoutCheckerInterval;
    }

    /**
     * When true and an inOut Exchange failed on the consumer side send the
     * caused Exception back in the response
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * When true, the message will be published with
     * <a href="https://www.rabbitmq.com/confirms.html">publisher
     * acknowledgements</a> turned on
     */
    public boolean isPublisherAcknowledgements() {
        return publisherAcknowledgements;
    }

    public void setPublisherAcknowledgements(final boolean publisherAcknowledgements) {
        this.publisherAcknowledgements = publisherAcknowledgements;
    }

    /**
     * The amount of time in milliseconds to wait for a basic.ack response from
     * RabbitMQ server
     */
    public long getPublisherAcknowledgementsTimeout() {
        return publisherAcknowledgementsTimeout;
    }

    public void setPublisherAcknowledgementsTimeout(final long publisherAcknowledgementsTimeout) {
        this.publisherAcknowledgementsTimeout = publisherAcknowledgementsTimeout;
    }

    /**
     * When true, an exception will be thrown when the message cannot be
     * delivered (basic.return) and the message is marked as mandatory.
     * PublisherAcknowledgement will also be activated in this case. See also <a
     * href=https://www.rabbitmq.com/confirms.html">publisher
     * acknowledgements</a> - When will messages be confirmed.
     */
    public boolean isGuaranteedDeliveries() {
        return guaranteedDeliveries;
    }

    public void setGuaranteedDeliveries(boolean guaranteedDeliveries) {
        this.guaranteedDeliveries = guaranteedDeliveries;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * This flag tells the server how to react if the message cannot be routed
     * to a queue. If this flag is set, the server will return an unroutable
     * message with a Return method. If this flag is zero, the server silently
     * drops the message.
     * <p/>
     * If the header is present rabbitmq.MANDATORY it will override this option.
     */
    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public boolean isImmediate() {
        return immediate;
    }

    /**
     * This flag tells the server how to react if the message cannot be routed
     * to a queue consumer immediately. If this flag is set, the server will
     * return an undeliverable message with a Return method. If this flag is
     * zero, the server will queue the message, but with no guarantee that it
     * will ever be consumed.
     * <p/>
     * If the header is present rabbitmq.IMMEDIATE it will override this option.
     */
    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }

    /**
     * Specify arguments for configuring the different RabbitMQ concepts, a
     * different prefix is required for each:
     * <ul>
     * <li>Exchange: arg.exchange.</li>
     * <li>Queue: arg.queue.</li>
     * <li>Binding: arg.binding.</li>
     * </ul>
     * For example to declare a queue with message ttl argument:
     * http://localhost:5672/exchange/queue?args=arg.queue.x-message-ttl=60000
     */
    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public Map<String, Object> getClientProperties() {
        return clientProperties;
    }

    /**
     * Connection client properties (client info used in negotiating with the
     * server)
     */
    public void setClientProperties(Map<String, Object> clientProperties) {
        this.clientProperties = clientProperties;
    }

    public String getSslProtocol() {
        return sslProtocol;
    }

    /**
     * Enables SSL on connection, accepted value are `true`, `TLS` and 'SSLv3`
     */
    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    /**
     * Configure SSL trust manager, SSL should be enabled for this option to be
     * effective
     */
    public void setTrustManager(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public boolean isAutoAck() {
        return autoAck;
    }

    /**
     * If messages should be auto acknowledged
     */
    public void setAutoAck(boolean autoAck) {
        this.autoAck = autoAck;
    }

    public boolean isAutoDelete() {
        return autoDelete;
    }

    /**
     * If it is true, the exchange will be deleted when it is no longer in use
     */
    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }

    public boolean isDurable() {
        return durable;
    }

    /**
     * If we are declaring a durable exchange (the exchange will survive a
     * server restart)
     */
    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    /**
     * Exclusive queues may only be accessed by the current connection, and are
     * deleted when that connection closes.
     */
    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isExclusiveConsumer() {
        return exclusiveConsumer;
    }

    /**
     * Request exclusive access to the queue (meaning only this consumer can
     * access the queue). This is useful when you want a long-lived shared queue
     * to be temporarily accessible by just one consumer.
     */
    public void setExclusiveConsumer(boolean exclusiveConsumer) {
        this.exclusiveConsumer = exclusiveConsumer;
    }

    public boolean isPassive() {
        return passive;
    }

    /**
     * Passive queues depend on the queue already to be available at RabbitMQ.
     */
    public void setPassive(boolean passive) {
        this.passive = passive;
    }

    /**
     * If true the producer will not declare and bind a queue. This can be used
     * for directing messages via an existing routing key.
     */
    public void setSkipQueueDeclare(boolean skipQueueDeclare) {
        this.skipQueueDeclare = skipQueueDeclare;
    }

    public boolean isSkipQueueDeclare() {
        return skipQueueDeclare;
    }

    /**
     * If true the queue will not be bound to the exchange after declaring it
     */
    public boolean isSkipQueueBind() {
        return skipQueueBind;
    }

    public void setSkipQueueBind(boolean skipQueueBind) {
        this.skipQueueBind = skipQueueBind;
    }

    /**
     * This can be used if we need to declare the queue but not the exchange
     */
    public void setSkipExchangeDeclare(boolean skipExchangeDeclare) {
        this.skipExchangeDeclare = skipExchangeDeclare;
    }

    public boolean isSkipExchangeDeclare() {
        return skipExchangeDeclare;
    }

    public boolean isDeclare() {
        return declare;
    }

    /**
     * If the option is true, camel declare the exchange and queue name and bind
     * them together. If the option is false, camel won't declare the exchange
     * and queue name on the server.
     */
    public void setDeclare(boolean declare) {
        this.declare = declare;
    }

    public String getDeadLetterExchange() {
        return deadLetterExchange;
    }

    /**
     * The name of the dead letter exchange
     */
    public void setDeadLetterExchange(String deadLetterExchange) {
        this.deadLetterExchange = deadLetterExchange;
    }

    public String getDeadLetterQueue() {
        return deadLetterQueue;
    }

    /**
     * The name of the dead letter queue
     */
    public void setDeadLetterQueue(String deadLetterQueue) {
        this.deadLetterQueue = deadLetterQueue;
    }

    public String getDeadLetterRoutingKey() {
        return deadLetterRoutingKey;
    }

    /**
     * The routing key for the dead letter exchange
     */
    public void setDeadLetterRoutingKey(String deadLetterRoutingKey) {
        this.deadLetterRoutingKey = deadLetterRoutingKey;
    }

    public String getDeadLetterExchangeType() {
        return deadLetterExchangeType;
    }

    /**
     * The type of the dead letter exchange
     */
    public void setDeadLetterExchangeType(String deadLetterExchangeType) {
        this.deadLetterExchangeType = deadLetterExchangeType;
    }

    /**
     * Allow pass null values to header
     */
    public boolean isAllowNullHeaders() {
        return allowNullHeaders;
    }

    public void setAllowNullHeaders(boolean allowNullHeaders) {
        this.allowNullHeaders = allowNullHeaders;
    }

    public ExceptionHandler getConnectionFactoryExceptionHandler() {
        return connectionFactoryExceptionHandler;
    }

    /**
     * Custom rabbitmq ExceptionHandler for ConnectionFactory
     */
    public void setConnectionFactoryExceptionHandler(ExceptionHandler connectionFactoryExceptionHandler) {
        this.connectionFactoryExceptionHandler = connectionFactoryExceptionHandler;
    }
}
