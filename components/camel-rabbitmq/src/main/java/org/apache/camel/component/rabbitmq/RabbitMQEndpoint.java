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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.TrustManager;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Address;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Envelope;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The rabbitmq component allows you produce and consume messages from
 * <a href="http://www.rabbitmq.com/">RabbitMQ</a> instances.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "rabbitmq", title = "RabbitMQ", syntax = "rabbitmq:hostname:portNumber/exchangeName", consumerClass = RabbitMQConsumer.class, label = "messaging")
public class RabbitMQEndpoint extends DefaultEndpoint implements AsyncEndpoint {
    // header to indicate that the message body needs to be de-serialized
    public static final String SERIALIZE_HEADER = "CamelSerialize";

    @UriPath
    @Metadata(required = "true")
    private String hostname;
    @UriPath(defaultValue = "5672")
    @Metadata(required = "true")
    private int portNumber;
    @UriPath
    @Metadata(required = "true")
    private String exchangeName;
    @UriParam(label = "security", defaultValue = ConnectionFactory.DEFAULT_USER, secret = true)
    private String username = ConnectionFactory.DEFAULT_USER;
    @UriParam(label = "security", defaultValue = ConnectionFactory.DEFAULT_PASS, secret = true)
    private String password = ConnectionFactory.DEFAULT_PASS;
    @UriParam(defaultValue = ConnectionFactory.DEFAULT_VHOST)
    private String vhost = ConnectionFactory.DEFAULT_VHOST;
    @UriParam(label = "consumer,advanced", defaultValue = "10")
    private int threadPoolSize = 10;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean autoAck = true;
    @UriParam(label = "common", defaultValue = "true")
    private boolean autoDelete = true;
    @UriParam(label = "common", defaultValue = "true")
    private boolean durable = true;
    @UriParam(label = "common")
    private boolean exclusive;
    @UriParam(label = "common")
    private boolean passive;
    @UriParam(label = "producer")
    private boolean bridgeEndpoint;
    @UriParam(label = "common")
    private String queue = String.valueOf(UUID.randomUUID().toString().hashCode());
    @UriParam(label = "common", defaultValue = "direct", enums = "direct,fanout,headers,topic")
    private String exchangeType = "direct";
    @UriParam(label = "common")
    private String routingKey;
    @UriParam(label = "common")
    private boolean skipQueueDeclare;
    @UriParam(label = "common")
    private boolean skipQueueBind;
    @UriParam(label = "common")
    private boolean skipExchangeDeclare;
    @UriParam(label = "advanced")
    private Address[] addresses;
    @UriParam(defaultValue = "" + ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT)
    private int connectionTimeout = ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT;
    @UriParam(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_CHANNEL_MAX)
    private int requestedChannelMax = ConnectionFactory.DEFAULT_CHANNEL_MAX;
    @UriParam(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_FRAME_MAX)
    private int requestedFrameMax = ConnectionFactory.DEFAULT_FRAME_MAX;
    @UriParam(label = "advanced", defaultValue = "" + ConnectionFactory.DEFAULT_HEARTBEAT)
    private int requestedHeartbeat = ConnectionFactory.DEFAULT_HEARTBEAT;
    @UriParam(label = "security")
    private String sslProtocol;
    @UriParam(label = "security")
    private TrustManager trustManager;
    @UriParam(label = "advanced")
    private Map<String, Object> clientProperties;
    @UriParam(label = "advanced")
    private ConnectionFactory connectionFactory;
    @UriParam(label = "advanced")
    private Boolean automaticRecoveryEnabled;
    @UriParam(label = "advanced", defaultValue = "5000")
    private Integer networkRecoveryInterval = 5000;
    @UriParam(label = "advanced")
    private Boolean topologyRecoveryEnabled;
    @UriParam(label = "consumer")
    private boolean prefetchEnabled;
    @UriParam(label = "consumer")
    private int prefetchSize;
    @UriParam(label = "consumer")
    private int prefetchCount;
    @UriParam(label = "consumer")
    private boolean prefetchGlobal;
    @UriParam(label = "consumer", defaultValue = "1")
    private int concurrentConsumers = 1;
    @UriParam(defaultValue = "true")
    private boolean declare = true;
    @UriParam(label = "common")
    private String deadLetterExchange;
    @UriParam(label = "common")
    private String deadLetterRoutingKey;
    @UriParam(label = "common")
    private String deadLetterQueue;
    @UriParam(label = "common", defaultValue = "direct", enums = "direct,fanout,headers,topic")
    private String deadLetterExchangeType = "direct";
    @UriParam(label = "producer", defaultValue = "10")
    private int channelPoolMaxSize = 10;
    @UriParam(label = "producer", defaultValue = "1000")
    private long channelPoolMaxWait = 1000;
    @UriParam(label = "producer")
    private boolean mandatory;
    @UriParam(label = "producer")
    private boolean immediate;
    @UriParam(label = "advanced", prefix = "arg.", multiValue = true)
    private Map<String, Object> args;
    @UriParam(label = "advanced")
    @Deprecated
    private Map<String, Object> exchangeArgs = new HashMap<>();
    @UriParam(label = "advanced")
    @Deprecated
    private Map<String, Object> queueArgs = new HashMap<>();
    @UriParam(label = "advanced")
    @Deprecated
    private Map<String, Object> bindingArgs = new HashMap<>();
    @UriParam(label = "advanced")
    @Deprecated
    private ArgsConfigurer queueArgsConfigurer;
    @UriParam(label = "advanced")
    @Deprecated
    private ArgsConfigurer exchangeArgsConfigurer;
    @UriParam(label = "advanced", defaultValue = "20000")
    private long requestTimeout = 20000;
    @UriParam(label = "advanced", defaultValue = "1000")
    private long requestTimeoutCheckerInterval = 1000;
    @UriParam(label = "advanced")
    private boolean transferException;
    @UriParam(label = "producer")
    private boolean publisherAcknowledgements;
    @UriParam(label = "producer")
    private long publisherAcknowledgementsTimeout;
    @UriParam(label = "producer")
    private boolean guaranteedDeliveries;
    // camel-jms supports this setting but it is not currently configurable in
    // camel-rabbitmq
    private boolean useMessageIDAsCorrelationID = true;
    // camel-jms supports this setting but it is not currently configurable in
    // camel-rabbitmq
    private String replyToType = ReplyToType.Temporary.name();
    // camel-jms supports this setting but it is not currently configurable in
    // camel-rabbitmq
    private String replyTo;

