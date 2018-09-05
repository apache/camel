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

import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.ExchangeHelper;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Stanza;

/**
 * Represents a {@link org.apache.camel.Message} for working with XMPP
 */
public class XmppMessage extends DefaultMessage {
    private Stanza xmppPacket;

    public XmppMessage() {
        this(new Message());
    }

    public XmppMessage(Message message) {
        this.xmppPacket = message;
    }

    public XmppMessage(Stanza stanza) {
        this.xmppPacket = stanza;
    }

    @Override
    public String toString() {
        if (xmppPacket != null) {
            return "XmppMessage: " + xmppPacket;
        } else {
            return "XmppMessage: " + getBody();
        }
    }

    /**
     * Returns the underlying XMPP message
     */
    public Message getXmppMessage() {
        return (xmppPacket instanceof Message) ? (Message) xmppPacket : null;
    }

    public void setXmppMessage(Message xmppMessage) {
        this.xmppPacket = xmppMessage;
    }

    /**
     * Returns the underlying XMPP packet
     */
    public Stanza getXmppPacket() {
        return xmppPacket;
    }

    public void setXmppPacket(Stanza xmppPacket) {
        this.xmppPacket = xmppPacket;
    }

    @Override
    public XmppMessage newInstance() {
        XmppMessage answer = new XmppMessage();
        answer.setCamelContext(getCamelContext());
        return answer;
    }

    @Override
    protected Object createBody() {
        if (xmppPacket != null) {
            XmppBinding binding = ExchangeHelper.getBinding(getExchange(), XmppBinding.class);
            if (binding != null) {
                return (getHeader(XmppConstants.DOC_HEADER) == null) ? binding.extractBodyFromXmpp(getExchange(), xmppPacket) : getHeader(XmppConstants.DOC_HEADER);
            }
        }
        return null;
    }

    @Override
    protected void populateInitialHeaders(Map<String, Object> map) {
        if (xmppPacket != null) {
            XmppBinding binding = ExchangeHelper.getBinding(getExchange(), XmppBinding.class);
            if (binding != null) {
                map.putAll(binding.extractHeadersFromXmpp(xmppPacket, getExchange()));
            }
        }
    }
}
