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
package org.apache.camel.component.jms;

import javax.jms.Message;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;

/**
 * Represents an {@link Exchange} for working with JMS messages while exposing the inbound and outbound JMS {@link Message}
 * objects via {@link #getInMessage()} and {@link #getOutMessage()}
 *
 * @version $Revision:520964 $
 */
public class JmsExchange extends DefaultExchange {
    private JmsBinding binding;

    public JmsExchange(Endpoint endpoint, ExchangePattern pattern, JmsBinding binding) {
        super(endpoint, pattern);
        this.binding = binding;
    }

    public JmsExchange(Endpoint endpoint, ExchangePattern pattern, JmsBinding binding, Message message) {
        this(endpoint, pattern, binding);
        setIn(new JmsMessage(message));
    }

    public JmsExchange(DefaultExchange parent, JmsBinding binding) {
        super(parent);
        this.binding = binding;
    }

    @Override
    public JmsMessage getIn() {
        return (JmsMessage) super.getIn();
    }

    @Override
    public JmsMessage getOut() {
        return (JmsMessage) super.getOut();
    }

    @Override
    public JmsMessage getOut(boolean lazyCreate) {
        return (JmsMessage) super.getOut(lazyCreate);
    }

    @Override
    public JmsMessage getFault() {
        return (JmsMessage) super.getFault();
    }

    @Override
    public JmsMessage getFault(boolean lazyCreate) {
        return (JmsMessage) super.getFault(lazyCreate);
    }

    public JmsBinding getBinding() {
        return binding;
    }

    @Override
    public Exchange newInstance() {
        return new JmsExchange(this, binding);
    }

    // Expose JMS APIs
    //-------------------------------------------------------------------------

    /**
     * Return the underlying JMS In message
     *
     * @return the JMS In message
     */
    public Message getInMessage() {
        return getIn().getJmsMessage();
    }

    /**
     * Return the underlying JMS Out message
     *
     * @return the JMS out message
     */
    public Message getOutMessage() {
        return getOut().getJmsMessage();
    }

    /**
     * Return the underlying JMS Fault message
     *
     * @return the JMS fault message
     */
    public Message getFaultMessage() {
        return getFault().getJmsMessage();
    }

    // Implementation methods
    //-------------------------------------------------------------------------

    @Override
    protected JmsMessage createInMessage() {
        return new JmsMessage();
    }

    @Override
    protected JmsMessage createOutMessage() {
        return new JmsMessage();
    }

    @Override
    protected org.apache.camel.Message createFaultMessage() {
        return new JmsMessage();
    }
}
