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
package org.apache.camel.component.sjms.producer;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.TransactionCommitStrategy;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.tx.DefaultTransactionCommitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.sjms.jms.JmsMessageHelper.isTopicPrefix;

/**
 * A Camel Producer that provides the InOnly Exchange pattern.
 */
public class InOnlyProducer extends SjmsProducer {

    private static final Logger LOG = LoggerFactory.getLogger(InOnlyProducer.class);

    public InOnlyProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected TransactionCommitStrategy getCommitStrategy() {
        if (isEndpointTransacted()) {
            return super.getCommitStrategy() == null ? new DefaultTransactionCommitStrategy() : super.getCommitStrategy();
        }
        return null;
    }

    @Override
    protected void sendMessage(Exchange exchange, Session session, String destinationName) {
        try {
            template.execute(session, sc -> {
                MessageProducer producer = null;
                try {
                    Message answer = getEndpoint().getBinding().makeJmsMessage(exchange, sc);

                    // when in InOnly mode the JMSReplyTo is a bit complicated
                    // we only want to set the JMSReplyTo on the answer if
                    // there is a JMSReplyTo from the header/endpoint and
                    // we have been told to preserveMessageQos

                    Object jmsReplyTo = JmsMessageHelper.getJMSReplyTo(answer);
                    if (getEndpoint().isDisableReplyTo()) {
                        // honor disable reply to configuration
                        LOG.trace("ReplyTo is disabled on endpoint: {}", getEndpoint());
                        JmsMessageHelper.setJMSReplyTo(answer, null);
                    } else {
                        // if the binding did not create the reply to then we have to try to create it here
                        if (jmsReplyTo == null) {
                            // prefer reply to from header over endpoint configured
                            jmsReplyTo = exchange.getIn().getHeader("JMSReplyTo", String.class);
                            if (jmsReplyTo == null) {
                                jmsReplyTo = getEndpoint().getReplyTo();
                            }
                        }
                    }

                    // we must honor these special flags to preserve QoS
                    // as we are not OUT capable and thus do not expect a reply, and therefore
                    // the consumer of this message should not return a reply so we remove it
                    // unless we use preserveMessageQos=true to tell that we still want to use JMSReplyTo
                    if (jmsReplyTo != null && !(getEndpoint().isPreserveMessageQos() || getEndpoint().isExplicitQosEnabled())) {
                        // log at debug what we are doing, as higher level may cause noise in production logs
                        // this behavior is also documented at the camel website
                        if (LOG.isDebugEnabled()) {
                            LOG.debug(
                                    "Disabling JMSReplyTo: {} for destination: {}. Use preserveMessageQos=true to force Camel to keep the JMSReplyTo on endpoint: {}",
                                    new Object[] { jmsReplyTo, destinationName, getEndpoint() });
                        }
                        jmsReplyTo = null;
                    }

                    // the reply to is a String, so we need to look up its Destination instance
                    // and if needed create the destination using the session if needed to
                    if (jmsReplyTo instanceof String) {
                        String replyTo = (String) jmsReplyTo;
                        // we need to null it as we use the String to resolve it as a Destination instance
                        jmsReplyTo = resolveOrCreateDestination(replyTo, sc);
                    }

                    // set the JMSReplyTo on the answer if we are to use it
                    Destination replyTo = null;
                    String replyToOverride = getEndpoint().getReplyToOverride();
                    if (replyToOverride != null) {
                        replyTo = resolveOrCreateDestination(replyToOverride, sc);
                    } else if (jmsReplyTo instanceof Destination) {
                        replyTo = (Destination) jmsReplyTo;
                    }
                    if (replyTo != null) {
                        LOG.debug("Using JMSReplyTo destination: {}", replyTo);
                        JmsMessageHelper.setJMSReplyTo(answer, replyTo);
                    } else {
                        // do not use JMSReplyTo
                        LOG.trace("Not using JMSReplyTo");
                        JmsMessageHelper.setJMSReplyTo(answer, null);
                    }

                    producer = getEndpoint().getJmsObjectFactory().createMessageProducer(sc, getEndpoint(), destinationName);
                    template.send(producer, answer);
                } finally {
                    close(producer);
                }
                return null;
            });
        } catch (Exception e) {
            exchange.setException(new CamelExchangeException("Unable to complete sending the JMS message", exchange, e));
        }
    }

    protected Destination resolveOrCreateDestination(String destinationName, Session session)
            throws JMSException {
        boolean isPubSub = isTopicPrefix(destinationName)
                || (!JmsMessageHelper.isQueuePrefix(destinationName) && getEndpoint().isTopic());
        return getEndpoint().getDestinationCreationStrategy().createDestination(session, destinationName, isPubSub);
    }

}
