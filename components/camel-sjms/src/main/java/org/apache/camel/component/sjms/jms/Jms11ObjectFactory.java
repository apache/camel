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
package org.apache.camel.component.sjms.jms;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.camel.Endpoint;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * JMS 1.1 object factory
 */
public class Jms11ObjectFactory implements JmsObjectFactory {

    @Override
    public MessageConsumer createMessageConsumer(Session session, Endpoint endpoint)
            throws Exception {
        SjmsEndpoint sjmsEndpoint = (SjmsEndpoint)endpoint;
        Destination destination = sjmsEndpoint.getDestinationCreationStrategy().createDestination(session, sjmsEndpoint.getDestinationName(), sjmsEndpoint.isTopic());
        return createMessageConsumer(session, destination, sjmsEndpoint.getMessageSelector(), sjmsEndpoint.isTopic(), sjmsEndpoint.getDurableSubscriptionId(), true, false);
    }

    public MessageConsumer createMessageConsumer(
            Session session,
            Destination destination,
            String messageSelector,
            boolean topic,
            String subscriptionId,
            boolean durable,
            boolean shared) throws Exception {
        // noLocal is default false according to JMS spec
        return createMessageConsumer(session, destination, messageSelector, topic, subscriptionId, durable, shared, false);
    }


    @Override
    public MessageConsumer createMessageConsumer(Session session, Destination destination,
            String messageSelector, boolean topic, String subscriptionId, boolean durable,
            boolean shared, boolean noLocal) throws Exception {
        MessageConsumer messageConsumer;

        if (topic) {
            if (ObjectHelper.isNotEmpty(subscriptionId)) {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    messageConsumer = session.createDurableSubscriber((Topic) destination, subscriptionId,
                            messageSelector, noLocal);
                } else {
                    messageConsumer = session.createDurableSubscriber((Topic) destination, subscriptionId);
                }
            } else {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    messageConsumer = session.createConsumer(destination, messageSelector, noLocal);
                } else {
                    messageConsumer = session.createConsumer(destination);
                }
            }
        } else {
            if (ObjectHelper.isNotEmpty(messageSelector)) {
                messageConsumer = session.createConsumer(destination, messageSelector);
            } else {
                messageConsumer = session.createConsumer(destination);
            }
        }
        return messageConsumer;
    }

    @Override
    public MessageProducer createMessageProducer(Session session, Endpoint endpoint)
            throws Exception {
        SjmsEndpoint sjmsEndpoint = (SjmsEndpoint)endpoint;
        Destination destination = sjmsEndpoint.getDestinationCreationStrategy().createDestination(session, sjmsEndpoint.getDestinationName(), sjmsEndpoint.isTopic());

        return createMessageProducer(session, destination, sjmsEndpoint.isPersistent(), sjmsEndpoint.getTtl());
    }

    public MessageProducer createMessageProducer(
            Session session,
            Destination destination,
            boolean persistent,
            long ttl) throws Exception {
        MessageProducer messageProducer = session.createProducer(destination);
        messageProducer.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
        if (ttl > 0) {
            messageProducer.setTimeToLive(ttl);
        }
        return messageProducer;
    }
}
