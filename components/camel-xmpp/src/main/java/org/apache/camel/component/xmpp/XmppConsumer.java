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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.PacketTypeFilter;
import org.jivesoftware.smack.filter.ToContainsFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * A {@link org.apache.camel.Consumer Consumer} which listens to XMPP packets
 *
 * @version $Revision$
 */
public class XmppConsumer extends DefaultConsumer implements PacketListener, MessageListener {
    private static final transient Log LOG = LogFactory.getLog(XmppConsumer.class);
    private final XmppEndpoint endpoint;
    private MultiUserChat muc;
    private XMPPConnection connection;

    public XmppConsumer(XmppEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        connection = endpoint.createConnection();

        if (endpoint.getRoom() == null) {

            // if an existing chat session has been opened (for example by a producer) let's
            // just add a listener to that chat
            Chat privateChat = connection.getChatManager().getThreadChat(endpoint.getParticipant());

            if (privateChat != null) {
                LOG.debug("Adding listener to existing chat opened to " + privateChat.getParticipant());
                privateChat.addMessageListener(this);
            } else {                
                privateChat = connection.getChatManager().createChat(endpoint.getParticipant(), endpoint.getParticipant(), this);
                LOG.debug("Opening private chat to " + privateChat.getParticipant());
            }

        } else {
            // add the presence packet listener to the connection so we only get packets that concers us
            // we must add the listener before creating the muc
            final ToContainsFilter toFilter = new ToContainsFilter(endpoint.getParticipant());
            final AndFilter packetFilter = new AndFilter(new PacketTypeFilter(Presence.class), toFilter);
            connection.addPacketListener(this, packetFilter);

            muc = new MultiUserChat(connection, endpoint.resolveRoom(connection));
            muc.addMessageListener(this);
            DiscussionHistory history = new DiscussionHistory();
            history.setMaxChars(0); // we do not want any historical messages

            muc.join(endpoint.getNickname(), null, history, SmackConfiguration.getPacketReplyTimeout());
            if (LOG.isInfoEnabled()) {
                LOG.info("Joined room: " + muc.getRoom() + " as: " + endpoint.getNickname());
            }
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (muc != null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Leaving room: " + muc.getRoom());
            }
            muc.removeMessageListener(this);
            muc.leave();
            muc = null;
        }
        //the endpoint will clean up the connection
    }

    public void processPacket(Packet packet) {
        if (packet instanceof Message) {
            processMessage(null, (Message) packet);
        }
    }

    public void processMessage(Chat chat, Message message) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Recieved XMPP message: " + message.getBody());
        }

        Exchange exchange = endpoint.createExchange(message);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
    }

}
