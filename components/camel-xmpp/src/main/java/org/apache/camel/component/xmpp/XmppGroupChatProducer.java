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

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * @version $Revision$
 */
public class XmppGroupChatProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(XmppGroupChatProducer.class);
    private final XmppEndpoint endpoint;
    private final String room;
    private MultiUserChat chat;

    public XmppGroupChatProducer(XmppEndpoint endpoint) throws XMPPException, CamelException {
        super(endpoint);
        this.endpoint = endpoint;
        this.room = endpoint.resolveRoom();
        if (room == null) {
            throw new IllegalArgumentException("No room property specified");
        }
    }

    public void process(Exchange exchange) {
        // TODO it would be nice if we could reuse the message from the exchange
        Message message = chat.createMessage();
        message.setTo(room);
        message.setFrom(endpoint.getUser());

        endpoint.getBinding().populateXmppMessage(message, exchange);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Sending XMPP message: " + message.getBody());
        }
        try {
            chat.sendMessage(message);
        } catch (XMPPException e) {
            throw new RuntimeXmppException(e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (chat == null) {
            chat = new MultiUserChat(endpoint.getConnection(), room);
            DiscussionHistory history = new DiscussionHistory();
            history.setMaxChars(0); // we do not want any historical messages
            chat.join(this.endpoint.getNickname(), null, history, SmackConfiguration.getPacketReplyTimeout());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (chat != null) {
            chat.leave();
            chat = null;
        }
        super.doStop();
    }

    // Properties
    // -------------------------------------------------------------------------
    public MultiUserChat getChat() {
        return chat;
    }

    public void setChat(MultiUserChat chat) {
        this.chat = chat;
    }

    public String getRoom() {
        return room;
    }
}
