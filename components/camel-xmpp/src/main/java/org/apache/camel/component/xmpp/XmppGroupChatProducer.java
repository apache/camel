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
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
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
    private XMPPConnection connection;
    private MultiUserChat chat;
    private String room;

    public XmppGroupChatProducer(XmppEndpoint endpoint) throws XMPPException {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) {
        Message message = chat.createMessage();
        message.setTo(room);
        message.setFrom(endpoint.getUser());

        endpoint.getBinding().populateXmppMessage(message, exchange);
        try {
            // make sure we are connected
            if (!connection.isConnected()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reconnecting to: " + XmppEndpoint.getConnectionMessage(connection));
                }
                connection.connect();
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending XMPP message: " + message.getBody());
            }
            chat.sendMessage(message);
            // must invoke nextMessage to consume the response from the server
            // otherwise the client local queue will fill up (CAMEL-1467)
            chat.nextMessage();
        } catch (XMPPException e) {
            throw new RuntimeExchangeException("Cannot send XMPP message: " + message, exchange, e);
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (connection == null) {
            connection = endpoint.createConnection();
        }

        if (chat == null) {
            room = endpoint.resolveRoom(connection);
            chat = new MultiUserChat(connection, room);
            DiscussionHistory history = new DiscussionHistory();
            history.setMaxChars(0); // we do not want any historical messages
            chat.join(endpoint.getNickname(), null, history, SmackConfiguration.getPacketReplyTimeout());
            if (LOG.isInfoEnabled()) {
                LOG.info("Joined room: " + room + " as: " + endpoint.getNickname());
            }
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (chat != null) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Leaving room: " + room);
            }
            chat.leave();
            chat = null;
        }
        if (connection != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Disconnecting from: " + XmppEndpoint.getConnectionMessage(connection));
            }
            connection.disconnect();
            connection = null;
        }
        super.doStop();
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getRoom() {
        return room;
    }

}
