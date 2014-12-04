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
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.DefaultHeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategy;
import org.apache.camel.spi.HeaderFilterStrategyAware;
import org.apache.camel.util.ObjectHelper;
import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionConfiguration.SecurityMode;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.XMPPError;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A XMPP Endpoint
 *
 * @version 
 */
public class XmppEndpoint extends DefaultEndpoint implements HeaderFilterStrategyAware {
    private static final Logger LOG = LoggerFactory.getLogger(XmppEndpoint.class);
    private HeaderFilterStrategy headerFilterStrategy = new DefaultHeaderFilterStrategy();
    private XmppBinding binding;
    private String host;
    private int port;
    private String user;
    private String password;
    private String resource = "Camel";
    private boolean login = true;
    private boolean createAccount;
    private String room;
    private String participant;
    private String nickname;
    private String serviceName;
    private XMPPConnection connection;
    private boolean testConnectionOnStartup = true;
    private int connectionPollDelay = 10;
    private boolean useTls = true;
    private boolean acceptAllCertificates = true; // TODO change to false

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

    public Consumer createConsumer(Processor processor) throws Exception {
        XmppConsumer answer = new XmppConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    @Override
    public Exchange createExchange(ExchangePattern pattern) {
        return createExchange(pattern, null);
    }

    public Exchange createExchange(Message message) {
        return createExchange(getExchangePattern(), message);
    }

    private Exchange createExchange(ExchangePattern pattern, Message message) {
        Exchange exchange = new DefaultExchange(this, getExchangePattern());
        exchange.setProperty(Exchange.BINDING, getBinding());
        exchange.setIn(new XmppMessage(message));
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

        ConnectionConfiguration connectionConfiguration;
        if (connection == null) {
            if (port > 0) {
                if (getServiceName() == null) {
                    connectionConfiguration = new ConnectionConfiguration(host, port);
                } else {
                    connectionConfiguration = new ConnectionConfiguration(host, port, serviceName);
                }
            } else {
                connectionConfiguration = new ConnectionConfiguration(host);
            }

            if (useTls) {
                connectionConfiguration.setSecurityMode(SecurityMode.required);
            } else {
                connectionConfiguration.setSecurityMode(SecurityMode.disabled);
            }

            if (acceptAllCertificates) {
                // TODO Replace with
                // org.jivesoftware.smack.util.TLSUtils.acceptAllCertificates(ConnectionConfiguration)
                // once camel-xmpp uses Smack 4.1
                SSLContext context;
                try {
                    context = SSLContext.getInstance("TLS");
                    X509TrustManager acceptAllTrustManager = new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] arg0, String arg1)
                                        throws CertificateException {
                            // Nothing to do here
                        }
                        @Override
                        public void checkServerTrusted(X509Certificate[] arg0, String arg1)
                                        throws CertificateException {
                            // Nothing to do here
                        }
                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    };
                    context.init(null, new TrustManager[] { acceptAllTrustManager }, new SecureRandom());
                    connectionConfiguration.setCustomSSLContext(context);
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    throw new IOException(e);
                }

            }
            connection = new XMPPTCPConnection(connectionConfiguration);
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
    /*
     * If there is no "@" symbol in the room, find the chat service JID and
     * return fully qualified JID for the room as room@conference.server.domain
     */
    public String resolveRoom(XMPPConnection connection) throws XMPPException, SmackException {
        ObjectHelper.notEmpty(room, "room");

        if (room.indexOf('@', 0) != -1) {
            return room;
        }

        Collection<String> mucServices = MultiUserChat.getServiceNames(connection);
        if (mucServices.isEmpty()) {
            throw new SmackException("Cannot find Multi User Chat service on connection: " + getConnectionMessage(connection));
        }

        String chatServer = mucServices.iterator().next();
        LOG.debug("Detected chat server: {}", chatServer);

        return room + "@" + chatServer;
    }

    public String getConnectionDescription() {
        return host + ":" + port + "/" + serviceName;
    }

    public static String getConnectionMessage(XMPPConnection connection) {
        return connection.getHost() + ":" + connection.getPort() + "/" + connection.getServiceName();
    }

    public static String getXmppExceptionLogMessage(XMPPErrorException e) {
        XMPPError xmppError = e.getXMPPError();
        StringBuilder strBuff = new StringBuilder();
        if (xmppError != null) {
            strBuff.append("[ ").append(xmppError.getType()).append(" ] ")
                .append(xmppError.getCondition()).append(" : ")
                .append(xmppError.getMessage());
        }
        return strBuff.toString();
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

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public boolean isLogin() {
        return login;
    }

    public void setLogin(boolean login) {
        this.login = login;
    }

    public boolean isCreateAccount() {
        return createAccount;
    }

    public void setCreateAccount(boolean createAccount) {
        this.createAccount = createAccount;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getParticipant() {
        // participant is optional so use user if not provided
        return participant != null ? participant : user;
    }

    public void setParticipant(String participant) {
        this.participant = participant;
    }

    public String getNickname() {
        return nickname != null ? nickname : getUser();
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public void setAcceptAllCertificates(boolean acceptAllCertificates) {
        this.acceptAllCertificates = acceptAllCertificates;
    }

    public String getServiceName() {
        return serviceName;
    }    

    public HeaderFilterStrategy getHeaderFilterStrategy() {
        return headerFilterStrategy;
    }

    public void setHeaderFilterStrategy(HeaderFilterStrategy headerFilterStrategy) {
        this.headerFilterStrategy = headerFilterStrategy;
    }

    public boolean isTestConnectionOnStartup() {
        return testConnectionOnStartup;
    }

    public void setTestConnectionOnStartup(boolean testConnectionOnStartup) {
        this.testConnectionOnStartup = testConnectionOnStartup;
    }

    public int getConnectionPollDelay() {
        return connectionPollDelay;
    }

    public void setConnectionPollDelay(int connectionPollDelay) {
        this.connectionPollDelay = connectionPollDelay;
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
