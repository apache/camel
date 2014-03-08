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
import java.util.List;

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.BatchMessage;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.TransactionCommitStrategy;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.tx.DefaultTransactionCommitStrategy;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;

/**
 * A Camel Producer that provides the InOnly Exchange pattern..
 */
public class InOnlyProducer extends SjmsProducer {

    public InOnlyProducer(SjmsEndpoint endpoint) {
        super(endpoint);
    }

    /*
     * @see org.apache.camel.component.sjms.SjmsProducer#doCreateProducerModel()
     * @return
     * @throws Exception
     */
    @Override
    public MessageProducerResources doCreateProducerModel() throws Exception {
        MessageProducerResources answer = null;
        Connection conn = null;
        try {
            conn = getConnectionResource().borrowConnection();
            
            TransactionCommitStrategy commitStrategy = null;
            Session session = null;
            MessageProducer messageProducer = null;
            
            if (isEndpointTransacted()) {
                if (getCommitStrategy() != null) {
                    commitStrategy = getCommitStrategy();
                } else {
                    commitStrategy = new DefaultTransactionCommitStrategy();
                }
                session = conn.createSession(true, getAcknowledgeMode());
            } else {
                session = conn.createSession(false, getAcknowledgeMode());
            }
            if (isTopic()) {
                messageProducer = JmsObjectFactory.createMessageProducer(session, getDestinationName(), isTopic(), isPersistent(), getTtl());
            } else {
                messageProducer = JmsObjectFactory.createQueueProducer(session, getDestinationName());
            }
            answer = new MessageProducerResources(session, messageProducer, commitStrategy);
        } catch (Exception e) {
            log.error("Unable to create the MessageProducer: " + e.getLocalizedMessage());
        } finally {
            if (conn != null) {
                getConnectionResource().returnConnection(conn);
            }
        }
        return answer;
    }

    /*
     * @see
     * org.apache.camel.component.sjms.SjmsProducer#sendMessage(org.apache.camel.Exchange, org.apache.camel.AsyncCallback)
     * @param exchange
     * @param callback
     * @throws Exception
     */
    @Override
    public void sendMessage(Exchange exchange, AsyncCallback callback) throws Exception {
        List<Message> messages = new ArrayList<Message>();
        MessageProducerResources producer = getProducers().borrowObject();
        try {
            if (getProducers() != null) {
                if (exchange.getIn().getBody() != null) {
                    if (exchange.getIn().getBody() instanceof List) {
                        List<?> payload = (List<?>)exchange.getIn().getBody();
                        for (Object object : payload) {
                            Message message = null;
                            if (BatchMessage.class.isInstance(object)) {
                                BatchMessage<?> batchMessage = (BatchMessage<?>)object;
                                message = JmsMessageHelper.createMessage(producer.getSession(), batchMessage.getPayload(), batchMessage.getHeaders(), getSjmsEndpoint()
                                    .getJmsKeyFormatStrategy());
                            } else {
                                message = JmsMessageHelper.createMessage(producer.getSession(), object, exchange.getIn().getHeaders(), getSjmsEndpoint().getJmsKeyFormatStrategy());
                            }
                            messages.add(message);
                        }
                    } else {
                        Object payload = exchange.getIn().getBody();
                        Message message = JmsMessageHelper
                            .createMessage(producer.getSession(), payload, exchange.getIn().getHeaders(), getSjmsEndpoint().getJmsKeyFormatStrategy());
                        messages.add(message);
                    }
                }

                if (isEndpointTransacted()) {
                    exchange.getUnitOfWork().addSynchronization(new SessionTransactionSynchronization(producer.getSession(), producer.getCommitStrategy()));
                }
                for (Message message : messages) {
                    producer.getMessageProducer().send(message);
                }
            }
        } catch (Exception e) {
            exchange.setException(new Exception("Unable to complet sending the message: " + e.getLocalizedMessage()));
        } finally {
            getProducers().returnObject(producer);
            callback.done(isSynchronous());
        }
    }
}
