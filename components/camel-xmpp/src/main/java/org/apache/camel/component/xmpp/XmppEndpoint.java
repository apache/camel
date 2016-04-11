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

import java.io.IOException;
import java.util.Iterator;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;
import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To send and receive messages from a XMPP (chat) server.
 */
@UriEndpoint(scheme = "xmpp", title = "XMPP", syntax = "xmpp:host:port/participant", alternativeSyntax = "xmpp:user:password@host:port/participant",
        consumerClass = XmppConsumer.class, label = "chat,messaging")
public class XmppEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(XmppEndpoint.class);

    private XMPPConnection connection;
    private XmppBinding binding;

    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath @Metadata(required = "true")
    private int port;
    @UriPath
    private String participant;
    @UriParam
    private String user;
    @UriParam
    private String password;
    @UriParam(defaultValue = "Camel")
    private String resource = "Camel";
    @UriParam(defaultValue = "true")
    private boolean login = true;
    @UriParam
    private boolean createAccount;
    @UriParam
    private String room;
    @UriParam
    private String nickname;
    @UriParam
    private String serviceName;
    @UriParam
    private boolean pubsub;
    @UriParam
    private boolean doc;
    @UriParam(defaultValue = "true")
    private boolean testConnectionOnStartup = true;
    @UriParam(defaultValue = "10")
    private int connectionPollDelay = 10;
    @UriParam
    private HeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();
    @UriParam
    private ConnectionConfiguration connectionConfig;

    public XmppEndpoint() {
    }

    public XmppEndpoint(String uri, XmppComponent component) {
        super(uri, component);
    }

    @Deprecated
    public XmppEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer createProducer() throws Exception {
        if (room != null) {
            return createGroupChatProducer();
        } else {
            if (isPubsub()) {
                return createPubSubProducer();
            }
            if (getParticipant() == null) {
                throw new IllegalArgumentException("No room or participant configured on this endpoint: " + this);
            }
            return createPrivateChatProducer(getParticipant());
        }
    }

    public Producer createGroupChatProducer() throws Exception {
        return new XmppGroupChatProducer(this);
    }

    public Producer createPrivateChatProducer(String participant) throws Exception {
        return new XmppPrivateChatProducer(this, participant);
    }

    public Producer createPubSubProducer() throws Exception {
        return new XmppPubSubProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        XmppConsumer answer = new XmppConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Exchange createExchange(Packet packet) {
        Exchange exchange = super.createExchange();
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(new XmppMessage(packet));
        return exchange;
    }

    @Override
    protected String createEndpointUri() {
        return "xmpp://" + host + ":" + port + "/" + getParticipant() + "?serviceName=" + serviceName;
    }

    public boolean isSingleton() {
        return true;
    }

    public synchronized XMPPConnection createConnection() throws XMPPException, SmackException, IOException {
        if (connection != null && connection.isConnected()) {
            return connection;
        }

        if (connection == null) {
            connection = createConnectionInternal();
        }

        connection.connect();

        connection.addPacketListener(new XmppLogger("INBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });
        connection.addPacketSendingListener(new XmppLogger("OUTBOUND"), new PacketFilter() {
            public boolean accept(Packet packet) {
                return true;
            }
        });

        if (!connection.isAuthenticated()) {
            if (user != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Logging in to XMPP as user: {} on connection: {}", user, getConnectionMessage(connection));
                }
                if (password == null) {
                    LOG.warn("No password configured for user: {} on connection: {}", user, getConnectionMessage(connection));
                }

                if (createAccount) {
                    AccountManager accountManager = AccountManager.getInstance(connection);
                    accountManager.createAccount(user, password);
                }
                if (login) {
                    if (resource != null) {
                        connection.login(user, password, resource);
                    } else {
                        connection.login(user, password);
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Logging in anonymously to XMPP on connection: {}", getConnectionMessage(connection));
                }
                connection.loginAnonymously();
            }

            // presence is not needed to be sent after login
        }

        return connection;
    }

    private XMPPTCPConnection createConnectionInternal() {
        if (connectionConfig != null) {
            return new XMPPTCPConnection(connectionConfig);
        }

        if (port == 0) {
            port = 5222;
        }
        String sName = getServiceName() == null ? host : getServiceName();
        ConnectionConfiguration conf = new ConnectionConfiguration(host, port, sName);
        return new XMPPTCPConnection(conf);
    }

    /*
     * If there is no "@" symbol in the room, find the chat service JID and
     * return fully qualified JID for the room as room@conference.server.domain
     */
    public String resolveRoom(XMPPConnection connection) throws XMPPException, SmackException {
        ObjectHelper.notEmpty(room, "room");

        if (room.indexOf('@', 0) != -1) {
            return room;
        }

        Iterator<String> iterator = MultiUserChat.getServiceNames(connection).iterator();
        if (!iterator.hasNext()) {
            throw new XMPPErrorException("Cannot find Multi User Chat service",
                                         new XMPPError(new XMPPError.Condition("Cannot find Multi User Chat service on connection: " + getConnectionMessage(connection))));
        }

        String chatServer = iterator.next();
        LOG.debug("Detected chat server: {}", chatServer);

        return room + "@" + chatServer;
    }

    public String getConnectionDescription() {
        return host + ":" + port + "/" + serviceName;
    }

    public static String getConnectionMessage(XMPPConnection connection) {
        return connection.getHost() + ":" + connection.getPort() + "/" + connection.getServiceName();
    }

    public String getChatId() {
        return "Chat:" + getParticipant() + ":" + getUser();
    }

    // Properties
    // -------------------------------------------------------------------------
    public XmppBinding getBinding() {
        if (binding == null) {
            binding = new XmppBinding(headerFilterStrategy);
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from an XMPP
     * message
     */
    public void setBinding(XmppBinding binding) {
        this.binding = binding;
    }

    public String getHost() {
        return host;
    }

    /**
     * Hostname for the chat server
     */
    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    /**
     * Port number for the chat server
     */
    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    /**
     * User name (without server name). If not specified, anonymous login will be attempted.
     */
    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for login
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getResource() {
        return resource;
    }

    /**
     * XMPP resource. The default is Camel.
     */
    public void setResource(String resource) {
        this.resource = resource;
    }

    public boolean isLogin() {
        return login;
    }

    /**
     * Whether to login the user.
     */
    public void setLogin(boolean login) {
        this.login = login;
    }

    public boolean isCreateAccount() {
        return createAccount;
    }

    /**
     * If true, an attempt to create an account will be made. Default is false.
     */
    public void setCreateAccount(boolean createAccount) {
        this.createAccount = createAccount;
    }

    public String getRoom() {
        return room;
    }

    /**
     * If this option is specified, the component will connect to MUC (Multi User Chat).
     * Usually, the domain name for MUC is different from the login domain.
     * For example, if you are superman@jabber.org and want to join the krypton room, then the room URL is
     * krypton@conference.jabber.org. Note the conference part.
     * It is not a requirement to provide the full room JID. If the room parameter does not contain the @ symbol,
     * the domain part will be discovered and added by Camel
     */
    public void setRoom(String room) {
        this.room = room;
    }

    public String getParticipant() {
        // participant is optional so use user if not provided
        return participant != null ? participant : user;
    }

    /**
     * JID (Jabber ID) of person to receive messages. room parameter has precedence over participant.
     */
    public void setParticipant(String participant) {
        this.participant = participant;
    }

    public String getNickname() {
        return nickname != null ? nickname : getUser();
    }

    /**
     * Use nickname when joining room. If room is specified and nickname is not, user will be used for the nickname.
     */
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    /**
     * The name of the service you are connecting to. For Google Talk, this would be gmail.com.
     */
    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public ConnectionConfiguration getConnectionConfig() {
        return connectionConfig;
    }

    /**
     * To use an existing connection configuration
     */
    public void setConnectionConfig(ConnectionConfiguration connectionConfig) {
        this.connectionConfig = connectionConfig;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    /**
     * Specifies whether to test the connection on startup. This is used to ensure that the XMPP client has a valid
     * connection to the XMPP server when the route starts. Camel throws an exception on startup if a connection
     * cannot be established. When this option is set to false, Camel will attempt to establish a "lazy" connection
     * when needed by a producer, and will poll for a consumer connection until the connection is established. Default is true.
     */
    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public int getConnectionPollDelay() {
        return connectionPollDelay;
    }

    /**
     * The amount of time in seconds between polls (in seconds) to verify the health of the XMPP connection, or between attempts
     * to establish an initial consumer connection. Camel will try to re-establish a connection if it has become inactive.
     * Default is 10 seconds.
     */
    public void setConnectionPollDelay(int connectionPollDelay) {
        this.connectionPollDelay = connectionPollDelay;
    }

    /**
     * Accept pubsub packets on input, default is false
     */
    public void setPubsub(boolean pubsub) {
        this.pubsub = pubsub;
        if (pubsub) {
            setDoc(true);
        }
    }

    public boolean isPubsub() {
        return pubsub;
    }

    /**
     * Set a doc header on the IN message containing a Document form of the incoming packet;
     * default is true if presence or pubsub are true, otherwise false
     */
    public void setDoc(boolean doc) {
        this.doc = doc;
    }

    public boolean isDoc() {
        return doc;
    }

    // Implementation methods
    // -------------------------------------------------------------------------

    @Override
    protected void doStop() throws Exception {
        if (connection != null) {
            connection.disconnect();
        }
        connection = null;
        binding = null;
    }

}
