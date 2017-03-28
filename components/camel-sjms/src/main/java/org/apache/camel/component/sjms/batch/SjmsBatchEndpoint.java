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
package org.apache.camel.component.sjms.batch;

import java.util.concurrent.ScheduledExecutorService;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sjms.SjmsHeaderFilterStrategy;
import org.apache.camel.component.sjms.SjmsMessage;
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.DestinationNameParser;
import org.apache.camel.component.sjms.jms.JmsBinding;
import org.apache.camel.component.sjms.jms.JmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.MessageCreatedStrategy;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.language.simple.SimpleLanguage;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The sjms-batch component is a specialized for highly performant, transactional batch consumption from a JMS queue.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "sjms-batch", title = "Simple JMS Batch", syntax = "sjms-batch:destinationName",
        consumerClass = SjmsBatchComponent.class, label = "messaging", consumerOnly = true)
public class SjmsBatchEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    public static final int DEFAULT_COMPLETION_SIZE = 200; // the default dispatch queue size in ActiveMQ
    public static final int DEFAULT_COMPLETION_TIMEOUT = 500;

    private JmsBinding binding;

    @UriPath @Metadata(required = "true")
    private String destinationName;
    @UriParam @Metadata(required = "true")
    private AggregationStrategy aggregationStrategy;
    @UriParam(defaultValue = "1")
    private int consumerCount = 1;
    @UriParam(defaultValue = "200")
    private int completionSize = DEFAULT_COMPLETION_SIZE;
    @UriParam(defaultValue = "500")
    private int completionTimeout = DEFAULT_COMPLETION_TIMEOUT;
    @UriParam(defaultValue = "1000")
    private int completionInterval;
    @UriParam(javaType = "java.lang.String")
    private Predicate completionPredicate;
    @UriParam
    private boolean eagerCheckCompletion;
    @UriParam
    private boolean sendEmptyMessageWhenIdle;
    @UriParam(defaultValue = "1000")
    private int pollDuration = 1000;
    @UriParam
    private boolean includeAllJMSXProperties;
    @UriParam(defaultValue = "true")
    private boolean allowNullBody = true;
    @UriParam(defaultValue = "true")
    private boolean mapJmsMessage = true;
    @UriParam(label = "advanced")
    private HeaderFilterStrategy headerFilterStrategy;
    @UriParam(label = "advanced")
    private MessageCreatedStrategy messageCreatedStrategy;
    @UriParam(label = "advanced")
    private JmsKeyFormatStrategy jmsKeyFormatStrategy;
    @UriParam(label = "advanced")
    private ScheduledExecutorService timeoutCheckerExecutorService;
    @UriParam(label = "advanced")
    private boolean asyncStartListener;
    @UriParam(label = "advanced", defaultValue = "5000")
    private int recoveryInterval = 5000;
    @UriParam(label = "advanced", defaultValue = "-1")
    private int keepAliveDelay = -1;

    public SjmsBatchEndpoint() {
    }

    public SjmsBatchEndpoint(String endpointUri, Component component, String remaining) {
        super(endpointUri, component);

        DestinationNameParser parser = new DestinationNameParser();
        if (parser.isTopic(remaining)) {
            throw new IllegalArgumentException("Only batch consumption from queues is supported. For topics you "
                    + "should use a regular JMS consumer with an aggregator.");
        }
        this.destinationName = parser.getShortName(remaining);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public SjmsBatchComponent getComponent() {
        return (SjmsBatchComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Producer not supported");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        SjmsBatchConsumer consumer = new SjmsBatchConsumer(this, processor);
        consumer.setTimeoutCheckerExecutorService(timeoutCheckerExecutorService);
        configureConsumer(consumer);
        return consumer;
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

    public AggregationStrategy getAggregationStrategy() {
        return aggregationStrategy;
    }

    /**
     * The aggregation strategy to use, which merges all the batched messages into a single message
     */
    public void setAggregationStrategy(AggregationStrategy aggregationStrategy) {
        this.aggregationStrategy = aggregationStrategy;
    }

    /**
     * The destination name. Only queues are supported, names may be prefixed by 'queue:'.
     */
    public String getDestinationName() {
        return destinationName;
    }

    public int getConsumerCount() {
        return consumerCount;
    }

    /**
     * The number of JMS sessions to consume from
     */
    public void setConsumerCount(int consumerCount) {
        this.consumerCount = consumerCount;
    }

    public int getCompletionSize() {
        return completionSize;
    }

    /**
     * The number of messages consumed at which the batch will be completed
     */
    public void setCompletionSize(int completionSize) {
        this.completionSize = completionSize;
    }

    public int getCompletionTimeout() {
        return completionTimeout;
    }

    /**
     * The timeout in millis from receipt of the first first message when the batch will be completed.
     * The batch may be empty if the timeout triggered and there was no messages in the batch.
     * <br/>
     * Notice you cannot use both completion timeout and completion interval at the same time, only one can be configured.
     */
    public void setCompletionTimeout(int completionTimeout) {
        this.completionTimeout = completionTimeout;
    }

    public int getCompletionInterval() {
        return completionInterval;
    }

    /**
     * The completion interval in millis, which causes batches to be completed in a scheduled fixed rate every interval.
     * The batch may be empty if the timeout triggered and there was no messages in the batch.
     * <br/>
     * Notice you cannot use both completion timeout and completion interval at the same time, only one can be configured.
     */
    public void setCompletionInterval(int completionInterval) {
        this.completionInterval = completionInterval;
    }

    public Predicate getCompletionPredicate() {
        return completionPredicate;
    }

    /**
     * The completion predicate, which causes batches to be completed when the predicate evaluates as true.
     * <p/>
     * The predicate can also be configured using the simple language using the string syntax.
     * You may want to set the option eagerCheckCompletion to true to let the predicate match the incoming message,
     * as otherwise it matches the aggregated message.
     */
    public void setCompletionPredicate(Predicate completionPredicate) {
        this.completionPredicate = completionPredicate;
    }

    public void setCompletionPredicate(String predicate) {
        // uses simple language
        this.completionPredicate = SimpleLanguage.predicate(predicate);
    }

    public boolean isEagerCheckCompletion() {
        return eagerCheckCompletion;
    }

    /**
     * Use eager completion checking which means that the completionPredicate will use the incoming Exchange.
     * As opposed to without eager completion checking the completionPredicate will use the aggregated Exchange.
     */
    public void setEagerCheckCompletion(boolean eagerCheckCompletion) {
        this.eagerCheckCompletion = eagerCheckCompletion;
    }

    public boolean isSendEmptyMessageWhenIdle() {
        return sendEmptyMessageWhenIdle;
    }

    /**
     * If using completion timeout or interval, then the batch may be empty if the timeout triggered and there was no messages in the batch.
     * If this option is <tt>true</tt> and the batch is empty then an empty message is added to the batch so an empty message is routed.
     */
    public void setSendEmptyMessageWhenIdle(boolean sendEmptyMessageWhenIdle) {
        this.sendEmptyMessageWhenIdle = sendEmptyMessageWhenIdle;
    }

    public int getPollDuration() {
        return pollDuration;
    }

    /**
     * The duration in milliseconds of each poll for messages.
     * completionTimeOut will be used if it is shorter and a batch has started.
     */
    public void setPollDuration(int pollDuration) {
        this.pollDuration = pollDuration;
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

    public ScheduledExecutorService getTimeoutCheckerExecutorService() {
        return timeoutCheckerExecutorService;
    }

    /**
     * If using the completionInterval option a background thread is created to trigger the completion interval.
     * Set this option to provide a custom thread pool to be used rather than creating a new thread for every consumer.
     */
    public void setTimeoutCheckerExecutorService(ScheduledExecutorService timeoutCheckerExecutorService) {
        this.timeoutCheckerExecutorService = timeoutCheckerExecutorService;
    }

    public boolean isAsyncStartListener() {
        return asyncStartListener;
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

    public int getRecoveryInterval() {
        return recoveryInterval;
    }

    /**
     * Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds.
     * The default is 5000 ms, that is, 5 seconds.
     */
    public void setRecoveryInterval(int recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    /**
     * The delay in millis between attempts to re-establish a valid session.
     * If this is a positive value the SjmsBatchConsumer will attempt to create a new session if it sees an IllegalStateException
     * during message consumption. This delay value allows you to pause between attempts to prevent spamming the logs.
     * If this is a negative value (default is -1) then the SjmsBatchConsumer will behave as it always has before - that is
     * it will bail out and the route will shut down if it sees an IllegalStateException.
     */
    public void setKeepAliveDelay(int keepAliveDelay) {
        this.keepAliveDelay = keepAliveDelay;
    }

    public int getKeepAliveDelay() {
        return keepAliveDelay;
    }

}
