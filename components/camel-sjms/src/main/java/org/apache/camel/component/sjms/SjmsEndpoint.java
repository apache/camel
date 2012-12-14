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

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.KeyFormatStrategy;
import org.apache.camel.component.sjms.jms.SessionAcknowledgementType;
import org.apache.camel.component.sjms.jms.SessionPool;
import org.apache.camel.component.sjms.producer.InOnlyProducer;
import org.apache.camel.component.sjms.producer.InOutProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A JMS Endpoint
 */
public class SjmsEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    protected final transient Logger logger = LoggerFactory.getLogger(getClass());

    private SessionPool sessions;
    private boolean synchronous = true;
    private boolean transacted;
    private String namedReplyTo;
    private SessionAcknowledgementType acknowledgementMode = SessionAcknowledgementType.AUTO_ACKNOWLEDGE;
    private boolean topic;
    private int sessionCount = 1;
    private int producerCount = 1;
    private int consumerCount = 1;
    private long ttl = -1;
    private boolean persistent = true;
    private String durableSubscriptionId;
    private long responseTimeOut = 5000;
    private String messageSelector;
    private int transactionBatchCount = -1;
    private long transactionBatchTimeout = 5000;
    private TransactionCommitStrategy transactionCommitStrategy;

    public SjmsEndpoint() {
    }

    public SjmsEndpoint(String uri, Component component) {
        super(uri, component);
        if (getEndpointUri().indexOf("://queue:") > -1) {
            topic = false;
        } else if (getEndpointUri().indexOf("://topic:") > -1) {
            topic = true;
        } else {
            throw new IllegalArgumentException("Endpoint URI unsupported: " + uri);
        }
    }

    @Override
    public SjmsComponent getComponent() {
        return (SjmsComponent) super.getComponent();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        //
        // TODO since we only need a session pool for one use case, find a
        // better way
        //
        // We only create a session pool when we are not transacted.
        // Transacted listeners or producers need to be paired with the
        // Session that created them.
        if (!isTransacted() && getExchangePattern().equals(ExchangePattern.InOnly)) {
            sessions = new SessionPool(getSessionCount(), getConnectionResource());

            // TODO fix the string hack
            sessions.setAcknowledgeMode(SessionAcknowledgementType.valueOf(getAcknowledgementMode() + ""));
            getSessions().fillPool();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (getSessions() != null) {
            getSessions().drainPool();
        }
        super.doStop();
    }

    @Override
    public Producer createProducer() throws Exception {
        SjmsProducer producer = null;
        if (getExchangePattern().equals(ExchangePattern.InOnly)) {
            producer = new InOnlyProducer(this);
        } else {
            producer = new InOutProducer(this);
        }
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new SjmsConsumer(this, processor);
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public String getDestinationName() {
        String answer = getEndpointUri().substring(getEndpointUri().lastIndexOf(":") + 1);
        if (answer.indexOf("?") > -1) {
            answer = answer.substring(0, answer.lastIndexOf("?"));
        }
        return answer;
    }

    public ConnectionResource getConnectionResource() {
        return getComponent().getConnectionResource();
    }

    public HeaderFilterStrategy getSjmsHeaderFilterStrategy() {
        return getComponent().getHeaderFilterStrategy();
    }

    public KeyFormatStrategy getJmsKeyFormatStrategy() {
        return getComponent().getKeyFormatStrategy();
    }

    /**
     * Returns a SessionPool if available.
     * 
     * @return the sessions
     */
    public SessionPool getSessions() {
        return sessions;
    }

    /**
     * SessionPool used by endpoints that do not require a dedicated session per
     * consumer or producer.
     * 
     * @param sessions default null
     */
    public void setSessions(SessionPool sessions) {
        this.sessions = sessions;
    }

    /**
     * Use to determine whether or not to process exchanges synchronously.
     * 
     * @return true if endoint is synchronous, otherwise false
     */
    public boolean isSynchronous() {
        return synchronous;
    }

    /**
     * Flag can be set to enable/disable synchronous exchange processing.
     * 
     * @param synchronous true to process synchronously, default is true
     */
    public void setSynchronous(boolean synchronous) {
        this.synchronous = synchronous;
    }

    /**
     * Returns the configured acknowledgementMode.
     * 
     * @return the acknowledgementMode
     */
    public SessionAcknowledgementType getAcknowledgementMode() {
        return acknowledgementMode;
    }

    /**
     * Sets the acknowledgementMode configured on this endpoint.
     * 
     * @param acknowledgementMode default is
     *            SessionAcknowledgementType.AUTO_ACKNOWLEDGE
     */
    public void setAcknowledgementMode(SessionAcknowledgementType acknowledgementMode) {
        this.acknowledgementMode = acknowledgementMode;
    }

    /**
     * Flag set by the endpoint used by consumers and producers to determine if
     * the endpoint is a JMS Topic.
     * 
     * @return the topic true if endpoint is a JMS Topic, default is false
     */
    public boolean isTopic() {
        return topic;
    }

    /**
     * Returns the number of Session instances expected on this endpoint.
     * 
     * @return the sessionCount
     */
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
    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    /**
     * Returns the number of consumer listeners for this endpoint.
     * 
     * @return the producerCount
     */
    public int getProducerCount() {
        return producerCount;
    }

    /**
     * Sets the number of producers used for this endpoint.
     * 
     * @param producerCount the number of producers for this endpoint, default
     *            is 1
     */
    public void setProducerCount(int producerCount) {
        this.producerCount = producerCount;
    }

    /**
     * Returns the number of consumer listeners for this endpoint.
     * 
     * @return the consumerCount
     */
    public int getConsumerCount() {
        return consumerCount;
    }

    /**
     * Sets the number of consumer listeners used for this endpoint.
     * 
     * @param consumerCount the number of consumers for this endpoint, default
     *            is 1
     */
    public void setConsumerCount(int consumerCount) {
        this.consumerCount = consumerCount;
    }

    /**
     * Returns the Time To Live set on this endpoint.
     * 
     * @return the ttl
     */
    public long getTtl() {
        return ttl;
    }

    /**
     * Flag used to adjust the Time To Live value of produced messages.
     * 
     * @param ttl a new TTL, default is -1 (disabled)
     */
    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    /**
     * Use to determine if the enpoint has message persistence enabled or
     * disabled.
     * 
     * @return true if persistent, otherwise false
     */
    public boolean isPersistent() {
        return persistent;
    }

    /**
     * Flag used to enable/disable message persistence.
     * 
     * @param persistent true if persistent, default is true
     */
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    /**
     * Gets the durable subscription Id.
     * 
     * @return the durableSubscriptionId
     */
    public String getDurableSubscriptionId() {
        return durableSubscriptionId;
    }

    /**
     * Sets the durable subscription Id required for durable topics.
     * 
     * @param durableSubscriptionId durable subscription Id or null
     */
    public void setDurableSubscriptionId(String durableSubscriptionId) {
        this.durableSubscriptionId = durableSubscriptionId;
    }

    /**
     * Returns the InOut response timeout.
     * 
     * @return the responseTimeOut
     */
    public long getResponseTimeOut() {
        return responseTimeOut;
    }

    /**
     * Sets the amount of time we should wait before timing out a InOut
     * response.
     * 
     * @param responseTimeOut response timeout
     */
    public void setResponseTimeOut(long responseTimeOut) {
        this.responseTimeOut = responseTimeOut;
    }

    /**
     * Returns the JMS Message selector syntax used to refine the messages being
     * consumed.
     * 
     * @return the messageSelector
     */
    public String getMessageSelector() {
        return messageSelector;
    }

    /**
     * Sets the JMS Message selector syntax.
     * 
     * @param messageSelector Message selector syntax or null
     */
    public void setMessageSelector(String messageSelector) {
        this.messageSelector = messageSelector;
    }

    /**
     * If transacted, returns the nubmer of messages to be processed before
     * committing the transaction.
     * 
     * @return the transactionBatchCount
     */
    public int getTransactionBatchCount() {
        return transactionBatchCount;
    }

    /**
     * If transacted sets the number of messages to process before committing a
     * transaction.
     * 
     * @param transactionBatchCount number of messages to process before
     *            committing, default is 1
     */
    public void setTransactionBatchCount(int transactionBatchCount) {
        this.transactionBatchCount = transactionBatchCount;
    }

    /**
     * Returns the timeout value for batch transactions.
     * 
     * @return long
     */
    public long getTransactionBatchTimeout() {
        return transactionBatchTimeout;
    }

    /**
     * Sets timeout value for batch transactions.
     * 
     * @param transactionBatchTimeout
     */
    public void setTransactionBatchTimeout(long transactionBatchTimeout) {
        if (transactionBatchTimeout >= 1000) {
            this.transactionBatchTimeout = transactionBatchTimeout;
        }
    }

    /**
     * Gets the commit strategy.
     * 
     * @return the transactionCommitStrategy
     */
    public TransactionCommitStrategy getTransactionCommitStrategy() {
        return transactionCommitStrategy;
    }

    /**
     * Sets the commit strategy.
     * 
     * @param transactionCommitStrategy commit strategy to use when processing
     *            transacted messages
     */
    public void setTransactionCommitStrategy(TransactionCommitStrategy transactionCommitStrategy) {
        this.transactionCommitStrategy = transactionCommitStrategy;
    }

    /**
     * Use to determine if transactions are enabled or disabled.
     * 
     * @return true if transacted, otherwise false
     */
    public boolean isTransacted() {
        return transacted;
    }

    /**
     * Enable/disable flag for transactions
     * 
     * @param transacted true if transacted, otherwise false
     */
    public void setTransacted(boolean transacted) {
        if (transacted) {
            setAcknowledgementMode(SessionAcknowledgementType.SESSION_TRANSACTED);
        }
        this.transacted = transacted;
    }

    /**
     * Returns the reply to destination name used for InOut producer endpoints.
     * 
     * @return the namedReplyTo
     */
    public String getNamedReplyTo() {
        return namedReplyTo;
    }

    /**
     * Sets the reply to destination name used for InOut producer endpoints.
     * 
     * @param the namedReplyTo the JMS reply to destination name
     */
    public void setNamedReplyTo(String namedReplyTo) {
        this.namedReplyTo = namedReplyTo;
        this.setExchangePattern(ExchangePattern.InOut);
    }
}
