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
package org.apache.camel.component.xmpp;

import java.io.IOException;

import org.apache.camel.Exchange;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.support.DefaultProducer;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppGroupChatProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(XmppGroupChatProducer.class);
    private final XmppEndpoint endpoint;
    private XMPPTCPConnection connection;
    private MultiUserChat chat;
    private String room;

    public XmppGroupChatProducer(XmppEndpoint endpoint) throws XMPPException {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) {
        if (connection == null) {
            try {
                connection = endpoint.createConnection();
            } catch (Exception e) {
                throw new RuntimeExchangeException("Could not connect to XMPP server.", exchange, e);
            }
        }

        if (chat == null) {
            try {
                initializeChat();
            } catch (Exception e) {
                throw new RuntimeExchangeException("Could not initialize XMPP chat.", exchange, e);
            }
        }

        Message message = chat.createMessage();
        try {
            message.setTo(JidCreate.from(room));
            message.setFrom(JidCreate.from(endpoint.getUser()));

            endpoint.getBinding().populateXmppMessage(message, exchange);

            // make sure we are connected
            if (!connection.isConnected()) {
                this.reconnect();
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending XMPP message: {}", message.getBody());
            }
            chat.sendMessage(message);
            // must invoke nextMessage to consume the response from the server
            // otherwise the client local queue will fill up (CAMEL-1467)
            chat.pollMessage();
        } catch (Exception e) {
            throw new RuntimeExchangeException("Could not send XMPP message: " + message, exchange, e);
        }
    }

    private synchronized void reconnect() throws InterruptedException, IOException, SmackException, XMPPException {
        if (!connection.isConnected()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Reconnecting to: {}", XmppEndpoint.getConnectionMessage(connection));
            }
            connection.connect();
        }
    }

    @Override
    protected void doStart() throws Exception {
        if (connection == null) {
            try {
                connection = endpoint.createConnection();
            } catch (SmackException e) {
                if (endpoint.isTestConnectionOnStartup()) {
                    throw new RuntimeException("Could not connect to XMPP server:  " + endpoint.getConnectionDescription(), e);
                } else {
                    LOG.warn("Could not connect to XMPP server. {}  Producer will attempt lazy connection when needed.", e.getMessage());
                }
            }
        }

        if (chat == null && connection != null) {
            initializeChat();
        }

        super.doStart();
    }

    protected synchronized void initializeChat() throws InterruptedException, SmackException, XMPPException, XmppStringprepException {
        if (chat == null) {
            room = endpoint.resolveRoom(connection);
            String roomPassword = endpoint.getRoomPassword();
            MultiUserChatManager chatManager = MultiUserChatManager.getInstanceFor(connection);
            chat = chatManager.getMultiUserChat(JidCreate.entityBareFrom(room));
            MucEnterConfiguration.Builder mucc = chat.getEnterConfigurationBuilder(Resourcepart.from(endpoint.getNickname()))
                    .requestNoHistory();
            if (roomPassword != null) {
                mucc.withPassword(roomPassword);
            }
            chat.join(mucc.build());
            LOG.info("Joined room: {} as: {}", room, endpoint.getNickname());
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (chat != null) {
            LOG.info("Leaving room: {}", room);
            chat.leave();
        }
        chat = null;

        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
        connection = null;

        super.doStop();
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getRoom() {
        return room;
    }

}
