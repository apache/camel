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

import javax.jms.Connection;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.SjmsEndpoint;
import org.apache.camel.component.sjms.SjmsProducer;
import org.apache.camel.component.sjms.jms.JmsMessageHelper;
import org.apache.camel.component.sjms.jms.JmsObjectFactory;
import org.apache.camel.component.sjms.tx.SessionTransactionSynchronization;

/**
 * The InOnlyProducer is responsible for publishing messages to the JMS
 * {@link Destination} for the value specified in the destinationName field.
 */
public class InOnlyProducer extends SjmsProducer {
    
    public InOnlyProducer(SjmsEndpoint endpoint) {
        super(endpoint);
    }
    
    /*
     * @see org.apache.camel.component.sjms.SjmsProducer#doCreateProducerModel()
     *
     * @return
     * @throws Exception
     */
    public MessageProducerResources doCreateProducerModel() throws Exception {
        Connection conn = getConnectionResource().borrowConnection();
        Session session = null;
        if (isEndpointTransacted()) {
            session = conn.createSession(true, getAcknowledgeMode());
        } else {
            session = conn.createSession(false, getAcknowledgeMode());
        }
        MessageProducer messageProducer = null;
        if (isTopic()) {
            messageProducer = JmsObjectFactory.createMessageProducer(session, getDestinationName(), isTopic(), isPersistent(), getTtl());
        } else {
            messageProducer = JmsObjectFactory.createQueueProducer(session, getDestinationName());
        }
        getConnectionResource().returnConnection(conn);
        return new MessageProducerResources(session, messageProducer);
    }
    
    @Override
    public void sendMessage(Exchange exchange, AsyncCallback callback) throws Exception {
        if (getProducers() != null) {
            MessageProducerResources producer = getProducers().borrowObject();
            
            if (isEndpointTransacted()) {
                exchange.getUnitOfWork().addSynchronization(new SessionTransactionSynchronization(producer.getSession()));
            }
            
            Message message = JmsMessageHelper.createMessage(exchange, producer.getSession());
            producer.getMessageProducer().send(message);
            getProducers().returnObject(producer);
            callback.done(isSynchronous());
        }
    }
}
