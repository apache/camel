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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.sjms.consumer.DefaultConsumer;
import org.apache.camel.component.sjms.jms.ConnectionResource;
import org.apache.camel.component.sjms.jms.SessionAcknowledgementType;
import org.apache.camel.component.sjms.jms.SessionPool;
import org.apache.camel.component.sjms.producer.InOnlyProducer;
import org.apache.camel.component.sjms.producer.InOutProducer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.HeaderFilterStrategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO Add Class documentation for SjmsEndpoint
 *
 */
public class SjmsEndpoint extends DefaultEndpoint implements MultipleConsumersSupport {
    protected final transient Logger logger = LoggerFactory
            .getLogger(getClass());

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
    

    public SjmsEndpoint() {
        super();
    }

    public SjmsEndpoint(String uri, Component component) {
        super(uri, component);
        if (getEndpointUri().indexOf("://queue:") > -1) {
            setTopic(false);
        } else if (getEndpointUri().indexOf("://topic:") > -1) {
            setTopic(true);
        }  else {
            throw new RuntimeCamelException("Endpoint URI unsupported: " + uri);
        }
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        
        //
        // TODO since we only need a session pool for one use case, find a better way
        //
        // We only create a session pool when we are not transacted.
        // Transacted listeners or producers need to be paired with the
        // Session that created them.
        if (!isTransacted()) {
            sessions = new SessionPool(getSessionCount(), getConnectionResource());
            
            // TODO fix the string hack
            sessions.setAcknowledgeMode(SessionAcknowledgementType
                    .valueOf(getAcknowledgementMode() + ""));
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
        if (this.getExchangePattern().equals(ExchangePattern.InOnly)) {
            producer = new InOnlyProducer(this);
        } else {
            producer = new InOutProducer(this);
        }
        return producer;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new DefaultConsumer(this, processor);
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

    public SjmsComponent getSjmsComponent() {
        return (SjmsComponent) this.getComponent();
    }

    public ConnectionResource getConnectionResource() {
        return this.getSjmsComponent().getConnectionResource();
    }

    public HeaderFilterStrategy getSjmsHeaderFilterStrategy() {
        return getSjmsComponent().getHeaderFilterStrategy();
    }

    public KeyFormatStrategy getJmsKeyFormatStrategy() {
        return getSjmsComponent().getKeyFormatStrategy();
    }

    public SessionPool getSessions() {
        return sessions;
    }

    public void setSynchronous(boolean asyncConsumer) {
        this.synchronous = asyncConsumer;
    }

    public boolean isSynchronous() {
        return synchronous;
    }

    public void setTransacted(boolean transacted) {
        if (transacted) {
            setAcknowledgementMode(SessionAcknowledgementType.SESSION_TRANSACTED);
        }
        this.transacted = transacted;
    }

    public boolean isTransacted() {
        return transacted;
    }

    public void setNamedReplyTo(String namedReplyTo) {
        this.namedReplyTo = namedReplyTo;
        this.setExchangePattern(ExchangePattern.InOut);
    }

    public String getNamedReplyTo() {
        return namedReplyTo;
    }

    public void setAcknowledgementMode(SessionAcknowledgementType acknowledgementMode) {
        this.acknowledgementMode = acknowledgementMode;
    }

    public SessionAcknowledgementType getAcknowledgementMode() {
        return acknowledgementMode;
    }

    public void setTopic(boolean topic) {
        this.topic = topic;
    }

    public boolean isTopic() {
        return topic;
    }

    public void setProducerCount(int producerCount) {
        this.producerCount = producerCount;
    }

    public int getProducerCount() {
        return producerCount;
    }

    public void setConsumerCount(int consumerCount) {
        this.consumerCount = consumerCount;
    }

    public int getConsumerCount() {
        return consumerCount;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public boolean isPersistent() {
        return persistent;
    }

    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    public String getDurableSubscriptionId() {
        return durableSubscriptionId;
    }

    public void setDurableSubscriptionId(String durableSubscription) {
        this.durableSubscriptionId = durableSubscription;
    }

    public int getSessionCount() {
        return sessionCount;
    }

    public void setSessionCount(int sessionCount) {
        this.sessionCount = sessionCount;
    }

    public void setResponseTimeOut(long responseTimeOut) {
        this.responseTimeOut = responseTimeOut;
    }

    public long getResponseTimeOut() {
        return responseTimeOut;
    }

    public void setMessageSelector(String messageSelector) {
        this.messageSelector = messageSelector;
    }

    public String getMessageSelector() {
        return messageSelector;
    }
}
