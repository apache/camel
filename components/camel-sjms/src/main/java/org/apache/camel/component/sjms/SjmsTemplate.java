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

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.MessageCreator;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.sjms.SjmsHelper.closeConnection;
import static org.apache.camel.component.sjms.SjmsHelper.closeConsumer;
import static org.apache.camel.component.sjms.SjmsHelper.closeProducer;
import static org.apache.camel.component.sjms.SjmsHelper.closeSession;
import static org.apache.camel.component.sjms.SjmsHelper.commitIfNeeded;
import static org.apache.camel.component.sjms.SjmsHelper.isTransactionOrClientAcknowledgeMode;

public class SjmsTemplate {

    private final ConnectionFactory connectionFactory;
    private final boolean transacted;
    private final int acknowledgeMode;
    private DestinationCreationStrategy destinationCreationStrategy;

    private boolean preserveMessageQos;
    private boolean explicitQosEnabled;
    private int deliveryMode = Message.DEFAULT_DELIVERY_MODE;
    private int priority = Message.DEFAULT_PRIORITY;
    private long timeToLive = Message.DEFAULT_TIME_TO_LIVE;

    public SjmsTemplate(ConnectionFactory connectionFactory, boolean transacted, int acknowledgeMode) {
        ObjectHelper.notNull(connectionFactory, "ConnectionFactory", this);

        this.connectionFactory = connectionFactory;
        this.transacted = transacted;
        this.acknowledgeMode = acknowledgeMode;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setDestinationCreationStrategy(DestinationCreationStrategy destinationCreationStrategy) {
        this.destinationCreationStrategy = destinationCreationStrategy;
    }

    public void setQoSSettings(int deliveryMode, int priority, long timeToLive) {
        if (deliveryMode != 0) {
            this.deliveryMode = deliveryMode;
            this.explicitQosEnabled = true;
        }
        if (priority != 0) {
            this.priority = priority;
            this.explicitQosEnabled = true;
        }
        if (timeToLive > 0) {
            this.timeToLive = timeToLive;
            this.explicitQosEnabled = true;
        }
    }

    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        this.explicitQosEnabled = explicitQosEnabled;
    }

    public void setPreserveMessageQos(boolean preserveMessageQos) {
        this.preserveMessageQos = preserveMessageQos;
    }

    public Object execute(SessionCallback sessionCallback, boolean startConnection) throws Exception {
        Connection con = null;
        Session session = null;
        try {
            con = createConnection();
            if (startConnection) {
                con.start();
            }
            session = createSession(con);
            return sessionCallback.doInJms(session);
        } finally {
            sessionCallback.onClose(con, session);
        }
    }

    public void execute(Session session, SessionCallback sessionCallback) throws Exception {
        if (session == null) {
            execute(sessionCallback, false);
        } else {
            try {
                sessionCallback.doInJms(session);
            } finally {
                sessionCallback.onClose(null, session);
            }
        }
    }

    public void send(Exchange exchange, String destinationName, MessageCreator messageCreator, boolean isTopic)
            throws Exception {

        final SessionCallback callback = new SessionCallback() {

            private volatile Message message;
            private volatile boolean transacted;

            @Override
            public Object doInJms(Session session) throws Exception {
                this.transacted = isTransactionOrClientAcknowledgeMode(session);

                if (transacted) {
                    // remember current session if transactional
                    exchange.setProperty(SjmsConstants.JMS_SESSION, session);
                }

                Destination dest = destinationCreationStrategy.createDestination(session, destinationName, isTopic);
                this.message = messageCreator.createMessage(session);
                MessageProducer producer = session.createProducer(dest);
                try {
                    send(producer, message);
                } finally {
                    closeProducer(producer);
                }

                return null;
            }

            @Override
            public void onClose(Connection connection, Session session) {
                try {
                    if (transacted) {
                        // defer closing till end of UoW
                        TransactionOnCompletion toc = new TransactionOnCompletion(session, this.message);
                        if (!exchange.getExchangeExtension().containsOnCompletion(toc)) {
                            exchange.getExchangeExtension().addOnCompletion(toc);
                        }
                    } else {
                        closeSession(session);
                        closeConnection(connection);
                    }
                } catch (Exception e) {
                    // ignore
                }
            }
        };

        execute(callback, false);
    }

    public void send(MessageProducer producer, Message message) throws Exception {
        if (preserveMessageQos) {
            long ttl = message.getJMSExpiration();
            if (ttl != 0) {
                ttl = ttl - System.currentTimeMillis();
                // Message had expired.. so set the ttl as small as possible
                if (ttl <= 0) {
                    ttl = 1;
                }
            }

            int priority = message.getJMSPriority();
            if (priority < 0 || priority > 9) {
                // use priority from endpoint if not provided on message with a valid range
                priority = this.priority;
            }

            // if a delivery mode was set as a JMS header then we have used a temporary
            // property to store it - CamelJMSDeliveryMode. Otherwise we could not keep
            // track whether it was set or not as getJMSDeliveryMode() will default return 1 regardless
            // if it was set or not, so we can never tell if end user provided it in a header
            int resolvedDeliveryMode = resolveDeliveryMode(message);

            producer.send(message, resolvedDeliveryMode, priority, ttl);
        } else if (explicitQosEnabled) {
            producer.send(message, deliveryMode, priority, timeToLive);
        } else {
            producer.send(message);
        }
    }

    private static int resolveDeliveryMode(Message message) throws JMSException {
        int resolvedDeliveryMode;
        if (JmsMessageHelper.hasProperty(message, JmsConstants.JMS_DELIVERY_MODE)) {
            resolvedDeliveryMode = message.getIntProperty(JmsConstants.JMS_DELIVERY_MODE);
            // remove the temporary property
            JmsMessageHelper.removeJmsProperty(message, JmsConstants.JMS_DELIVERY_MODE);
        } else {
            // use the existing delivery mode from the message
            resolvedDeliveryMode = message.getJMSDeliveryMode();
        }
        return resolvedDeliveryMode;
    }

    public Message receive(String destinationName, String messageSelector, boolean isTopic, long timeout) throws Exception {
        Object obj = execute(sc -> {
            Destination dest = destinationCreationStrategy.createDestination(sc, destinationName, isTopic);
            MessageConsumer consumer;

            if (ObjectHelper.isNotEmpty(messageSelector)) {
                consumer = sc.createConsumer(dest, messageSelector);
            } else {
                consumer = sc.createConsumer(dest);
            }

            Message message = null;
            try {
                if (timeout < 0) {
                    message = consumer.receiveNoWait();
                } else if (timeout == 0) {
                    message = consumer.receive();
                } else {
                    message = consumer.receive(timeout);
                }
            } finally {
                // success then commit if we need to
                commitIfNeeded(sc, message);
                closeConsumer(consumer);
            }
            return message;
        }, true);
        return (Message) obj;
    }

    public Connection createConnection() throws Exception {
        return connectionFactory.createConnection();
    }

    public Session createSession(Connection connection) throws Exception {
        return connection.createSession(transacted, acknowledgeMode);
    }

}
