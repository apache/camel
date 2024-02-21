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

import java.util.concurrent.ExecutorService;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.DeliveryMode;
import jakarta.jms.ExceptionListener;
import jakarta.jms.Message;
import jakarta.jms.Session;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sjms.consumer.EndpointMessageListener;
import org.apache.camel.component.sjms.consumer.SimpleMessageListenerContainer;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DestinationNameParser;
import org.apache.camel.component.sjms.jms.Jms11ObjectFactory;
import org.apache.camel.component.sjms.jms.JmsBinding;
import org.apache.camel.component.sjms.jms.JmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.jms.MessageCreatedStrategy;
import org.apache.camel.component.sjms.jms.SessionAcknowledgementType;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.SynchronousDelegateProducer;
import org.apache.camel.util.StringHelper;

/**
 * Send and receive messages to/from a JMS Queue or Topic using plain JMS 1.x API.
 *
 * This component uses plain JMS API, whereas the jms component uses Spring JMS.
 */
@UriEndpoint(firstVersion = "2.11.0", scheme = "sjms", title = "Simple JMS", syntax = "sjms:destinationType:destinationName",
             category = { Category.MESSAGING }, headersClass = SjmsConstants.class)
public class SjmsEndpoint extends DefaultEndpoint
        implements AsyncEndpoint, MultipleConsumersSupport, HeaderFilterStrategyAware {

    private boolean topic;
    private JmsBinding binding;

    @UriPath(enums = "queue,topic", defaultValue = "queue", description = "The kind of destination to use")
    private String destinationType;
    @UriPath(description = "DestinationName is a JMS queue or topic name. By default, the destinationName is interpreted as a queue name.")
    @Metadata(required = true)
    private String destinationName;
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
    @UriParam(label = "common",
              description = "Provides an explicit ReplyTo destination (overrides any incoming value of Message.getJMSReplyTo() in consumer).")
    private String replyTo;
    @UriParam(label = "producer",
              description = "Allows for explicitly specifying which kind of strategy to use for replyTo queues when doing request/reply over JMS."
                            + " Possible values are: Temporary or Exclusive."
                            + " By default Camel will use temporary queues. However if replyTo has been configured, then Exclusive is used.")
    private ReplyToType replyToType;
    @UriParam(defaultValue = "AUTO_ACKNOWLEDGE",
              enums = "SESSION_TRANSACTED,CLIENT_ACKNOWLEDGE,AUTO_ACKNOWLEDGE,DUPS_OK_ACKNOWLEDGE",
              description = "The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE")
    private SessionAcknowledgementType acknowledgementMode = SessionAcknowledgementType.AUTO_ACKNOWLEDGE;
    @UriParam(defaultValue = "1", label = "consumer",
              description = "Specifies the default number of concurrent consumers when consuming from JMS (not for request/reply over JMS)."
                            + " See also the maxMessagesPerTask option to control dynamic scaling up/down of threads."
                            + " When doing request/reply over JMS then the option replyToConcurrentConsumers is used to control number"
                            + " of concurrent consumers on the reply message listener.")
    private int concurrentConsumers = 1;
    @UriParam(defaultValue = "1", label = "producer",
              description = "Specifies the default number of concurrent consumers when doing request/reply over JMS."
                            + " See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.")
    private int replyToConcurrentConsumers = 1;
    @UriParam(label = "producer,advanced", defaultValue = "false",
              description = "Set if the deliveryMode, priority or timeToLive qualities of service should be used when sending messages."
                            + " This option is based on Spring's JmsTemplate. The deliveryMode, priority and timeToLive options are applied to the current endpoint."
                            + " This contrasts with the preserveMessageQos option, which operates at message granularity,"
                            + " reading QoS properties exclusively from the Camel In message headers.")
    private Boolean explicitQosEnabled;
    @UriParam(label = "producer,advanced",
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
    @UriParam(label = "consumer,advanced", defaultValue = "Poison JMS message due to ${exception.message}",
              description = "If eagerLoadingOfProperties is enabled and the JMS message payload (JMS body or JMS properties) is poison (cannot be read/mapped),"
                            + " then set this text as the message body instead so the message can be processed"
                            + " (the cause of the poison are already stored as exception on the Exchange)."
                            + " This can be turned off by setting eagerPoisonBody=false."
                            + " See also the option eagerLoadingOfProperties.")
    private String eagerPoisonBody = "Poison JMS message payload: ${exception.message}";
    @UriParam(label = "consumer,advanced",
              description = "Enables eager loading of JMS properties and payload as soon as a message is loaded"
                            + " which generally is inefficient as the JMS properties may not be required"
                            + " but sometimes can catch early any issues with the underlying JMS provider"
                            + " and the use of JMS properties. See also the option eagerPoisonBody.")
    private boolean eagerLoadingOfProperties;
    @UriParam(enums = "1,2", label = "producer",
              description = "Specifies the delivery mode to be used."
                            + " Possible values are those defined by jakarta.jms.DeliveryMode."
                            + " NON_PERSISTENT = 1 and PERSISTENT = 2.")
    private Integer deliveryMode;
    @UriParam(defaultValue = "-1", label = "producer",
              description = "When sending messages, specifies the time-to-live of the message (in milliseconds).")
    private long timeToLive = -1;
    @UriParam(label = "consumer",
              description = "Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only be used by a single JMS connection instance."
                            + " It is typically only required for durable topic subscriptions."
                            + " If using Apache ActiveMQ you may prefer to use Virtual Topics instead.")
    private String clientId;
    @UriParam(label = "consumer",
              description = "The durable subscriber name for specifying durable topic subscriptions. The clientId option must be configured as well.")
    private String durableSubscriptionName;
    @UriParam(defaultValue = "20000", label = "producer", javaType = "java.time.Duration",
              description = "The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds)."
                            + " The default is 20 seconds. You can include the header \"CamelJmsRequestTimeout\" to override this endpoint configured"
                            + " timeout value, and thus have per message individual timeout values."
                            + " See also the requestTimeoutCheckerInterval option.")
    private long requestTimeout = 20000L;
    @UriParam(label = "consumer,advanced",
              description = "Sets the JMS Message selector syntax.")
    private String messageSelector;
    @UriParam(description = "Specifies whether to test the connection on startup."
                            + " This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker."
                            + " If a connection cannot be granted then Camel throws an exception on startup."
                            + " This ensures that Camel is not started with failed connections."
                            + " The JMS producers is tested as well.")
    private boolean testConnectionOnStartup;
    @UriParam(label = "advanced",
              description = "Whether to startup the consumer message listener asynchronously, when starting a route."
                            + " For example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying and/or fail over."
                            + " This will cause Camel to block while starting routes. By setting this option to true, you will let routes startup, while the JmsConsumer connects to the JMS broker"
                            + " using a dedicated thread in asynchronous mode. If this option is used, then beware that if the connection could not be established, then an exception is logged at WARN level,"
                            + " and the consumer will not be able to receive messages; You can then restart the route to retry.")
    private boolean asyncStartListener;
    @UriParam(label = "advanced",
              description = "Whether to stop the consumer message listener asynchronously, when stopping a route.")
    private boolean asyncStopListener;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Specifies whether the consumer container should auto-startup.")
    private boolean autoStartup = true;
    @UriParam(label = "consumer,advanced",
              description = "Whether a JMS consumer is allowed to send a reply message to the same destination that the consumer is using to"
                            + " consume from. This prevents an endless loop by consuming and sending back the same message to itself.")
    private boolean replyToSameDestinationAllowed;
    @UriParam(label = "producer,advanced", defaultValue = "true",
              description = "Whether to allow sending messages with no body. If this option is false and the message body is null, then an JMSException is thrown.")
    private boolean allowNullBody = true;
    @UriParam(label = "advanced", defaultValue = "true",
              description = "Specifies whether Camel should auto map the received JMS message to a suited payload type, such as jakarta.jms.TextMessage to a String etc."
                            + " See section about how mapping works below for more details.")
    private boolean mapJmsMessage = true;
    @UriParam(label = "advanced",
              description = "To use a custom DestinationCreationStrategy.")
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();
    @UriParam(label = "advanced",
              description = "To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of <tt>jakarta.jms.Message</tt> objects when Camel is sending a JMS message.")
    private MessageCreatedStrategy messageCreatedStrategy;
    @UriParam(label = "advanced",
              description = "Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification."
                            + " Camel provides two implementations out of the box: default and passthrough. The default strategy will safely marshal dots and hyphens (. and -)."
                            + " The passthrough strategy leaves the key as is. Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters."
                            + " You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy and refer to it using the # notation.")
    private JmsKeyFormatStrategy jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
    @UriParam(label = "common",
              description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @UriParam(label = "advanced",
              description = "Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.")
    private ExceptionListener exceptionListener;
    @UriParam(defaultValue = "5000", label = "advanced", javaType = "java.time.Duration",
              description = "Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds."
                            + " The default is 5000 ms, that is, 5 seconds.")
    private long recoveryInterval = 5000;
    @UriParam(label = "advanced",
              description = "If enabled and you are using Request Reply messaging (InOut) and an Exchange failed on the consumer side,"
                            + " then the caused Exception will be send back in response as a jakarta.jms.ObjectMessage."
                            + " If the client is Camel, the returned Exception is rethrown. This allows you to use Camel JMS as a bridge"
                            + " in your routing - for example, using persistent queues to enable robust routing."
                            + " Notice that if you also have transferExchange enabled, this option takes precedence."
                            + " The caught exception is required to be serializable."
                            + " The original Exception on the consumer side can be wrapped in an outer exception"
                            + " such as org.apache.camel.RuntimeCamelException when returned to the producer."
                            + " Use this with caution as the data is using Java Object serialization and requires the received to be able to deserialize the data at Class level, "
                            + " which forces a strong coupling between the producers and consumer!")
    private boolean transferException;
    @UriParam(label = "producer,advanced",
              description = "Use this option to force disabling time to live."
                            + " For example when you do request/reply over JMS, then Camel will by default use the requestTimeout value"
                            + " as time to live on the message being sent. The problem is that the sender and receiver systems have"
                            + " to have their clocks synchronized, so they are in sync. This is not always so easy to archive."
                            + " So you can use disableTimeToLive=true to not set a time to live value on the sent message."
                            + " Then the message will not expire on the receiver system. See below in section About time to live for more details.")
    private boolean disableTimeToLive;
    @UriParam(label = "consumer",
              description = "Whether the JmsConsumer processes the Exchange asynchronously."
                            + " If enabled then the JmsConsumer may pickup the next message from the JMS queue,"
                            + " while the previous message is being processed asynchronously (by the Asynchronous Routing Engine)."
                            + " This means that messages may be processed not 100% strictly in order. If disabled (as default)"
                            + " then the Exchange is fully processed before the JmsConsumer will pickup the next message from the JMS queue."
                            + " Note if transacted has been enabled, then asyncConsumer=true does not run asynchronously, as transaction"
                            + "  must be executed synchronously (Camel 3.0 may support async transactions).")
    private boolean asyncConsumer;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;

    private JmsObjectFactory jmsObjectFactory = new Jms11ObjectFactory();

    public SjmsEndpoint() {
    }

    public SjmsEndpoint(String uri, Component component, String remaining) {
        super(uri, component);
        this.topic = DestinationNameParser.isTopic(remaining);
        this.destinationName = DestinationNameParser.getShortName(remaining);
    }

    @Override
    public SjmsComponent getComponent() {
        return (SjmsComponent) super.getComponent();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (headerFilterStrategy == null) {
            headerFilterStrategy = new SjmsHeaderFilterStrategy(includeAllJMSXProperties);
        }
    }

    /**
     * Gets the destination name which was configured from the endpoint uri.
     *
     * @return the destination name resolved from the endpoint uri
     */
    public String getEndpointConfiguredDestinationName() {
        String remainder = StringHelper.after(getEndpointKey(), "//");
        if (remainder != null && remainder.contains("?")) {
            // remove parameters
            remainder = StringHelper.before(remainder, "?");
        }
        return JmsMessageHelper.normalizeDestinationName(remainder);
    }

    @Override
    public Producer createProducer() throws Exception {
        if (isTransacted() && getExchangePattern().isOutCapable()) {
            throw new IllegalArgumentException("SjmsProducer cannot be both transacted=true and exchangePattern=InOut");
        }

        Producer answer = new SjmsProducer(this);
        if (isSynchronous()) {
            return new SynchronousDelegateProducer(answer);
        } else {
            return answer;
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        MessageListenerContainer container = createMessageListenerContainer(this);
        SjmsConsumer consumer = new SjmsConsumer(this, processor, container);

        EndpointMessageListener listener = new EndpointMessageListener(consumer, this, processor);
        configureMessageListener(listener);
        container.setMessageListener(listener);

        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        SjmsPollingConsumer answer = new SjmsPollingConsumer(this, createInOnlyTemplate());
        configurePollingConsumer(answer);
        return answer;
    }

    public void configureMessageListener(EndpointMessageListener listener) {
        if (isDisableReplyTo()) {
            listener.setDisableReplyTo(true);
        }
        if (!"false".equals(eagerPoisonBody)) {
            listener.setEagerPoisonBody(eagerPoisonBody);
        }
        listener.setEagerLoadingOfProperties(eagerLoadingOfProperties);
        if (getReplyTo() != null) {
            listener.setReplyToDestination(getReplyTo());
        }
        listener.setAsync(isAsyncConsumer());
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    public Exchange createExchange(Message message, Session session) {
        Exchange exchange = createExchange(getExchangePattern());
        exchange.setIn(new SjmsMessage(exchange, message, session, getBinding()));
        return exchange;
    }

    /**
     * Factory method for creating a new template for InOnly message exchanges
     */
    public SjmsTemplate createInOnlyTemplate() {
        SjmsTemplate template = new SjmsTemplate(getConnectionFactory(), isTransacted(), getAcknowledgementMode().intValue());

        // configure qos if enabled
        if (isExplicitQosEnabled()) {
            int dm = isDeliveryPersistent() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
            if (getDeliveryMode() != null) {
                dm = getDeliveryMode();
            }
            template.setQoSSettings(dm, getPriority(), getTimeToLive());
        }
        template.setDestinationCreationStrategy(getDestinationCreationStrategy());

        return template;
    }

    /**
     * Factory method for creating a new template for InOut message exchanges
     */
    public SjmsTemplate createInOutTemplate() {
        // must be auto-ack mode for InOut (request/reply mode)
        SjmsTemplate template = new SjmsTemplate(getConnectionFactory(), false, Session.AUTO_ACKNOWLEDGE);

        // configure qos if enabled
        if (isExplicitQosEnabled()) {
            int dm = isDeliveryPersistent() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
            if (getDeliveryMode() != null) {
                dm = getDeliveryMode();
            }
            template.setQoSSettings(dm, getPriority(), getTimeToLive());
        }
        if (getRequestTimeout() > 0) {
            template.setExplicitQosEnabled(true);

            // prefer to use timeToLive over requestTimeout if both specified
            long ttl = getTimeToLive() > 0 ? getTimeToLive() : getRequestTimeout();
            if (!isDisableTimeToLive()) {
                // only use TTL if not disabled
                template.setQoSSettings(0, 0, ttl);
            }
        }
        template.setDestinationCreationStrategy(getDestinationCreationStrategy());
        template.setPreserveMessageQos(isPreserveMessageQos());

        return template;
    }

    public MessageListenerContainer createMessageListenerContainer(SjmsEndpoint endpoint) {
        SimpleMessageListenerContainer answer = new SimpleMessageListenerContainer(endpoint);
        answer.setConcurrentConsumers(concurrentConsumers);
        return answer;
    }

    /**
     * When one of the QoS properties are configured such as {@link #setDeliveryPersistent(boolean)},
     * {@link #setPriority(int)} or {@link #setTimeToLive(long)} then we should auto default the setting of
     * {@link #setExplicitQosEnabled(Boolean)} if it has not been configured yet
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

    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    protected ExecutorService getAsyncStartStopExecutorService() {
        if (getComponent() == null) {
            throw new IllegalStateException(
                    "AsyncStartStopListener requires JmsComponent to be configured on this endpoint: " + this);
        }
        // use shared thread pool from component
        return getComponent().getAsyncStartStopExecutorService();
    }

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

    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy strategy) {
        this.headerFilterStrategy = strategy;
    }

    public boolean isIncludeAllJMSXProperties() {
        return includeAllJMSXProperties;
    }

    public void setIncludeAllJMSXProperties(boolean includeAllJMSXProperties) {
        this.includeAllJMSXProperties = includeAllJMSXProperties;
    }

    public SessionAcknowledgementType getAcknowledgementMode() {
        return acknowledgementMode;
    }

    public void setAcknowledgementMode(SessionAcknowledgementType acknowledgementMode) {
        this.acknowledgementMode = acknowledgementMode;
    }

    public boolean isTopic() {
        return topic;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getReplyToConcurrentConsumers() {
        return replyToConcurrentConsumers;
    }

    public void setReplyToConcurrentConsumers(int replyToConcurrentConsumers) {
        this.replyToConcurrentConsumers = replyToConcurrentConsumers;
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

    public String getEagerPoisonBody() {
        return eagerPoisonBody;
    }

    public boolean isEagerLoadingOfProperties() {
        return eagerLoadingOfProperties;
    }

    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        this.eagerLoadingOfProperties = eagerLoadingOfProperties;
    }

    public void setEagerPoisonBody(String eagerPoisonBody) {
        this.eagerPoisonBody = eagerPoisonBody;
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

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getDurableSubscriptionName() {
        return durableSubscriptionName;
    }

    public void setDurableSubscriptionName(String durableSubscriptionName) {
        this.durableSubscriptionName = durableSubscriptionName;
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

    public void setMessageSelector(String messageSelector) {
        this.messageSelector = messageSelector;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public void setTransacted(boolean transacted) {
        if (transacted) {
            setAcknowledgementMode(SessionAcknowledgementType.SESSION_TRANSACTED);
        }
        this.transacted = transacted;
    }

    public String getReplyTo() {
        return replyTo;
    }

    public void setReplyTo(String replyTo) {
        this.replyTo = replyTo;
        setExchangePattern(ExchangePattern.InOut);
    }

    public ReplyToType getReplyToType() {
        return replyToType;
    }

    public void setReplyToType(ReplyToType replyToType) {
        this.replyToType = replyToType;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public void setAsyncStartListener(boolean asyncStartListener) {
        this.asyncStartListener = asyncStartListener;
    }

    public void setAsyncStopListener(boolean asyncStopListener) {
        this.asyncStopListener = asyncStopListener;
    }

    public boolean isAsyncStartListener() {
        return asyncStartListener;
    }

    public boolean isAsyncStopListener() {
        return asyncStopListener;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public DestinationCreationStrategy getDestinationCreationStrategy() {
        return destinationCreationStrategy;
    }

    public void setDestinationCreationStrategy(DestinationCreationStrategy destinationCreationStrategy) {
        this.destinationCreationStrategy = destinationCreationStrategy;
    }

    public boolean isReplyToSameDestinationAllowed() {
        return replyToSameDestinationAllowed;
    }

    public void setReplyToSameDestinationAllowed(boolean replyToSameDestinationAllowed) {
        this.replyToSameDestinationAllowed = replyToSameDestinationAllowed;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }

    public boolean isMapJmsMessage() {
        return mapJmsMessage;
    }

    public void setMapJmsMessage(boolean mapJmsMessage) {
        this.mapJmsMessage = mapJmsMessage;
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return messageCreatedStrategy;
    }

    public void setMessageCreatedStrategy(MessageCreatedStrategy messageCreatedStrategy) {
        this.messageCreatedStrategy = messageCreatedStrategy;
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        return jmsKeyFormatStrategy;
    }

    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        this.jmsKeyFormatStrategy = jmsKeyFormatStrategy;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    public JmsObjectFactory getJmsObjectFactory() {
        return jmsObjectFactory;
    }

    public void setJmsObjectFactory(JmsObjectFactory jmsObjectFactory) {
        this.jmsObjectFactory = jmsObjectFactory;
    }

    public boolean isTransferException() {
        return transferException;
    }

    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isDisableTimeToLive() {
        return disableTimeToLive;
    }

    public void setDisableTimeToLive(boolean disableTimeToLive) {
        this.disableTimeToLive = disableTimeToLive;
    }

    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public boolean isAsyncConsumer() {
        return asyncConsumer;
    }

    public void setAsyncConsumer(boolean asyncConsumer) {
        this.asyncConsumer = asyncConsumer;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }
}
