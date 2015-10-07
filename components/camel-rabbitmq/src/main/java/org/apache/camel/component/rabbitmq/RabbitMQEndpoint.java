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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.NotSerializableException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
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
import com.rabbitmq.client.LongString;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.TypeConversionException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(scheme = "rabbitmq", title = "RabbitMQ", syntax = "rabbitmq:hostname:portNumber/exchangeName", consumerClass = RabbitMQConsumer.class, label = "messaging")
public class RabbitMQEndpoint extends DefaultEndpoint {
    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQEndpoint.class);
    // header to indicate that the message body needs to be de-serialized
    private static final String SERIALIZE_HEADER = "CamelSerialize";

    @UriPath @Metadata(required = "true")
    private String hostname;
    @UriPath(defaultValue = "5672") @Metadata(required = "true")
    private int portNumber;
    @UriPath @Metadata(required = "true")
    private String exchangeName;
    @UriParam(defaultValue = ConnectionFactory.DEFAULT_USER)
    private String username = ConnectionFactory.DEFAULT_USER;
    @UriParam(defaultValue = ConnectionFactory.DEFAULT_PASS)
    private String password = ConnectionFactory.DEFAULT_PASS;
    @UriParam(defaultValue = ConnectionFactory.DEFAULT_VHOST)
    private String vhost = ConnectionFactory.DEFAULT_VHOST;
    @UriParam(label = "consumer", defaultValue = "10")
    private int threadPoolSize = 10;
    @UriParam(label = "consumer", defaultValue = "true")
    private boolean autoAck = true;
    @UriParam(defaultValue = "true")
    private boolean autoDelete = true;
    @UriParam(defaultValue = "true")
    private boolean durable = true;
    @UriParam(label = "producer")
    private boolean bridgeEndpoint;
    @UriParam
    private String queue = String.valueOf(UUID.randomUUID().toString().hashCode());
    @UriParam(defaultValue = "direct", enums = "direct,fanout,headers,topic")
    private String exchangeType = "direct";
    @UriParam
    private String routingKey;
    @UriParam
    private Address[] addresses;
    @UriParam(defaultValue = "" + ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT)
    private int connectionTimeout = ConnectionFactory.DEFAULT_CONNECTION_TIMEOUT;
    @UriParam(defaultValue = "" + ConnectionFactory.DEFAULT_CHANNEL_MAX)
    private int requestedChannelMax = ConnectionFactory.DEFAULT_CHANNEL_MAX;
    @UriParam(defaultValue = "" + ConnectionFactory.DEFAULT_FRAME_MAX)
    private int requestedFrameMax = ConnectionFactory.DEFAULT_FRAME_MAX;
    @UriParam(defaultValue = "" + ConnectionFactory.DEFAULT_HEARTBEAT)
    private int requestedHeartbeat = ConnectionFactory.DEFAULT_HEARTBEAT;
    @UriParam
    private String sslProtocol;
    @UriParam
    private TrustManager trustManager;
    @UriParam
    private Map<String, Object> clientProperties;
    @UriParam
    private ConnectionFactory connectionFactory;
    @UriParam
    private Boolean automaticRecoveryEnabled;
    @UriParam
    private Integer networkRecoveryInterval;
    @UriParam
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
    @UriParam
    private String deadLetterExchange;
    @UriParam
    private String deadLetterRoutingKey;
    @UriParam
    private String deadLetterQueue;
    @UriParam(defaultValue = "direct", enums = "direct,fanout,headers,topic")
    private String deadLetterExchangeType = "direct";
    @UriParam(label = "producer", defaultValue = "10")
    private int channelPoolMaxSize = 10;
    @UriParam(label = "producer", defaultValue = "1000")
    private long channelPoolMaxWait = 1000;
    @UriParam(label = "producer")
    private boolean mandatory;
    @UriParam(label = "producer")
    private boolean immediate;
    @UriParam
    private ArgsConfigurer queueArgsConfigurer;
    @UriParam
    private ArgsConfigurer exchangeArgsConfigurer;

    @UriParam
    private long requestTimeout = 20000;
    @UriParam
    private long requestTimeoutCheckerInterval = 1000;
    @UriParam
    private boolean transferException;
    // camel-jms supports this setting but it is not currently configurable in camel-rabbitmq
    private boolean useMessageIDAsCorrelationID = true;
    // camel-jms supports this setting but it is not currently configurable in camel-rabbitmq
    private String replyToType = ReplyToType.Temporary.name();
    // camel-jms supports this setting but it is not currently configurable in camel-rabbitmq
    private String replyTo;

    private RabbitMQMessageConverter messageConverter = new RabbitMQMessageConverter();
    

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
        setRabbitExchange(exchange, envelope, properties, body);
        return exchange;
    }

    /**
     * Gets the message converter to convert between rabbit and camel
     */
    protected RabbitMQMessageConverter getMessageConverter() {
        return messageConverter;
    }

    public void setRabbitExchange(Exchange camelExchange, Envelope envelope, AMQP.BasicProperties properties, byte[] body) {
        Message message;
        if (camelExchange.getIn() != null) {
            // Use the existing message so we keep the headers
            message = camelExchange.getIn();
        } else {
            message = new DefaultMessage();
            camelExchange.setIn(message);
        }

        if (envelope != null) {
            message.setHeader(RabbitMQConstants.ROUTING_KEY, envelope.getRoutingKey());
            message.setHeader(RabbitMQConstants.EXCHANGE_NAME, envelope.getExchange());
            message.setHeader(RabbitMQConstants.DELIVERY_TAG, envelope.getDeliveryTag());
        }

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

        if (hasSerializeHeader(properties)) {
            Object messageBody = null;
            try (InputStream b = new ByteArrayInputStream(body);
                            ObjectInputStream o = new ObjectInputStream(b);) {
                messageBody = o.readObject();
            } catch (IOException | ClassNotFoundException e) {
                LOG.warn("Could not deserialize the object");
            }
            if (messageBody instanceof Throwable) {
                LOG.debug("Reply was an Exception. Setting the Exception on the Exchange");
                camelExchange.setException((Throwable) messageBody);
            } else {
                message.setBody(messageBody);
            }
        } else {
            // Set the body as a byte[] and let the type converter deal with it
            message.setBody(body);
        }

    }

    private boolean hasSerializeHeader(AMQP.BasicProperties properties) {
        if (properties == null || properties.getHeaders() == null) {
            return false;
        }
        if (properties.getHeaders().containsKey(SERIALIZE_HEADER) && properties.getHeaders().get(SERIALIZE_HEADER).equals(true)) {
            return true;
        }
        return false;
    }

    /**
     * Sends the body that is on the exchange
     */
    public void publishExchangeToChannel(Exchange camelExchange, Channel channel, String routingKey) throws IOException {
        Message msg;
        if (camelExchange.hasOut()) {
            msg = camelExchange.getOut();
        } else {
            msg = camelExchange.getIn();
        }

        // Remove the SERIALIZE_HEADER in case it was previously set
        if (msg.getHeaders() != null && msg.getHeaders().containsKey(SERIALIZE_HEADER)) {
            LOG.debug("Removing the {} header", SERIALIZE_HEADER);
            msg.getHeaders().remove(SERIALIZE_HEADER);
        }

        AMQP.BasicProperties properties;
        byte[] body;
        try {
            // To maintain backwards compatibility try the TypeConverter (The DefaultTypeConverter seems to only work on Strings)
            body = camelExchange.getContext().getTypeConverter().mandatoryConvertTo(byte[].class, camelExchange, msg.getBody());

            properties = getMessageConverter().buildProperties(camelExchange).build();
        } catch (NoTypeConversionAvailableException | TypeConversionException e) {
            if (msg.getBody() instanceof Serializable) {
                // Add the header so the reply processor knows to de-serialize it
                msg.getHeaders().put(SERIALIZE_HEADER, true);

                properties = getMessageConverter().buildProperties(camelExchange).build();

                try (ByteArrayOutputStream b = new ByteArrayOutputStream(); ObjectOutputStream o = new ObjectOutputStream(b);) {
                    o.writeObject(msg.getBody());
                    body = b.toByteArray();
                } catch (NotSerializableException nse) {
                    LOG.warn("Can not send object " + msg.getBody().getClass() + " via RabbitMQ because it contains non-serializable objects.");
                    throw new RuntimeCamelException(e);
                }
            } else if (msg.getBody() == null) {
                properties = getMessageConverter().buildProperties(camelExchange).build();
                body = null;
            } else {
                LOG.warn("Could not convert {} to byte[]", msg.getBody());
                throw new RuntimeCamelException(e);
            }
        }
        String rabbitExchange = getExchangeName(msg);

        Boolean mandatory = camelExchange.getIn().getHeader(RabbitMQConstants.MANDATORY, isMandatory(), Boolean.class);
        Boolean immediate = camelExchange.getIn().getHeader(RabbitMQConstants.IMMEDIATE, isImmediate(), Boolean.class);

        LOG.debug("Sending message to exchange: {} with CorrelationId = {}", rabbitExchange, properties.getCorrelationId());
        channel.basicPublish(rabbitExchange, routingKey, mandatory, immediate, properties, body);
    }

    /**
     * Extracts name of the rabbitmq exchange
     */
    protected String getExchangeName(Message msg) {
        String exchangeName = msg.getHeader(RabbitMQConstants.EXCHANGE_NAME, String.class);
        // If it is BridgeEndpoint we should ignore the message header of EXCHANGE_NAME
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
        Map<String, Object> queueArgs = new HashMap<String, Object>();
        Map<String, Object> exchangeArgs = new HashMap<String, Object>();
        
        if (deadLetterExchange != null) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_EXCHANGE, getDeadLetterExchange());
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_ROUTING_KEY, getDeadLetterRoutingKey());
            // TODO Do we need to setup the args for the DeadLetter?
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
        
        if (getQueueArgsConfigurer() != null) {
            getQueueArgsConfigurer().configurArgs(queueArgs);
        }
        if (getExchangeArgsConfigurer() != null) {
            getExchangeArgsConfigurer().configurArgs(exchangeArgs);
        }
        
        channel.exchangeDeclare(getExchangeName(),
                getExchangeType(),
                isDurable(),
                isAutoDelete(), exchangeArgs);
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
     * The consumer uses a Thread Pool Executor with a fixed number of threads. This setting allows you to set that number of threads.
     */
    public void setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public int getPortNumber() {
        return portNumber;
    }

    /**
     * Port number for the host with the running rabbitmq instance or cluster. Default value is 5672.
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
     * If we are declaring a durable exchange (the exchange will survive a server restart)
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
     * The exchange name determines which exchange produced messages will sent to.
     * In the case of consumers, the exchange name determines which exchange the queue will bind to.
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
     * The routing key to use when binding a consumer queue to the exchange.
     * For producer routing keys, you set the header rabbitmq.ROUTING_KEY.
     */
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    /**
     * If the bridgeEndpoint is true, the producer will ignore the message header of "rabbitmq.EXCHANGE_NAME" and "rabbitmq.ROUTING_KEY"
     */
    public void setBridgeEndpoint(boolean bridgeEndpoint) {
        this.bridgeEndpoint = bridgeEndpoint;
    }

    public boolean isBridgeEndpoint() {
        return bridgeEndpoint;
    }

    /**
     * If this option is set, camel-rabbitmq will try to create connection based on the setting of option addresses.
     * The addresses value is a string which looks like "server1:12345, server2:12345"
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
     * To use a custom RabbitMQ connection factory.
     * When this option is set, all connection options (connectionTimeout, requestedChannelMax...) set on URI are not used
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }

    /**
     * Configure SSL trust manager, SSL should be enabled for this option to be effective
     */
    public void setTrustManager(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    public Map<String, Object> getClientProperties() {
        return clientProperties;
    }

    /**
     * Connection client properties (client info used in negotiating with the server)
     */
    public void setClientProperties(Map<String, Object> clientProperties) {
        this.clientProperties = clientProperties;
    }

    public Boolean getAutomaticRecoveryEnabled() {
        return automaticRecoveryEnabled;
    }

    /**
     * Enables connection automatic recovery (uses connection implementation that performs automatic recovery when connection shutdown is not initiated by the application)
     */
    public void setAutomaticRecoveryEnabled(Boolean automaticRecoveryEnabled) {
        this.automaticRecoveryEnabled = automaticRecoveryEnabled;
    }

    public Integer getNetworkRecoveryInterval() {
        return networkRecoveryInterval;
    }

    /**
     * Network recovery interval in milliseconds (interval used when recovering from network failure)
     */
    public void setNetworkRecoveryInterval(Integer networkRecoveryInterval) {
        this.networkRecoveryInterval = networkRecoveryInterval;
    }

    public Boolean getTopologyRecoveryEnabled() {
        return topologyRecoveryEnabled;
    }

    /**
     * Enables connection topology recovery (should topology recovery be performed?)
     */
    public void setTopologyRecoveryEnabled(Boolean topologyRecoveryEnabled) {
        this.topologyRecoveryEnabled = topologyRecoveryEnabled;
    }

    public boolean isPrefetchEnabled() {
        return prefetchEnabled;
    }

    /**
     * Enables the quality of service on the RabbitMQConsumer side.
     * You need to specify the option of prefetchSize, prefetchCount, prefetchGlobal at the same time
     */
    public void setPrefetchEnabled(boolean prefetchEnabled) {
        this.prefetchEnabled = prefetchEnabled;
    }

    /**
     * The maximum amount of content (measured in octets) that the server will deliver, 0 if unlimited.
     * You need to specify the option of prefetchSize, prefetchCount, prefetchGlobal at the same time
     */
    public void setPrefetchSize(int prefetchSize) {
        this.prefetchSize = prefetchSize;
    }

    public int getPrefetchSize() {
        return prefetchSize;
    }

    /**
     * The maximum number of messages that the server will deliver, 0 if unlimited.
     * You need to specify the option of prefetchSize, prefetchCount, prefetchGlobal at the same time
     */
    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    /**
     * If the settings should be applied to the entire channel rather than each consumer
     * You need to specify the option of prefetchSize, prefetchCount, prefetchGlobal at the same time
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
     * Number of concurrent consumers when consuming from broker. (eg similar as to the same option for the JMS component).
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public boolean isDeclare() {
        return declare;
    }

    /**
     * If the option is true, camel declare the exchange and queue name and bind them together.
     * If the option is false, camel won't declare the exchange and queue name on the server.
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
     * Set the maximum number of milliseconds to wait for a channel from the pool
     */
    public void setChannelPoolMaxWait(long channelPoolMaxWait) {
        this.channelPoolMaxWait = channelPoolMaxWait;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    /**
     * This flag tells the server how to react if the message cannot be routed to a queue.
     * If this flag is set, the server will return an unroutable message with a Return method.
     * If this flag is zero, the server silently drops the message.
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
     * This flag tells the server how to react if the message cannot be routed to a queue consumer immediately.
     * If this flag is set, the server will return an undeliverable message with a Return method.
     * If this flag is zero, the server will queue the message, but with no guarantee that it will ever be consumed.
     * <p/>
     * If the header is present rabbitmq.IMMEDIATE it will override this option.
     */
    public void setImmediate(boolean immediate) {
        this.immediate = immediate;
    }

    public ArgsConfigurer getQueueArgsConfigurer() {
        return queueArgsConfigurer;
    }

    /**
     * Set the configurer for setting the queue args in Channel.queueDeclare
     */
    public void setQueueArgsConfigurer(ArgsConfigurer queueArgsConfigurer) {
        this.queueArgsConfigurer = queueArgsConfigurer;
    }

    public ArgsConfigurer getExchangeArgsConfigurer() {
        return exchangeArgsConfigurer;
    }
    
    /**
     * Set the configurer for setting the exchange args in Channel.exchangeDeclare
     */
    public void setExchangeArgsConfigurer(ArgsConfigurer exchangeArgsConfigurer) {
        this.exchangeArgsConfigurer = exchangeArgsConfigurer;
    }

    /**
     * Set timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds)
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
     * When true and an inOut Exchange failed on the consumer side send the caused Exception back in the response 
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isTransferException() {
        return transferException;
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
}
