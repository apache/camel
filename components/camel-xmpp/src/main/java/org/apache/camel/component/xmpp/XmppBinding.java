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
package org.apache.camel.component.xmpp;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.jivesoftware.smack.packet.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link XmppMessage} to and from a
 * XMPP {@link Message}
 *
 * @version 
 */
public class XmppBinding {

    private static final Logger LOG = LoggerFactory.getLogger(XmppBinding.class);
    private HeaderFilterStrategy headerFilterStrategy;

    public XmppBinding() {
        this.headerFilterStrategy = new DefaultHeaderFilterStrategy();
    }

    public XmppBinding(HeaderFilterStrategy headerFilterStrategy) {
        ObjectHelper.notNull(headerFilterStrategy, "headerFilterStrategy");
        this.headerFilterStrategy = headerFilterStrategy;
    }

    /**
     * Populates the given XMPP message from the inbound exchange
     */
    public void populateXmppMessage(Message message, Exchange exchange) {
        message.setBody(exchange.getIn().getBody(String.class));

        Set<Map.Entry<String, Object>> entries = exchange.getIn().getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (!headerFilterStrategy.applyFilterToCamelHeaders(name, value, exchange)) {

                if ("subject".equalsIgnoreCase(name)) {
                    // special for subject
                    String subject = exchange.getContext().getTypeConverter().convertTo(String.class, value);
                    message.setSubject(subject);
                } else if ("language".equalsIgnoreCase(name)) {
                    // special for language
                    String language = exchange.getContext().getTypeConverter().convertTo(String.class, value);
                    message.setLanguage(language);
                } else {
                    try {
                        message.setProperty(name, value);
                        LOG.trace("Added property name: {} value: {}", name, value.toString());
                    } catch (IllegalArgumentException iae) {
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Cannot add property " + name + " to XMPP message due: ", iae);
                        }
                    }
                }
            }
        }
        
        String id = exchange.getExchangeId();
        if (id != null) {
            message.setProperty("exchangeId", id);
        }
    }

    /**
     * Extracts the body from the XMPP message
     */
    public Object extractBodyFromXmpp(Exchange exchange, Message message) {
        return message.getBody();
    }

    public Map<String, Object> extractHeadersFromXmpp(Message xmppMessage, Exchange exchange) {
        Map<String, Object> answer = new HashMap<String, Object>();

        for (String name : xmppMessage.getPropertyNames()) {
            Object value = xmppMessage.getProperty(name);

            if (!headerFilterStrategy.applyFilterToExternalHeaders(name, value, exchange)) {
                answer.put(name, value);
            }
        }

        answer.put(XmppConstants.MESSAGE_TYPE, xmppMessage.getType());
        answer.put(XmppConstants.SUBJECT, xmppMessage.getSubject());
        answer.put(XmppConstants.THREAD_ID, xmppMessage.getThread());
        answer.put(XmppConstants.FROM, xmppMessage.getFrom());
        answer.put(XmppConstants.PACKET_ID, xmppMessage.getPacketID());
        answer.put(XmppConstants.TO, xmppMessage.getTo());
                
        return answer;
    }
}
