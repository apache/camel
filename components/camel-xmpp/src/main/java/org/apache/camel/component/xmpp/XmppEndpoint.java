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
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To send and receive messages from a XMPP (chat) server.
 */
@UriEndpoint(firstVersion = "1.0", scheme = "xmpp", title = "XMPP", syntax = "xmpp:host:port/participant", alternativeSyntax = "xmpp:user:password@host:port/participant",
        label = "chat,messaging")
public class XmppEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {

    private static final Logger LOG = LoggerFactory.getLogger(XmppEndpoint.class);

    private volatile XMPPTCPConnection connection;
    private XmppBinding binding;

    @UriPath @Metadata(required = true)
    private String host;
    @UriPath @Metadata(required = true)
    private int port;
    @UriPath(label = "common")
    private String participant;
    @UriParam(label = "security", secret = true)
    private String user;
    @UriParam(label = "security", secret = true)
    private String password;
    @UriParam(label = "common,advanced", defaultValue = "Camel")
    private String resource = "Camel";
    @UriParam(label = "common", defaultValue = "true")
    private boolean login = true;
    @UriParam(label = "common,advanced")
    private boolean createAccount;
    @UriParam(label = "common")
    private String room;
    @UriParam(label = "security", secret = true)
    private String roomPassword;
    @UriParam(label = "common")
    private String nickname;
    @UriParam(label = "common")
    private String serviceName;
    @UriParam(label = "common")
    private boolean pubsub;
    @UriParam(label = "consumer")
    private boolean doc;
    @UriParam(label = "common", defaultValue = "true")
    private boolean testConnectionOnStartup = true;
    @UriParam(label = "consumer", defaultValue = "10")
    private int connectionPollDelay = 10;
    @UriParam(label = "filter")
    private HeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();
    @UriParam(label = "advanced")
    private ConnectionConfiguration connectionConfig;

    public XmppEndpoint() {
    }

    public XmppEndpoint(String uri, XmppComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        if (room != null) {
            return createGroupChatProducer();
        } else {
            if (isPubsub()) {
                return createPubSubProducer();
            }
            if (isDoc()) {
                return createDirectProducer();
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

    public Producer createDirectProducer() throws Exception {
        return new XmppDirectProducer(this);
    }

    public Producer createPubSubProducer() throws Exception {
        return new XmppPubSubProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        XmppConsumer answer = new XmppConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Exchange createExchange(Stanza packet) {
        Exchange exchange = super.createExchange();
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(new XmppMessage(exchange, packet));
        return exchange;
    }

    @Override
    protected String createEndpointUri() {
        return "xmpp://" + host + ":" + port + "/" + getParticipant() + "?serviceName=" + serviceName;
    }

    public synchronized XMPPTCPConnection createConnection() throws InterruptedException, IOException, SmackException, XMPPException {
        if (connection != null && connection.isConnected()) {
            // use existing working connection
            return connection;
        }

        // prepare for creating new connection
        connection = null;

        LOG.trace("Creating new connection ...");
        XMPPTCPConnection newConnection = createConnectionInternal();

        newConnection.connect();

        newConnection.addSyncStanzaListener(new XmppLogger("INBOUND"), stanza -> true);
        newConnection.addSyncStanzaListener(new XmppLogger("OUTBOUND"), stanza -> true);

        if (!newConnection.isAuthenticated()) {
            if (user != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Logging in to XMPP as user: {} on connection: {}", user, getConnectionMessage(newConnection));
                }
                if (password == null) {
                    LOG.warn("No password configured for user: {} on connection: {}", user, getConnectionMessage(newConnection));
                }

                if (createAccount) {
                    AccountManager accountManager = AccountManager.getInstance(newConnection);
                    accountManager.createAccount(Localpart.from(user), password);
                }
                if (login) {
                    if (resource != null) {
                        newConnection.login(user, password, Resourcepart.from(resource));
                    } else {
                        newConnection.login(user, password);
                    }
                }
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Logging in anonymously to XMPP on connection: {}", getConnectionMessage(newConnection));
                }
                newConnection.login();
            }

            // presence is not needed to be sent after login
        }

        // okay new connection was created successfully so assign it as the connection
        LOG.debug("Created new connection successfully: {}", newConnection);
        connection = newConnection;
        return connection;
    }

    private XMPPTCPConnection createConnectionInternal() throws UnknownHostException, XmppStringprepException {
        if (connectionConfig != null) {
            return new XMPPTCPConnection(ObjectHelper.cast(XMPPTCPConnectionConfiguration.class, connectionConfig));
        }

        if (port == 0) {
            port = 5222;
        }
        String sName = getServiceName() == null ? host : getServiceName();
        XMPPTCPConnectionConfiguration conf = XMPPTCPConnectionConfiguration.builder()
                .setHostAddress(InetAddress.getByName(host))
                .setPort(port)
                .setXmppDomain(sName)
                .build();
        return new XMPPTCPConnection(conf);
    }

    /*
     * If there is no "@" symbol in the room, find the chat service JID and
     * return fully qualified JID for the room as room@conference.server.domain
     */
    public String resolveRoom(XMPPConnection connection) throws InterruptedException, SmackException, XMPPException  {
        StringHelper.notEmpty(room, "room");

        if (room.indexOf('@', 0) != -1) {
            return room;
        }

        MultiUserChatManager multiUserChatManager = MultiUserChatManager.getInstanceFor(connection);
        List<DomainBareJid> xmppServiceDomains = multiUserChatManager.getXMPPServiceDomains();
        if (xmppServiceDomains.isEmpty()) {
            throw new XMPPErrorException(null,
                    StanzaError.from(Condition.item_not_found, "Cannot find any XMPPServiceDomain by MultiUserChatManager on connection: " + getConnectionMessage(connection)).build());
        }

        return room + "@" + xmppServiceDomains.iterator().next();
    }

    public String getConnectionDescription() {
        return host + ":" + port + "/" + serviceName;
    }

    public static String getConnectionMessage(XMPPConnection connection) {
        return connection.getHost() + ":" + connection.getPort() + "/" + connection.getXMPPServiceDomain();
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

    /**
     * Password for room
     */
    public void setRoomPassword(String roomPassword) {
        this.roomPassword = roomPassword;
    }

    protected String getRoomPassword() {
        return roomPassword;
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

    @Override
    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    /**
     * To use a custom HeaderFilterStrategy to filter header to and from Camel message.
     */
    @Override
    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public ConnectionConfiguration getConnectionConfig() {
        return connectionConfig;
    }

    /**
     * To use an existing connection configuration. Currently {@link org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration} is only supported (XMPP over TCP).
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
