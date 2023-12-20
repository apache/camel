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
package org.apache.camel.component.springrabbit;

import java.util.Map;

import org.apache.camel.AsyncEndpoint;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.PropertiesHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.AsyncRabbitTemplate;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;

import static org.apache.camel.component.springrabbit.SpringRabbitMQConstants.DIRECT_MESSAGE_LISTENER_CONTAINER;

/**
 * Send and receive messages from RabbitMQ using Spring RabbitMQ client.
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "spring-rabbitmq", title = "Spring RabbitMQ",
             syntax = "spring-rabbitmq:exchangeName",
             category = { Category.MESSAGING }, headersClass = SpringRabbitMQConstants.class)
public class SpringRabbitMQEndpoint extends DefaultEndpoint implements AsyncEndpoint {

    public static final String ARG_PREFIX = "arg.";
    public static final String CONSUMER_ARG_PREFIX = "consumer.";
    public static final String EXCHANGE_ARG_PREFIX = "exchange.";
    public static final String QUEUE_ARG_PREFIX = "queue.";
    public static final String BINDING_ARG_PREFIX = "binding.";
    public static final String DLQ_EXCHANGE_ARG_PREFIX = "dlq.exchange.";
    public static final String DLQ_QUEUE_ARG_PREFIX = "dlq.queue.";
    public static final String DLQ_BINDING_PREFIX = "dlq.binding.";

    private static final Logger LOG = LoggerFactory.getLogger(SpringRabbitMQEndpoint.class);

    @UriPath
    @Metadata(required = true,
              description = "The exchange name determines the exchange to which the produced messages will be sent to."
                            + " In the case of consumers, the exchange name determines the exchange the queue will be bound to."
                            + " Note: to use default exchange then do not use empty name, but use default instead.")
    private String exchangeName;
    @UriParam(label = "consumer", defaultValue = "direct", enums = "direct,fanout,headers,topic",
              description = "The type of the exchange")
    private String exchangeType = "direct";
    @UriParam(label = "common",
              description = "The value of a routing key to use. Default is empty which is not helpful when using the default (or any direct) exchange, but fine if the exchange is a headers exchange for instance.")
    private String routingKey = "";
    @UriParam(label = "common",
              description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @UriParam(label = "consumer",
              description = "The queue(s) to use for consuming messages. Multiple queue names can be separated by comma."
                            + " If none has been configured then Camel will generate an unique id as the queue name for the consumer.")
    private String queues;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Specifies whether the consumer container should auto-startup.")
    private boolean autoStartup = true;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Specifies whether the consumer should auto declare binding between exchange, queue and routing key when starting.")
    private boolean autoDeclare = true;
    @UriParam(label = "consumer",
              description = "Whether the consumer processes the Exchange asynchronously."
                            + " If enabled then the consumer may pickup the next message from the queue,"
                            + " while the previous message is being processed asynchronously (by the Asynchronous Routing Engine)."
                            + " This means that messages may be processed not 100% strictly in order. If disabled (as default)"
                            + " then the Exchange is fully processed before the consumer will pickup the next message from the queue.")
    private boolean asyncConsumer;
    @UriParam(description = "Specifies whether to test the connection on startup."
                            + " This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker."
                            + " If a connection cannot be granted then Camel throws an exception on startup."
                            + " This ensures that Camel is not started with failed connections."
                            + " The JMS producers is tested as well.")
    private boolean testConnectionOnStartup;
    @UriParam(label = "advanced",
              description = "To use a custom MessageConverter so you can be in control how to map to/from a org.springframework.amqp.core.Message.")
    private MessageConverter messageConverter;
    @UriParam(label = "advanced",
              description = "To use a custom MessagePropertiesConverter so you can be in control how to map to/from a org.springframework.amqp.core.MessageProperties.")
    private MessagePropertiesConverter messagePropertiesConverter;
    @UriParam(label = "advanced", prefix = ARG_PREFIX, multiValue = true,
              description = "Specify arguments for configuring the different RabbitMQ concepts, a different prefix is required for each element:"
                            + " arg.consumer. arg.exchange. arg.queue. arg.binding. arg.dlq.exchange. arg.dlq.queue. arg.dlq.binding."
                            + " For example to declare a queue with message ttl argument: args=arg.queue.x-message-ttl=60000")
    private Map<String, Object> args;
    @UriParam(label = "consumer",
              description = "Flag controlling the behaviour of the container with respect to message acknowledgement. The most common usage is to let the container handle the acknowledgements"
                            + " (so the listener doesn't need to know about the channel or the message)."
                            + " Set to AcknowledgeMode.MANUAL if the listener will send the acknowledgements itself using Channel.basicAck(long, boolean). Manual acks are consistent with either a transactional or non-transactional channel,"
                            + " but if you are doing no other work on the channel at the same other than receiving a single message then the transaction is probably unnecessary."
                            + " Set to AcknowledgeMode.NONE to tell the broker not to expect any acknowledgements, and it will assume all messages are acknowledged as soon as they are sent (this is autoack in native Rabbit broker terms)."
                            + " If AcknowledgeMode.NONE then the channel cannot be transactional (so the container will fail on start up if that flag is accidentally set).")
    private AcknowledgeMode acknowledgeMode = AcknowledgeMode.AUTO;
    @UriParam(label = "consumer", description = "Set to true for an exclusive consumer")
    private boolean exclusive;
    @UriParam(label = "consumer", description = "Set to true for an no-local consumer")
    private boolean noLocal;
    @UriParam(label = "consumer", description = "The name of the dead letter exchange")
    private String deadLetterExchange;
    @UriParam(label = "consumer", description = "The name of the dead letter queue")
    private String deadLetterQueue;
    @UriParam(label = "consumer", description = "The routing key for the dead letter exchange")
    private String deadLetterRoutingKey;
    @UriParam(label = "consumer", defaultValue = "direct", enums = "direct,fanout,headers,topic",
              description = "The type of the dead letter exchange")
    private String deadLetterExchangeType = "direct";
    @UriParam(label = "common",
              description = "Specifies whether Camel ignores the ReplyTo header in messages. If true, Camel does not send a reply back to"
                            + " the destination specified in the ReplyTo header. You can use this option if you want Camel to consume from a"
                            + " route and you do not want Camel to automatically send back a reply message because another component in your code"
                            + " handles the reply message. You can also use this option if you want to use Camel as a proxy between different"
                            + " message brokers and you want to route message from one system to another.")
    private boolean disableReplyTo;
    @UriParam(label = "producer", javaType = "java.time.Duration", defaultValue = "30000",
              description = "Specify the timeout in milliseconds to be used when waiting for a reply message when doing request/reply (InOut) messaging."
                            + " The default value is 30 seconds. A negative value indicates an indefinite timeout (Beware that this will cause a memory leak if a reply is not received).")
    private long replyTimeout = 30000;
    @UriParam(label = "producer", javaType = "java.time.Duration", defaultValue = "5000",
              description = "Specify the timeout in milliseconds to be used when waiting for a message sent to be confirmed by RabbitMQ when doing send only messaging (InOnly)."
                            + " The default value is 5 seconds. A negative value indicates an indefinite timeout.")
    private long confirmTimeout = 5000;
    @UriParam(label = "producer", enums = "auto,enabled,disabled", defaultValue = "auto",
              description = "Controls whether to wait for confirms. The connection factory must be configured for publisher confirms and this method."
                            + " auto = Camel detects if the connection factory uses confirms or not. disabled = Confirms is disabled. enabled = Confirms is enabled.")
    private String confirm = "auto";
    @UriParam(label = "producer", defaultValue = "false",
              description = "Use a separate connection for publishers and consumers")
    private boolean usePublisherConnection;
    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether to allow sending messages with no body. If this option is false and the message body is null, then an MessageConversionException is thrown.")
    private boolean allowNullBody;
    @UriParam(defaultValue = "false", label = "advanced",
              description = "Sets whether synchronous processing should be strictly used")
    private boolean synchronous;
    @UriParam(label = "consumer,advanced",
              description = "Tell the broker how many messages to send in a single request. Often this can be set quite high to improve throughput.")
    private Integer prefetchCount;
    @UriParam(label = "consumer,advanced", defaultValue = DIRECT_MESSAGE_LISTENER_CONTAINER, enums = "DMLC,SMLC",
              description = "The type of the MessageListenerContainer")
    private String messageListenerContainerType = DIRECT_MESSAGE_LISTENER_CONTAINER;
    @UriParam(label = "consumer,advanced", description = "The number of consumers")
    private Integer concurrentConsumers;
    @UriParam(label = "consumer,advanced", description = "The maximum number of consumers (available only with SMLC)")
    private Integer maxConcurrentConsumers;
    @UriParam(label = "consumer,advanced", description = "Custom retry configuration to use. "
                                                         + "If this is configured then the other settings such as maximumRetryAttempts for retry are not in use.")
    private RetryOperationsInterceptor retry;
    @UriParam(label = "consumer", defaultValue = "5",
              description = "How many times a Rabbitmq consumer will retry the same message if Camel failed to process the message")
    private int maximumRetryAttempts = 5;
    @UriParam(label = "consumer", defaultValue = "1000",
              description = "Delay in millis a Rabbitmq consumer will wait before redelivering a message that Camel failed to process")
    private int retryDelay = 1000;
    @UriParam(label = "consumer", defaultValue = "true",
              description = "Whether a Rabbitmq consumer should reject the message without requeuing. This enables failed messages to be sent to a Dead Letter Exchange/Queue, if the broker is so configured.")
    private boolean rejectAndDontRequeue = true;

    public SpringRabbitMQEndpoint(String endpointUri, Component component, String exchangeName) {
        super(endpointUri, component);
        this.exchangeName = exchangeName;
    }

    @Override
    public SpringRabbitMQComponent getComponent() {
        return (SpringRabbitMQComponent) super.getComponent();
    }

    @Override
    protected void doInit() throws Exception {
        if (allowNullBody) {
            // need to wrap message converter in allow null
            messageConverter = new AllowNullBodyMessageConverter(messageConverter);
        }
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public String getQueues() {
        return queues;
    }

    public void setQueues(String queues) {
        this.queues = queues;
    }

    public boolean isAutoStartup() {
        return autoStartup;
    }

    public void setAutoStartup(boolean autoStartup) {
        this.autoStartup = autoStartup;
    }

    public boolean isAutoDeclare() {
        return autoDeclare;
    }

    public void setAutoDeclare(boolean autoDeclare) {
        this.autoDeclare = autoDeclare;
    }

    public boolean isAsyncConsumer() {
        return asyncConsumer;
    }

    public void setAsyncConsumer(boolean asyncConsumer) {
        this.asyncConsumer = asyncConsumer;
    }

    public String getRoutingKey() {
        return routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public MessageConverter getMessageConverter() {
        return messageConverter;
    }

    public void setMessageConverter(MessageConverter messageConverter) {
        this.messageConverter = messageConverter;
    }

    public MessagePropertiesConverter getMessagePropertiesConverter() {
        return messagePropertiesConverter;
    }

    public void setMessagePropertiesConverter(MessagePropertiesConverter messagePropertiesConverter) {
        this.messagePropertiesConverter = messagePropertiesConverter;
    }

    public String getExchangeType() {
        return exchangeType;
    }

    public void setExchangeType(String exchangeType) {
        this.exchangeType = exchangeType;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public AcknowledgeMode getAcknowledgeMode() {
        return acknowledgeMode;
    }

    public void setAcknowledgeMode(AcknowledgeMode acknowledgeMode) {
        this.acknowledgeMode = acknowledgeMode;
    }

    public boolean isExclusive() {
        return exclusive;
    }

    public void setExclusive(boolean exclusive) {
        this.exclusive = exclusive;
    }

    public boolean isNoLocal() {
        return noLocal;
    }

    public void setNoLocal(boolean noLocal) {
        this.noLocal = noLocal;
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

    public boolean isDisableReplyTo() {
        return disableReplyTo;
    }

    public void setDisableReplyTo(boolean disableReplyTo) {
        this.disableReplyTo = disableReplyTo;
    }

    public long getReplyTimeout() {
        return replyTimeout;
    }

    public void setReplyTimeout(long replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    public long getConfirmTimeout() {
        return confirmTimeout;
    }

    public void setConfirmTimeout(long confirmTimeout) {
        this.confirmTimeout = confirmTimeout;
    }

    public String getConfirm() {
        return confirm;
    }

    public void setConfirm(String confirm) {
        this.confirm = confirm;
    }

    public boolean isUsePublisherConnection() {
        return usePublisherConnection;
    }

    public void setUsePublisherConnection(boolean usePublisherConnection) {
        this.usePublisherConnection = usePublisherConnection;
    }

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    public Integer getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(Integer prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public String getMessageListenerContainerType() {
        return messageListenerContainerType;
    }

    public void setMessageListenerContainerType(String messageListenerContainerType) {
        this.messageListenerContainerType = messageListenerContainerType;
    }

    public Integer getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(Integer concurrentConsumers) {
        this.concurrentConsumers = concurrentConsumers;
    }

    public Integer getMaxConcurrentConsumers() {
        return maxConcurrentConsumers;
    }

    public void setMaxConcurrentConsumers(Integer maxConcurrentConsumers) {
        this.maxConcurrentConsumers = maxConcurrentConsumers;
    }

    public RetryOperationsInterceptor getRetry() {
        return retry;
    }

    public void setRetry(RetryOperationsInterceptor retry) {
        this.retry = retry;
    }

    public int getMaximumRetryAttempts() {
        return maximumRetryAttempts;
    }

    public void setMaximumRetryAttempts(int maximumRetryAttempts) {
        this.maximumRetryAttempts = maximumRetryAttempts;
    }

    public int getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(int retryDelay) {
        this.retryDelay = retryDelay;
    }

    public boolean isRejectAndDontRequeue() {
        return rejectAndDontRequeue;
    }

    public void setRejectAndDontRequeue(boolean rejectAndDontRequeue) {
        this.rejectAndDontRequeue = rejectAndDontRequeue;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        AbstractMessageListenerContainer listenerContainer = createMessageListenerContainer();
        SpringRabbitMQConsumer consumer = new SpringRabbitMQConsumer(this, processor, listenerContainer);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        SpringRabbitPollingConsumer answer = new SpringRabbitPollingConsumer(this, createInOnlyTemplate());
        configurePollingConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SpringRabbitMQProducer(this);
    }

    public Exchange createExchange(Message message) {
        Object body = getMessageConverter().fromMessage(message);

        Exchange exchange = super.createExchange();
        exchange.getMessage().setBody(body);

        Map<String, Object> headers
                = getMessagePropertiesConverter().fromMessageProperties(message.getMessageProperties(), exchange);
        if (!headers.isEmpty()) {
            exchange.getMessage().setHeaders(headers);
        }

        return exchange;
    }

    public Map<String, Object> getConsumerArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, CONSUMER_ARG_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    public Map<String, Object> getExchangeArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, EXCHANGE_ARG_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    public Map<String, Object> getQueueArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, QUEUE_ARG_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    public Map<String, Object> getBindingArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, BINDING_ARG_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    public Map<String, Object> getDlqExchangeArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, DLQ_EXCHANGE_ARG_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    public Map<String, Object> getDlqQueueArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, DLQ_QUEUE_ARG_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    public Map<String, Object> getDlqBindingArgs() {
        Map<String, Object> answer = PropertiesHelper.extractProperties(args, DLQ_BINDING_PREFIX, false);
        prepareArgs(answer);
        return answer;
    }

    /**
     * Factory method for creating a new template for InOnly message exchanges
     */
    public RabbitTemplate createInOnlyTemplate() {
        RabbitTemplate template = new RabbitTemplate(getConnectionFactory());
        template.setRoutingKey(getRoutingKey());
        template.setUsePublisherConnection(usePublisherConnection);
        return template;
    }

    /**
     * Factory method for creating a new template for InOut message exchanges
     */
    public AsyncRabbitTemplate createInOutTemplate() {
        RabbitTemplate template = new RabbitTemplate(getConnectionFactory());
        template.setRoutingKey(routingKey);
        template.setUsePublisherConnection(usePublisherConnection);
        AsyncRabbitTemplate asyncTemplate = new AsyncRabbitTemplate(template);
        // use receive timeout (for reply timeout) on the async template
        asyncTemplate.setReceiveTimeout(replyTimeout);
        return asyncTemplate;
    }

    public AbstractMessageListenerContainer createMessageListenerContainer() {
        return getComponent().getListenerContainerFactory().createListenerContainer(this);
    }

    public void configureMessageListener(EndpointMessageListener listener) {
        listener.setAsync(isAsyncConsumer());
        listener.setDisableReplyTo(isDisableReplyTo());
    }

    protected boolean parseArgsBoolean(Map<String, Object> args, String key, String defaultValue) {
        Object answer = args.remove(key);
        if (answer == null) {
            answer = defaultValue;
        }
        if (answer != null) {
            return getCamelContext().getTypeConverter().convertTo(boolean.class, answer);
        } else {
            return false;
        }
    }

    protected String parseArgsString(Map<String, Object> args, String key, String defaultValue) {
        Object answer = args.remove(key);
        if (answer == null) {
            answer = defaultValue;
        }
        if (answer != null) {
            return getCamelContext().getTypeConverter().convertTo(String.class, answer);
        } else {
            return null;
        }
    }

    public void declareElements(AbstractMessageListenerContainer container) {
        AmqpAdmin admin = null;
        if (container instanceof MessageListenerContainer) {
            admin = ((MessageListenerContainer) container).getAmqpAdmin();
        }
        if (admin != null && autoDeclare) {
            // bind dead letter exchange
            if (deadLetterExchange != null) {
                ExchangeBuilder eb = new ExchangeBuilder(deadLetterExchange, deadLetterExchangeType);
                eb.withArguments(getDlqExchangeArgs());
                final org.springframework.amqp.core.Exchange rabbitExchange = eb.build();
                admin.declareExchange(rabbitExchange);
                if (deadLetterQueue != null) {
                    QueueBuilder qb = QueueBuilder.durable(deadLetterQueue);
                    Map<String, Object> args = getDlqQueueArgs();
                    qb.withArguments(args);
                    final Queue rabbitQueue = qb.build();
                    admin.declareQueue(rabbitQueue);

                    Binding binding = new Binding(
                            rabbitQueue.getName(), Binding.DestinationType.QUEUE, rabbitExchange.getName(),
                            deadLetterRoutingKey,
                            getDlqBindingArgs());
                    admin.declareBinding(binding);

                    LOG.info("Auto-declaring durable DeadLetterExchange: {} routingKey: {}", deadLetterExchange,
                            deadLetterRoutingKey);
                }
            }

            Map<String, Object> map = getExchangeArgs();
            boolean durable = parseArgsBoolean(map, "durable", "true");
            boolean autoDelete = parseArgsBoolean(map, "autoDelete", "false");
            if (!durable || autoDelete) {
                LOG.info("Auto-declaring a non-durable or auto-delete Exchange ({}) durable:{}, auto-delete:{}. "
                         + "It will be deleted by the broker if it shuts down, and can be redeclared by closing and "
                         + "reopening the connection.",
                        exchangeName, durable, autoDelete);
            }

            String en = SpringRabbitMQHelper.isDefaultExchange(getExchangeName()) ? "" : getExchangeName();
            ExchangeBuilder eb = new ExchangeBuilder(en, getExchangeType());
            eb.durable(durable);
            if (autoDelete) {
                eb.autoDelete();
            }
            eb.withArguments(map);
            final org.springframework.amqp.core.Exchange rabbitExchange = eb.build();
            admin.declareExchange(rabbitExchange);

            // if the consumer has no specific queue names then auto-create an unique queue (auto deleted)
            String queuesToDeclare = queues;
            String autoDeleteDefault = "false";
            boolean generateUniqueQueue = false;
            if (queuesToDeclare == null) {
                // no explicit queue names so use a single blank so we can create a single new unique queue for the consumer
                queuesToDeclare = " ";
                generateUniqueQueue = true;
            }

            for (String queue : queuesToDeclare.split(",")) {
                queue = queue.trim();
                map = getQueueArgs();
                prepareDeadLetterQueueArgs(map);
                durable = parseArgsBoolean(map, "durable", "false");
                autoDelete = parseArgsBoolean(map, "autoDelete", autoDeleteDefault);
                boolean exclusive = parseArgsBoolean(map, "exclusive", "false");

                QueueBuilder qb;
                if (queue.isEmpty()) {
                    qb = durable ? QueueBuilder.durable() : QueueBuilder.nonDurable();
                } else {
                    qb = durable ? QueueBuilder.durable(queue) : QueueBuilder.nonDurable(queue);
                }
                if (autoDelete) {
                    qb.autoDelete();
                }
                if (exclusive) {
                    qb.exclusive();
                }
                // setup DLQ
                String dle = parseArgsString(args, "x-dead-letter-exchange", deadLetterExchange);
                if (dle != null) {
                    qb.deadLetterExchange(dle);
                }
                String dlrk = parseArgsString(args, "x-dead-letter-routing-key", deadLetterRoutingKey);
                if (dlrk != null) {
                    qb.deadLetterRoutingKey(dlrk);
                }
                qb.withArguments(map);
                final Queue rabbitQueue = qb.build();

                if (!durable || autoDelete || exclusive) {
                    LOG.info("Auto-declaring a non-durable, auto-delete, or exclusive Queue ({})"
                             + "durable:{}, auto-delete:{}, exclusive:{}. It will be redeclared if the broker stops and "
                             + "is restarted while the connection factory is alive, but all messages will be lost.",
                            rabbitQueue.getName(), durable, autoDelete, exclusive);
                }

                String qn = admin.declareQueue(rabbitQueue);

                // if we auto created a new unique queue then the container needs to know the queue name
                if (generateUniqueQueue) {
                    container.setQueueNames(qn);
                }

                // bind queue to exchange
                Binding binding = new Binding(
                        qn, Binding.DestinationType.QUEUE, rabbitExchange.getName(), routingKey,
                        getBindingArgs());
                admin.declareBinding(binding);
            }
        }
    }

    private void prepareDeadLetterQueueArgs(Map<String, Object> args) {
        if (deadLetterExchange != null) {
            args.put(SpringRabbitMQConstants.DEAD_LETTER_EXCHANGE, deadLetterExchange);
            if (deadLetterRoutingKey != null) {
                args.put(SpringRabbitMQConstants.DEAD_LETTER_ROUTING_KEY, deadLetterRoutingKey);
            }
        }
    }

    private void prepareArgs(Map<String, Object> args) {
        // some arguments must be in numeric values so we need to fix this
        Object arg = args.get(SpringRabbitMQConstants.MAX_LENGTH);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.MAX_LENGTH, Long.parseLong((String) arg));
        }
        arg = args.get(SpringRabbitMQConstants.MAX_LENGTH_BYTES);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.MAX_LENGTH_BYTES, Long.parseLong((String) arg));
        }
        arg = args.get(SpringRabbitMQConstants.MAX_PRIORITY);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.MAX_PRIORITY, Integer.parseInt((String) arg));
        }
        arg = args.get(SpringRabbitMQConstants.DELIVERY_LIMIT);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.DELIVERY_LIMIT, Integer.parseInt((String) arg));
        }
        arg = args.get(SpringRabbitMQConstants.MESSAGE_TTL);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.MESSAGE_TTL, Long.parseLong((String) arg));
        }
        arg = args.get(SpringRabbitMQConstants.EXPIRES);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.EXPIRES, Long.parseLong((String) arg));
        }
        arg = args.get(SpringRabbitMQConstants.SINGLE_ACTIVE_CONSUMER);
        if (arg instanceof String) {
            args.put(SpringRabbitMQConstants.SINGLE_ACTIVE_CONSUMER, Boolean.parseBoolean((String) arg));
        }
    }

}
