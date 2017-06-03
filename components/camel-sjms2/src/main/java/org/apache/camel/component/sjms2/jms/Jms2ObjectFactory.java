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
package org.apache.camel.component.sjms2.jms;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.camel.Endpoint;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms2.Sjms2Endpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * JMS 2.0 object factory
 */
public class Jms2ObjectFactory implements JmsObjectFactory {

    @Override
    public MessageConsumer createMessageConsumer(Session session, Endpoint endpoint)
            throws Exception {
        Sjms2Endpoint sjms2Endpoint = (Sjms2Endpoint) endpoint;
        Destination destination = sjms2Endpoint.getDestinationCreationStrategy().createDestination(session, sjms2Endpoint.getDestinationName(), sjms2Endpoint.isTopic());
        return createMessageConsumer(session,
                destination,
                sjms2Endpoint.getMessageSelector(),
                sjms2Endpoint.isTopic(),
                sjms2Endpoint.getSubscriptionId(),
                sjms2Endpoint.isDurable(),
                sjms2Endpoint.isShared());
    }

    @Override
    public MessageConsumer createMessageConsumer(Session session, Destination destination,
            String messageSelector, boolean topic, String subscriptionId, boolean durable,
            boolean shared) throws Exception {
        // noLocal is default false according to JMS spec
        return createMessageConsumer(session,
                destination,
                messageSelector,
                topic,
                subscriptionId,
                durable,
                shared,
                false);
    }

    @Override
    public MessageConsumer createMessageConsumer(Session session, Destination destination,
            String messageSelector, boolean topic, String subscriptionId, boolean durable,
            boolean shared, boolean noLocal) throws Exception {

        if (topic) {
            return createTopicMessageConsumer(session,
                    destination,
                    messageSelector,
                    subscriptionId,
                    durable,
                    shared,
                    noLocal);
        } else {
            return createQueueMessageConsumer(session, destination, messageSelector);
        }
    }

    private MessageConsumer createQueueMessageConsumer(Session session, Destination destination,
            String messageSelector) throws JMSException {
        if (ObjectHelper.isNotEmpty(messageSelector)) {
            return session.createConsumer(destination, messageSelector);
        } else {
            return session.createConsumer(destination);
        }
    }

    private MessageConsumer createTopicMessageConsumer(Session session, Destination destination,
            String messageSelector, String subscriptionId, boolean durable, boolean shared,
            boolean noLocal) throws JMSException {
        if (ObjectHelper.isNotEmpty(subscriptionId)) {
            return createSubscriptionTopicConsumer(session,
                    destination,
                    messageSelector,
                    subscriptionId,
                    durable,
                    shared,
                    noLocal);
        } else {
            return createSubscriptionlessTopicConsumer(session,
                    destination,
                    messageSelector,
                    noLocal);
        }
    }

    private MessageConsumer createSubscriptionTopicConsumer(Session session,
            Destination destination, String messageSelector, String subscriptionId, boolean durable,
            boolean shared, boolean noLocal) throws JMSException {
        if (shared) {
            if (durable) {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    return session.createSharedDurableConsumer((Topic) destination,
                            subscriptionId,
                            messageSelector);
                } else {
                    return session.createSharedDurableConsumer((Topic) destination, subscriptionId);
                }
            } else {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    return session.createSharedConsumer((Topic) destination,
                            subscriptionId,
                            messageSelector);
                } else {
                    return session.createSharedConsumer((Topic) destination, subscriptionId);
                }
            }
        } else {
            if (durable) {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    return session.createDurableSubscriber((Topic) destination,
                            subscriptionId,
                            messageSelector,
                            noLocal);
                } else {
                    return session.createDurableSubscriber((Topic) destination, subscriptionId);
                }
            } else {
                return createSubscriptionlessTopicConsumer(session,
                        destination,
                        messageSelector,
                        noLocal);
            }
        }
    }

    private MessageConsumer createSubscriptionlessTopicConsumer(Session session,
            Destination destination, String messageSelector, boolean noLocal) throws JMSException {
        if (ObjectHelper.isNotEmpty(messageSelector)) {
            return session.createConsumer(destination, messageSelector, noLocal);
        } else {
            return session.createConsumer(destination);
        }
    }

    @Override
    public MessageProducer createMessageProducer(Session session, Endpoint endpoint)
            throws Exception {
        Sjms2Endpoint sjms2Endpoint = (Sjms2Endpoint)endpoint;
        Destination destination = sjms2Endpoint.getDestinationCreationStrategy().createDestination(session, sjms2Endpoint.getDestinationName(), sjms2Endpoint.isTopic());

        return createMessageProducer(session, destination, sjms2Endpoint.isPersistent(), sjms2Endpoint.getTtl());
    }

    @Override
    public MessageProducer createMessageProducer(Session session, Destination destination,
            boolean persistent, long ttl) throws Exception {
        MessageProducer messageProducer = session.createProducer(destination);
        messageProducer.setDeliveryMode(persistent
                ? DeliveryMode.PERSISTENT
                : DeliveryMode.NON_PERSISTENT);
        if (ttl > 0) {
            messageProducer.setTimeToLive(ttl);
        }
        return messageProducer;
    }
}
