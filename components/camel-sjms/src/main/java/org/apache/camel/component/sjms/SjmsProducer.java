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

import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;
import org.apache.camel.support.DefaultAsyncProducer;

/**
 * Base SjmsProducer class.
 */
public abstract class SjmsProducer extends DefaultAsyncProducer {

    protected SjmsTemplate template;

    public SjmsProducer(Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected void doInit() throws Exception {
        template = new SjmsTemplate(getEndpoint().getConnectionFactory(), isEndpointTransacted(), getAcknowledgeMode());

        // configure qos if enabled
        if (getEndpoint().isExplicitQosEnabled()) {
            int dm = getEndpoint().isDeliveryPersistent() ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT;
            if (getEndpoint().getDeliveryMode() != null) {
                dm = getEndpoint().getDeliveryMode();
            }
            template.setQoSSettings(dm, getEndpoint().getPriority(), getEndpoint().getTimeToLive());
        }
    }

    @Override
    public SjmsEndpoint getEndpoint() {
        return (SjmsEndpoint) super.getEndpoint();
    }

    protected Destination resolveDestinationName(Session session, String destinationName) throws JMSException {
        return getEndpoint().getDestinationCreationStrategy().createDestination(session,
                destinationName, getEndpoint().isTopic());
    }

    protected abstract void sendMessage(Exchange exchange, Session sharedSession, String destinationName);

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        String destinationName = exchange.getMessage().getHeader(SjmsConstants.JMS_DESTINATION_NAME, String.class);
        if (destinationName != null) {
            // remove the header so it wont be propagated
            exchange.getMessage().removeHeader(SjmsConstants.JMS_DESTINATION_NAME);
        }

        return doProcess(exchange, callback, destinationName);
    }

    protected boolean doProcess(Exchange exchange, AsyncCallback callback, String destinationName) {
        if (destinationName == null) {
            destinationName = getDestinationName();
        }
        try {
            if (isEndpointTransacted()) {
                Connection connection = null;
                Session session = null;
                boolean close = false;
                if (isSharedJMSSession()) {
                    session = exchange.getIn().getHeader(SjmsConstants.JMS_SESSION, Session.class);
                }
                if (session == null) {
                    close = true;
                    connection = template.createConnection();
                    session = template.createSession(connection);
                }
                exchange.getUnitOfWork().addSynchronization(
                        new SessionTransactionSynchronization(connection, session, getCommitStrategy(), close));
                sendMessage(exchange, session, destinationName);
            } else {
                sendMessage(exchange, null, destinationName);
            }
        } catch (Throwable e) {
            exchange.setException(e);
        }
        callback.done(true);
        return true;
    }

    /**
     * Gets the acknowledgment mode for this instance of DestinationProducer.
     */
    public int getAcknowledgeMode() {
        return getEndpoint().getAcknowledgementMode().intValue();
    }

    /**
     * Gets the synchronous value for this instance of DestinationProducer.
     */
    public boolean isSynchronous() {
        return getEndpoint().isSynchronous();
    }

    /**
     * Gets the replyTo for this instance of DestinationProducer.
     */
    public String getReplyTo() {
        return getEndpoint().getReplyTo();
    }

    /**
     * Gets the destinationName for this instance of DestinationProducer.
     */
    public String getDestinationName() {
        return getEndpoint().getDestinationName();
    }

    /**
     * Test to verify if this endpoint is a JMS Topic or Queue.
     */
    public boolean isTopic() {
        return getEndpoint().isTopic();
    }

    /**
     * Test to determine if this endpoint should use a JMS Transaction.
     */
    public boolean isEndpointTransacted() {
        return getEndpoint().isTransacted();
    }

    /**
     * Test to determine if this endpoint should share a JMS Session with other SJMS endpoints.
     */
    public boolean isSharedJMSSession() {
        return getEndpoint().isSharedJMSSession();
    }

    /**
     * Gets consumerCount for this instance of SjmsProducer.
     */
    public int getConsumerCount() {
        return getEndpoint().getConsumerCount();
    }

    /**
     * Gets commitStrategy for this instance of SjmsProducer.
     */
    protected TransactionCommitStrategy getCommitStrategy() {
        if (isEndpointTransacted()) {
            return getEndpoint().getTransactionCommitStrategy();
        }
        return null;
    }

    protected static void close(MessageProducer producer) {
        if (producer != null) {
            try {
                producer.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }

}
