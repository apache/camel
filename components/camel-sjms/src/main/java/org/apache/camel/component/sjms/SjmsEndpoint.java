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
package org.apache.camel.component.sjms;

import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.sjms.jms.ConnectionFactoryResource;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationNameParser;
import org.apache.camel.component.sjms.jms.Jms11ObjectFactory;
import org.apache.camel.component.sjms.jms.JmsBinding;
import org.apache.camel.component.sjms.jms.JmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.jms.MessageCreatedStrategy;
import org.apache.camel.component.sjms.jms.SessionAcknowledgementType;
import org.apache.camel.component.sjms.producer.InOnlyProducer;
import org.apache.camel.component.sjms.producer.InOutProducer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.EndpointHelper;
import org.apache.camel.support.LoggingExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Send and receive messages to/from a JMS Queue or Topic using plain JMS 1.x API.
 *
 * This component uses plain JMS API where as the jms component uses Spring JMS.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "sjms", title = "Simple JMS", syntax = "sjms:destinationType:destinationName",
             category = { Category.MESSAGING })
public class SjmsEndpoint extends DefaultEndpoint
        implements AsyncEndpoint, MultipleConsumersSupport, HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(SjmsEndpoint.class);

    private boolean topic;
    private JmsBinding binding;

    @UriPath(enums = "queue,topic", defaultValue = "queue", description = "The kind of destination to use")
    private String destinationType;
    @UriPath(description = "DestinationName is a JMS queue or topic name. By default, the destinationName is interpreted as a queue name.")
    @Metadata(required = true)
    private String destinationName;
    // TODO: get rid of synchronous true for consumer
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Sets whether synchronous processing should be strictly used or Camel is allowed to use asynchronous processing (if supported).")
    private boolean synchronous = true;
    @UriParam(label = "advanced",
              description = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced",
              description = "Whether to include all JMSXxxx properties when mapping from JMS to Camel Message."
                            + " Setting this to true will include properties such as JMSXAppID, and JMSXUserID etc. Note: If you are using a custom headerFilterStrategy then this option does not apply.")
    private boolean includeAllJMSXProperties;
    @UriParam(label = "transaction",
              description = "Specifies whether to use transacted mode")
    private boolean transacted;
    @UriParam(label = "transaction,advanced", defaultValue = "true",
              description = "Specifies whether to share JMS session with other SJMS endpoints. Turn this off if your route is accessing to multiple JMS providers."
                            + " If you need transaction against multiple JMS providers, use jms component to leverage XA transaction.")
    private boolean sharedJMSSession = true;
    @UriParam(label = "common",
              description = "Provides an explicit ReplyTo destination (overrides any incoming value of Message.getJMSReplyTo() in consumer).")
    private String replyTo;
    @UriParam(defaultValue = "AUTO_ACKNOWLEDGE",
              enums = "SESSION_TRANSACTED,CLIENT_ACKNOWLEDGE,AUTO_ACKNOWLEDGE,DUPS_OK_ACKNOWLEDGE",
              description = "The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE")
    private SessionAcknowledgementType acknowledgementMode = SessionAcknowledgementType.AUTO_ACKNOWLEDGE;
    @UriParam(label = "consumer", defaultValue = "1",
              description = "Sets the number of consumer listeners used for this endpoint.")
    private int consumerCount = 1;
    @UriParam(label = "producer", defaultValue = "false",
              description = "Set if the deliveryMode, priority or timeToLive qualities of service should be used when sending messages."
                            + " This option is based on Spring's JmsTemplate. The deliveryMode, priority and timeToLive options are applied to the current endpoint."
                            + " This contrasts with the preserveMessageQos option, which operates at message granularity,"
                            + " reading QoS properties exclusively from the Camel In message headers.")
    private Boolean explicitQosEnabled;
    @UriParam(label = "producer",
              description = "Set to true, if you want to send message using the QoS settings specified on the message,"
                            + " instead of the QoS settings on the JMS endpoint. The following three headers are considered JMSPriority, JMSDeliveryMode,"
                            + " and JMSExpiration. You can provide all or only some of them. If not provided, Camel will fall back to use the"
                            + " values from the endpoint instead. So, when using this option, the headers override the values from the endpoint."
                            + " The explicitQosEnabled option, by contrast, will only use options set on the endpoint, and not values from the message header.")
    private boolean preserveMessageQos;
    @UriParam(defaultValue = "" + Message.DEFAULT_PRIORITY, enums = "1,2,3,4,5,6,7,8,9", label = "producer",
              description = "Values greater than 1 specify the message priority when sending (where 1 is the lowest priority and 9 is the highest)."
                            + " The explicitQosEnabled option must also be enabled in order for this option to have any effect.")
    private int priority = Message.DEFAULT_PRIORITY;
    @UriParam(defaultValue = "true", label = "producer",
              description = "Specifies whether persistent delivery is used by default.")
    private boolean deliveryPersistent = true;
    @UriParam(description = "Specifies whether Camel ignores the JMSReplyTo header in messages. If true, Camel does not send a reply back to"
                            + " the destination specified in the JMSReplyTo header. You can use this option if you want Camel to consume from a"
                            + " route and you do not want Camel to automatically send back a reply message because another component in your code"
                            + " handles the reply message. You can also use this option if you want to use Camel as a proxy between different"
                            + " message brokers and you want to route message from one system to another.")
    private boolean disableReplyTo;
    @UriParam(label = "producer",
              description = "Provides an explicit ReplyTo destination in the JMS message, which overrides the setting of replyTo."
                            + " It is useful if you want to forward the message to a remote Queue and receive the reply message from the ReplyTo destination.")
    private String replyToOverride;
    @UriParam(defaultValue = "true", label = "consumer",
              description = "Specifies whether to use persistent delivery by default for replies.")
    private boolean replyToDeliveryPersistent = true;
    @UriParam(enums = "1,2", label = "producer",
              description = "Specifies the delivery mode to be used."
                            + " Possible values are those defined by javax.jms.DeliveryMode."
                            + " NON_PERSISTENT = 1 and PERSISTENT = 2.")
    private Integer deliveryMode;
    @UriParam(defaultValue = "-1", label = "producer",
              description = "When sending messages, specifies the time-to-live of the message (in milliseconds).")
    private long timeToLive = -1;
    @UriParam(label = "consumer",
              description = "Sets the durable subscription Id required for durable topics.")
    private String durableSubscriptionId;
    @UriParam(defaultValue = "20000", label = "producer", javaType = "java.time.Duration",
              description = "The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds)."
                            + " The default is 20 seconds. You can include the header \"CamelJmsRequestTimeout\" to override this endpoint configured"
                            + " timeout value, and thus have per message individual timeout values."
                            + " See also the requestTimeoutCheckerInterval option.")
    private long requestTimeout = 20000L;
    @UriParam(label = "consumer,advanced",
              description = "Sets the JMS Message selector syntax.")
    private String messageSelector;
    @UriParam(label = "advanced",
              description = "Whether to startup the consumer message listener asynchronously, when starting a route."
                            + " For example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying and/or failover."
                            + " This will cause Camel to block while starting routes. By setting this option to true, you will let routes startup, while the JmsConsumer connects to the JMS broker"
                            + " using a dedicated thread in asynchronous mode. If this option is used, then beware that if the connection could not be established, then an exception is logged at WARN level,"
                            + " and the consumer will not be able to receive messages; You can then restart the route to retry.")
    private boolean asyncStartListener;
    @UriParam(label = "advanced",
              description = "Whether to stop the consumer message listener asynchronously, when stopping a route.")
    private boolean asyncStopListener;
    @UriParam(label = "producer,advanced", defaultValue = "true",
              description = "Whether to allow sending messages with no body. If this option is false and the message body is null, then an JMSException is thrown.")
    private boolean allowNullBody = true;
    @UriParam(label = "advanced", defaultValue = "true",
              description = "Specifies whether Camel should auto map the received JMS message to a suited payload type, such as javax.jms.TextMessage to a String etc."
                            + " See section about how mapping works below for more details.")
    private boolean mapJmsMessage = true;
    @UriParam(label = "transaction",
              description = "Sets the commit strategy.")
    private TransactionCommitStrategy transactionCommitStrategy;
    @UriParam(label = "advanced",
              description = "To use a custom DestinationCreationStrategy.")
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();
    @UriParam(label = "advanced",
              description = "To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of <tt>javax.jms.Message</tt> objects when Camel is sending a JMS message.")
    private MessageCreatedStrategy messageCreatedStrategy;
    @UriParam(label = "advanced",
              description = "Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification."
                            + " Camel provides two implementations out of the box: default and passthrough. The default strategy will safely marshal dots and hyphens (. and -)."
                            + " The passthrough strategy leaves the key as is. Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters."
                            + " You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy and refer to it using the # notation.")
    private JmsKeyFormatStrategy jmsKeyFormatStrategy;
    @UriParam(label = "advanced",
              description = "Initializes the connectionResource for the endpoint, which takes precedence over the component's connectionResource, if any")
    private ConnectionResource connectionResource;
    @UriParam(label = "advanced",
              description = "Initializes the connectionFactory for the endpoint, which takes precedence over the component's connectionFactory, if any")
    private ConnectionFactory connectionFactory;
    @UriParam(label = "advanced",
              description = "The maximum number of connections available to this endpoint")
    @Deprecated
    private Integer connectionCount;
    @UriParam(label = "advanced",
              description = "Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.")
    private ExceptionListener exceptionListener;
    @UriParam(defaultValue = "WARN", label = "consumer,logging",
              description = "Allows to configure the default errorHandler logging level for logging uncaught exceptions.")
    private LoggingLevel errorHandlerLoggingLevel = LoggingLevel.WARN;
    @UriParam(defaultValue = "true", label = "consumer,logging",
              description = "Allows to control whether stacktraces should be logged or not, by the default errorHandler.")
    private boolean errorHandlerLogStackTrace = true;
    @UriParam(label = "consumer", description = "Try to apply reconnection logic on consumer pool", defaultValue = "true")
    private boolean reconnectOnError = true;
    @UriParam(label = "consumer", javaType = "java.time.Duration",
              description = "Backoff in millis on consumer pool reconnection attempts", defaultValue = "5000")
    private long reconnectBackOff = 5000;

    @Deprecated
    private volatile boolean closeConnectionResource;

    private JmsObjectFactory jmsObjectFactory = new Jms11ObjectFactory();

    public SjmsEndpoint() {
    }

    public SjmsEndpoint(String uri, Component component, String remaining) {
        super(uri, component);
        // TODO: optimize for dynamic destination name via toD (eg ${ } somewhere)
        this.topic = DestinationNameParser.isTopic(remaining);
        this.destinationName = DestinationNameParser.getShortName(remaining);
    }

    @Override
    public SjmsComponent getComponent() {
        return (SjmsComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        SjmsProducer producer;
        // TODO: Merge inOutProducer and InOnlyProducer into one class so this can be dynamic via exchange MEP
        if (!isDisableReplyTo() && getExchangePattern().equals(ExchangePattern.InOut)) {
            producer = new InOutProducer(this);
        } else {
            producer = new InOnlyProducer(this);
        }
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SjmsConsumer answer = new SjmsConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    @Deprecated
    public ConnectionResource createConnectionResource(Object source) {
        if (getConnectionFactory() == null) {
            throw new IllegalArgumentException(
                    String.format("ConnectionResource or ConnectionFactory must be configured for %s", this));
        }

        try {
            LOG.debug("Creating ConnectionResource with connectionCount: {} using ConnectionFactory: {}",
                    getConnectionCount(), getConnectionFactory());
            // We always use a connection pool, even for a pool of 1
            ConnectionFactoryResource connections = new ConnectionFactoryResource(
                    getConnectionCount(), getConnectionFactory(),
                    getComponent().getConnectionUsername(), getComponent().getConnectionPassword(),
                    getComponent().getConnectionClientId(),
                    getComponent().getConnectionMaxWait(), getComponent().isConnectionTestOnBorrow());
            if (exceptionListener != null) {
                connections.setExceptionListener(exceptionListener);
            } else {
                // add a exception listener that logs so we can see any errors that happens
                ExceptionListener listener = new SjmsLoggingExceptionListener(
                        new LoggingExceptionHandler(getCamelContext(), source.getClass()), isErrorHandlerLogStackTrace());
                connections.setExceptionListener(listener);
            }
            connections.fillPool();
            return connections;
        } catch (Exception e) {
            throw RuntimeCamelException.wrapRuntimeCamelException(e);
        }
    }

    public Exchange createExchange(Message message, Session session) {
        Exchange exchange = createExchange(getExchangePattern());
        exchange.setIn(new SjmsMessage(exchange, message, session, getBinding()));
        return exchange;
    }

    /**
     * When one of the QoS properties are configured such as {@link #setDeliveryPersistent(boolean)},
     * {@link #setPriority(int)} or {@link #setTimeToLive(long)} then we should auto default the setting of
     * {@link #setExplicitQosEnabled(Boolean)} if its not been configured yet
     */
    protected void configuredQoS() {
        if (explicitQosEnabled == null) {
            explicitQosEnabled = true;
        }
    }

    public boolean isPreserveMessageQos() {
        return preserveMessageQos;
    }

    public void setPreserveMessageQos(boolean preserveMessageQos) {
        this.preserveMessageQos = preserveMessageQos;
    }

    public JmsBinding getBinding() {
        if (binding == null) {
            binding = createBinding();
        }
        return binding;
    }

    /**
     * Creates the {@link org.apache.camel.component.sjms.jms.JmsBinding} to use.
     */
    protected JmsBinding createBinding() {
        return new JmsBinding(
                isMapJmsMessage(), isAllowNullBody(), getHeaderFilterStrategy(), getJmsKeyFormatStrategy(),
                getMessageCreatedStrategy());
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS message
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    /**
     * DestinationName is a JMS queue or topic name. By default, the destinationName is interpreted as a queue name.
     */
    public void setDestinationName(String destinationName) {
        this.destinationName = destinationName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new SjmsHeaderFilterStrategy(isIncludeAllJMSXProperties());
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public boolean isIncludeAllJMSXProperties() {
        return includeAllJMSXProperties;
    }

    /**
     * Whether to include all JMSXxxx properties when mapping from JMS to Camel Message. Setting this to true will
     * include properties such as JMSXAppID, and JMSXUserID etc. Note: If you are using a custom headerFilterStrategy
     * then this option does not apply.
     */
    public void setIncludeAllJMSXProperties(boolean includeAllJMSXProperties) {
        this.includeAllJMSXProperties = includeAllJMSXProperties;
    }

    public ConnectionResource getConnectionResource() {
        ConnectionResource answer = null;
        if (connectionResource != null) {
            answer = connectionResource;
        }
        if (answer == null) {
            answer = getComponent().getConnectionResource();
        }
        return answer;
    }

    /**
     * Initializes the connectionResource for the endpoint, which takes precedence over the component's
     * connectionResource, if any
     */
    public void setConnectionResource(ConnectionResource connectionResource) {
        this.connectionResource = connectionResource;
    }

    public void setConnectionResource(String connectionResource) {
        this.connectionResource
                = EndpointHelper.resolveReferenceParameter(getCamelContext(), connectionResource, ConnectionResource.class);
    }

    @Override
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Sets whether synchronous processing should be strictly used or Camel is allowed to use asynchronous processing
     * (if supported).
     */
    @Override
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public SessionAcknowledgementType getAcknowledgementMode() {
        return acknowledgementMode;
    }

    /**
     * The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE,
     * DUPS_OK_ACKNOWLEDGE
     */
    public void setAcknowledgementMode(SessionAcknowledgementType acknowledgementMode) {
        this.acknowledgementMode = acknowledgementMode;
    }

    /**
     * Flag set by the endpoint used by consumers and producers to determine if the endpoint is a JMS Topic.
     */
    public boolean isTopic() {
        return topic;
    }

    public int getConsumerCount() {
        return consumerCount;
    }

    /**
     * Sets the number of consumer listeners used for this endpoint.
     */
    public void setConsumerCount(int consumerCount) {
        this.consumerCount = consumerCount;
    }

    public Boolean getExplicitQosEnabled() {
        return explicitQosEnabled;
    }

    public void setExplicitQosEnabled(Boolean explicitQosEnabled) {
        this.explicitQosEnabled = explicitQosEnabled;
    }

    public boolean isExplicitQosEnabled() {
        return explicitQosEnabled != null ? explicitQosEnabled : false;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
        configuredQoS();
    }

    public boolean isDeliveryPersistent() {
        return deliveryPersistent;
    }

    public void setDeliveryPersistent(boolean deliveryPersistent) {
        this.deliveryPersistent = deliveryPersistent;
        configuredQoS();
    }

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    public String getReplyToOverride() {
        return replyToOverride;
    }

    public void setReplyToOverride(String replyToOverride) {
        this.replyToOverride = replyToOverride;
    }

    public boolean isReplyToDeliveryPersistent() {
        return replyToDeliveryPersistent;
    }

    public void setReplyToDeliveryPersistent(boolean replyToDeliveryPersistent) {
        this.replyToDeliveryPersistent = replyToDeliveryPersistent;
    }

    public Integer getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(Integer deliveryMode) {
        this.deliveryMode = deliveryMode;
        configuredQoS();
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
        configuredQoS();
    }

    public String getDurableSubscriptionId() {
        return durableSubscriptionId;
    }

    /**
     * Sets the durable subscription Id required for durable topics.
     */
    public void setDurableSubscriptionId(String durableSubscriptionId) {
        this.durableSubscriptionId = durableSubscriptionId;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public String getMessageSelector() {
        return messageSelector;
    }

    /**
     * Sets the JMS Message selector syntax.
     */
    public void setMessageSelector(String messageSelector) {
        this.messageSelector = messageSelector;
    }

    public TransactionCommitStrategy getTransactionCommitStrategy() {
        return transactionCommitStrategy;
    }

    /**
     * Sets the commit strategy.
     */
    public void setTransactionCommitStrategy(TransactionCommitStrategy transactionCommitStrategy) {
        this.transactionCommitStrategy = transactionCommitStrategy;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Specifies whether to use transacted mode
     */
    public void setTransacted(boolean transacted) {
        if (transacted) {
            setAcknowledgementMode(SessionAcknowledgementType.SESSION_TRANSACTED);
        }
        this.transacted = transacted;
    }

    public boolean isSharedJMSSession() {
        return sharedJMSSession;
    }

    /**
     * Specifies whether to share JMS session with other SJMS endpoints. Turn this off if your route is accessing to
     * multiple JMS providers. If you need transaction against multiple JMS providers, use jms component to leverage XA
     * transaction.
     */
    public void setSharedJMSSession(boolean share) {
        this.sharedJMSSession = share;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
        setExchangePattern(ExchangePattern.InOut);
    }

    /**
     * Whether to startup the consumer message listener asynchronously, when starting a route. For example if a
     * JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying and/or failover.
     * This will cause Camel to block while starting routes. By setting this option to true, you will let routes
     * startup, while the JmsConsumer connects to the JMS broker using a dedicated thread in asynchronous mode. If this
     * option is used, then beware that if the connection could not be established, then an exception is logged at WARN
     * level, and the consumer will not be able to receive messages; You can then restart the route to retry.
     */
    public void setAsyncStartListener(boolean asyncStartListener) {
        this.asyncStartListener = asyncStartListener;
    }

    /**
     * Whether to stop the consumer message listener asynchronously, when stopping a route.
     */
    public void setAsyncStopListener(boolean asyncStopListener) {
        this.asyncStopListener = asyncStopListener;
    }

    public boolean isAsyncStartListener() {
        return asyncStartListener;
    }

    public boolean isAsyncStopListener() {
        return asyncStopListener;
    }

    public DestinationCreationStrategy getDestinationCreationStrategy() {
        return destinationCreationStrategy;
    }

    /**
     * To use a custom DestinationCreationStrategy.
     */
    public void setDestinationCreationStrategy(DestinationCreationStrategy destinationCreationStrategy) {
        this.destinationCreationStrategy = destinationCreationStrategy;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    /**
     * Whether to allow sending messages with no body. If this option is false and the message body is null, then an
     * JMSException is thrown.
     */
    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }

    public boolean isMapJmsMessage() {
        return mapJmsMessage;
    }

    /**
     * Specifies whether Camel should auto map the received JMS message to a suited payload type, such as
     * javax.jms.TextMessage to a String etc. See section about how mapping works below for more details.
     */
    public void setMapJmsMessage(boolean mapJmsMessage) {
        this.mapJmsMessage = mapJmsMessage;
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return messageCreatedStrategy;
    }

    /**
     * To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of
     * <tt>javax.jms.Message</tt> objects when Camel is sending a JMS message.
     */
    public void setMessageCreatedStrategy(MessageCreatedStrategy messageCreatedStrategy) {
        this.messageCreatedStrategy = messageCreatedStrategy;
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        if (jmsKeyFormatStrategy == null) {
            jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
        }
        return jmsKeyFormatStrategy;
    }

    /**
     * Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification. Camel
     * provides two implementations out of the box: default and passthrough. The default strategy will safely marshal
     * dots and hyphens (. and -). The passthrough strategy leaves the key as is. Can be used for JMS brokers which do
     * not care whether JMS header keys contain illegal characters. You can provide your own implementation of the
     * org.apache.camel.component.jms.JmsKeyFormatStrategy and refer to it using the # notation.
     */
    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        this.jmsKeyFormatStrategy = jmsKeyFormatStrategy;
    }

    /**
     * Initializes the connectionFactory for the endpoint, which takes precedence over the component's
     * connectionFactory, if any
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public void setConnectionFactory(String connectionFactory) {
        this.connectionFactory
                = EndpointHelper.resolveReferenceParameter(getCamelContext(), connectionFactory, ConnectionFactory.class);
    }

    public ConnectionFactory getConnectionFactory() {
        if (connectionFactory != null) {
            return connectionFactory;
        }
        return getComponent().getConnectionFactory();
    }

    public int getConnectionCount() {
        if (connectionCount != null) {
            return connectionCount;
        }
        return getComponent().getConnectionCount();
    }

    /**
     * The maximum number of connections available to this endpoint
     */
    public void setConnectionCount(Integer connectionCount) {
        this.connectionCount = connectionCount;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    /**
     * Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.
     */
    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    public LoggingLevel getErrorHandlerLoggingLevel() {
        return errorHandlerLoggingLevel;
    }

    /**
     * Allows to configure the default errorHandler logging level for logging uncaught exceptions.
     */
    public void setErrorHandlerLoggingLevel(LoggingLevel errorHandlerLoggingLevel) {
        this.errorHandlerLoggingLevel = errorHandlerLoggingLevel;
    }

    public boolean isErrorHandlerLogStackTrace() {
        return errorHandlerLogStackTrace;
    }

    /**
     * Allows to control whether stacktraces should be logged or not, by the default errorHandler.
     */
    public void setErrorHandlerLogStackTrace(boolean errorHandlerLogStackTrace) {
        this.errorHandlerLogStackTrace = errorHandlerLogStackTrace;
    }

    public JmsObjectFactory getJmsObjectFactory() {
        return jmsObjectFactory;
    }

    /**
     * To use a custom Jms Object factory
     */
    public void setJmsObjectFactory(JmsObjectFactory jmsObjectFactory) {
        this.jmsObjectFactory = jmsObjectFactory;
    }

    public boolean isReconnectOnError() {
        return reconnectOnError;
    }

    /**
     * Try to apply reconnection logic on consumer pool
     */
    public void setReconnectOnError(boolean reconnectOnError) {
        this.reconnectOnError = reconnectOnError;
    }

    public long getReconnectBackOff() {
        return reconnectBackOff;
    }

    /**
     * Backoff in millis on consumer pool reconnection attempts
     */
    public void setReconnectBackOff(long reconnectBackOff) {
        this.reconnectBackOff = reconnectBackOff;
    }
}
