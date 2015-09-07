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
package org.apache.camel.component.sjms.producer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.BatchMessage;
import org.apache.camel.component.sjms.MessageProducerResources;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.TransactionCommitStrategy;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.tx.DefaultTransactionCommitStrategy;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;

/**
 * A Camel Producer that provides the InOnly Exchange pattern..
 */
public class InOnlyProducer extends SjmsProducer {

    public InOnlyProducer(final Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public MessageProducerResources doCreateProducerModel() throws Exception {
        MessageProducerResources answer;
        Connection conn = getConnectionResource().borrowConnection();
        try {
            TransactionCommitStrategy commitStrategy = null;
            if (isEndpointTransacted()) {
                commitStrategy = getCommitStrategy() == null ? new DefaultTransactionCommitStrategy() : getCommitStrategy();
            }
            Session session = conn.createSession(isEndpointTransacted(), getAcknowledgeMode());
            Destination destination = getEndpoint().getDestinationCreationStrategy().createDestination(session, getDestinationName(), isTopic());
            MessageProducer messageProducer = JmsObjectFactory.createMessageProducer(session, destination, isPersistent(), getTtl());

            answer = new MessageProducerResources(session, messageProducer, commitStrategy);

        } catch (Exception e) {
            log.error("Unable to create the MessageProducer", e);
            throw e;
        } finally {
            getConnectionResource().returnConnection(conn);
        }
        return answer;
    }

    @Override
    public void sendMessage(final Exchange exchange, final AsyncCallback callback, final MessageProducerResources producer) throws Exception {
        try {
            Collection<Message> messages = new ArrayList<Message>(1);
            if (exchange.getIn().getBody() != null) {
                if (exchange.getIn().getBody() instanceof List) {
                    Iterable<?> payload = (Iterable<?>) exchange.getIn().getBody();
                    for (final Object object : payload) {
                        Message message;
                        if (BatchMessage.class.isInstance(object)) {
                            BatchMessage<?> batchMessage = (BatchMessage<?>) object;
                            message = getEndpoint().getBinding().makeJmsMessage(exchange, batchMessage.getPayload(), batchMessage.getHeaders(), producer.getSession(), null);
                        } else {
                            message = getEndpoint().getBinding().makeJmsMessage(exchange, object, exchange.getIn().getHeaders(), producer.getSession(), null);
                        }
                        messages.add(message);
                    }
                } else {
                    Message message = getEndpoint().getBinding().makeJmsMessage(exchange, producer.getSession());
                    messages.add(message);
                }
            } else {
                Message message = getEndpoint().getBinding().makeJmsMessage(exchange, producer.getSession());
                messages.add(message);
            }

            if (isEndpointTransacted()) {
                exchange.getUnitOfWork().addSynchronization(new SessionTransactionSynchronization(producer.getSession(), producer.getCommitStrategy()));
            }
            for (final Message message : messages) {
                producer.getMessageProducer().send(message);
            }
        } catch (Exception e) {
            exchange.setException(new Exception("Unable to complete sending the message: ", e));
        } finally {
            if (producer != null) {
                getProducers().returnObject(producer);
            }
            callback.done(isSynchronous());
        }
    }

}
