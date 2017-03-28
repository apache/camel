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
package org.apache.camel.component.sjms;

import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
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
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.LoggingExceptionHandler;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The sjms component (simple jms) allows messages to be sent to (or consumed from) a JMS Queue or Topic (uses JMS 1.x API).
 *
 * This component uses plain JMS API where as the jms component uses Spring JMS.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "sjms", title = "Simple JMS", syntax = "sjms:destinationType:destinationName", consumerClass = SjmsConsumer.class, label = "messaging")
public class SjmsEndpoint extends DefaultEndpoint implements AsyncEndpoint, MultipleConsumersSupport, HeaderFilterStrategyAware {
    protected final Logger logger = LoggerFactory.getLogger(getClass());

    private boolean topic;

    private JmsBinding binding;

    @UriPath(enums = "queue,topic", defaultValue = "queue", description = "The kind of destination to use")
    private String destinationType;
    @UriPath(description = "DestinationName is a JMS queue or topic name. By default, the destinationName is interpreted as a queue name.") @Metadata(required = "true")
    private String destinationName;
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
    @UriParam(label = "consumer,transaction",
            description = "Specifies whether to use transacted mode")
    private boolean transacted;
    @UriParam(label = "transaction,advanced", defaultValue = "true",
            description = "Specifies whether to share JMS session with other SJMS endpoints. Turn this off if your route is accessing to multiple JMS providers."
                + " If you need transaction against multiple JMS providers, use jms component to leverage XA transaction.")
    private boolean sharedJMSSession = true;
    @UriParam(label = "producer",
            description = "Sets the reply to destination name used for InOut producer endpoints.")
    private String namedReplyTo;
    @UriParam(defaultValue = "AUTO_ACKNOWLEDGE", enums = "SESSION_TRANSACTED,CLIENT_ACKNOWLEDGE,AUTO_ACKNOWLEDGE,DUPS_OK_ACKNOWLEDGE",
            description = "The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE")
    private SessionAcknowledgementType acknowledgementMode = SessionAcknowledgementType.AUTO_ACKNOWLEDGE;
    @Deprecated
    private int sessionCount = 1;
    @UriParam(label = "producer", defaultValue = "1",
            description = "Sets the number of producers used for this endpoint.")
    private int producerCount = 1;
    @UriParam(label = "consumer", defaultValue = "1",
            description = "Sets the number of consumer listeners used for this endpoint.")
    private int consumerCount = 1;
    @UriParam(label = "producer", defaultValue = "-1",
            description = "Flag used to adjust the Time To Live value of produced messages.")
    private long ttl = -1;
    @UriParam(label = "producer", defaultValue = "true",
            description = "Flag used to enable/disable message persistence.")
    private boolean persistent = true;
    @UriParam(label = "consumer",
            description = "Sets the durable subscription Id required for durable topics.")
    private String durableSubscriptionId;
    @UriParam(label = "producer,advanced", defaultValue = "5000",
            description = "Sets the amount of time we should wait before timing out a InOut response.")
    private long responseTimeOut = 5000;
    @UriParam(label = "consumer,advanced",
            description = "Sets the JMS Message selector syntax.")
    private String messageSelector;
    @UriParam(label = "consumer,transaction", defaultValue = "-1",
            description = "If transacted sets the number of messages to process before committing a transaction.")
    private int transactionBatchCount = -1;
    @UriParam(label = "consumer,transaction", defaultValue = "5000",
            description = "Sets timeout (in millis) for batch transactions, the value should be 1000 or higher.")
    private long transactionBatchTimeout = 5000;
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
            description = "Whether to prefill the producer connection pool on startup, or create connections lazy when needed.")
    private boolean prefillPool = true;
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
                + "Camel provides two implementations out of the box: default and passthrough. The default strategy will safely marshal dots and hyphens (. and -)."
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

    private volatile boolean closeConnectionResource;

    private JmsObjectFactory jmsObjectFactory = new Jms11ObjectFactory();

    public SjmsEndpoint() {
    }

    public SjmsEndpoint(String uri, Component component, String remaining) {
        super(uri, component);
        DestinationNameParser parser = new DestinationNameParser();
        this.topic = parser.isTopic(remaining);
        this.destinationName = parser.getShortName(remaining);
    }

    @Override
    public SjmsComponent getComponent() {
        return (SjmsComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (!isAsyncStartListener()) {
            // if we are not async starting then create connection eager
            if (getConnectionResource() == null) {
                if (getConnectionFactory() != null) {
                    connectionResource = createConnectionResource(this);
                    // we created the resource so we should close it when stopping
                    closeConnectionResource = true;
                }
            } else if (getConnectionResource() instanceof ConnectionFactoryResource) {
                ((ConnectionFactoryResource) getConnectionResource()).fillPool();
            }
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (closeConnectionResource) {
            if (connectionResource instanceof ConnectionFactoryResource) {
                ((ConnectionFactoryResource) getConnectionResource()).drainPool();
            }
            closeConnectionResource = false;
            connectionResource = null;
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        SjmsProducer producer;
        if (getExchangePattern().equals(ExchangePattern.InOnly)) {
            producer = new InOnlyProducer(this);
        } else {
            producer = new InOutProducer(this);
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

    @Override
    public boolean isSingleton() {
        return true;
    }

    protected ConnectionResource createConnectionResource(Object source) {
        if (getConnectionFactory() == null) {
            throw new IllegalArgumentException(String.format("ConnectionResource or ConnectionFactory must be configured for %s", this));
        }

        try {
            logger.debug("Creating ConnectionResource with connectionCount: {} using ConnectionFactory", getConnectionCount(), getConnectionFactory());
            // We always use a connection pool, even for a pool of 1
            ConnectionFactoryResource connections = new ConnectionFactoryResource(getConnectionCount(), getConnectionFactory(),
                getComponent().getConnectionUsername(), getComponent().getConnectionPassword(), getComponent().getConnectionClientId(),
                getComponent().getConnectionMaxWait(), getComponent().isConnectionTestOnBorrow());
            if (exceptionListener != null) {
                connections.setExceptionListener(exceptionListener);
            } else {
                // add a exception listener that logs so we can see any errors that happens
                ExceptionListener listener = new SjmsLoggingExceptionListener(new LoggingExceptionHandler(getCamelContext(), source.getClass()), isErrorHandlerLogStackTrace());
                connections.setExceptionListener(listener);
            }
            connections.fillPool();
            return connections;
        } catch (Exception e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
    }

    public Exchange createExchange(Message message, Session session) {
        Exchange exchange = createExchange(getExchangePattern());
        exchange.setIn(new SjmsMessage(message, session, getBinding()));
        return exchange;
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
        return new JmsBinding(isMapJmsMessage(), isAllowNullBody(), getHeaderFilterStrategy(), getJmsKeyFormatStrategy(), getMessageCreatedStrategy());
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS
     * message
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

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        if (headerFilterStrategy == null) {
            headerFilterStrategy = new SjmsHeaderFilterStrategy(isIncludeAllJMSXProperties());
        }
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public boolean isIncludeAllJMSXProperties() {
        return includeAllJMSXProperties;
    }

    /**
     * Whether to include all JMSXxxx properties when mapping from JMS to Camel Message.
     * Setting this to true will include properties such as JMSXAppID, and JMSXUserID etc.
     * Note: If you are using a custom headerFilterStrategy then this option does not apply.
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
     * Initializes the connectionResource for the endpoint, which takes precedence over the component's connectionResource, if any
     */
    public void setConnectionResource(String connectionResource) {
        this.connectionResource = EndpointHelper.resolveReferenceParameter(getCamelContext(), connectionResource, ConnectionResource.class);
    }


    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Sets whether synchronous processing should be strictly used or Camel is allowed to use asynchronous processing (if supported).
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public SessionAcknowledgementType getAcknowledgementMode() {
        return acknowledgementMode;
    }

    /**
     * The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     */
    public void setAcknowledgementMode(SessionAcknowledgementType acknowledgementMode) {
        this.acknowledgementMode = acknowledgementMode;
    }

    /**
     * Flag set by the endpoint used by consumers and producers to determine if
     * the endpoint is a JMS Topic.
     */
    public boolean isTopic() {
        return topic;
    }

    /**
     * Returns the number of Session instances expected on this endpoint.
     */
    @Deprecated
    public int getSessionCount() {
        return sessionCount;
    }

    /**
     * Sets the number of Session instances used for this endpoint. Value is
     * ignored for endpoints that require a dedicated session such as a
     * transacted or InOut endpoint.
     *
     * @param sessionCount the number of Session instances, default is 1
     */
    @Deprecated
    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public int getProducerCount() {
        return producerCount;
    }

    /**
     * Sets the number of producers used for this endpoint.
     */
    public void setProducerCount(int producerCount) {
        this.producerCount = producerCount;
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

    public long getTtl() {
        return ttl;
    }

    /**
     * Flag used to adjust the Time To Live value of produced messages.
     */
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Flag used to enable/disable message persistence.
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
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

    public long getResponseTimeOut() {
        return responseTimeOut;
    }

    /**
     * Sets the amount of time we should wait before timing out a InOut response.
     */
    public void setResponseTimeOut(long responseTimeOut) {
        this.responseTimeOut = responseTimeOut;
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

    public int getTransactionBatchCount() {
        return transactionBatchCount;
    }

    /**
     * If transacted sets the number of messages to process before committing a transaction.
     */
    public void setTransactionBatchCount(int transactionBatchCount) {
        this.transactionBatchCount = transactionBatchCount;
    }

    public long getTransactionBatchTimeout() {
        return transactionBatchTimeout;
    }

    /**
     * Sets timeout (in millis) for batch transactions, the value should be 1000 or higher.
     */
    public void setTransactionBatchTimeout(long transactionBatchTimeout) {
        if (transactionBatchTimeout >= 1000) {
            this.transactionBatchTimeout = transactionBatchTimeout;
        }
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
     * Specifies whether to share JMS session with other SJMS endpoints.
     * Turn this off if your route is accessing to multiple JMS providers.
     * If you need transaction against multiple JMS providers, use jms
     * component to leverage XA transaction.
     */
    public void setSharedJMSSession(boolean share) {
        this.sharedJMSSession = share;
    }

    public String getNamedReplyTo() {
        return namedReplyTo;
    }

    /**
     * Sets the reply to destination name used for InOut producer endpoints.
     */
    public void setNamedReplyTo(String namedReplyTo) {
        this.namedReplyTo = namedReplyTo;
        this.setExchangePattern(ExchangePattern.InOut);
    }

    /**
     * Whether to startup the consumer message listener asynchronously, when starting a route.
     * For example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying
     * and/or failover. This will cause Camel to block while starting routes. By setting this option to true,
     * you will let routes startup, while the JmsConsumer connects to the JMS broker using a dedicated thread
     * in asynchronous mode. If this option is used, then beware that if the connection could not be established,
     * then an exception is logged at WARN level, and the consumer will not be able to receive messages;
     * You can then restart the route to retry.
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

    public boolean isPrefillPool() {
        return prefillPool;
    }

    /**
     * Whether to prefill the producer connection pool on startup, or create connections lazy when needed.
     */
    public void setPrefillPool(boolean prefillPool) {
        this.prefillPool = prefillPool;
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
     * Whether to allow sending messages with no body. If this option is false and the message body is null, then an JMSException is thrown.
     */
    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }

    public boolean isMapJmsMessage() {
        return mapJmsMessage;
    }

    /**
     * Specifies whether Camel should auto map the received JMS message to a suited payload type, such as javax.jms.TextMessage to a String etc.
     * See section about how mapping works below for more details.
     */
    public void setMapJmsMessage(boolean mapJmsMessage) {
        this.mapJmsMessage = mapJmsMessage;
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return messageCreatedStrategy;
    }

    /**
     * To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of <tt>javax.jms.Message</tt>
     * objects when Camel is sending a JMS message.
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
     * Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification.
     * Camel provides two implementations out of the box: default and passthrough.
     * The default strategy will safely marshal dots and hyphens (. and -). The passthrough strategy leaves the key as is.
     * Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters.
     * You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy
     * and refer to it using the # notation.
     */
    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        this.jmsKeyFormatStrategy = jmsKeyFormatStrategy;
    }

    /**
     * Initializes the connectionFactory for the endpoint, which takes precedence over the component's connectionFactory, if any
     */
    public void setConnectionFactory(String connectionFactory) {
        this.connectionFactory = EndpointHelper.resolveReferenceParameter(getCamelContext(), connectionFactory, ConnectionFactory.class);

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
}