    private final RabbitMQMessageConverter messageConverter = new RabbitMQMessageConverter();
    private final RabbitMQConnectionFactorySupport factoryCreator = new RabbitMQConnectionFactorySupport();
    private final RabbitMQDeclareSupport declareSupport = new RabbitMQDeclareSupport(this);

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
        Exchange exchange = super.createExchange();
        messageConverter.populateRabbitExchange(exchange, envelope, properties, body, false);
        return exchange;
    }

    /**
     * Gets the message converter to convert between rabbit and camel
     */
    protected RabbitMQMessageConverter getMessageConverter() {
        return messageConverter;
    }

    /**
     * Sends the body that is on the exchange
     */
    public void publishExchangeToChannel(Exchange camelExchange, Channel channel, String routingKey) throws IOException {
        new RabbitMQMessagePublisher(camelExchange, channel, routingKey, this).publish();
    }

    /**
     * Extracts name of the rabbitmq exchange
     */
    protected String getExchangeName(Message msg) {
        String exchangeName = msg.getHeader(RabbitMQConstants.EXCHANGE_NAME, String.class);
        // If it is BridgeEndpoint we should ignore the message header of
        // EXCHANGE_NAME
        if (exchangeName == null || isBridgeEndpoint()) {
            exchangeName = getExchangeName();
        }
        return exchangeName;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        RabbitMQConsumer consumer = new RabbitMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Connection connect(ExecutorService executor) throws IOException, TimeoutException {
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
        declareSupport.declareAndBindExchangesAndQueuesUsing(channel);
    }

    private ConnectionFactory getOrCreateConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = factoryCreator.createFactoryFor(this);
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

    public String getHostname() {
        return hostname;
    }

    /**
     * The hostname of the running rabbitmq instance or cluster.
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
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

    public int getPortNumber() {
        return portNumber;
    }

    /**
     * Port number for the host with the running rabbitmq instance or cluster.
     * Default value is 5672.
     */
    public void setPortNumber(int portNumber) {
        this.portNumber = portNumber;
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

    public String getQueue() {
        return queue;
    }

    /**
     * The queue to receive messages from
     */
    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    /**
     * The exchange name determines which exchange produced messages will sent
     * to. In the case of consumers, the exchange name determines which exchange
     * the queue will bind to.
     */
    public void setExchangeName(String exchangeName) {
        this.exchangeName = exchangeName;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    /**
     * The exchange type such as direct or topic.
     */
    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    /**
     * The routing key to use when binding a consumer queue to the exchange. For
     * producer routing keys, you set the header rabbitmq.ROUTING_KEY.
     */
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
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
     * 
     * @return
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

    /**
     * If the bridgeEndpoint is true, the producer will ignore the message
     * header of "rabbitmq.EXCHANGE_NAME" and "rabbitmq.ROUTING_KEY"
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If this option is set, camel-rabbitmq will try to create connection based
     * on the setting of option addresses. The addresses value is a string which
     * looks like "server1:12345, server2:12345"
     */
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

    public String getSslProtocol() {
        return sslProtocol;
    }

    /**
     * Enables SSL on connection, accepted value are `true`, `TLS` and 'SSLv3`
     */
    public void setSslProtocol(String sslProtocol) {
        this.sslProtocol = sslProtocol;
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

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * Number of concurrent consumers when consuming from broker. (eg similar as
     * to the same option for the JMS component).
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
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

    /**
     * Key/value args for configuring the exchange parameters when declare=true
     *
     * @Deprecated Use args instead e.g arg.exchange.x-message-ttl=1000
     */
    @Deprecated
    public void setExchangeArgs(Map<String, Object> exchangeArgs) {
        this.exchangeArgs = exchangeArgs;
    }

    public Map<String, Object> getExchangeArgs() {
        return exchangeArgs;
    }

    /**
     * Key/value args for configuring the queue parameters when declare=true
     *
     * @Deprecated Use args instead e.g arg.queue.x-message-ttl=1000
     */
    public void setQueueArgs(Map<String, Object> queueArgs) {
        this.queueArgs = queueArgs;
    }

    public Map<String, Object> getQueueArgs() {
        return queueArgs;
    }

    /**
     * Key/value args for configuring the queue binding parameters when
     * declare=true
     *
     * @Deprecated Use args instead e.g arg.binding.foo=bar
     */
    public void setBindingArgs(Map<String, Object> bindingArgs) {
        this.bindingArgs = bindingArgs;
    }

    public Map<String, Object> getBindingArgs() {
        return bindingArgs;
    }

    public ArgsConfigurer getQueueArgsConfigurer() {
        return queueArgsConfigurer;
    }

    /**
     * Set the configurer for setting the queue args in Channel.queueDeclare
     *
     * @Deprecated Use args instead e.g arg.queue.x-message-ttl=1000
     */
    public void setQueueArgsConfigurer(ArgsConfigurer queueArgsConfigurer) {
        this.queueArgsConfigurer = queueArgsConfigurer;
    }

    public ArgsConfigurer getExchangeArgsConfigurer() {
        return exchangeArgsConfigurer;
    }

    /**
     * Set the configurer for setting the exchange args in
     * Channel.exchangeDeclare
     *
     * @Deprecated Use args instead e.g arg.exchange.x-message-ttl=1000
     */
    public void setExchangeArgsConfigurer(ArgsConfigurer exchangeArgsConfigurer) {
        this.exchangeArgsConfigurer = exchangeArgsConfigurer;
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
     * Get useMessageIDAsCorrelationID for inOut exchange
     */
    public boolean isUseMessageIDAsCorrelationID() {
        return useMessageIDAsCorrelationID;
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
     * PublisherAcknowledgement will also be activated in this case See also <a
     * href=https://www.rabbitmq.com/confirms.html">publisher
     * acknowledgements</a> - When will messages be confirmed?
     */
    public boolean isGuaranteedDeliveries() {
        return guaranteedDeliveries;
    }

    public void setGuaranteedDeliveries(boolean guaranteedDeliveries) {
        this.guaranteedDeliveries = guaranteedDeliveries;
    }

    /**
     * Get replyToType for inOut exchange
     */
    public String getReplyToType() {
        return replyToType;
    }

    /**
     * Gets the Queue to reply to if you dont want to use temporary reply queues
     */
    public String getReplyTo() {
        return replyTo;
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

    public boolean isPassive() {
        return passive;
    }

    /**
     * Passive queues depend on the queue already to be available at RabbitMQ.
     */
    public void setPassive(boolean passive) {
        this.passive = passive;
    }

}
