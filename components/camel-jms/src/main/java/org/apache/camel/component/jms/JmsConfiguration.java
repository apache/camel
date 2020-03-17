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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.LoggingLevel;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.JmsException;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jms.listener.SimpleMessageListenerContainer;
import org.springframework.jms.support.JmsUtils;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.Assert;
import org.springframework.util.ErrorHandler;

import static org.apache.camel.component.jms.JmsMessageHelper.normalizeDestinationName;

@UriParams
public class JmsConfiguration implements Cloneable {

    public static final String QUEUE_PREFIX = "queue:";
    public static final String TOPIC_PREFIX = "topic:";
    public static final String TEMP_QUEUE_PREFIX = "temp-queue:";
    public static final String TEMP_TOPIC_PREFIX = "temp-topic:";

    private static final Logger LOG = LoggerFactory.getLogger(JmsConfiguration.class);

    // these are too advanced and seldom used, we should consider removing those as there is plenty of options already
    private JmsOperations jmsOperations;
    private ConnectionFactory templateConnectionFactory;
    private ConnectionFactory listenerConnectionFactory;

    @UriParam(description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @UriParam(label = "security", secret = true, description = "Username to use with the ConnectionFactory. You can also configure username/password directly on the ConnectionFactory.")
    private String username;
    @UriParam(label = "security", secret = true, description = "Password to use with the ConnectionFactory. You can also configure username/password directly on the ConnectionFactory.")
    private String password;

    private int acknowledgementMode = -1;
    @UriParam(defaultValue = "AUTO_ACKNOWLEDGE", enums = "SESSION_TRANSACTED,CLIENT_ACKNOWLEDGE,AUTO_ACKNOWLEDGE,DUPS_OK_ACKNOWLEDGE", label = "consumer",
            description = "The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE")
    private String acknowledgementModeName;
    @UriParam(label = "advanced", description = "A pluggable org.springframework.jms.support.destination.DestinationResolver that allows you to use your own resolver"
            + " (for example, to lookup the real destination in a JNDI registry).")
    private DestinationResolver destinationResolver;
    // Used to configure the spring Container
    @UriParam(label = "advanced",
            description = "Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.")
    private ExceptionListener exceptionListener;
    @UriParam(label = "consumer,advanced", defaultValue = "Default",
            description = "The consumer type to use, which can be one of: Simple, Default, or Custom."
                    + " The consumer type determines which Spring JMS listener to use. Default will use org.springframework.jms.listener.DefaultMessageListenerContainer,"
                    + " Simple will use org.springframework.jms.listener.SimpleMessageListenerContainer."
                    + " When Custom is specified, the MessageListenerContainerFactory defined by the messageListenerContainerFactory option"
                    + " will determine what org.springframework.jms.listener.AbstractMessageListenerContainer to use.")
    private ConsumerType consumerType = ConsumerType.Default;
    @UriParam(label = "advanced",
            description = "Specifies a org.springframework.util.ErrorHandler to be invoked in case of any uncaught exceptions thrown while processing a Message."
                    + " By default these exceptions will be logged at the WARN level, if no errorHandler has been configured."
                    + " You can configure logging level and whether stack traces should be logged using errorHandlerLoggingLevel and errorHandlerLogStackTrace options."
                    + " This makes it much easier to configure, than having to code a custom errorHandler.")
    private ErrorHandler errorHandler;
    @UriParam(defaultValue = "WARN", label = "consumer,logging",
            description = "Allows to configure the default errorHandler logging level for logging uncaught exceptions.")
    private LoggingLevel errorHandlerLoggingLevel = LoggingLevel.WARN;
    @UriParam(defaultValue = "true", label = "consumer,logging",
            description = "Allows to control whether stacktraces should be logged or not, by the default errorHandler.")
    private boolean errorHandlerLogStackTrace = true;
    @UriParam(label = "consumer", defaultValue = "true",
            description = "Specifies whether the consumer container should auto-startup.")
    private boolean autoStartup = true;
    @UriParam(label = "consumer,advanced",
            description = "Whether the DefaultMessageListenerContainer used in the reply managers for request-reply messaging allow "
                    + " the DefaultMessageListenerContainer.runningAllowed flag to quick stop in case JmsConfiguration#isAcceptMessagesWhileStopping"
                    + " is enabled, and org.apache.camel.CamelContext is currently being stopped. This quick stop ability is enabled by"
                    + " default in the regular JMS consumers but to enable for reply managers you must enable this flag.")
    private boolean allowReplyManagerQuickStop;
    @UriParam(label = "consumer,advanced",
            description = "Specifies whether the consumer accept messages while it is stopping."
                    + " You may consider enabling this option, if you start and stop JMS routes at runtime, while there are still messages"
                    + " enqueued on the queue. If this option is false, and you stop the JMS route, then messages may be rejected,"
                    + " and the JMS broker would have to attempt redeliveries, which yet again may be rejected, and eventually the message"
                    + " may be moved at a dead letter queue on the JMS broker. To avoid this its recommended to enable this option.")
    private boolean acceptMessagesWhileStopping;
    @UriParam(description = "Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only be used by a single JMS connection instance."
            + " It is typically only required for durable topic subscriptions."
            + " If using Apache ActiveMQ you may prefer to use Virtual Topics instead.")
    private String clientId;
    @UriParam(description = "The durable subscriber name for specifying durable topic subscriptions. The clientId option must be configured as well.")
    private String durableSubscriptionName;
    @UriParam(label = "consumer,advanced",
            description = "Specifies whether the listener session should be exposed when consuming messages.")
    private boolean exposeListenerSession = true;
    @UriParam(label = "consumer,advanced",
            description = "Allows you to specify a custom task executor for consuming messages.")
    private TaskExecutor taskExecutor;
    @UriParam(label = "advanced",
            description = "Specifies whether to inhibit the delivery of messages published by its own connection.")
    private boolean pubSubNoLocal;
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
    @UriParam(defaultValue = "-1", label = "advanced",
            description = "The number of messages per task. -1 is unlimited."
                    + " If you use a range for concurrent consumers (eg min < max), then this option can be used to set"
                    + " a value to eg 100 to control how fast the consumers will shrink when less work is required.")
    private int maxMessagesPerTask = -1;
    @UriParam(label = "consumer", description = "Sets the cache level by ID for the underlying JMS resources. See cacheLevelName option for more details.")
    private int cacheLevel = -1;
    @UriParam(defaultValue = "CACHE_AUTO", enums = "CACHE_AUTO,CACHE_CONNECTION,CACHE_CONSUMER,CACHE_NONE,CACHE_SESSION", label = "consumer",
            description = "Sets the cache level by name for the underlying JMS resources."
                    + " Possible values are: CACHE_AUTO, CACHE_CONNECTION, CACHE_CONSUMER, CACHE_NONE, and CACHE_SESSION."
                    + " The default setting is CACHE_AUTO. See the Spring documentation and Transactions Cache Levels for more information.")
    private String cacheLevelName;
    @UriParam(defaultValue = "5000", label = "advanced",
            description = "Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds."
                    + " The default is 5000 ms, that is, 5 seconds.")
    private long recoveryInterval = 5000;
    @UriParam(defaultValue = "1000", label = "advanced",
            description = "The timeout for receiving messages (in milliseconds).")
    private long receiveTimeout = 1000;
    @UriParam(defaultValue = "20000", label = "producer",
            description = "The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds)."
                    + " The default is 20 seconds. You can include the header \"CamelJmsRequestTimeout\" to override this endpoint configured"
                    + " timeout value, and thus have per message individual timeout values."
                    + " See also the requestTimeoutCheckerInterval option.")
    private long requestTimeout = 20000L;
    @UriParam(defaultValue = "1000", label = "advanced",
            description = "Configures how often Camel should check for timed out Exchanges when doing request/reply over JMS."
                    + " By default Camel checks once per second. But if you must react faster when a timeout occurs,"
                    + " then you can lower this interval, to check more frequently. The timeout is determined by the option requestTimeout.")
    private long requestTimeoutCheckerInterval = 1000L;
    @UriParam(defaultValue = "1", label = "advanced",
            description = "Specifies the limit for idle executions of a receive task, not having received any message within its execution."
                    + " If this limit is reached, the task will shut down and leave receiving to other executing tasks"
                    + " (in the case of dynamic scheduling; see the maxConcurrentConsumers setting)."
                    + " There is additional doc available from Spring.")
    private int idleTaskExecutionLimit = 1;
    @UriParam(defaultValue = "1", label = "advanced",
            description = "Specify the limit for the number of consumers that are allowed to be idle at any given time.")
    private int idleConsumerLimit = 1;
    @UriParam(defaultValue = "100", label = "advanced",
            description = "Interval in millis to sleep each time while waiting for provisional correlation id to be updated.")
    private long waitForProvisionCorrelationToBeUpdatedThreadSleepingTime = 100L;
    @UriParam(defaultValue = "50", label = "advanced",
            description = "Number of times to wait for provisional correlation id to be updated to the actual correlation id when doing request/reply over JMS"
                    + " and when the option useMessageIDAsCorrelationID is enabled.")
    private int waitForProvisionCorrelationToBeUpdatedCounter = 50;
    @UriParam(label = "consumer",
            description = "Specifies the maximum number of concurrent consumers when consuming from JMS (not for request/reply over JMS)."
                    + " See also the maxMessagesPerTask option to control dynamic scaling up/down of threads."
                    + " When doing request/reply over JMS then the option replyToMaxConcurrentConsumers is used to control number"
                    + " of concurrent consumers on the reply message listener.")
    private int maxConcurrentConsumers;
    @UriParam(label = "producer",
            description = "Specifies the maximum number of concurrent consumers when using request/reply over JMS."
                    + " See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.")
    private int replyToMaxConcurrentConsumers;
    @UriParam(label = "producer", defaultValue = "1",
            description = "Specifies the maximum number of concurrent consumers for continue routing when timeout occurred when using request/reply over JMS.")
    private int replyToOnTimeoutMaxConcurrentConsumers = 1;
    // JmsTemplate only
    @UriParam(label = "producer", defaultValue = "false",
            description = "Set if the deliveryMode, priority or timeToLive qualities of service should be used when sending messages."
                    + " This option is based on Spring's JmsTemplate. The deliveryMode, priority and timeToLive options are applied to the current endpoint."
                    + " This contrasts with the preserveMessageQos option, which operates at message granularity,"
                    + " reading QoS properties exclusively from the Camel In message headers.")
    private Boolean explicitQosEnabled;
    @UriParam(defaultValue = "true", label = "producer",
            description = "Specifies whether persistent delivery is used by default.")
    private boolean deliveryPersistent = true;
    @UriParam(enums = "1,2", label = "producer",
            description = "Specifies the delivery mode to be used."
                    + " Possibles values are those defined by javax.jms.DeliveryMode."
                    + " NON_PERSISTENT = 1 and PERSISTENT = 2.")
    private Integer deliveryMode;
    @UriParam(defaultValue = "true", label = "consumer",
            description = "Specifies whether to use persistent delivery by default for replies.")
    private boolean replyToDeliveryPersistent = true;
    @UriParam(label = "consumer", description = "Sets the JMS selector to use")
    private String selector;
    @UriParam(defaultValue = "-1", label = "producer",
            description = "When sending messages, specifies the time-to-live of the message (in milliseconds).")
    private long timeToLive = -1;
    @UriParam(label = "advanced",
            description = "To use a custom Spring org.springframework.jms.support.converter.MessageConverter so you can be in control how to map to/from a javax.jms.Message.")
    private MessageConverter messageConverter;
    @UriParam(defaultValue = "true", label = "advanced",
            description = "Specifies whether Camel should auto map the received JMS message to a suited payload type, such as javax.jms.TextMessage to a String etc.")
    private boolean mapJmsMessage = true;
    @UriParam(defaultValue = "true", label = "advanced",
            description = "When sending, specifies whether message IDs should be added. This is just an hint to the JMS broker."
                    + " If the JMS provider accepts this hint, these messages must have the message ID set to null; if the provider ignores the hint, "
                    + "the message ID must be set to its normal unique value.")
    private boolean messageIdEnabled = true;
    @UriParam(defaultValue = "true", label = "advanced",
            description = "Specifies whether timestamps should be enabled by default on sending messages. This is just an hint to the JMS broker."
                    + " If the JMS provider accepts this hint, these messages must have the timestamp set to zero; if the provider ignores the hint "
                    + "the timestamp must be set to its normal value.")
    private boolean messageTimestampEnabled = true;
    @UriParam(defaultValue = "" + Message.DEFAULT_PRIORITY, enums = "1,2,3,4,5,6,7,8,9", label = "producer",
            description = "Values greater than 1 specify the message priority when sending (where 0 is the lowest priority and 9 is the highest)."
                    + " The explicitQosEnabled option must also be enabled in order for this option to have any effect.")
    private int priority = Message.DEFAULT_PRIORITY;
    // Transaction related configuration
    @UriParam(label = "transaction",
            description = "Specifies whether to use transacted mode")
    private boolean transacted;
    @UriParam(defaultValue = "true", label = "transaction,advanced",
            description = "If true, Camel will create a JmsTransactionManager, if there is no transactionManager injected when option transacted=true.")
    private boolean lazyCreateTransactionManager = true;
    @UriParam(label = "transaction,advanced",
            description = "The Spring transaction manager to use.")
    private PlatformTransactionManager transactionManager;
    @UriParam(label = "transaction,advanced",
            description = "The name of the transaction to use.")
    private String transactionName;
    @UriParam(defaultValue = "-1", label = "transaction,advanced",
            description = "The timeout value of the transaction (in seconds), if using transacted mode.")
    private int transactionTimeout = -1;
    @UriParam(label = "producer",
            description = "Set to true, if you want to send message using the QoS settings specified on the message,"
                    + " instead of the QoS settings on the JMS endpoint. The following three headers are considered JMSPriority, JMSDeliveryMode,"
                    + " and JMSExpiration. You can provide all or only some of them. If not provided, Camel will fall back to use the"
                    + " values from the endpoint instead. So, when using this option, the headers override the values from the endpoint."
                    + " The explicitQosEnabled option, by contrast, will only use options set on the endpoint, and not values from the message header.")
    private boolean preserveMessageQos;
    @UriParam(description = "Specifies whether Camel ignores the JMSReplyTo header in messages. If true, Camel does not send a reply back to"
            + " the destination specified in the JMSReplyTo header. You can use this option if you want Camel to consume from a"
            + " route and you do not want Camel to automatically send back a reply message because another component in your code"
            + " handles the reply message. You can also use this option if you want to use Camel as a proxy between different"
            + " message brokers and you want to route message from one system to another.")
    private boolean disableReplyTo;
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
    // Always make a JMS message copy when it's passed to Producer
    @UriParam(label = "producer,advanced",
            description = "If true, Camel will always make a JMS message copy of the message when it is passed to the producer for sending."
                    + " Copying the message is needed in some situations, such as when a replyToDestinationSelectorName is set"
                    + " (incidentally, Camel will set the alwaysCopyMessage option to true, if a replyToDestinationSelectorName is set)")
    private boolean alwaysCopyMessage;
    @UriParam(label = "advanced",
            description = "Specifies whether JMSMessageID should always be used as JMSCorrelationID for InOut messages.")
    private boolean useMessageIDAsCorrelationID;
    @UriParam(label = "consumer",
            description = "Provides an explicit ReplyTo destination, which overrides any incoming value of Message.getJMSReplyTo().")
    private String replyTo;
    @UriParam(label = "producer,advanced",
            description = "Sets the JMS Selector using the fixed name to be used so you can filter out your own replies"
                    + " from the others when using a shared queue (that is, if you are not using a temporary reply queue).")
    private String replyToDestinationSelectorName;
    @UriParam(label = "producer",
            description = "Provides an explicit ReplyTo destination in the JMS message, which overrides the setting of replyTo."
                    + " It is useful if you want to forward the message to a remote Queue and receive the reply message from the ReplyTo destination.")
    private String replyToOverride;
    @UriParam(label = "consumer,advanced",
            description = "Whether a JMS consumer is allowed to send a reply message to the same destination that the consumer is using to"
                    + " consume from. This prevents an endless loop by consuming and sending back the same message to itself.")
    private boolean replyToSameDestinationAllowed;
    @UriParam(enums = "Bytes,Map,Object,Stream,Text",
            description = "Allows you to force the use of a specific javax.jms.Message implementation for sending JMS messages."
                    + " Possible values are: Bytes, Map, Object, Stream, Text."
                    + " By default, Camel would determine which JMS message type to use from the In body type. This option allows you to specify it.")
    private JmsMessageType jmsMessageType;
    @UriParam(label = "advanced", enums = "default,passthrough",
            description = "Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification."
                    + " Camel provides two implementations out of the box: default and passthrough."
                    + " The default strategy will safely marshal dots and hyphens (. and -). The passthrough strategy leaves the key as is."
                    + " Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters."
                    + " You can provide your own implementation of the org.apache.camel.component.jms.JmsKeyFormatStrategy"
                    + " and refer to it using the # notation.")
    private JmsKeyFormatStrategy jmsKeyFormatStrategy;
    @UriParam(label = "advanced",
            description = "You can transfer the exchange over the wire instead of just the body and headers."
                    + " The following fields are transferred: In body, Out body, Fault body, In headers, Out headers, Fault headers,"
                    + " exchange properties, exchange exception."
                    + " This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level."
                    + " You must enable this option on both the producer and consumer side, so Camel knows the payloads is an Exchange and not a regular payload."
                    + " Use this with caution as the data is using Java Object serialization and requires the received to be able to deserialize the data at Class level, "
                    + " which forces a strong coupling between the producers and consumer having to use compatible Camel versions!")
    private boolean transferExchange;
    @UriParam(label = "advanced",
            description = "Controls whether or not to include serialized headers."
                    + " Applies only when {@code transferExchange} is {@code true}."
                    + " This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.")
    private boolean allowSerializedHeaders;
    @UriParam(label = "advanced",
            description = "If enabled and you are using Request Reply messaging (InOut) and an Exchange failed on the consumer side,"
                    + " then the caused Exception will be send back in response as a javax.jms.ObjectMessage."
                    + " If the client is Camel, the returned Exception is rethrown. This allows you to use Camel JMS as a bridge"
                    + " in your routing - for example, using persistent queues to enable robust routing."
                    + " Notice that if you also have transferExchange enabled, this option takes precedence."
                    + " The caught exception is required to be serializable."
                    + " The original Exception on the consumer side can be wrapped in an outer exception"
                    + " such as org.apache.camel.RuntimeCamelException when returned to the producer."
                    + " Use this with caution as the data is using Java Object serialization and requires the received to be able to deserialize the data at Class level, "
                    + " which forces a strong coupling between the producers and consumer!")
    private boolean transferException;
    @UriParam(description = "Specifies whether to test the connection on startup."
            + " This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker."
            + " If a connection cannot be granted then Camel throws an exception on startup."
            + " This ensures that Camel is not started with failed connections."
            + " The JMS producers is tested as well.")
    private boolean testConnectionOnStartup;
    @UriParam(label = "advanced",
            description = "Whether to startup the JmsConsumer message listener asynchronously, when starting a route."
                    + " For example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying"
                    + " and/or failover. This will cause Camel to block while starting routes. By setting this option to true,"
                    + " you will let routes startup, while the JmsConsumer connects to the JMS broker using a dedicated thread"
                    + " in asynchronous mode. If this option is used, then beware that if the connection could not be established,"
                    + " then an exception is logged at WARN level, and the consumer will not be able to receive messages;"
                    + " You can then restart the route to retry.")
    private boolean asyncStartListener;
    @UriParam(label = "advanced",
            description = "Whether to stop the JmsConsumer message listener asynchronously, when stopping a route.")
    private boolean asyncStopListener;
    // if the message is a JmsMessage and mapJmsMessage=false, force the
    // producer to send the javax.jms.Message body to the next JMS destination
    @UriParam(label = "producer,advanced",
            description = "When using mapJmsMessage=false Camel will create a new JMS message to send to a new JMS destination"
                    + " if you touch the headers (get or set) during the route. Set this option to true to force Camel to send"
                    + " the original JMS message that was received.")
    private boolean forceSendOriginalMessage;
    // to force disabling time to live (works in both in-only or in-out mode)
    @UriParam(label = "producer,advanced",
            description = "Use this option to force disabling time to live."
                    + " For example when you do request/reply over JMS, then Camel will by default use the requestTimeout value"
                    + " as time to live on the message being sent. The problem is that the sender and receiver systems have"
                    + " to have their clocks synchronized, so they are in sync. This is not always so easy to archive."
                    + " So you can use disableTimeToLive=true to not set a time to live value on the sent message."
                    + " Then the message will not expire on the receiver system. See below in section About time to live for more details.")
    private boolean disableTimeToLive;
    @UriParam(label = "producer",
            description = "Allows for explicitly specifying which kind of strategy to use for replyTo queues when doing request/reply over JMS."
                    + " Possible values are: Temporary, Shared, or Exclusive."
                    + " By default Camel will use temporary queues. However if replyTo has been configured, then Shared is used by default."
                    + " This option allows you to use exclusive queues instead of shared ones."
                    + " See Camel JMS documentation for more details, and especially the notes about the implications if running in a clustered environment,"
                    + " and the fact that Shared reply queues has lower performance than its alternatives Temporary and Exclusive.")
    private ReplyToType replyToType;
    @UriParam(label = "consumer",
            description = "Whether the JmsConsumer processes the Exchange asynchronously."
                    + " If enabled then the JmsConsumer may pickup the next message from the JMS queue,"
                    + " while the previous message is being processed asynchronously (by the Asynchronous Routing Engine)."
                    + " This means that messages may be processed not 100% strictly in order. If disabled (as default)"
                    + " then the Exchange is fully processed before the JmsConsumer will pickup the next message from the JMS queue."
                    + " Note if transacted has been enabled, then asyncConsumer=true does not run asynchronously, as transaction"
                    + "  must be executed synchronously (Camel 3.0 may support async transactions).")
    private boolean asyncConsumer;
    // the cacheLevelName of reply manager
    @UriParam(label = "producer,advanced", enums = "CACHE_AUTO,CACHE_CONNECTION,CACHE_CONSUMER,CACHE_NONE,CACHE_SESSION",
            description = "Sets the cache level by name for the reply consumer when doing request/reply over JMS."
                    + " This option only applies when using fixed reply queues (not temporary)."
                    + " Camel will by default use: CACHE_CONSUMER for exclusive or shared w/ replyToSelectorName."
                    + " And CACHE_SESSION for shared without replyToSelectorName. Some JMS brokers such as IBM WebSphere"
                    + " may require to set the replyToCacheLevelName=CACHE_NONE to work."
                    + " Note: If using temporary queues then CACHE_NONE is not allowed,"
                    + " and you must use a higher value such as CACHE_CONSUMER or CACHE_SESSION.")
    private String replyToCacheLevelName;
    @UriParam(defaultValue = "true", label = "producer,advanced",
            description = "Whether to allow sending messages with no body. If this option is false and the message body is null, then an JMSException is thrown.")
    private boolean allowNullBody = true;
    @UriParam(label = "advanced",
            description = "Registry ID of the MessageListenerContainerFactory used to determine what"
                    + " org.springframework.jms.listener.AbstractMessageListenerContainer to use to consume messages."
                    + " Setting this will automatically set consumerType to Custom.")
    private MessageListenerContainerFactory messageListenerContainerFactory;
    @UriParam(label = "producer,advanced",
            description = "Only applicable when sending to JMS destination using InOnly (eg fire and forget)."
                    + " Enabling this option will enrich the Camel Exchange with the actual JMSMessageID"
                    + " that was used by the JMS client when the message was sent to the JMS destination.")
    private boolean includeSentJMSMessageID;
    @UriParam(label = "consumer,advanced",
            description = "Specifies what default TaskExecutor type to use in the DefaultMessageListenerContainer,"
                    + " for both consumer endpoints and the ReplyTo consumer of producer endpoints."
                    + " Possible values: SimpleAsync (uses Spring's SimpleAsyncTaskExecutor) or ThreadPool"
                    + " (uses Spring's ThreadPoolTaskExecutor with optimal values - cached threadpool-like)."
                    + " If not set, it defaults to the previous behaviour, which uses a cached thread pool"
                    + " for consumer endpoints and SimpleAsync for reply consumers."
                    + " The use of ThreadPool is recommended to reduce thread trash in elastic configurations"
                    + " with dynamically increasing and decreasing concurrent consumers.")
    private DefaultTaskExecutorType defaultTaskExecutorType;
    @UriParam(label = "advanced",
            description = "Whether to include all JMSXxxx properties when mapping from JMS to Camel Message."
                    + " Setting this to true will include properties such as JMSXAppID, and JMSXUserID etc."
                    + " Note: If you are using a custom headerFilterStrategy then this option does not apply.")
    private boolean includeAllJMSXProperties;
    @UriParam(label = "advanced",
            description = "To use the given MessageCreatedStrategy which are invoked when Camel creates new instances of javax.jms.Message objects when Camel is sending a JMS message.")
    private MessageCreatedStrategy messageCreatedStrategy;
    @UriParam(label = "producer,advanced",
            description = "When using InOut exchange pattern use this JMS property instead of JMSCorrelationID"
                    + " JMS property to correlate messages. If set messages will be correlated solely on the"
                    + " value of this property JMSCorrelationID property will be ignored and not set by Camel.")
    private String correlationProperty;
    @UriParam(label = "producer,advanced",
            description = "This option is used to allow additional headers which may have values that are invalid according to JMS specification."
                    + " For example some message systems such as WMQ do this with header names using prefix JMS_IBM_MQMD_ containing values with byte array or other invalid types."
                    + " You can specify multiple header names separated by comma, and use * as suffix for wildcard matching.")
    private String allowAdditionalHeaders;
    @UriParam(label = "producer", description = "Sets whether JMS date properties should be formatted according to the ISO 8601 standard.")
    private boolean formatDateHeadersToIso8601;
    @UriParam(defaultValue = "true", label = "advanced", description = "Whether optimizing for Apache Artemis streaming mode.")
    private boolean artemisStreamingEnabled = true;

    // JMS 2.0 API
    @UriParam(label = "consumer", description = "Set the name of a subscription to create. To be applied in case"
            + " of a topic (pub-sub domain) with a shared or durable subscription."
            + " The subscription name needs to be unique within this client's"
            + " JMS client id. Default is the class name of the specified message listener."
            + " Note: Only 1 concurrent consumer (which is the default of this"
            + " message listener container) is allowed for each subscription,"
            + " except for a shared subscription (which requires JMS 2.0).")
    private String subscriptionName;
    @UriParam(label = "consumer", description = "Set whether to make the subscription durable. The durable subscription name"
            + " to be used can be specified through the subscriptionName property."
            + " Default is false. Set this to true to register a durable subscription,"
            + " typically in combination with a subscriptionName value (unless"
            + " your message listener class name is good enough as subscription name)."
            + " Only makes sense when listening to a topic (pub-sub domain),"
            + " therefore this method switches the pubSubDomain flag as well.")
    private boolean subscriptionDurable;
    @UriParam(label = "consumer", description = "Set whether to make the subscription shared. The shared subscription name"
            + " to be used can be specified through the subscriptionName property."
            + " Default is false. Set this to true to register a shared subscription,"
            + " typically in combination with a subscriptionName value (unless"
            + " your message listener class name is good enough as subscription name)."
            + " Note that shared subscriptions may also be durable, so this flag can"
            + " (and often will) be combined with subscriptionDurable as well."
            + " Only makes sense when listening to a topic (pub-sub domain),"
            + " therefore this method switches the pubSubDomain flag as well."
            + " Requires a JMS 2.0 compatible message broker.")
    private boolean subscriptionShared;
    @UriParam(label = "producer,advanced", description = "Sets whether StreamMessage type is enabled or not."
            + " Message payloads of streaming kind such as files, InputStream, etc will either by sent as BytesMessage or StreamMessage."
            + " This option controls which kind will be used. By default BytesMessage is used which enforces the entire message payload to be read into memory."
            + " By enabling this option the message payload is read into memory in chunks and each chunk is then written to the StreamMessage until no more data.")
    private boolean streamMessageTypeEnabled;
    @UriParam(defaultValue = "-1", label = "producer", description = "Sets delivery delay to use for send calls for JMS. "
            + "This option requires JMS 2.0 compliant broker.")
    private long deliveryDelay = -1;

    public JmsConfiguration() {
    }

    public JmsConfiguration(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Returns a copy of this configuration
     */
    public JmsConfiguration copy() {
        try {
            return (JmsConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public static class CamelJmsTemplate extends JmsTemplate {
        private JmsConfiguration config;

        public CamelJmsTemplate(JmsConfiguration config, ConnectionFactory connectionFactory) {
            super(connectionFactory);
            this.config = config;
        }

        public void send(final String destinationName,
                         final MessageCreator messageCreator,
                         final MessageSentCallback callback) throws JmsException {
            execute(session -> {
                Destination destination = resolveDestinationName(session, destinationName);
                return doSendToDestination(destination, messageCreator, callback, session);
            }, false);
        }

        public void send(final Destination destination,
                         final MessageCreator messageCreator,
                         final MessageSentCallback callback) throws JmsException {
            execute(session -> doSendToDestination(destination, messageCreator, callback, session), false);
        }

        @Override
        public void send(final String destinationName,
                         final MessageCreator messageCreator) throws JmsException {
            execute(session -> {
                Destination destination = resolveDestinationName(session, destinationName);
                return doSendToDestination(destination, messageCreator, null, session);
            }, false);
        }

        @Override
        public void send(final Destination destination,
                         final MessageCreator messageCreator) throws JmsException {
            execute(session -> doSendToDestination(destination, messageCreator, null, session), false);
        }

        private Object doSendToDestination(final Destination destination,
                                           final MessageCreator messageCreator,
                                           final MessageSentCallback callback,
                                           final Session session) throws JMSException {

            Assert.notNull(messageCreator, "MessageCreator must not be null");
            MessageProducer producer = createProducer(session, destination);
            Message message;
            try {
                message = messageCreator.createMessage(session);
                doSend(producer, message);
                if (message != null && callback != null) {
                    callback.sent(session, message, destination);
                }
                // Check commit - avoid commit call within a JTA transaction.
                if (session.getTransacted() && isSessionLocallyTransacted(session)) {
                    // Transacted session created by this template -> commit.
                    JmsUtils.commitIfNecessary(session);
                }
            } finally {
                JmsUtils.closeMessageProducer(producer);
            }
            return null;
        }

        /**
         * Override so we can support preserving the Qos settings that have
         * been set on the message.
         */
        @Override
        protected void doSend(MessageProducer producer, Message message) throws JMSException {
            if (config.isPreserveMessageQos()) {
                long ttl = message.getJMSExpiration();
                if (ttl != 0) {
                    ttl = ttl - System.currentTimeMillis();
                    // Message had expired.. so set the ttl as small as possible
                    if (ttl <= 0) {
                        ttl = 1;
                    }
                }

                int priority = message.getJMSPriority();
                if (priority < 0 || priority > 9) {
                    // use priority from endpoint if not provided on message with a valid range
                    priority = this.getPriority();
                }

                // if a delivery mode was set as a JMS header then we have used a temporary
                // property to store it - CamelJMSDeliveryMode. Otherwise we could not keep
                // track whether it was set or not as getJMSDeliveryMode() will default return 1 regardless
                // if it was set or not, so we can never tell if end user provided it in a header
                int deliveryMode;
                if (JmsMessageHelper.hasProperty(message, JmsConstants.JMS_DELIVERY_MODE)) {
                    deliveryMode = message.getIntProperty(JmsConstants.JMS_DELIVERY_MODE);
                    // remove the temporary property
                    JmsMessageHelper.removeJmsProperty(message, JmsConstants.JMS_DELIVERY_MODE);
                } else {
                    // use the existing delivery mode from the message
                    deliveryMode = message.getJMSDeliveryMode();
                }

                // need to log just before so the message is 100% correct when logged
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending JMS message to: {} with message: {}", producer.getDestination(), message);
                }
                producer.send(message, deliveryMode, priority, ttl);
            } else {
                // need to log just before so the message is 100% correct when logged
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sending JMS message to: {} with message: {}", producer.getDestination(), message);
                }
                super.doSend(producer, message);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Sent JMS message to: {} with message: {}", producer.getDestination(), message);
                }
            }
        }
    }

    /**
     * Creates a {@link JmsOperations} object used for request/response using a request timeout value
     */
    public JmsOperations createInOutTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination, long requestTimeout) {
        JmsOperations answer = createInOnlyTemplate(endpoint, pubSubDomain, destination);
        if (answer instanceof JmsTemplate && requestTimeout > 0) {
            JmsTemplate jmsTemplate = (JmsTemplate) answer;
            jmsTemplate.setExplicitQosEnabled(true);

            // prefer to use timeToLive over requestTimeout if both specified
            long ttl = timeToLive > 0 ? timeToLive : requestTimeout;
            if (!isDisableTimeToLive()) {
                // only use TTL if not disabled
                jmsTemplate.setTimeToLive(ttl);
            }

            if (acknowledgementMode >= 0) {
                jmsTemplate.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                jmsTemplate.setSessionAcknowledgeModeName(acknowledgementModeName);
            } else {
                // default to AUTO
                jmsTemplate.setSessionAcknowledgeMode(Session.AUTO_ACKNOWLEDGE);
            }
        }
        return answer;
    }

    /**
     * Creates a {@link JmsOperations} object used for one way messaging
     */
    public JmsOperations createInOnlyTemplate(JmsEndpoint endpoint, boolean pubSubDomain, String destination) {
        if (jmsOperations != null) {
            return jmsOperations;
        }

        ConnectionFactory factory = getOrCreateTemplateConnectionFactory();
        JmsTemplate template = new CamelJmsTemplate(this, factory);

        template.setPubSubDomain(pubSubDomain);
        if (destinationResolver != null) {
            template.setDestinationResolver(destinationResolver);
            if (endpoint instanceof DestinationEndpoint) {
                LOG.debug("You are overloading the destinationResolver property on a DestinationEndpoint; are you sure you want to do that?");
            }
        } else if (endpoint instanceof DestinationEndpoint) {
            DestinationEndpoint destinationEndpoint = (DestinationEndpoint) endpoint;
            template.setDestinationResolver(createDestinationResolver(destinationEndpoint));
        }
        template.setDefaultDestinationName(destination);

        template.setExplicitQosEnabled(isExplicitQosEnabled());

        // have to use one or the other.. doesn't make sense to use both
        if (deliveryMode != null) {
            template.setDeliveryMode(deliveryMode);
        } else {
            template.setDeliveryPersistent(deliveryPersistent);
        }

        if (messageConverter != null) {
            template.setMessageConverter(messageConverter);
        }
        template.setMessageIdEnabled(messageIdEnabled);
        template.setMessageTimestampEnabled(messageTimestampEnabled);
        if (priority >= 0) {
            template.setPriority(priority);
        }
        template.setPubSubNoLocal(pubSubNoLocal);
        // only set TTL if we have a positive value and it has not been disabled
        if (timeToLive >= 0 && !isDisableTimeToLive()) {
            template.setTimeToLive(timeToLive);
        }

        template.setSessionTransacted(transacted);
        if (transacted) {
            template.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            // This is here for completeness, but the template should not get
            // used for receiving messages.
            if (acknowledgementMode >= 0) {
                template.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                template.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
        }

        template.setDeliveryDelay(deliveryDelay);

        return template;
    }

    public AbstractMessageListenerContainer createMessageListenerContainer(JmsEndpoint endpoint) throws Exception {
        AbstractMessageListenerContainer container = chooseMessageListenerContainerImplementation(endpoint);
        configureMessageListenerContainer(container, endpoint);
        return container;
    }

    public AbstractMessageListenerContainer chooseMessageListenerContainerImplementation(JmsEndpoint endpoint) {
        switch (consumerType) {
            case Simple:
                return new SimpleJmsMessageListenerContainer(endpoint);
            case Default:
                return new DefaultJmsMessageListenerContainer(endpoint);
            case Custom:
                return getCustomMessageListenerContainer(endpoint);
            default:
                throw new IllegalArgumentException("Unknown consumer type: " + consumerType);
        }
    }

    private AbstractMessageListenerContainer getCustomMessageListenerContainer(JmsEndpoint endpoint) {
        if (messageListenerContainerFactory != null) {
            return messageListenerContainerFactory.createMessageListenerContainer(endpoint);
        }
        return null;
    }

    // Properties
    // -------------------------------------------------------------------------

    public ConsumerType getConsumerType() {
        return consumerType;
    }

    /**
     * The consumer type to use, which can be one of: Simple, Default, or Custom.
     * The consumer type determines which Spring JMS listener to use. Default will use org.springframework.jms.listener.DefaultMessageListenerContainer,
     * Simple will use org.springframework.jms.listener.SimpleMessageListenerContainer.
     * When Custom is specified, the MessageListenerContainerFactory defined by the messageListenerContainerFactory option
     * will determine what org.springframework.jms.listener.AbstractMessageListenerContainer to use.
     */
    public void setConsumerType(ConsumerType consumerType) {
        this.consumerType = consumerType;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public ConnectionFactory getOrCreateConnectionFactory() {
        if (connectionFactory == null) {
            connectionFactory = createConnectionFactory();
        }
        return connectionFactory;
    }

    /**
     * Sets the default connection factory to be used if a connection factory is
     * not specified for either
     * {@link #setTemplateConnectionFactory(ConnectionFactory)} or
     * {@link #setListenerConnectionFactory(ConnectionFactory)}
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username to use with the ConnectionFactory. You can also configure username/password directly on the ConnectionFactory.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to use with the ConnectionFactory. You can also configure username/password directly on the ConnectionFactory.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public ConnectionFactory getListenerConnectionFactory() {
        return listenerConnectionFactory;
    }

    public ConnectionFactory getOrCreateListenerConnectionFactory() {
        if (listenerConnectionFactory == null) {
            listenerConnectionFactory = createListenerConnectionFactory();
        }
        return listenerConnectionFactory;
    }

    /**
     * Sets the connection factory to be used for consuming messages
     */
    public void setListenerConnectionFactory(ConnectionFactory listenerConnectionFactory) {
        this.listenerConnectionFactory = listenerConnectionFactory;
    }

    public ConnectionFactory getTemplateConnectionFactory() {
        return templateConnectionFactory;
    }

    public ConnectionFactory getOrCreateTemplateConnectionFactory() {
        if (templateConnectionFactory == null) {
            templateConnectionFactory = createTemplateConnectionFactory();
        }
        return templateConnectionFactory;
    }

    /**
     * Sets the connection factory to be used for sending messages via the
     * {@link JmsTemplate} via {@link #createInOnlyTemplate(JmsEndpoint, boolean, String)}
     */
    public void setTemplateConnectionFactory(ConnectionFactory templateConnectionFactory) {
        this.templateConnectionFactory = templateConnectionFactory;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    /**
     * Specifies whether the consumer container should auto-startup.
     */
    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public boolean isAcceptMessagesWhileStopping() {
        return acceptMessagesWhileStopping;
    }

    /**
     * Specifies whether the consumer accept messages while it is stopping.
     * You may consider enabling this option, if you start and stop JMS routes at runtime, while there are still messages
     * enqueued on the queue. If this option is false, and you stop the JMS route, then messages may be rejected,
     * and the JMS broker would have to attempt redeliveries, which yet again may be rejected, and eventually the message
     * may be moved at a dead letter queue on the JMS broker. To avoid this its recommended to enable this option.
     */
    public void setAcceptMessagesWhileStopping(boolean acceptMessagesWhileStopping) {
        this.acceptMessagesWhileStopping = acceptMessagesWhileStopping;
    }

    /**
     * Whether the {@link DefaultMessageListenerContainer} used in the reply managers for request-reply messaging allow
     * the {@link DefaultMessageListenerContainer#runningAllowed()} flag to quick stop in case {@link JmsConfiguration#isAcceptMessagesWhileStopping()}
     * is enabled, and {@link org.apache.camel.CamelContext} is currently being stopped. This quick stop ability is enabled by
     * default in the regular JMS consumers but to enable for reply managers you must enable this flag.
     */
    public boolean isAllowReplyManagerQuickStop() {
        return allowReplyManagerQuickStop;
    }

    public void setAllowReplyManagerQuickStop(boolean allowReplyManagerQuickStop) {
        this.allowReplyManagerQuickStop = allowReplyManagerQuickStop;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only be used by a single JMS connection instance.
     * It is typically only required for durable topic subscriptions.
     * <p>
     * If using Apache ActiveMQ you may prefer to use Virtual Topics instead.
     */
    public void setClientId(String consumerClientId) {
        this.clientId = consumerClientId;
    }

    public String getDurableSubscriptionName() {
        return durableSubscriptionName;
    }

    /**
     * The durable subscriber name for specifying durable topic subscriptions. The clientId option must be configured as well.
     */
    public void setDurableSubscriptionName(String durableSubscriptionName) {
        this.durableSubscriptionName = durableSubscriptionName;
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

    /**
     * Specifies a org.springframework.util.ErrorHandler to be invoked in case of any uncaught exceptions thrown while processing a Message.
     * By default these exceptions will be logged at the WARN level, if no errorHandler has been configured.
     * You can configure logging level and whether stack traces should be logged using errorHandlerLoggingLevel and errorHandlerLogStackTrace options.
     * This makes it much easier to configure, than having to code a custom errorHandler.
     */
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
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

    public String getAcknowledgementModeName() {
        return acknowledgementModeName;
    }

    /**
     * The JMS acknowledgement name, which is one of: SESSION_TRANSACTED, CLIENT_ACKNOWLEDGE, AUTO_ACKNOWLEDGE, DUPS_OK_ACKNOWLEDGE
     */
    public void setAcknowledgementModeName(String consumerAcknowledgementMode) {
        this.acknowledgementModeName = consumerAcknowledgementMode;
        this.acknowledgementMode = -1;
    }

    public boolean isExposeListenerSession() {
        return exposeListenerSession;
    }

    /**
     * Specifies whether the listener session should be exposed when consuming messages.
     */
    public void setExposeListenerSession(boolean exposeListenerSession) {
        this.exposeListenerSession = exposeListenerSession;
    }

    public TaskExecutor getTaskExecutor() {
        return taskExecutor;
    }

    /**
     * Allows you to specify a custom task executor for consuming messages.
     */
    public void setTaskExecutor(TaskExecutor taskExecutor) {
        this.taskExecutor = taskExecutor;
    }

    public boolean isPubSubNoLocal() {
        return pubSubNoLocal;
    }

    /**
     * Specifies whether to inhibit the delivery of messages published by its own connection.
     */
    public void setPubSubNoLocal(boolean pubSubNoLocal) {
        this.pubSubNoLocal = pubSubNoLocal;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    /**
     * Specifies the default number of concurrent consumers when consuming from JMS (not for request/reply over JMS).
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     * <p>
     * When doing request/reply over JMS then the option replyToConcurrentConsumers is used to control number
     * of concurrent consumers on the reply message listener.
     */
    public void setConcurrentConsumers(int concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public int getReplyToConcurrentConsumers() {
        return replyToConcurrentConsumers;
    }

    /**
     * Specifies the default number of concurrent consumers when doing request/reply over JMS.
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     */
    public void setReplyToConcurrentConsumers(int replyToConcurrentConsumers) {
        this.replyToConcurrentConsumers = replyToConcurrentConsumers;
    }

    public int getMaxMessagesPerTask() {
        return maxMessagesPerTask;
    }

    /**
     * The number of messages per task. -1 is unlimited.
     * If you use a range for concurrent consumers (eg min < max), then this option can be used to set
     * a value to eg 100 to control how fast the consumers will shrink when less work is required.
     */
    public void setMaxMessagesPerTask(int maxMessagesPerTask) {
        this.maxMessagesPerTask = maxMessagesPerTask;
    }

    public int getCacheLevel() {
        return cacheLevel;
    }

    /**
     * Sets the cache level by ID for the underlying JMS resources. See cacheLevelName option for more details.
     */
    public void setCacheLevel(int cacheLevel) {
        this.cacheLevel = cacheLevel;
    }

    public String getCacheLevelName() {
        return cacheLevelName;
    }

    /**
     * Sets the cache level by name for the underlying JMS resources.
     * Possible values are: CACHE_AUTO, CACHE_CONNECTION, CACHE_CONSUMER, CACHE_NONE, and CACHE_SESSION.
     * The default setting is CACHE_AUTO. See the Spring documentation and Transactions Cache Levels for more information.
     */
    public void setCacheLevelName(String cacheName) {
        this.cacheLevelName = cacheName;
    }

    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    /**
     * Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds.
     * The default is 5000 ms, that is, 5 seconds.
     */
    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public long getReceiveTimeout() {
        return receiveTimeout;
    }

    /**
     * The timeout for receiving messages (in milliseconds).
     */
    public void setReceiveTimeout(long receiveTimeout) {
        this.receiveTimeout = receiveTimeout;
    }

    public PlatformTransactionManager getTransactionManager() {
        return transactionManager;
    }

    public PlatformTransactionManager getOrCreateTransactionManager() {
        if (transactionManager == null && isTransacted() && isLazyCreateTransactionManager()) {
            transactionManager = createTransactionManager();
        }
        return transactionManager;
    }

    /**
     * The Spring transaction manager to use.
     */
    public void setTransactionManager(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public String getTransactionName() {
        return transactionName;
    }

    /**
     * The name of the transaction to use.
     */
    public void setTransactionName(String transactionName) {
        this.transactionName = transactionName;
    }

    public int getTransactionTimeout() {
        return transactionTimeout;
    }

    /**
     * The timeout value of the transaction (in seconds), if using transacted mode.
     */
    public void setTransactionTimeout(int transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    public int getIdleTaskExecutionLimit() {
        return idleTaskExecutionLimit;
    }

    /**
     * Specifies the limit for idle executions of a receive task, not having received any message within its execution.
     * If this limit is reached, the task will shut down and leave receiving to other executing tasks
     * (in the case of dynamic scheduling; see the maxConcurrentConsumers setting).
     * There is additional doc available from Spring.
     */
    public void setIdleTaskExecutionLimit(int idleTaskExecutionLimit) {
        this.idleTaskExecutionLimit = idleTaskExecutionLimit;
    }

    public int getIdleConsumerLimit() {
        return idleConsumerLimit;
    }

    /**
     * Specify the limit for the number of consumers that are allowed to be idle at any given time.
     */
    public void setIdleConsumerLimit(int idleConsumerLimit) {
        this.idleConsumerLimit = idleConsumerLimit;
    }

    public int getWaitForProvisionCorrelationToBeUpdatedCounter() {
        return waitForProvisionCorrelationToBeUpdatedCounter;
    }

    /**
     * Number of times to wait for provisional correlation id to be updated to the actual correlation id when doing request/reply over JMS
     * and when the option useMessageIDAsCorrelationID is enabled.
     */
    public void setWaitForProvisionCorrelationToBeUpdatedCounter(int counter) {
        this.waitForProvisionCorrelationToBeUpdatedCounter = counter;
    }

    public long getWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime() {
        return waitForProvisionCorrelationToBeUpdatedThreadSleepingTime;
    }

    /**
     * Interval in millis to sleep each time while waiting for provisional correlation id to be updated.
     */
    public void setWaitForProvisionCorrelationToBeUpdatedThreadSleepingTime(long sleepingTime) {
        this.waitForProvisionCorrelationToBeUpdatedThreadSleepingTime = sleepingTime;
    }

    public int getMaxConcurrentConsumers() {
        return maxConcurrentConsumers;
    }

    /**
     * Specifies the maximum number of concurrent consumers when consuming from JMS (not for request/reply over JMS).
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     * <p>
     * When doing request/reply over JMS then the option replyToMaxConcurrentConsumers is used to control number
     * of concurrent consumers on the reply message listener.
     */
    public void setMaxConcurrentConsumers(int maxConcurrentConsumers) {
        this.maxConcurrentConsumers = maxConcurrentConsumers;
    }

    public int getReplyToMaxConcurrentConsumers() {
        return replyToMaxConcurrentConsumers;
    }

    /**
     * Specifies the maximum number of concurrent consumers when using request/reply over JMS.
     * See also the maxMessagesPerTask option to control dynamic scaling up/down of threads.
     */
    public void setReplyToMaxConcurrentConsumers(int replyToMaxConcurrentConsumers) {
        this.replyToMaxConcurrentConsumers = replyToMaxConcurrentConsumers;
    }

    public int getReplyToOnTimeoutMaxConcurrentConsumers() {
        return replyToOnTimeoutMaxConcurrentConsumers;
    }

    /**
     * Specifies the maximum number of concurrent consumers for continue routing when timeout occurred when using request/reply over JMS.
     */
    public void setReplyToOnTimeoutMaxConcurrentConsumers(int replyToOnTimeoutMaxConcurrentConsumers) {
        this.replyToOnTimeoutMaxConcurrentConsumers = replyToOnTimeoutMaxConcurrentConsumers;
    }

    public boolean isExplicitQosEnabled() {
        return explicitQosEnabled != null ? explicitQosEnabled : false;
    }

    public Boolean getExplicitQosEnabled() {
        return explicitQosEnabled;
    }

    /**
     * Set if the deliveryMode, priority or timeToLive qualities of service should be used when sending messages.
     * This option is based on Spring's JmsTemplate. The deliveryMode, priority and timeToLive options are applied to the current endpoint.
     * This contrasts with the preserveMessageQos option, which operates at message granularity,
     * reading QoS properties exclusively from the Camel In message headers.
     */
    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        this.explicitQosEnabled = explicitQosEnabled;
    }

    public boolean isDeliveryPersistent() {
        return deliveryPersistent;
    }

    /**
     * Specifies whether persistent delivery is used by default.
     */
    public void setDeliveryPersistent(boolean deliveryPersistent) {
        this.deliveryPersistent = deliveryPersistent;
        configuredQoS();
    }

    public Integer getDeliveryMode() {
        return deliveryMode;
    }

    /**
     * Specifies the delivery mode to be used.
     * Possibles values are those defined by javax.jms.DeliveryMode.
     * NON_PERSISTENT = 1 and PERSISTENT = 2.
     */
    public void setDeliveryMode(Integer deliveryMode) {
        this.deliveryMode = deliveryMode;
        configuredQoS();
    }

    public boolean isReplyToDeliveryPersistent() {
        return replyToDeliveryPersistent;
    }

    /**
     * Specifies whether to use persistent delivery by default for replies.
     */
    public void setReplyToDeliveryPersistent(boolean replyToDeliveryPersistent) {
        this.replyToDeliveryPersistent = replyToDeliveryPersistent;
    }

    public long getTimeToLive() {
        return timeToLive;
    }

    /**
     * When sending messages, specifies the time-to-live of the message (in milliseconds).
     */
    public void setTimeToLive(long timeToLive) {
        this.timeToLive = timeToLive;
        configuredQoS();
    }

    public MessageConverter getMessageConverter() {
        return messageConverter;
    }

    /**
     * To use a custom Spring org.springframework.jms.support.converter.MessageConverter so you can be in control
     * how to map to/from a javax.jms.Message.
     */
    public void setMessageConverter(MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    public boolean isMapJmsMessage() {
        return mapJmsMessage;
    }

    /**
     * Specifies whether Camel should auto map the received JMS message to a suited payload type, such as javax.jms.TextMessage to a String etc.
     */
    public void setMapJmsMessage(boolean mapJmsMessage) {
        this.mapJmsMessage = mapJmsMessage;
    }

    public boolean isMessageIdEnabled() {
        return messageIdEnabled;
    }

    /**
     * When sending, specifies whether message IDs should be added. This is just an hint to the JMS Broker.
     * If the JMS provider accepts this hint, these messages must have the message ID set to null; if the provider ignores the hint, the message ID must be set to its normal unique value
     */
    public void setMessageIdEnabled(boolean messageIdEnabled) {
        this.messageIdEnabled = messageIdEnabled;
    }

    public boolean isMessageTimestampEnabled() {
        return messageTimestampEnabled;
    }

    /**
     * Specifies whether timestamps should be enabled by default on sending messages. This is just an hint to the JMS Broker.
     * If the JMS provider accepts this hint, these messages must have the timestamp set to zero; if the provider ignores the hint, the timestamp must be set to its normal value.
     */
    public void setMessageTimestampEnabled(boolean messageTimestampEnabled) {
        this.messageTimestampEnabled = messageTimestampEnabled;
    }

    public int getPriority() {
        return priority;
    }

    /**
     * Values greater than 1 specify the message priority when sending (where 0 is the lowest priority and 9 is the highest).
     * The explicitQosEnabled option must also be enabled in order for this option to have any effect.
     */
    public void setPriority(int priority) {
        this.priority = priority;
        configuredQoS();
    }

    public int getAcknowledgementMode() {
        return acknowledgementMode;
    }

    /**
     * The JMS acknowledgement mode defined as an Integer.
     * Allows you to set vendor-specific extensions to the acknowledgment mode.
     * For the regular modes, it is preferable to use the acknowledgementModeName instead.
     */
    public void setAcknowledgementMode(int consumerAcknowledgementMode) {
        this.acknowledgementMode = consumerAcknowledgementMode;
        this.acknowledgementModeName = null;
    }

    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Specifies whether to use transacted mode
     */
    public void setTransacted(boolean consumerTransacted) {
        this.transacted = consumerTransacted;
    }

    public boolean isLazyCreateTransactionManager() {
        return lazyCreateTransactionManager;
    }

    /**
     * If true, Camel will create a JmsTransactionManager, if there is no transactionManager injected when option transacted=true.
     */
    public void setLazyCreateTransactionManager(boolean lazyCreating) {
        this.lazyCreateTransactionManager = lazyCreating;
    }

    public String getEagerPoisonBody() {
        return eagerPoisonBody;
    }

    /**
     * If eagerLoadingOfProperties is enabled and the JMS message payload (JMS body or JMS properties) (cannot be read/mapped),
     * then set this text as the message body instead so the message can be processed
     * (the cause of the poison are already stored as exception on the Exchange).
     * This can be turned off by setting eagerPoisonBody=false.
     * See also the option eagerLoadingOfProperties.
     */
    public void setEagerPoisonBody(String eagerPoisonBody) {
        this.eagerPoisonBody = eagerPoisonBody;
    }

    public boolean isEagerLoadingOfProperties() {
        return eagerLoadingOfProperties;
    }

    /**
     * Enables eager loading of JMS properties and payload as soon as a message is loaded
     * which generally is inefficient as the JMS properties may not be required
     * but sometimes can catch early any issues with the underlying JMS provider
     * and the use of JMS properties. See also the option eagerPoisonBody.
     */
    public void setEagerLoadingOfProperties(boolean eagerLoadingOfProperties) {
        this.eagerLoadingOfProperties = eagerLoadingOfProperties;
    }

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    /**
     * Specifies whether Camel ignores the JMSReplyTo header in messages. If true, Camel does not send a reply back to
     * the destination specified in the JMSReplyTo header. You can use this option if you want Camel to consume from a
     * route and you do not want Camel to automatically send back a reply message because another component in your code
     * handles the reply message. You can also use this option if you want to use Camel as a proxy between different
     * message brokers and you want to route message from one system to another.
     */
    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    /**
     * Set to true, if you want to send message using the QoS settings specified on the message,
     * instead of the QoS settings on the JMS endpoint. The following three headers are considered JMSPriority, JMSDeliveryMode,
     * and JMSExpiration. You can provide all or only some of them. If not provided, Camel will fall back to use the
     * values from the endpoint instead. So, when using this option, the headers override the values from the endpoint.
     * The explicitQosEnabled option, by contrast, will only use options set on the endpoint, and not values from the message header.
     */
    public void setPreserveMessageQos(boolean preserveMessageQos) {
        this.preserveMessageQos = preserveMessageQos;
    }

    public JmsOperations getJmsOperations() {
        return jmsOperations;
    }

    /**
     * Allows you to use your own implementation of the org.springframework.jms.core.JmsOperations interface.
     * Camel uses JmsTemplate as default. Can be used for testing purpose, but not used much as stated in the spring API docs.
     */
    public void setJmsOperations(JmsOperations jmsOperations) {
        this.jmsOperations = jmsOperations;
    }

    public DestinationResolver getDestinationResolver() {
        return destinationResolver;
    }

    /**
     * A pluggable org.springframework.jms.support.destination.DestinationResolver that allows you to use your own resolver
     * (for example, to lookup the real destination in a JNDI registry).
     */
    public void setDestinationResolver(DestinationResolver destinationResolver) {
        this.destinationResolver = destinationResolver;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    public static DestinationResolver createDestinationResolver(final DestinationEndpoint destinationEndpoint) {
        return (session, destinationName, pubSubDomain) -> destinationEndpoint.getJmsDestination(session);
    }

    protected void configureMessageListenerContainer(AbstractMessageListenerContainer container,
                                                     JmsEndpoint endpoint) throws Exception {
        container.setConnectionFactory(getOrCreateListenerConnectionFactory());
        container.setConnectionFactory(getOrCreateListenerConnectionFactory());
        container.setConnectionFactory(getOrCreateListenerConnectionFactory());
        if (endpoint instanceof DestinationEndpoint) {
            container.setDestinationResolver(createDestinationResolver((DestinationEndpoint) endpoint));
        } else if (destinationResolver != null) {
            container.setDestinationResolver(destinationResolver);
        }
        container.setAutoStartup(autoStartup);

        if (durableSubscriptionName != null) {
            container.setDurableSubscriptionName(durableSubscriptionName);
            container.setSubscriptionDurable(true);
        }
        if (clientId != null) {
            container.setClientId(clientId);
        }

        if (exceptionListener != null) {
            container.setExceptionListener(exceptionListener);
        }

        if (errorHandler != null) {
            container.setErrorHandler(errorHandler);
        } else {
            ErrorHandler handler = new DefaultSpringErrorHandler(endpoint.getCamelContext(), EndpointMessageListener.class, getErrorHandlerLoggingLevel(), isErrorHandlerLogStackTrace());
            container.setErrorHandler(handler);
        }

        container.setAcceptMessagesWhileStopping(acceptMessagesWhileStopping);
        container.setExposeListenerSession(exposeListenerSession);
        container.setSessionTransacted(transacted);
        if (transacted) {
            container.setSessionAcknowledgeMode(Session.SESSION_TRANSACTED);
        } else {
            if (acknowledgementMode >= 0) {
                container.setSessionAcknowledgeMode(acknowledgementMode);
            } else if (acknowledgementModeName != null) {
                container.setSessionAcknowledgeModeName(acknowledgementModeName);
            }
        }

        if (endpoint.getSelector() != null && endpoint.getSelector().length() != 0) {
            container.setMessageSelector(endpoint.getSelector());
        }

        if (container instanceof DefaultMessageListenerContainer) {
            DefaultMessageListenerContainer listenerContainer = (DefaultMessageListenerContainer) container;
            configureDefaultMessageListenerContainer(endpoint, listenerContainer);
        } else if (container instanceof SimpleMessageListenerContainer) {
            SimpleMessageListenerContainer listenerContainer = (SimpleMessageListenerContainer) container;
            configureSimpleMessageListenerContainer(listenerContainer);
        }
    }

    private void configureSimpleMessageListenerContainer(SimpleMessageListenerContainer listenerContainer) {
        if (maxConcurrentConsumers > 0) {
            if (maxConcurrentConsumers < concurrentConsumers) {
                throw new IllegalArgumentException("Property maxConcurrentConsumers: " + maxConcurrentConsumers + " must be higher than concurrentConsumers: "
                        + concurrentConsumers);
            }
            listenerContainer.setConcurrency(concurrentConsumers + "-" + maxConcurrentConsumers);
        } else if (concurrentConsumers >= 0) {
            listenerContainer.setConcurrentConsumers(concurrentConsumers);
        }

        listenerContainer.setPubSubNoLocal(pubSubNoLocal);
        if (taskExecutor != null) {
            listenerContainer.setTaskExecutor(taskExecutor);
        }
    }

    private void configureDefaultMessageListenerContainer(JmsEndpoint endpoint, DefaultMessageListenerContainer container) {
        if (concurrentConsumers >= 0) {
            container.setConcurrentConsumers(concurrentConsumers);
        }

        if (cacheLevel >= 0) {
            container.setCacheLevel(cacheLevel);
        } else if (cacheLevelName != null) {
            container.setCacheLevelName(cacheLevelName);
        } else {
            container.setCacheLevel(defaultCacheLevel(endpoint));
        }

        if (idleTaskExecutionLimit >= 0) {
            container.setIdleTaskExecutionLimit(idleTaskExecutionLimit);
        }
        if (idleConsumerLimit >= 0) {
            container.setIdleConsumerLimit(idleConsumerLimit);
        }
        if (maxConcurrentConsumers > 0) {
            if (maxConcurrentConsumers < concurrentConsumers) {
                throw new IllegalArgumentException("Property maxConcurrentConsumers: " + maxConcurrentConsumers
                        + " must be higher than concurrentConsumers: " + concurrentConsumers);
            }
            container.setMaxConcurrentConsumers(maxConcurrentConsumers);
        }
        if (maxMessagesPerTask >= 0) {
            container.setMaxMessagesPerTask(maxMessagesPerTask);
        }
        container.setPubSubNoLocal(pubSubNoLocal);
        if (receiveTimeout >= 0) {
            container.setReceiveTimeout(receiveTimeout);
        }
        if (recoveryInterval >= 0) {
            container.setRecoveryInterval(recoveryInterval);
        }
        if (taskExecutor != null) {
            container.setTaskExecutor(taskExecutor);
        }
        PlatformTransactionManager tm = getOrCreateTransactionManager();
        if (tm != null) {
            container.setTransactionManager(tm);
        } else if (transactionManager == null && transacted && !lazyCreateTransactionManager) {
            container.setSessionTransacted(true);
        }
        if (transactionName != null) {
            container.setTransactionName(transactionName);
        }
        if (transactionTimeout >= 0) {
            container.setTransactionTimeout(transactionTimeout);
        }
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

        JmsOperations operations = listener.getTemplate();
        if (operations instanceof JmsTemplate) {
            JmsTemplate template = (JmsTemplate) operations;
            template.setDeliveryPersistent(isReplyToDeliveryPersistent());
        }
    }

    /**
     * Defaults the JMS cache level if none is explicitly specified.
     * <p>
     * Will return <tt>CACHE_AUTO</tt> which will pickup and use <tt>CACHE_NONE</tt>
     * if transacted has been enabled, otherwise it will use <tt>CACHE_CONSUMER</tt>
     * which is the most efficient.
     *
     * @param endpoint the endpoint
     * @return the cache level
     */
    protected int defaultCacheLevel(JmsEndpoint endpoint) {
        return DefaultMessageListenerContainer.CACHE_AUTO;
    }

    /**
     * Factory method which allows derived classes to customize the lazy
     * creation
     */
    protected ConnectionFactory createConnectionFactory() {
        ObjectHelper.notNull(connectionFactory, "connectionFactory");
        return null;
    }

    /**
     * Factory method which allows derived classes to customize the lazy
     * creation
     */
    protected ConnectionFactory createListenerConnectionFactory() {
        return getOrCreateConnectionFactory();
    }

    /**
     * Factory method which allows derived classes to customize the lazy
     * creation
     */
    protected ConnectionFactory createTemplateConnectionFactory() {
        return getOrCreateConnectionFactory();
    }

    /**
     * Factory method which which allows derived classes to customize the lazy
     * transaction manager creation
     */
    protected PlatformTransactionManager createTransactionManager() {
        JmsTransactionManager answer = new JmsTransactionManager();
        answer.setConnectionFactory(getOrCreateConnectionFactory());
        return answer;
    }

    public boolean isPreserveMessageQos() {
        return preserveMessageQos;
    }

    /**
     * When one of the QoS properties are configured such as {@link #setDeliveryPersistent(boolean)},
     * {@link #setPriority(int)} or {@link #setTimeToLive(long)} then we should auto default the
     * setting of {@link #setExplicitQosEnabled(boolean)} if its not been configured yet
     */
    protected void configuredQoS() {
        if (explicitQosEnabled == null) {
            explicitQosEnabled = true;
        }
    }

    public boolean isAlwaysCopyMessage() {
        return alwaysCopyMessage;
    }

    /**
     * If true, Camel will always make a JMS message copy of the message when it is passed to the producer for sending.
     * Copying the message is needed in some situations, such as when a replyToDestinationSelectorName is set
     * (incidentally, Camel will set the alwaysCopyMessage option to true, if a replyToDestinationSelectorName is set)
     */
    public void setAlwaysCopyMessage(boolean alwaysCopyMessage) {
        this.alwaysCopyMessage = alwaysCopyMessage;
    }

    public boolean isUseMessageIDAsCorrelationID() {
        return useMessageIDAsCorrelationID;
    }

    /**
     * Specifies whether JMSMessageID should always be used as JMSCorrelationID for InOut messages.
     */
    public void setUseMessageIDAsCorrelationID(boolean useMessageIDAsCorrelationID) {
        this.useMessageIDAsCorrelationID = useMessageIDAsCorrelationID;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * The timeout for waiting for a reply when using the InOut Exchange Pattern (in milliseconds).
     * The default is 20 seconds. You can include the header "CamelJmsRequestTimeout" to override this endpoint configured
     * timeout value, and thus have per message individual timeout values.
     * See also the requestTimeoutCheckerInterval option.
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public long getRequestTimeoutCheckerInterval() {
        return requestTimeoutCheckerInterval;
    }

    /**
     * Configures how often Camel should check for timed out Exchanges when doing request/reply over JMS.
     * By default Camel checks once per second. But if you must react faster when a timeout occurs,
     * then you can lower this interval, to check more frequently. The timeout is determined by the option requestTimeout.
     */
    public void setRequestTimeoutCheckerInterval(long requestTimeoutCheckerInterval) {
        this.requestTimeoutCheckerInterval = requestTimeoutCheckerInterval;
    }

    public String getReplyTo() {
        return replyTo;
    }

    /**
     * Provides an explicit ReplyTo destination, which overrides any incoming value of Message.getJMSReplyTo().
     */
    public void setReplyTo(String replyToDestination) {
        this.replyTo = normalizeDestinationName(replyToDestination);
    }

    public String getReplyToDestinationSelectorName() {
        return replyToDestinationSelectorName;
    }

    /**
     * Sets the JMS Selector using the fixed name to be used so you can filter out your own replies
     * from the others when using a shared queue (that is, if you are not using a temporary reply queue).
     */
    public void setReplyToDestinationSelectorName(String replyToDestinationSelectorName) {
        this.replyToDestinationSelectorName = replyToDestinationSelectorName;
        // in case of consumer -> producer and a named replyTo correlation selector
        // message pass through is impossible as we need to set the value of selector into
        // outgoing message, which would be read-only if pass through were to remain enabled
        if (replyToDestinationSelectorName != null) {
            setAlwaysCopyMessage(true);
        }
    }

    public String getReplyToOverride() {
        return replyToOverride;
    }

    /**
     * Provides an explicit ReplyTo destination in the JMS message, which overrides the setting of replyTo.
     * It is useful if you want to forward the message to a remote Queue and receive the reply message from the ReplyTo destination.
     */
    public void setReplyToOverride(String replyToDestination) {
        this.replyToOverride = normalizeDestinationName(replyToDestination);
    }

    public boolean isReplyToSameDestinationAllowed() {
        return replyToSameDestinationAllowed;
    }

    /**
     * Whether a JMS consumer is allowed to send a reply message to the same destination that the consumer is using to
     * consume from. This prevents an endless loop by consuming and sending back the same message to itself.
     */
    public void setReplyToSameDestinationAllowed(boolean replyToSameDestinationAllowed) {
        this.replyToSameDestinationAllowed = replyToSameDestinationAllowed;
    }

    public JmsMessageType getJmsMessageType() {
        return jmsMessageType;
    }

    /**
     * Allows you to force the use of a specific javax.jms.Message implementation for sending JMS messages.
     * Possible values are: Bytes, Map, Object, Stream, Text.
     * By default, Camel would determine which JMS message type to use from the In body type. This option allows you to specify it.
     */
    public void setJmsMessageType(JmsMessageType jmsMessageType) {
        if (jmsMessageType == JmsMessageType.Blob && !supportBlobMessage()) {
            throw new IllegalArgumentException("BlobMessage is not supported by this implementation");
        }
        this.jmsMessageType = jmsMessageType;
    }

    /**
     * Should get overridden by implementations which support BlobMessages
     *
     * @return false
     */
    protected boolean supportBlobMessage() {
        return false;
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

    public boolean isTransferExchange() {
        return transferExchange;
    }

    /**
     * You can transfer the exchange over the wire instead of just the body and headers.
     * The following fields are transferred: In body, Out body, Fault body, In headers, Out headers, Fault headers,
     * exchange properties, exchange exception.
     * This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.
     * You must enable this option on both the producer and consumer side, so Camel knows the payloads is an Exchange and not a regular payload.
     * Use this with caution as the data is using Java Object serialization and requires the received to be able to deserialize the data at Class level,
     * which forces a strong coupling between the producers and consumer having to use compatible Camel versions!
     */
    public void setTransferExchange(boolean transferExchange) {
        this.transferExchange = transferExchange;
    }

    public boolean isAllowSerializedHeaders() {
        return allowSerializedHeaders;
    }

    /**
     * Controls whether or not to include serialized headers.
     * Applies only when {@link #isTransferExchange()} is {@code true}.
     * This requires that the objects are serializable. Camel will exclude any non-serializable objects and log it at WARN level.
     */
    public void setAllowSerializedHeaders(boolean allowSerializedHeaders) {
        this.allowSerializedHeaders = allowSerializedHeaders;
    }

    public boolean isTransferException() {
        return transferException;
    }

    /**
     * If enabled and you are using Request Reply messaging (InOut) and an Exchange failed on the consumer side,
     * then the caused Exception will be send back in response as a javax.jms.ObjectMessage.
     * If the client is Camel, the returned Exception is rethrown. This allows you to use Camel JMS as a bridge
     * in your routing - for example, using persistent queues to enable robust routing.
     * Notice that if you also have transferExchange enabled, this option takes precedence.
     * The caught exception is required to be serializable.
     * The original Exception on the consumer side can be wrapped in an outer exception
     * such as org.apache.camel.RuntimeCamelException when returned to the producer.
     * Use this with caution as the data is using Java Object serialization and requires the received to be able to deserialize the data at Class level,
     * which forces a strong coupling between the producers and consumer!
     */
    public void setTransferException(boolean transferException) {
        this.transferException = transferException;
    }

    public boolean isAsyncStartListener() {
        return asyncStartListener;
    }

    /**
     * Whether to startup the JmsConsumer message listener asynchronously, when starting a route.
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

    public boolean isAsyncStopListener() {
        return asyncStopListener;
    }

    /**
     * Whether to stop the JmsConsumer message listener asynchronously, when stopping a route.
     */
    public void setAsyncStopListener(boolean asyncStopListener) {
        this.asyncStopListener = asyncStopListener;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    /**
     * Specifies whether to test the connection on startup.
     * This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker.
     * If a connection cannot be granted then Camel throws an exception on startup.
     * This ensures that Camel is not started with failed connections.
     * The JMS producers is tested as well.
     */
    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    /**
     * When using mapJmsMessage=false Camel will create a new JMS message to send to a new JMS destination
     * if you touch the headers (get or set) during the route. Set this option to true to force Camel to send
     * the original JMS message that was received.
     */
    public void setForceSendOriginalMessage(boolean forceSendOriginalMessage) {
        this.forceSendOriginalMessage = forceSendOriginalMessage;
    }

    public boolean isForceSendOriginalMessage() {
        return forceSendOriginalMessage;
    }

    public boolean isDisableTimeToLive() {
        return disableTimeToLive;
    }

    /**
     * Use this option to force disabling time to live.
     * For example when you do request/reply over JMS, then Camel will by default use the requestTimeout value
     * as time to live on the message being sent. The problem is that the sender and receiver systems have
     * to have their clocks synchronized, so they are in sync. This is not always so easy to archive.
     * So you can use disableTimeToLive=true to not set a time to live value on the sent message.
     * Then the message will not expire on the receiver system. See below in section About time to live for more details.
     */
    public void setDisableTimeToLive(boolean disableTimeToLive) {
        this.disableTimeToLive = disableTimeToLive;
    }

    public ReplyToType getReplyToType() {
        return replyToType;
    }

    /**
     * Allows for explicitly specifying which kind of strategy to use for replyTo queues when doing request/reply over JMS.
     * Possible values are: Temporary, Shared, or Exclusive.
     * By default Camel will use temporary queues. However if replyTo has been configured, then Shared is used by default.
     * This option allows you to use exclusive queues instead of shared ones.
     * See Camel JMS documentation for more details, and especially the notes about the implications if running in a clustered environment,
     * and the fact that Shared reply queues has lower performance than its alternatives Temporary and Exclusive.
     */
    public void setReplyToType(ReplyToType replyToType) {
        this.replyToType = replyToType;
    }

    public boolean isAsyncConsumer() {
        return asyncConsumer;
    }

    /**
     * Whether the JmsConsumer processes the Exchange asynchronously.
     * If enabled then the JmsConsumer may pickup the next message from the JMS queue,
     * while the previous message is being processed asynchronously (by the Asynchronous Routing Engine).
     * This means that messages may be processed not 100% strictly in order. If disabled (as default)
     * then the Exchange is fully processed before the JmsConsumer will pickup the next message from the JMS queue.
     * Note if transacted has been enabled, then asyncConsumer=true does not run asynchronously, as transaction
     * must be executed synchronously (Camel 3.0 may support async transactions).
     */
    public void setAsyncConsumer(boolean asyncConsumer) {
        this.asyncConsumer = asyncConsumer;
    }

    /**
     * Sets the cache level by name for the reply consumer when doing request/reply over JMS.
     * This option only applies when using fixed reply queues (not temporary).
     * Camel will by default use: CACHE_CONSUMER for exclusive or shared w/ replyToSelectorName.
     * And CACHE_SESSION for shared without replyToSelectorName. Some JMS brokers such as IBM WebSphere
     * may require to set the replyToCacheLevelName=CACHE_NONE to work.
     * Note: If using temporary queues then CACHE_NONE is not allowed,
     * and you must use a higher value such as CACHE_CONSUMER or CACHE_SESSION.
     */
    public void setReplyToCacheLevelName(String name) {
        this.replyToCacheLevelName = name;
    }

    public String getReplyToCacheLevelName() {
        return replyToCacheLevelName;
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

    public MessageListenerContainerFactory getMessageListenerContainerFactory() {
        return messageListenerContainerFactory;
    }

    /**
     * Registry ID of the MessageListenerContainerFactory used to determine what
     * org.springframework.jms.listener.AbstractMessageListenerContainer to use to consume messages.
     * Setting this will automatically set consumerType to Custom.
     */
    public void setMessageListenerContainerFactory(MessageListenerContainerFactory messageListenerContainerFactory) {
        this.messageListenerContainerFactory = messageListenerContainerFactory;
    }

    public boolean isIncludeSentJMSMessageID() {
        return includeSentJMSMessageID;
    }

    /**
     * Only applicable when sending to JMS destination using InOnly (eg fire and forget).
     * Enabling this option will enrich the Camel Exchange with the actual JMSMessageID
     * that was used by the JMS client when the message was sent to the JMS destination.
     */
    public void setIncludeSentJMSMessageID(boolean includeSentJMSMessageID) {
        this.includeSentJMSMessageID = includeSentJMSMessageID;
    }

    public DefaultTaskExecutorType getDefaultTaskExecutorType() {
        return defaultTaskExecutorType;
    }

    /**
     * Specifies what default TaskExecutor type to use in the DefaultMessageListenerContainer,
     * for both consumer endpoints and the ReplyTo consumer of producer endpoints.
     * Possible values: SimpleAsync (uses Spring's SimpleAsyncTaskExecutor) or ThreadPool
     * (uses Spring's ThreadPoolTaskExecutor with optimal values - cached threadpool-like).
     * If not set, it defaults to the previous behaviour, which uses a cached thread pool
     * for consumer endpoints and SimpleAsync for reply consumers.
     * The use of ThreadPool is recommended to reduce "thread trash" in elastic configurations
     * with dynamically increasing and decreasing concurrent consumers.
     */
    public void setDefaultTaskExecutorType(DefaultTaskExecutorType defaultTaskExecutorType) {
        this.defaultTaskExecutorType = defaultTaskExecutorType;
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

    public String getSelector() {
        return selector;
    }

    /**
     * Sets the JMS selector to use
     */
    public void setSelector(String selector) {
        this.selector = selector;
    }

    /**
     * Use this JMS property to correlate messages in InOut exchange pattern (request-reply)
     * instead of JMSCorrelationID property. This allows you to exchange messages with
     * systems that do not correlate messages using JMSCorrelationID JMS property. If used
     * JMSCorrelationID will not be used or set by Camel. The value of here named property
     * will be generated if not supplied in the header of the message under the same name.
     */
    public void setCorrelationProperty(final String correlationProperty) {
        this.correlationProperty = correlationProperty;
    }

    public String getCorrelationProperty() {
        return correlationProperty;
    }

    public String getAllowAdditionalHeaders() {
        return allowAdditionalHeaders;
    }

    /**
     * This option is used to allow additional headers which may have values that are invalid according to JMS specification.
     + For example some message systems such as WMQ do this with header names using prefix JMS_IBM_MQMD_ containing values with byte array or other invalid types.
     + You can specify multiple header names separated by comma, and use * as suffix for wildcard matching.
     */
    public void setAllowAdditionalHeaders(String allowAdditionalHeaders) {
        this.allowAdditionalHeaders = allowAdditionalHeaders;
    }

    public boolean isSubscriptionDurable() {
        return subscriptionDurable;
    }

    /**
     * Set whether to make the subscription durable. The durable subscription name
     * to be used can be specified through the "subscriptionName" property.
     * <p>Default is "false". Set this to "true" to register a durable subscription,
     * typically in combination with a "subscriptionName" value (unless
     * your message listener class name is good enough as subscription name).
     * <p>Only makes sense when listening to a topic (pub-sub domain),
     * therefore this method switches the "pubSubDomain" flag as well.
     */
    public void setSubscriptionDurable(boolean subscriptionDurable) {
        this.subscriptionDurable = subscriptionDurable;
    }

    public boolean isSubscriptionShared() {
        return subscriptionShared;
    }

    /**
     * Set whether to make the subscription shared. The shared subscription name
     * to be used can be specified through the "subscriptionName" property.
     * <p>Default is "false". Set this to "true" to register a shared subscription,
     * typically in combination with a "subscriptionName" value (unless
     * your message listener class name is good enough as subscription name).
     * Note that shared subscriptions may also be durable, so this flag can
     * (and often will) be combined with "subscriptionDurable" as well.
     * <p>Only makes sense when listening to a topic (pub-sub domain),
     * therefore this method switches the "pubSubDomain" flag as well.
     * <p><b>Requires a JMS 2.0 compatible message broker.</b>
     */
    public void setSubscriptionShared(boolean subscriptionShared) {
        this.subscriptionShared = subscriptionShared;
    }

    public String getSubscriptionName() {
        return subscriptionName;
    }

    /**
     * Set the name of a subscription to create. To be applied in case
     * of a topic (pub-sub domain) with a shared or durable subscription.
     * <p>The subscription name needs to be unique within this client's
     * JMS client id. Default is the class name of the specified message listener.
     * <p>Note: Only 1 concurrent consumer (which is the default of this
     * message listener container) is allowed for each subscription,
     * except for a shared subscription (which requires JMS 2.0).
     */
    public void setSubscriptionName(String subscriptionName) {
        this.subscriptionName = subscriptionName;
    }

    public boolean isStreamMessageTypeEnabled() {
        return streamMessageTypeEnabled;
    }

    /**
     * Sets whether StreamMessage type is enabled or not.
     * Message payloads of streaming kind such as files, InputStream, etc will either by sent as BytesMessage or StreamMessage.
     * This option controls which kind will be used. By default BytesMessage is used which enforces the entire message payload to be read into memory.
     * By enabling this option the message payload is read into memory in chunks and each chunk is then written to the StreamMessage until no more data.
     */
    public void setStreamMessageTypeEnabled(boolean streamMessageTypeEnabled) {
        this.streamMessageTypeEnabled = streamMessageTypeEnabled;
    }

    /**
     * Gets whether date headers should be formatted according to the ISO 8601
     * standard.
     */
    public boolean isFormatDateHeadersToIso8601() {
        return formatDateHeadersToIso8601;
    }

    /**
     * Sets whether date headers should be formatted according to the ISO 8601
     * standard.
     */
    public void setFormatDateHeadersToIso8601(boolean formatDateHeadersToIso8601) {
        this.formatDateHeadersToIso8601 = formatDateHeadersToIso8601;
    }

    public long getDeliveryDelay() {
        return deliveryDelay;
    }

    /**
     * Sets delivery delay to use for send calls for JMS. This option requires JMS 2.0 compliant broker.
     */
    public void setDeliveryDelay(long deliveryDelay) {
        this.deliveryDelay = deliveryDelay;
    }

    public boolean isArtemisStreamingEnabled() {
        return artemisStreamingEnabled;
    }

    /**
     * Whether optimizing for Apache Artemis streaming mode.
     */
    public void setArtemisStreamingEnabled(boolean artemisStreamingEnabled) {
        this.artemisStreamingEnabled = artemisStreamingEnabled;
    }
}
