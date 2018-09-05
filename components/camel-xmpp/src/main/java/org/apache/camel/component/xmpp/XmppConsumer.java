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

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.util.URISupport;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.chat2.IncomingChatMessageListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.filter.OrFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MucEnterConfiguration;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatException;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Resourcepart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Consumer Consumer} which listens to XMPP packets
 */
public class XmppConsumer extends DefaultConsumer implements IncomingChatMessageListener, MessageListener, StanzaListener {
    private static final Logger LOG = LoggerFactory.getLogger(XmppConsumer.class);
    private final XmppEndpoint endpoint;
    private MultiUserChat muc;
    private Chat privateChat;
    private ChatManager chatManager;
    private XMPPTCPConnection connection;
    private ScheduledExecutorService scheduledExecutor;

    public XmppConsumer(XmppEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        try {
            connection = endpoint.createConnection();
        } catch (SmackException e) {
            if (endpoint.isTestConnectionOnStartup()) {
                throw new RuntimeException("Could not connect to XMPP server.", e);
            } else {
                LOG.warn(e.getMessage());
                if (getExceptionHandler() != null) {
                    getExceptionHandler().handleException(e.getMessage(), e);
                }
                scheduleDelayedStart();
                return;
            }
        }

        chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addIncomingListener(this);

        OrFilter pubsubPacketFilter = new OrFilter();
        if (endpoint.isPubsub()) {
            //xep-0060: pubsub#notification_type can be 'headline' or 'normal'
            pubsubPacketFilter.addFilter(MessageTypeFilter.HEADLINE);
            pubsubPacketFilter.addFilter(MessageTypeFilter.NORMAL);
            connection.addSyncStanzaListener(this, pubsubPacketFilter);
        }

        if (endpoint.getRoom() == null) {
            privateChat = chatManager.chatWith(JidCreate.entityBareFrom(endpoint.getChatId()));
        } else {
            // add the presence packet listener to the connection so we only get packets that concerns us
            // we must add the listener before creating the muc

            final AndFilter packetFilter = new AndFilter(new StanzaTypeFilter(Presence.class));
            connection.addSyncStanzaListener(this, packetFilter);
            MultiUserChatManager mucm = MultiUserChatManager.getInstanceFor(connection);
            muc = mucm.getMultiUserChat(JidCreate.entityBareFrom(endpoint.resolveRoom(connection)));
            muc.addMessageListener(this);
            MucEnterConfiguration mucc = muc.getEnterConfigurationBuilder(Resourcepart.from(endpoint.getNickname()))
                    .requestNoHistory()
                    .build();
            muc.join(mucc);
            LOG.info("Joined room: {} as: {}", muc.getRoom(), endpoint.getNickname());
        }

        this.startRobustConnectionMonitor();
        super.doStart();
    }

    protected void scheduleDelayedStart() throws Exception {
        Runnable startRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    doStart();
                } catch (Exception e) {
                    LOG.warn("Ignoring an exception caught in the startup connection poller thread.", e);
                }
            }
        };
        LOG.info("Delaying XMPP consumer startup for endpoint {}. Trying again in {} seconds.",
                URISupport.sanitizeUri(endpoint.getEndpointUri()), endpoint.getConnectionPollDelay());
        getExecutor().schedule(startRunnable, endpoint.getConnectionPollDelay(), TimeUnit.SECONDS);
    }

    private void startRobustConnectionMonitor() throws Exception {
        Runnable connectionCheckRunnable = new Runnable() {
            @Override
            public void run() {
                try {
                    checkConnection();
                } catch (Exception e) {
                    LOG.warn("Ignoring an exception caught in the connection poller thread.", e);
                }
            }
        };
        // background thread to detect and repair lost connections
        getExecutor().scheduleAtFixedRate(connectionCheckRunnable, endpoint.getConnectionPollDelay(),
                endpoint.getConnectionPollDelay(), TimeUnit.SECONDS);
    }

    private void checkConnection() throws Exception {
        if (!connection.isConnected()) {
            LOG.info("Attempting to reconnect to: {}", XmppEndpoint.getConnectionMessage(connection));
            try {
                connection.connect();
                LOG.debug("Successfully connected to XMPP server through: {}", connection);
            } catch (SmackException e) {
                LOG.warn("Connection to XMPP server failed. Will try to reconnect later again.", e);
            }
        }
    }

    private ScheduledExecutorService getExecutor() {
        if (this.scheduledExecutor == null) {
            scheduledExecutor = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadScheduledExecutor(this, "connectionPoll");
        }
        return scheduledExecutor;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        // stop scheduler first
        if (scheduledExecutor != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(scheduledExecutor);
            scheduledExecutor = null;
        }

        if (muc != null) {
            LOG.info("Leaving room: {}", muc.getRoom());
            muc.removeMessageListener(this);
            muc.leave();
            muc = null;
        }
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
    }

    @Override
    public void newIncomingMessage(EntityBareJid from, Message message, Chat chat) {
        processMessage(message);
    }

    @Override
    public void processMessage(Message message) {
        processMessage(null, message);
    }

    @Override
    public void processStanza(Stanza stanza) throws SmackException.NotConnectedException, InterruptedException {
        if (stanza instanceof Message) {
            processMessage((Message) stanza);
        }
    }

    public void processMessage(Chat chat, Message message) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received XMPP message for {} from {} : {}", new Object[] {endpoint.getUser(), endpoint.getParticipant(), message.getBody()});
        }

        Exchange exchange = endpoint.createExchange(message);

        if (endpoint.isDoc()) {
            exchange.getIn().setHeader(XmppConstants.DOC_HEADER, message);
        }
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // must remove message from muc to avoid messages stacking up and causing OutOfMemoryError
            // pollMessage is a non blocking method
            // (see http://issues.igniterealtime.org/browse/SMACK-129)
            if (muc != null) {
                try {
                    muc.pollMessage();
                } catch (MultiUserChatException.MucNotJoinedException e) {
                    LOG.debug("Error while polling message from MultiUserChat. This exception will be ignored.", e);
                }
            }
        }
    }

}
