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

import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * A {@link org.apache.camel.Consumer Consumer} which listens to XMPP packets
 *
 * @version $Revision$
 */
public class XmppConsumer extends DefaultConsumer<XmppExchange> implements PacketListener, MessageListener {
    private static final transient Log LOG = LogFactory.getLog(XmppConsumer.class);
    private final XmppEndpoint endpoint;
    private Chat privateChat;
    private MultiUserChat muc;

    public XmppConsumer(XmppEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        if (endpoint.getRoom() == null) {
            privateChat = endpoint.getConnection().getChatManager().createChat(endpoint.getParticipant(), this);
            LOG.info("Open chat to " + privateChat.getParticipant());
        } else {
            muc = new MultiUserChat(endpoint.getConnection(), endpoint.resolveRoom());
            muc.addMessageListener(this);
            DiscussionHistory history = new DiscussionHistory();
            history.setMaxChars(0); // we do not want any historical messages
            muc.join(endpoint.getNickname(), null, history, SmackConfiguration.getPacketReplyTimeout());
            LOG.info("Joined room: " + muc.getRoom());
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (muc != null) {
            muc.leave();
            muc = null;
        }
    }

    public void processPacket(Packet packet) {
        Message message = (Message)packet;

        if (LOG.isDebugEnabled()) {
            LOG.debug("Recieved XMPP message: " + message.getBody());
        }

        XmppExchange exchange = endpoint.createExchange(message);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            LOG.error("Error while processing message", e);
        }
    }

    public void processMessage(Chat chat, Message message) {
        processPacket(message);
    }

}
