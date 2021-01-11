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
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;

/**
 * Send and receive messages from RabbitMQ using Spring RabbitMQ client.
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "spring-rabbitmq", title = "Spring RabbitMQ",
             syntax = "spring-rabbitmq:exchangeName",
             category = { Category.MESSAGING })
public class RabbitMQEndpoint extends DefaultEndpoint implements AsyncEndpoint {

    public static final String ARG_PREFIX = "arg.";
    public static final String EXCHANGE_ARG_PREFIX = "exchange.";
    public static final String QUEUE_ARG_PREFIX = "queue.";
    public static final String BINDING_ARG_PREFIX = "binding.";
    public static final String DLQ_EXCHANGE_ARG_PREFIX = "dlq.exchange.";
    public static final String DLQ_QUEUE_ARG_PREFIX = "dlq.queue.";
    public static final String DLQ_BINDING_PREFIX = "dlq.binding.";

    private static final Logger LOG = LoggerFactory.getLogger(RabbitMQEndpoint.class);

    @UriPath
    @Metadata(required = true,
              description = "The exchange name determines the exchange to which the produced messages will be sent to."
                            + " In the case of consumers, the exchange name determines the exchange the queue will be bound to."
                            + " Note: to use default exchange then do not use empty name, but use default instead.")
    private String exchangeName;
    @UriParam(description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @UriParam(label = "consumer", defaultValue = "direct", enums = "direct,fanout,headers,topic",
              description = "The type of the exchange")
    private String exchangeType = "direct";
    @UriParam
    @Metadata(label = "consumer",
              description = "The queue(s) to use for consuming messages. Multiple queue names can be separated by comma.")
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
    @UriParam(label = "common", description = "Routing key.")
    private String routingKey;
    // Transaction related configuration
    @UriParam(label = "transaction",
              description = "Specifies whether to use transacted mode")
    private boolean transacted;
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
                            + " arg.exchange. arg.queue. arg.binding. arg.dlq.exchange. arg.dlq.queue. arg.dlq.binding."
                            + " For example to declare a queue with message ttl argument: args=arg.queue.x-message-ttl=60000")
    private Map<String, Object> args;
    @UriParam(label = "consumer", description = "The name of the dead letter exchange")
    private String deadLetterExchange;
    @UriParam(label = "consumer", description = "The name of the dead letter queue")
    private String deadLetterQueue;
    @UriParam(label = "consumer", description = "The routing key for the dead letter exchange")
    private String deadLetterRoutingKey;
    @UriParam(label = "consumer", defaultValue = "direct", enums = "direct,fanout,headers,topic",
              description = "The type of the dead letter exchange")
    private String deadLetterExchangeType = "direct";

    public RabbitMQEndpoint(String endpointUri, Component component, String exchangeName) {
        super(endpointUri, component);
        this.exchangeName = exchangeName;
    }

    @Override
    public RabbitMQComponent getComponent() {
        return (RabbitMQComponent) super.getComponent();
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

    public boolean isTransacted() {
        return transacted;
    }

    public void setTransacted(boolean transacted) {
        this.transacted = transacted;
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

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        DefaultMessageListenerContainer listenerContainer = createMessageListenerContainer();
        RabbitMQConsumer consumer = new RabbitMQConsumer(this, processor, listenerContainer);
        configureConsumer(consumer);
        return consumer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new RabbitMQProducer(this);
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

    public Map<String, Object> getExchangeArgs() {
        return PropertiesHelper.extractProperties(args, EXCHANGE_ARG_PREFIX, false);
    }

    public Map<String, Object> getQueueArgs() {
        return PropertiesHelper.extractProperties(args, QUEUE_ARG_PREFIX, false);
    }

    public Map<String, Object> getBindingArgs() {
        return PropertiesHelper.extractProperties(args, BINDING_ARG_PREFIX, false);
    }

    public Map<String, Object> getDlqExchangeArgs() {
        return PropertiesHelper.extractProperties(args, DLQ_EXCHANGE_ARG_PREFIX, false);
    }

    public Map<String, Object> getDlqQueueArgs() {
        return PropertiesHelper.extractProperties(args, DLQ_QUEUE_ARG_PREFIX, false);
    }

    public Map<String, Object> getDlqBindingArgs() {
        return PropertiesHelper.extractProperties(args, DLQ_BINDING_PREFIX, false);
    }

    /**
     * Factory method for creating a new template for InOnly message exchanges
     */
    public RabbitTemplate createInOnlyTemplate() {
        RabbitTemplate template = new RabbitTemplate(getConnectionFactory());
        template.setRoutingKey(getRoutingKey());
        return template;
    }

    /**
     * Factory method for creating a new template for InOut message exchanges
     */
    public RabbitTemplate createInOutTemplate() {
        RabbitTemplate template = new RabbitTemplate(getConnectionFactory());
        template.setRoutingKey(getRoutingKey());
        return template;
    }

    public DefaultMessageListenerContainer createMessageListenerContainer() throws Exception {
        DefaultMessageListenerContainer listener = new DefaultMessageListenerContainer(getConnectionFactory());
        if (getQueues() != null) {
            listener.setQueueNames(getQueues().split(","));
        }

        AmqpAdmin admin = getComponent().getAmqpAdmin();
        if (autoDeclare && admin == null) {
            admin = new RabbitAdmin(getConnectionFactory());
        }
        listener.setAutoDeclare(autoDeclare);
        listener.setAmqpAdmin(admin);
        return listener;
    }

    public void configureMessageListener(EndpointMessageListener listener) {
        // TODO: any endpoint options to configure
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

    public void declareElements(DefaultMessageListenerContainer container) {
        AmqpAdmin admin = container.getAmqpAdmin();
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
                    formatSpecialQueueArguments(args);
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

            Map<String, Object> args = getExchangeArgs();
            boolean durable = parseArgsBoolean(args, "durable", "true");
            boolean autoDelete = parseArgsBoolean(args, "autoDelete", "false");
            if ((!durable || autoDelete)) {
                LOG.info("Auto-declaring a non-durable or auto-delete Exchange ("
                         + exchangeName
                         + ") durable:" + durable + ", auto-delete:" + autoDelete + ". "
                         + "It will be deleted by the broker if it shuts down, and can be redeclared by closing and "
                         + "reopening the connection.");
            }

            String en = RabbitMQHelper.isDefaultExchange(getExchangeName()) ? "" : getExchangeName();
            ExchangeBuilder eb = new ExchangeBuilder(en, getExchangeType());
            eb.durable(durable);
            if (autoDelete) {
                eb.autoDelete();
            }
            eb.withArguments(args);
            final org.springframework.amqp.core.Exchange rabbitExchange = eb.build();
            admin.declareExchange(rabbitExchange);

            if (queues != null) {
                for (String queue : queues.split(",")) {
                    args = getQueueArgs();
                    populateQueueArgumentsFromDeadLetterExchange(args);
                    formatSpecialQueueArguments(args);
                    durable = parseArgsBoolean(args, "durable", "false");
                    autoDelete = parseArgsBoolean(args, "autoDelete", "false");
                    boolean exclusive = parseArgsBoolean(args, "exclusive", "false");

                    if ((!durable || autoDelete || exclusive)) {
                        LOG.info("Auto-declaring a non-durable, auto-delete, or exclusive Queue ("
                                 + queue
                                 + ") durable:" + durable + ", auto-delete:" + autoDelete + ", exclusive:"
                                 + exclusive + ". "
                                 + "It will be redeclared if the broker stops and is restarted while the connection factory is "
                                 + "alive, but all messages will be lost.");
                    }

                    QueueBuilder qb = durable ? QueueBuilder.durable(queue) : QueueBuilder.nonDurable(queue);
                    if (autoDelete) {
                        qb.autoDelete();
                    }
                    if (exclusive) {
                        qb.exclusive();
                    }
                    qb.withArguments(args);
                    final Queue rabbitQueue = qb.build();
                    admin.declareQueue(rabbitQueue);

                    // bind queue to exchange
                    Binding binding = new Binding(
                            rabbitQueue.getName(), Binding.DestinationType.QUEUE, rabbitExchange.getName(), routingKey,
                            getBindingArgs());
                    admin.declareBinding(binding);
                }
            }
        }
    }

    private void populateQueueArgumentsFromDeadLetterExchange(Map<String, Object> queueArgs) {
        if (deadLetterExchange != null) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_EXCHANGE, deadLetterExchange);

            if (deadLetterRoutingKey != null) {
                queueArgs.put(RabbitMQConstants.RABBITMQ_DEAD_LETTER_ROUTING_KEY, deadLetterRoutingKey);
            }
        }
    }

    private void formatSpecialQueueArguments(Map<String, Object> queueArgs) {
        // some arguments must be in numeric values so we need to fix this
        Object queueLengthLimit = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_LENGTH_LIMIT_KEY);
        if (queueLengthLimit instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_LENGTH_LIMIT_KEY, Long.parseLong((String) queueLengthLimit));
        }

        Object queueMaxPriority = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_MAX_PRIORITY_KEY);
        if (queueMaxPriority instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_MAX_PRIORITY_KEY, Integer.parseInt((String) queueMaxPriority));
        }

        Object queueMessageTtl = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_MESSAGE_TTL_KEY);
        if (queueMessageTtl instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_MESSAGE_TTL_KEY, Long.parseLong((String) queueMessageTtl));
        }

        Object queueExpiration = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_TTL_KEY);
        if (queueExpiration instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_TTL_KEY, Long.parseLong((String) queueExpiration));
        }

        Object singleConsumer = queueArgs.get(RabbitMQConstants.RABBITMQ_QUEUE_SINGLE_ACTIVE_CONSUMER_KEY);
        if (singleConsumer instanceof String) {
            queueArgs.put(RabbitMQConstants.RABBITMQ_QUEUE_SINGLE_ACTIVE_CONSUMER_KEY,
                    Boolean.parseBoolean((String) singleConsumer));
        }
    }

}
