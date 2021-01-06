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
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.MessageCreator;
import org.apache.camel.util.ObjectHelper;

import static org.apache.camel.component.sjms.SjmsHelper.*;

public class SjmsTemplate {

    private final ConnectionFactory connectionFactory;
    private final boolean transacted;
    private final int acknowledgeMode;
    private DestinationCreationStrategy destinationCreationStrategy;

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
        if (timeToLive != 0) {
            this.timeToLive = timeToLive;
            this.explicitQosEnabled = true;
        }
    }

    public void setExplicitQosEnabled(boolean explicitQosEnabled) {
        this.explicitQosEnabled = explicitQosEnabled;
    }

    public <T> T execute(SessionCallback<T> sessionCallback) throws Exception {
        Connection con = null;
        Session session = null;
        try {
            con = createConnection();
            session = createSession(con);
            return sessionCallback.doInJms(session);
        } finally {
            closeSession(session);
            closeConnection(con);
        }
    }

    public <T> T execute(Session session, SessionCallback<T> sessionCallback) throws Exception {
        if (session == null) {
            return execute(sessionCallback);
        } else {
            return sessionCallback.doInJms(session);
        }
    }

    public void send(String destinationName, MessageCreator messageCreator, boolean isTopic) throws Exception {
        execute(session -> {
            Destination dest = destinationCreationStrategy.createDestination(session, destinationName, isTopic);
            Message message = messageCreator.createMessage(session);
            MessageProducer producer = session.createProducer(dest);
            try {
                send(producer, message);
            } finally {
                closeProducer(producer);
            }
            return null;
        });
    }

    public void send(MessageProducer producer, Message message) throws Exception {
        if (explicitQosEnabled) {
            producer.send(message, deliveryMode, priority, timeToLive);
        } else {
            producer.send(message);
        }
    }

    public Connection createConnection() throws Exception {
        return connectionFactory.createConnection();
    }

    public Session createSession(Connection connection) throws Exception {
        return connection.createSession(transacted, acknowledgeMode);
    }

}
