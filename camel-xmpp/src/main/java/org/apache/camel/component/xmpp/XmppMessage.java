/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.xmpp;

import org.apache.camel.impl.DefaultMessage;
import org.jivesoftware.smack.packet.Message;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Represents a {@link org.apache.camel.Message} for working with XMPP
 *
 * @version $Revision:520964 $
 */
public class XmppMessage extends DefaultMessage {
    private Message xmppMessage;

    public XmppMessage() {
        this(new Message());
    }

    public XmppMessage(Message jmsMessage) {
        this.xmppMessage = jmsMessage;
    }

    @Override
    public String toString() {
        if (xmppMessage != null) {
            return "XmppMessage: " + xmppMessage;
        }
        else {
            return "XmppMessage: " + getBody();
        }
    }

    @Override
    public XmppExchange getExchange() {
        return (XmppExchange) super.getExchange();
    }

    /**
     * Returns the underlying XMPP message
     *
     * @return the underlying XMPP message
     */
    public Message getXmppMessage() {
        return xmppMessage;
    }

    public void setXmppMessage(Message xmppMessage) {
        this.xmppMessage = xmppMessage;
    }

    public Object getHeader(String name) {
        return xmppMessage.getProperty(name);
    }

    @Override
    public void setHeader(String name, Object value) {
        if (value == null) {
            xmppMessage.deleteProperty(name);
        }
        else {
            xmppMessage.setProperty(name, value);
        }
    }

    @Override
    public Map<String, Object> getHeaders() {
        Map<String, Object> answer = new HashMap<String, Object>();
        Iterator iter = xmppMessage.getPropertyNames();
        while (iter.hasNext()) {
            String name = (String) iter.next();
            answer.put(name, xmppMessage.getProperty(name));
        }
        return answer;
    }

    @Override
    public XmppMessage newInstance() {
        return new XmppMessage();
    }

    @Override
    protected Object createBody() {
        if (xmppMessage != null) {
            return getExchange().getBinding().extractBodyFromXmpp(getExchange(), xmppMessage);
        }
        return null;
    }
}
