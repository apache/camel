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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.MessageCreator;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Session;

/**
 * @version $Revision:520964 $
 */
public class JmsEndpoint extends DefaultEndpoint<JmsExchange> {
    private static final Log log = LogFactory.getLog(JmsEndpoint.class);
    private JmsBinding binding;
    private JmsTemplate template;
    private String destination;

    public JmsEndpoint(String endpointUri, CamelContext container, String destination, JmsTemplate template) {
        super(endpointUri, container);
        this.destination = destination;
        this.template = template;
    }

    public Producer<JmsExchange> createProducer() throws Exception {
        return startService(new JmsProducer(this, template));
    }

    public Consumer<JmsExchange> createConsumer(Processor<JmsExchange> processor) throws Exception {
        AbstractMessageListenerContainer listenerContainer = createMessageListenerContainer(template);
        listenerContainer.setDestinationName(destination);
        listenerContainer.setPubSubDomain(template.isPubSubDomain());
        listenerContainer.setConnectionFactory(template.getConnectionFactory());

        // TODO support optional parameters
        // selector
        // messageConverter
        // durableSubscriberName


        return startService(new JmsConsumer(this, processor, listenerContainer));
    }

    public JmsExchange createExchange() {
        return new JmsExchange(getContext(), getBinding());
    }

    public JmsExchange createExchange(Message message) {
        return new JmsExchange(getContext(), getBinding(), message);
    }

    // Properties
    //-------------------------------------------------------------------------
    public JmsBinding getBinding() {
        if (binding == null) {
            binding = new JmsBinding();
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS message
     *
     * @param binding the binding to use
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    public JmsOperations getTemplate() {
        return template;
    }

    public String getDestination() {
        return destination;
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    protected AbstractMessageListenerContainer createMessageListenerContainer(JmsTemplate template) {
        // TODO use an enum to auto-switch container types?

        //return new SimpleMessageListenerContainer();
        return new DefaultMessageListenerContainer();
    }

}
