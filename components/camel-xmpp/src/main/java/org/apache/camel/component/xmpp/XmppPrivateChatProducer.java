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
import org.apache.camel.util.StringHelper;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XmppPrivateChatProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(XmppPrivateChatProducer.class);
    private final XmppEndpoint endpoint;
    private XMPPTCPConnection connection;
    private final String participant;

    public XmppPrivateChatProducer(XmppEndpoint endpoint, String participant) {
        super(endpoint);
        this.endpoint = endpoint;
        this.participant = participant;
        StringHelper.notEmpty(participant, "participant");

        LOG.debug("Creating XmppPrivateChatProducer to participant {}", participant);
    }

    @Override
    public void process(Exchange exchange) {

        // make sure we are connected
        try {
            if (connection == null) {
                connection = endpoint.createConnection();
            }

            if (!connection.isConnected()) {
                this.reconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not connect to XMPP server.", e);
        }

        String participant = endpoint.getParticipant();
        String thread = endpoint.getChatId();
        if (participant == null) {
            participant = getParticipant();
        } else {
            thread = "Chat:" + participant + ":" + endpoint.getUser();
        }

        Message message = new Message();
        try {
            message.setTo(JidCreate.from(participant));
            message.setThread(thread);
            message.setType(Message.Type.normal);

            ChatManager chatManager = ChatManager.getInstanceFor(connection);
            Chat chat = getOrCreateChat(chatManager, participant);

            endpoint.getBinding().populateXmppMessage(message, exchange);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending XMPP message to {} from {} : {}", participant, endpoint.getUser(), message.getBody());
            }
            chat.send(message);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Could not send XMPP message to " + participant + " from " + endpoint.getUser() + " : " + message
                    + " to: " + XmppEndpoint.getConnectionMessage(connection), exchange, e);
        }
    }

    private Chat getOrCreateChat(ChatManager chatManager, final String participant) throws XmppStringprepException {
        // this starts a new chat or retrieves the pre-existing one in a threadsafe manner
        return chatManager.chatWith(JidCreate.entityBareFrom(participant));
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
                    throw new RuntimeException("Could not establish connection to XMPP server: " + endpoint.getConnectionDescription(), e);
                } else {
                    LOG.warn("Could not connect to XMPP server: {} Producer will attempt lazy connection when needed.", e.getMessage());
                }
            }
        }
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
        connection = null;
        super.doStop();
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getParticipant() {
        return participant;
    }
}
