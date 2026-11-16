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

import java.util.function.Function;

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
import static org.apache.camel.component.sjms.SjmsHelper.rollbackIfNeeded;

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
                    send(producer, message, exchange.getIn());
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
        send(producer, message, null);
    }

    /**
     * Sends the message with the producer.
     *
     * @param producer     the producer
     * @param message      the JMS message
     * @param camelMessage the Camel message the JMS message was created from. With preserveMessageQos, the priority and
     *                     delivery mode of the JMS message are only used when this message has the JMSPriority or
     *                     JMSDeliveryMode header, otherwise those of the endpoint. When null, those of the JMS message
     *                     are used.
     */
    public void send(MessageProducer producer, Message message, org.apache.camel.Message camelMessage) throws Exception {
        if (preserveMessageQos) {
            long ttl = message.getJMSExpiration();
            if (ttl != 0) {
                ttl = ttl - System.currentTimeMillis();
                // Message had expired.. so set the ttl as small as possible
                if (ttl <= 0) {
                    ttl = 1;
                }
            } else {
                // the message has no expiration, so use the time to live of the endpoint (timeToLive, or the
                // requestTimeout for request/reply), which is 0 (never expires) when none is configured
                ttl = this.timeToLive;
            }

            // a new JMS message always has a priority and delivery mode (the binding sets PERSISTENT, and the
            // priority is the default of the JMS client, 0 with ActiveMQ Classic), so they are only taken from the
            // message when the Camel message has the header; otherwise the endpoint values are used
            int priority = this.priority;
            if (camelMessage == null || camelMessage.getHeader(JmsConstants.JMS_PRIORITY) != null) {
                int messagePriority = message.getJMSPriority();
                // use priority from endpoint if not provided on message with a valid range
                if (messagePriority >= 0 && messagePriority <= 9) {
                    priority = messagePriority;
                }
            }

            int resolvedDeliveryMode = this.deliveryMode;
            if (camelMessage == null || camelMessage.getHeader(JmsConstants.JMS_DELIVERY_MODE) != null) {
                resolvedDeliveryMode = resolveDeliveryMode(message);
            }

            producer.send(message, resolvedDeliveryMode, priority, ttl);
        } else if (explicitQosEnabled) {
            producer.send(message, deliveryMode, priority, timeToLive);
        } else {
            producer.send(message);
        }
    }

    private static int resolveDeliveryMode(Message message) throws JMSException {
        int resolvedDeliveryMode;
        // the sjms binding sets the JMSDeliveryMode header with setJMSDeliveryMode, so the binding never creates this
        // property; it is kept for a JMS message that carries it as a property
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
        return receive(destinationName, messageSelector, isTopic, timeout, Function.identity());
    }

    /**
     * Receives a message, and applies the function to it before the message is acknowledged (or the session committed),
     * and before the consumer, the session and the connection are closed.
     * <p/>
     * {@link #receive(String, String, boolean, long)} returns the message after they are closed, when a JMS client may
     * no longer read it: the ActiveMQ Artemis client streams the body of a large message through the consumer. If the
     * function fails, the message is not acknowledged: a transacted session is rolled back, and a client acknowledge
     * session is recovered, so the message is delivered again.
     *
     * @param  destinationName the name of the destination
     * @param  messageSelector the message selector, or <tt>null</tt> for none
     * @param  isTopic         whether the destination is a topic
     * @param  timeout         the receive timeout in millis, 0 to wait without timeout, or a negative value to not wait
     * @param  function        the function to apply to the received message
     * @return                 the result of the function, or <tt>null</tt> if no message was received
     */
    @SuppressWarnings("unchecked")
    public <T> T receive(
            String destinationName, String messageSelector, boolean isTopic, long timeout, Function<Message, T> function)
            throws Exception {
        Object obj = execute(sc -> {
            Destination dest = destinationCreationStrategy.createDestination(sc, destinationName, isTopic);
            MessageConsumer consumer;

            if (ObjectHelper.isNotEmpty(messageSelector)) {
                consumer = sc.createConsumer(dest, messageSelector);
            } else {
                consumer = sc.createConsumer(dest);
            }

            try {
                Message message;
                if (timeout < 0) {
                    message = consumer.receiveNoWait();
                } else if (timeout == 0) {
                    message = consumer.receive();
                } else {
                    message = consumer.receive(timeout);
                }
                T answer;
                try {
                    answer = message != null ? function.apply(message) : null;
                } catch (RuntimeException | Error e) {
                    rollbackOnException(sc, e);
                    throw e;
                }
                // success then commit if we need to
                commitIfNeeded(sc, message);
                return answer;
            } finally {
                closeConsumer(consumer);
            }
        }, true);
        return (T) obj;
    }

    /**
     * Rolls back a transacted session, or recovers a client acknowledge session, when the received message could not be
     * read. A session of a pooled connection factory is not closed after the receive, so the message would otherwise
     * stay unacknowledged on it, and the next acknowledge on that session would acknowledge it too. A failure of the
     * rollback is added to the cause as a suppressed exception.
     */
    private static void rollbackOnException(Session session, Throwable cause) {
        try {
            rollbackIfNeeded(session);
        } catch (JMSException | RuntimeException e) {
            cause.addSuppressed(e);
        }
    }

    public Connection createConnection() throws Exception {
        return connectionFactory.createConnection();
    }

    public Session createSession(Connection connection) throws Exception {
        return connection.createSession(transacted, acknowledgeMode);
    }

}
