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
package org.apache.camel.component.jms.issues;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.IllegalStateException;
import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageProducer;
import jakarta.jms.Session;

import org.apache.camel.component.jms.JmsConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.SessionCallback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JmsProducerCommitTest {

    private static final String EXCEPTION_MESSAGE = "The Session is closed";

    @Test
    public void shouldThrowIllegalStateException() throws Exception {

        /* Message */
        Message message = mock(Message.class);

        /* Session */
        Session session = mock(Session.class);
        when(session.getTransacted()).thenReturn(true);
        doThrow(new IllegalStateException(EXCEPTION_MESSAGE)).when(session).commit();

        /* JmsTemplate */
        MessageProducer messageProducer = Mockito.mock(MessageProducer.class);
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        JmsConfiguration configuration = mock(JmsConfiguration.class);
        CamelJmsTemplateTest jmsTemplate = Mockito.spy(new CamelJmsTemplateTest(configuration, connectionFactory, session));
        doReturn(messageProducer).when(jmsTemplate).createProducer(isA(Session.class), isA(Destination.class));

        /* Run Test */
        org.springframework.jms.IllegalStateException thrown
                = assertThrows(org.springframework.jms.IllegalStateException.class, () -> {
                    jmsTemplate.send("test", s -> {
                        return message;
                    });
                });
        assertEquals(EXCEPTION_MESSAGE, thrown.getMessage());
        assertEquals(thrown.getCause().getClass(), IllegalStateException.class);

    }

    static class CamelJmsTemplateTest extends JmsConfiguration.CamelJmsTemplate {

        Session session;

        public CamelJmsTemplateTest(JmsConfiguration config, ConnectionFactory connectionFactory, Session session) {
            super(config, connectionFactory);
            this.session = session;
        }

        @Override
        protected MessageProducer createProducer(Session session, Destination destination) throws JMSException {
            MessageProducer producer = doCreateProducer(session, destination);
            if (!isMessageIdEnabled()) {
                producer.setDisableMessageID(true);
            }
            if (!isMessageTimestampEnabled()) {
                producer.setDisableMessageTimestamp(true);
            }
            return producer;
        }

        @Override
        protected boolean isSessionLocallyTransacted(Session session) {
            return true;
        }

        public <T> T execute(SessionCallback<T> action, boolean startConnection) throws JmsException {
            try {
                return action.doInJms(session);
            } catch (JMSException ex) {
                throw convertJmsAccessException(ex);
            }
        }

        @Override
        protected void doSend(MessageProducer producer, Message message) {
        }
    }
}
