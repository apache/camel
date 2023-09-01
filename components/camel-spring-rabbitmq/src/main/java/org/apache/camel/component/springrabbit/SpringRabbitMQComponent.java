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

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;
import org.apache.camel.util.PropertiesHelper;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.retry.interceptor.RetryOperationsInterceptor;
import org.springframework.util.ErrorHandler;

import static org.apache.camel.component.springrabbit.SpringRabbitMQConstants.DIRECT_MESSAGE_LISTENER_CONTAINER;
import static org.apache.camel.component.springrabbit.SpringRabbitMQEndpoint.ARG_PREFIX;
import static org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer.DEFAULT_PREFETCH_COUNT;
import static org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer.DEFAULT_SHUTDOWN_TIMEOUT;

@Component("spring-rabbitmq")
public class SpringRabbitMQComponent extends HeaderFilterStrategyComponent {

    @Metadata(autowired = true,
              description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @Metadata(autowired = true,
              description = "Optional AMQP Admin service to use for auto declaring elements (queues, exchanges, bindings)")
    private AmqpAdmin amqpAdmin;
    @Metadata(description = "Specifies whether to test the connection on startup."
                            + " This ensures that when Camel starts that all the JMS consumers have a valid connection to the JMS broker."
                            + " If a connection cannot be granted then Camel throws an exception on startup."
                            + " This ensures that Camel is not started with failed connections."
                            + " The JMS producers is tested as well.")
    private boolean testConnectionOnStartup;
    @Metadata(label = "consumer", defaultValue = "true",
              description = "Specifies whether the consumer container should auto-startup.")
    private boolean autoStartup = true;
    @Metadata(label = "consumer", defaultValue = "false",
              description = "Specifies whether the consumer should auto declare binding between exchange, queue and routing key when starting."
                            + " Enabling this can be good for development to make it easy to standup exchanges, queues and bindings on the broker.")
    private boolean autoDeclare;
    @Metadata(label = "advanced",
              description = "To use a custom MessageConverter so you can be in control how to map to/from a org.springframework.amqp.core.Message.")
    private MessageConverter messageConverter;
    @Metadata(label = "advanced",
              description = "To use a custom MessagePropertiesConverter so you can be in control how to map to/from a org.springframework.amqp.core.MessageProperties.")
    private MessagePropertiesConverter messagePropertiesConverter;
    @Metadata(label = "producer", javaType = "java.time.Duration", defaultValue = "5000",
              description = "Specify the timeout in milliseconds to be used when waiting for a reply message when doing request/reply messaging."
                            + " The default value is 5 seconds. A negative value indicates an indefinite timeout.")
    private long replyTimeout = 5000;
    @Metadata(label = "consumer", description = "The name of the dead letter exchange")
    private String deadLetterExchange;
    @Metadata(label = "consumer", description = "The name of the dead letter queue")
    private String deadLetterQueue;
    @Metadata(label = "consumer", description = "The routing key for the dead letter exchange")
    private String deadLetterRoutingKey;
    @Metadata(label = "consumer", defaultValue = "direct", enums = "direct,fanout,headers,topic",
              description = "The type of the dead letter exchange")
    private String deadLetterExchangeType = "direct";
    @Metadata(label = "consumer,advanced",
              description = "To use a custom ErrorHandler for handling exceptions from the message listener (consumer)")
    private ErrorHandler errorHandler;
    @Metadata(label = "consumer,advanced", defaultValue = "" + DEFAULT_PREFETCH_COUNT,
              description = "Tell the broker how many messages to send to each consumer in a single request. Often this can be set quite high to improve throughput.")
    private int prefetchCount = DEFAULT_PREFETCH_COUNT;
    @Metadata(label = "consumer,advanced", javaType = "java.time.Duration", defaultValue = "" + DEFAULT_SHUTDOWN_TIMEOUT,
              description = "The time to wait for workers in milliseconds after the container is stopped. If any workers are active when the shutdown signal comes"
                            + " they will be allowed to finish processing as long as they can finish within this timeout.")
    private long shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;
    @Metadata(label = "consumer,advanced",
              description = "To use a custom factory for creating and configuring ListenerContainer to be used by the consumer for receiving messages")
    private ListenerContainerFactory listenerContainerFactory = new DefaultListenerContainerFactory();
    @Metadata(label = "advanced", description = "Switch on ignore exceptions such as mismatched properties when declaring")
    private boolean ignoreDeclarationExceptions;
    @Metadata(label = "consumer,advanced", defaultValue = DIRECT_MESSAGE_LISTENER_CONTAINER, enums = "DMLC,SMLC",
              description = "The type of the MessageListenerContainer")
    private String messageListenerContainerType = DIRECT_MESSAGE_LISTENER_CONTAINER;
    @Metadata(label = "consumer,advanced", defaultValue = "1", description = "The number of consumers")
    private int concurrentConsumers = 1;
    @Metadata(label = "consumer,advanced", description = "The maximum number of consumers (available only with SMLC)")
    private Integer maxConcurrentConsumers;
    @Metadata(label = "consumer,advanced", description = "Custom retry configuration to use. "
                                                         + "If this is configured then the other settings such as maximumRetryAttempts for retry are not in use.")
    private RetryOperationsInterceptor retry;
    @Metadata(label = "consumer", defaultValue = "5",
              description = "How many times a Rabbitmq consumer will retry the same message if Camel failed to process the message")
    private int maximumRetryAttempts = 5;
    @Metadata(label = "consumer", defaultValue = "1000",
              description = "Delay in msec a Rabbitmq consumer will wait before redelivering a message that Camel failed to process")
    private int retryDelay = 1000;
    @Metadata(label = "consumer", defaultValue = "true",
              description = "Whether a Rabbitmq consumer should reject the message without requeuing. This enables failed messages to be sent to a Dead Letter Exchange/Queue, if the broker is so configured.")
    private boolean rejectAndDontRequeue = true;
    @Metadata(label = "producer", defaultValue = "false",
              description = "Whether to allow sending messages with no body. If this option is false and the message body is null, then an MessageConversionException is thrown.")
    private boolean allowNullBody;

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        if (getHeaderFilterStrategy() == null) {
            setHeaderFilterStrategy(new SpringRabbitMQHeaderFilterStrategy());
        }
        if (messageConverter == null) {
            messageConverter = new DefaultMessageConverter(getCamelContext());
        }
        if (messagePropertiesConverter == null) {
            messagePropertiesConverter = new DefaultMessagePropertiesConverter(getCamelContext(), getHeaderFilterStrategy());
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringRabbitMQEndpoint endpoint = new SpringRabbitMQEndpoint(uri, this, remaining);
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setTestConnectionOnStartup(testConnectionOnStartup);
        endpoint.setMessageConverter(messageConverter);
        endpoint.setMessagePropertiesConverter(messagePropertiesConverter);
        endpoint.setAutoStartup(autoStartup);
        endpoint.setAutoDeclare(autoDeclare);
        endpoint.setDeadLetterExchange(deadLetterExchange);
        endpoint.setDeadLetterExchangeType(deadLetterExchangeType);
        endpoint.setDeadLetterQueue(deadLetterQueue);
        endpoint.setDeadLetterRoutingKey(deadLetterRoutingKey);
        endpoint.setReplyTimeout(replyTimeout);
        endpoint.setPrefetchCount(prefetchCount);
        endpoint.setMessageListenerContainerType(messageListenerContainerType);
        endpoint.setConcurrentConsumers(concurrentConsumers);
        endpoint.setMaxConcurrentConsumers(maxConcurrentConsumers);
        endpoint.setRetry(retry);
        endpoint.setMaximumRetryAttempts(maximumRetryAttempts);
        endpoint.setRetryDelay(retryDelay);
        endpoint.setRejectAndDontRequeue(rejectAndDontRequeue);
        endpoint.setAllowNullBody(allowNullBody);

        endpoint.setArgs(PropertiesHelper.extractProperties(parameters, ARG_PREFIX));
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public AmqpAdmin getAmqpAdmin() {
        return amqpAdmin;
    }

    public void setAmqpAdmin(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
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

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    public int getPrefetchCount() {
        return prefetchCount;
    }

    public void setPrefetchCount(int prefetchCount) {
        this.prefetchCount = prefetchCount;
    }

    public long getReplyTimeout() {
        return replyTimeout;
    }

    public void setReplyTimeout(long replyTimeout) {
        this.replyTimeout = replyTimeout;
    }

    public long getShutdownTimeout() {
        return shutdownTimeout;
    }

    public void setShutdownTimeout(long shutdownTimeout) {
        this.shutdownTimeout = shutdownTimeout;
    }

    public ListenerContainerFactory getListenerContainerFactory() {
        return listenerContainerFactory;
    }

    public void setListenerContainerFactory(ListenerContainerFactory listenerContainerFactory) {
        this.listenerContainerFactory = listenerContainerFactory;
    }

    public boolean isIgnoreDeclarationExceptions() {
        return ignoreDeclarationExceptions;
    }

    public void setIgnoreDeclarationExceptions(boolean ignoreDeclarationExceptions) {
        this.ignoreDeclarationExceptions = ignoreDeclarationExceptions;
    }

    public String getMessageListenerContainerType() {
        return messageListenerContainerType;
    }

    public void setMessageListenerContainerType(String messageListenerContainerType) {
        this.messageListenerContainerType = messageListenerContainerType;
    }

    public int getConcurrentConsumers() {
        return concurrentConsumers;
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
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

    public boolean isAllowNullBody() {
        return allowNullBody;
    }

    public void setAllowNullBody(boolean allowNullBody) {
        this.allowNullBody = allowNullBody;
    }
}
