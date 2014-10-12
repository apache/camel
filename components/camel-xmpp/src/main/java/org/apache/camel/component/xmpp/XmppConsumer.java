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
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.DiscussionHistory;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link org.apache.camel.Consumer Consumer} which listens to XMPP packets
 *
 * @version 
 */
public class XmppConsumer extends DefaultConsumer implements PacketListener, MessageListener, ChatManagerListener {
    private static final Logger LOG = LoggerFactory.getLogger(XmppConsumer.class);
    private final XmppEndpoint endpoint;
    private MultiUserChat muc;
    private Chat privateChat;
    private ChatManager chatManager;
    private XMPPConnection connection;
    private ScheduledExecutorService scheduledExecutor;

    public XmppConsumer(XmppEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        try {
            connection = endpoint.createConnection();
        } catch (XMPPErrorException e) {
            if (endpoint.isTestConnectionOnStartup()) {
                throw new RuntimeException("Could not connect to XMPP server.", e);
            }  else {
                LOG.warn(XmppEndpoint.getXmppExceptionLogMessage(e));
                if (getExceptionHandler() != null) {
                    getExceptionHandler().handleException(XmppEndpoint.getXmppExceptionLogMessage(e), e);
                }
                scheduleDelayedStart();
                return;
            }
        }

        chatManager = ChatManager.getInstanceFor(connection);
        chatManager.addChatListener(this);

        if (endpoint.getRoom() == null) {
            privateChat = chatManager.getThreadChat(endpoint.getChatId());

            if (privateChat != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Adding listener to existing chat opened to " + privateChat.getParticipant());
                }
                privateChat.addMessageListener(this);
            } else {                
                privateChat = chatManager.createChat(endpoint.getParticipant(), endpoint.getChatId(), this);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Opening private chat to " + privateChat.getParticipant());
                }
            }
        } else {
            muc = new MultiUserChat(connection, endpoint.resolveRoom(connection));
            muc.addMessageListener(this);
            DiscussionHistory history = new DiscussionHistory();
            history.setMaxChars(0); // we do not want any historical messages

            muc.join(endpoint.getNickname(), null, history, connection.getPacketReplyTimeout());
            if (LOG.isInfoEnabled()) {
                LOG.info("Joined room: {} as: {}", muc.getRoom(), endpoint.getNickname());
            }
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
            } catch (XMPPErrorException e) {
                LOG.warn(XmppEndpoint.getXmppExceptionLogMessage(e));
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
            if (LOG.isInfoEnabled()) {
                LOG.info("Leaving room: {}", muc.getRoom());
            }
            muc.removeMessageListener(this);
            muc.leave();
            muc = null;
        }
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
    }

    public void chatCreated(Chat chat, boolean createdLocally) {
        if (!createdLocally) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Accepting incoming chat session from " + chat.getParticipant());
            }
            chat.addMessageListener(this);
        }
    }

    public void processPacket(Packet packet) {
        processMessage(null, (Message)packet);
    }

    public void processMessage(Chat chat, Message message) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received XMPP message for {} from {} : {}", new Object[]{endpoint.getUser(), endpoint.getParticipant(), message.getBody()});
        }

        Exchange exchange = endpoint.createExchange(message);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        } finally {
            // must remove message from muc to avoid messages stacking up and causing OutOfMemoryError
            // pollMessage is a non blocking method
            // (see http://issues.igniterealtime.org/browse/SMACK-129)
            if (muc != null) {
                muc.pollMessage();
            }
        }
    }
}
