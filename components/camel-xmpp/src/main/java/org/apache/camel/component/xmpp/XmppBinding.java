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

import java.util.Map;
import java.util.Set;

import org.apache.camel.Exchange;

import org.jivesoftware.smack.packet.Message;

/**
 * A Strategy used to convert between a Camel {@link XmppExchange} and {@link XmppMessage} to and from a
 * XMPP {@link Message}
 *
 * @version $Revision$
 */
public class XmppBinding {
    /**
     * Populates the given XMPP message from the inbound exchange
     */
    public void populateXmppMessage(Message message, Exchange exchange) {
        message.setBody(exchange.getIn().getBody(String.class));

        Set<Map.Entry<String, Object>> entries = exchange.getIn().getHeaders().entrySet();
        for (Map.Entry<String, Object> entry : entries) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (shouldOutputHeader(exchange, name, value)) {
                message.setProperty(name, value);
            }
        }
        String id = exchange.getExchangeId();
        if (id != null) {
            message.setProperty("exchangeId", id);
        }
    }

    /**
     * Extracts the body from the XMPP message
     *
     * @param exchange
     * @param message
     */
    public Object extractBodyFromXmpp(XmppExchange exchange, Message message) {
        return message.getBody();
    }

    /**
     * Strategy to allow filtering of headers which are put on the XMPP message
     */
    protected boolean shouldOutputHeader(Exchange exchange, String headerName, Object headerValue) {
        return true;
    }
}
