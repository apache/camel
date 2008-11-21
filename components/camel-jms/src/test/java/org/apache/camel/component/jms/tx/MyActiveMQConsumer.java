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
package org.apache.camel.component.jms.tx;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;


import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class MyActiveMQConsumer implements Runnable {
    public static final String ACTIVEMQ_BROKER_URI = "failover:tcp://localhost:61616";
    public static final String REQUEST_QUEUE = "request";
    private static final transient Log LOG = LogFactory.getLog(MyActiveMQConsumer.class);
    
    private Connection connection;
    
    public MyActiveMQConsumer() throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(ACTIVEMQ_BROKER_URI);
        connection = factory.createConnection();
    }
    
    public void close() {
        try {
            connection.close();
        } catch (JMSException e) {
            LOG.info("Get the exception " + e + ", when close the JMS connection.");
            
        }
    }

    public void run() {
        try {
            Destination requestDestination = ActiveMQDestination
                .createDestination(REQUEST_QUEUE, ActiveMQDestination.QUEUE_TYPE);

            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            MessageConsumer consumer = session.createConsumer(requestDestination);
            connection.start();

            int i = 0;

            while (true) {
                TextMessage msg = (TextMessage)consumer.receive(1000 * 100);
                if (msg == null) {
                    LOG.debug("Response timed out.");
                } else {
                    Destination replyDestination = msg.getJMSReplyTo();
                    String correlationId = msg.getJMSCorrelationID();
                    LOG.debug("replyDestination: " + replyDestination);
                    MessageProducer sender = session.createProducer(replyDestination);
                    LOG.debug("Request No. " + (++i));
                    LOG.debug("msg: " + msg);
                    TextMessage response = session.createTextMessage();
                    response.setText("I was here: " + msg.getText());
                    response.setJMSCorrelationID(correlationId);
                    LOG.debug("reponse: " + response);
                    sender.send(response);
                }
            }
        } catch (JMSException exception) {
            LOG.info("Get the exception [" + exception + "], stop receive message");
        }
        
    }

}
