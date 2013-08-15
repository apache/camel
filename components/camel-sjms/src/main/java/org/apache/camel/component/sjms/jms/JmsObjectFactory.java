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

import org.apache.camel.util.ObjectHelper;

/**
 * TODO Add Class documentation for JmsObjectFactory
 *
 */
public final class JmsObjectFactory {
    
    private JmsObjectFactory() {
        //Helper class
    }

    public static Destination createDestination(Session session, String destinationName, boolean topic)
        throws Exception {
        if (topic) {
            return createTopic(session, destinationName);
        } else {
            return createQueue(session, destinationName);
        }
    }

    public static Destination createQueue(Session session, String destinationName) throws Exception {
        return session.createQueue(destinationName);
    }

    public static Destination createTemporaryDestination(Session session, boolean topic) throws Exception {
        if (topic) {
            return session.createTemporaryTopic();
        } else {
            return session.createTemporaryQueue();
        }
    }

    public static Destination createTopic(Session session, String destinationName) throws Exception {
        return session.createTopic(destinationName);
    }

    public static MessageConsumer createQueueConsumer(Session session, String destinationName) throws Exception {
        return createMessageConsumer(session, destinationName, null, false, null, true);
    }

    public static MessageConsumer createQueueConsumer(Session session, String destinationName, String messageSelector) throws Exception {
        return createMessageConsumer(session, destinationName, messageSelector, false, null, true);
    }

    public static MessageConsumer createTopicConsumer(Session session, String destinationName, String messageSelector) throws Exception {
        return createMessageConsumer(session, destinationName, messageSelector, true, null, true);
    }
    
    public static MessageConsumer createTemporaryMessageConsumer(
            Session session, 
            String messageSelector, 
            boolean topic, 
            String durableSubscriptionId,
            boolean noLocal) throws Exception {
        Destination destination = createTemporaryDestination(session, topic);
        return createMessageConsumer(session, destination, messageSelector, topic, durableSubscriptionId, noLocal);
    }
    
    public static MessageConsumer createMessageConsumer(
            Session session, 
            String destinationName, 
            String messageSelector, 
            boolean topic, 
            String durableSubscriptionId) throws Exception {
        return createMessageConsumer(session, destinationName, messageSelector, topic, durableSubscriptionId, true);
    }
    
    public static MessageConsumer createMessageConsumer(
            Session session, 
            String destinationName, 
            String messageSelector, 
            boolean topic, 
            String durableSubscriptionId,
            boolean noLocal) throws Exception {
        Destination destination = null;
        if (topic) {
            destination = session.createTopic(destinationName);
            
        } else {
            destination = session.createQueue(destinationName);
        }
        return createMessageConsumer(session, destination, messageSelector, topic, durableSubscriptionId, noLocal);
    }
    
    public static MessageConsumer createMessageConsumer(
            Session session, 
            Destination destination, 
            String messageSelector, 
            boolean topic, 
            String durableSubscriptionId,
            boolean noLocal) throws Exception {
        MessageConsumer messageConsumer = null;
        
        if (topic) {
            if (ObjectHelper.isNotEmpty(durableSubscriptionId)) {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    messageConsumer = session.createDurableSubscriber((Topic)destination, durableSubscriptionId,
                                                                 messageSelector, noLocal);
                } else {
                    messageConsumer = session.createDurableSubscriber((Topic)destination, durableSubscriptionId);
                }
            } else {
                if (ObjectHelper.isNotEmpty(messageSelector)) {
                    messageConsumer = session.createConsumer((Topic)destination, messageSelector, noLocal);
                } else {
                    messageConsumer = session.createConsumer((Topic)destination);
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
    
    public static MessageProducer createQueueProducer(
            Session session, 
            String destinationName) throws Exception {
        return createMessageProducer(session, destinationName, false, true, -1);
    }
    
    public static MessageProducer createTopicProducer(
            Session session, 
            String destinationName) throws Exception {
        return createMessageProducer(session, destinationName, true, false, -1);
    }
    
    public static MessageProducer createMessageProducer(
            Session session, 
            String destinationName, 
            boolean topic,
            boolean persitent,
            long ttl) throws Exception {
        MessageProducer messageProducer = null;
        Destination destination = null;
        if (topic) {
            if (destinationName.startsWith("topic://")) {
                destinationName = destinationName.substring("topic://".length());
            }
            destination = session.createTopic(destinationName);
        } else {
            if (destinationName.startsWith("queue://")) {
                destinationName = destinationName.substring("queue://".length());
            }
            destination = session.createQueue(destinationName);
        }
        messageProducer = session.createProducer(destination);

        if (persitent) {
            messageProducer.setDeliveryMode(DeliveryMode.PERSISTENT);
        } else {
            messageProducer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
        }
        if (ttl > 0) {
            messageProducer.setTimeToLive(ttl);
        }
        return messageProducer;
    }
}
