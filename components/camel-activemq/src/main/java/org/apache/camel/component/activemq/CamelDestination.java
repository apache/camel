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
package org.apache.camel.component.activemq;

import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.TopicPublisher;
import javax.jms.TopicSubscriber;

import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQSession;
import org.apache.activemq.CustomDestination;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Endpoint;
import org.apache.camel.component.jms.JmsBinding;
import org.apache.camel.component.jms.JmsEndpoint;

/**
 * 
 */
public class CamelDestination implements CustomDestination, CamelContextAware {
    private String uri;
    private Endpoint endpoint;
    private CamelContext camelContext;
    // add in dummy endpoint pending camel release with
    // https://issues.apache.org/activemq/browse/CAMEL-1982
    private JmsBinding binding = new JmsBinding(new JmsEndpoint());

    public CamelDestination() {
    }

    public CamelDestination(String uri) {
        this.uri = uri;
    }

    public String toString() {
        return uri.toString();
    }

    // CustomDestination interface
    // -----------------------------------------------------------------------
    public MessageConsumer createConsumer(ActiveMQSession session, String messageSelector) {
        return createConsumer(session, messageSelector, false);
    }

    public MessageConsumer createConsumer(ActiveMQSession session, String messageSelector, boolean noLocal) {
        return new CamelMessageConsumer(this, resolveEndpoint(session), session, messageSelector, noLocal);
    }

    public TopicSubscriber createSubscriber(ActiveMQSession session, String messageSelector, boolean noLocal) {
        return createDurableSubscriber(session, null, messageSelector, noLocal);
    }

    public TopicSubscriber createDurableSubscriber(ActiveMQSession session, String name, String messageSelector, boolean noLocal) {
        throw new UnsupportedOperationException("This destination is not a Topic: " + this);
    }

    public QueueReceiver createReceiver(ActiveMQSession session, String messageSelector) {
        throw new UnsupportedOperationException("This destination is not a Queue: " + this);
    }

    // Producers
    // -----------------------------------------------------------------------
    public MessageProducer createProducer(ActiveMQSession session) throws JMSException {
        return new CamelMessageProducer(this, resolveEndpoint(session), session);
    }

    public TopicPublisher createPublisher(ActiveMQSession session) throws JMSException {
        throw new UnsupportedOperationException("This destination is not a Topic: " + this);
    }

    public QueueSender createSender(ActiveMQSession session) throws JMSException {
        throw new UnsupportedOperationException("This destination is not a Queue: " + this);
    }

    // Properties
    // -----------------------------------------------------------------------

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public JmsBinding getBinding() {
        return binding;
    }

    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    // Implementation methods
    // -----------------------------------------------------------------------

    /**
     * Resolves the Camel Endpoint for this destination
     *
     * @return
     */
    protected Endpoint resolveEndpoint(ActiveMQSession session) {
        Endpoint answer = getEndpoint();
        if (answer == null) {
            answer = resolveCamelContext(session).getEndpoint(getUri());
            if (answer == null) {
                throw new IllegalArgumentException("No endpoint could be found for URI: " + getUri());
            }
        }
        return answer;
    }

    protected CamelContext resolveCamelContext(ActiveMQSession session) {
        CamelContext answer = getCamelContext();
        if (answer == null) {
            ActiveMQConnection connection = session.getConnection();
            if (connection instanceof CamelConnection) {
                CamelConnection camelConnection = (CamelConnection)connection;
                answer = camelConnection.getCamelContext();
            }
        }
        if (answer == null) {
            throw new IllegalArgumentException("No CamelContext has been configured");
        }
        return answer;
    }
}
