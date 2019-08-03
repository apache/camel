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

import java.io.File;
import java.util.Map;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a {@link org.apache.camel.Message} for working with JMS
 */
public class JmsMessage extends DefaultMessage {
    private static final Logger LOG = LoggerFactory.getLogger(JmsMessage.class);
    private Message jmsMessage;
    private Session jmsSession;
    private JmsBinding binding;

    public JmsMessage(Exchange exchange, Message jmsMessage, Session jmsSession, JmsBinding binding) {
        super(exchange);
        setJmsMessage(jmsMessage);
        setJmsSession(jmsSession);
        setBinding(binding);
    }

    @Override
    public String toString() {
        // do not print jmsMessage as there could be sensitive details
        if (jmsMessage != null) {
            try {
                return "JmsMessage[JmsMessageID: " + jmsMessage.getJMSMessageID() + "]";
            } catch (Throwable e) {
                // ignore
            }
        }
        return "JmsMessage@" + ObjectHelper.getIdentityHashCode(this);
    }

    @Override
    public void copyFrom(org.apache.camel.Message that) {
        if (that == this) {
            // the same instance so do not need to copy
            return;
        }

        // must initialize headers before we set the JmsMessage to avoid Camel
        // populating it before we do the copy
        getHeaders().clear();

        boolean copyMessageId = true;
        if (that instanceof JmsMessage) {
            JmsMessage thatMessage = (JmsMessage) that;
            this.jmsMessage = thatMessage.jmsMessage;
            if (this.jmsMessage != null) {
                // for performance lets not copy the messageID if we are a JMS message
                copyMessageId = false;
            }
        }
        if (copyMessageId) {
            setMessageId(that.getMessageId());
        }

        // cover over exchange if none has been assigned
        if (getExchange() == null) {
            setExchange(that.getExchange());
        }

        // copy body and fault flag
        setBody(that.getBody());

        // we have already cleared the headers
        if (that.hasHeaders()) {
            getHeaders().putAll(that.getHeaders());
        }
    }

    public JmsBinding getBinding() {
        if (binding == null) {
            binding = ExchangeHelper.getBinding(getExchange(), JmsBinding.class);
        }
        return binding;
    }

    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    /**
     * Returns the underlying JMS message
     */
    public Message getJmsMessage() {
        return jmsMessage;
    }

    public void setJmsMessage(Message jmsMessage) {
        if (jmsMessage != null) {
            try {
                setMessageId(jmsMessage.getJMSMessageID());
            } catch (JMSException e) {
                LOG.warn("Unable to retrieve JMSMessageID from JMS Message", e);
            }
        }
        this.jmsMessage = jmsMessage;
    }

    /**
     * Returns the underlying JMS session.
     * <p/>
     * This may be <tt>null</tt> if using {@link org.apache.camel.component.jms.JmsPollingConsumer},
     * or the broker component from Apache ActiveMQ 5.11.x or older.
     */
    public Session getJmsSession() {
        return jmsSession;
    }

    public void setJmsSession(Session jmsSession) {
        this.jmsSession = jmsSession;
    }

    @Override
    public void setBody(Object body) {
        super.setBody(body);
        if (body == null) {
            // preserver headers even if we set body to null
            ensureInitialHeaders();
            // remove underlying jmsMessage since we mutated body to null
            jmsMessage = null;
        }
    }

    @Override
    public Object getHeader(String name) {
        ensureInitialHeaders();
        return super.getHeader(name);
    }

    @Override
    public Map<String, Object> getHeaders() {
        ensureInitialHeaders();
        return super.getHeaders();
    }

    @Override
    public Object removeHeader(String name) {
        ensureInitialHeaders();
        return super.removeHeader(name);
    }

    @Override
    public void setHeaders(Map<String, Object> headers) {
        ensureInitialHeaders();
        super.setHeaders(headers);
    }

    @Override
    public void setHeader(String name, Object value) {
        ensureInitialHeaders();
        super.setHeader(name, value);
    }

    @Override
    public JmsMessage newInstance() {
        JmsMessage answer = new JmsMessage(null, null, null, binding);
        answer.setCamelContext(getCamelContext());
        return answer;
    }

    /**
     * Returns true if a new JMS message instance should be created to send to the next component
     */
    public boolean shouldCreateNewMessage() {
        return super.hasPopulatedHeaders();
    }

    /**
     * Ensure that the headers have been populated from the underlying JMS message
     * before we start mutating the headers
     */
    protected void ensureInitialHeaders() {
        if (jmsMessage != null && !hasPopulatedHeaders()) {
            // we have not populated headers so force this by creating
            // new headers and set it on super
            super.setHeaders(createHeaders());
        }
    }

    @Override
    protected Object createBody() {
        if (jmsMessage != null) {
            return getBinding().extractBodyFromJms(getExchange(), jmsMessage);
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (jmsMessage != null && map != null) {
            map.putAll(getBinding().extractHeadersFromJms(jmsMessage, getExchange()));
        }
    }

    @Override
    protected String createMessageId() {
        if (jmsMessage == null) {
            LOG.trace("No javax.jms.Message set so generating a new message id");
            return super.createMessageId();
        }
        try {
            String id = getDestinationAsString(jmsMessage.getJMSDestination());
            if (id != null) {
                id += jmsMessage.getJMSMessageID();
            } else {
                id = jmsMessage.getJMSMessageID();
            }
            return getSanitizedString(id);
        } catch (JMSException e) {
            throw new RuntimeExchangeException("Unable to retrieve JMSMessageID from JMS Message", getExchange(), e);
        }
    }

    @Override
    protected Boolean isTransactedRedelivered() {
        if (jmsMessage != null) {
            return JmsMessageHelper.getJMSRedelivered(jmsMessage);
        } else {
            return null;
        }
    }

    private String getDestinationAsString(Destination destination) throws JMSException {
        String result = null;
        if (destination == null) {
            result = "null destination!" + File.separator;
        } else if (destination instanceof Topic) {
            result = "topic" + File.separator + ((Topic) destination).getTopicName() + File.separator;
        } else if (destination instanceof Queue) {
            result = "queue" + File.separator + ((Queue) destination).getQueueName() + File.separator;
        }
        return result;
    }

    private String getSanitizedString(Object value) {
        return value != null ? value.toString().replaceAll("[^a-zA-Z0-9\\.\\_\\-]", "_") : "";
    }

}
