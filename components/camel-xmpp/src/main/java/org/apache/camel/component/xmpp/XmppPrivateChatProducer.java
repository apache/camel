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
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

/**
 * @version $Revision$
 */
public class XmppPrivateChatProducer extends DefaultProducer {
    private static final transient Log LOG = LogFactory.getLog(XmppPrivateChatProducer.class);
    private final XmppEndpoint endpoint;
    private XMPPConnection connection;
    private final String participant;

    public XmppPrivateChatProducer(XmppEndpoint endpoint, String participant) {
        super(endpoint);
        this.endpoint = endpoint;
        this.participant = participant;
        ObjectHelper.notEmpty(participant, "participant");

        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating XmppPrivateChatProducer to participant " + participant);
        }
    }

    public void process(Exchange exchange) {
        try {
            if (connection == null) {
                connection = endpoint.createConnection();
            }

            // make sure we are connected
            if (!connection.isConnected()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Reconnecting to: " + XmppEndpoint.getConnectionMessage(connection));
                }
                connection.connect();
            }
        } catch (XMPPException e) {
            throw new RuntimeExchangeException("Cannot connect to: "
                    + XmppEndpoint.getConnectionMessage(connection), exchange, e);
        }

        ChatManager chatManager = connection.getChatManager();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Looking for existing chat instance with thread ID " + endpoint.getChatId());
        }
        Chat chat = chatManager.getThreadChat(endpoint.getChatId());
        if (chat == null) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Creating new chat instance with thread ID " + endpoint.getChatId());
            }
            chat = chatManager.createChat(getParticipant(), endpoint.getChatId(), new MessageListener() {
                public void processMessage(Chat chat, Message message) {
                    // not here to do conversation
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Received and discarding message from " + getParticipant() + " : " + message.getBody());
                    }
                }
            });
        }

        Message message = null;
        try {
            message = new Message();
            message.setTo(getParticipant());
            message.setThread(endpoint.getChatId());
            message.setType(Message.Type.normal);

            endpoint.getBinding().populateXmppMessage(message, exchange);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Sending XMPP message to " + endpoint.getParticipant() + " from " + endpoint.getUser() + " : " + message.getBody());
            }
            chat.sendMessage(message);
        } catch (XMPPException xmppe) {
            throw new RuntimeExchangeException("Cannot send XMPP message: to " + endpoint.getParticipant() + " from " + endpoint.getUser() + " : " + message
                    + " to: " + XmppEndpoint.getConnectionMessage(connection), exchange, xmppe);
        } catch (Exception e) {
            throw new RuntimeExchangeException("Cannot send XMPP message to " + endpoint.getParticipant() + " from " + endpoint.getUser() + " : " + message
                    + " to: " + XmppEndpoint.getConnectionMessage(connection), exchange, e);
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public String getParticipant() {
        return participant;
    }
}
