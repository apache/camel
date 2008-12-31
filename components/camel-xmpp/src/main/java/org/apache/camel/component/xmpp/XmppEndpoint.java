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

import java.util.Iterator;

import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jivesoftware.smack.AccountManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;

/**
 * A XMPP Endpoint
 *
 * @version $Revision:520964 $
 */
public class XmppEndpoint extends DefaultEndpoint<XmppExchange> {
    private static final transient Log LOG = LogFactory.getLog(XmppEndpoint.class);
    private XmppBinding binding;
    private XMPPConnection connection;
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

    public XmppEndpoint(String uri, XmppComponent component) {
        super(uri, component);
        binding = new XmppBinding(component.getHeaderFilterStrategy());
    }

    public XmppEndpoint(String endpointUri) {
        super(endpointUri);
    }

    public Producer<XmppExchange> createProducer() throws Exception {
        if (room != null) {
            return createGroupChatProducer();
        } else {
            if (participant == null) {
                throw new IllegalArgumentException("No room or participant configured on this endpoint: "
                                                   + this);
            }
            return createPrivateChatProducer(participant);
        }
    }

    public Producer<XmppExchange> createGroupChatProducer() throws Exception {
        return new XmppGroupChatProducer(this);
    }

    public Producer<XmppExchange> createPrivateChatProducer(String participant) throws Exception {
        return new XmppPrivateChatProducer(this, participant);
    }

    public Consumer<XmppExchange> createConsumer(Processor processor) throws Exception {
        return new XmppConsumer(this, processor);
    }

    @Override
    public XmppExchange createExchange(ExchangePattern pattern) {
        return new XmppExchange(getCamelContext(), pattern, getBinding());
    }

    public XmppExchange createExchange(Message message) {
        return new XmppExchange(getCamelContext(), getExchangePattern(), getBinding(), message);
    }

    // Properties
    // -------------------------------------------------------------------------
    public XmppBinding getBinding() {
        if (binding == null) {
            binding = new XmppBinding();
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
        return participant;
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

    public String getServiceName() {
        return serviceName;
    }    
    
    public XMPPConnection getConnection() throws XMPPException {
        if (connection == null) {
            connection = createConnection();
        }
        return connection;
    }

    public void setConnection(XMPPConnection connection) {
        this.connection = connection;
    }

    // Implementation methods
    // -------------------------------------------------------------------------
    protected XMPPConnection createConnection() throws XMPPException {
        XMPPConnection connection;
        if (port > 0) {            
            if (getServiceName() == null) {
                connection = new XMPPConnection(new ConnectionConfiguration(host, port));
            } else {
                connection = new XMPPConnection(new ConnectionConfiguration(host, port, getServiceName()));
            }
        } else {
            connection = new XMPPConnection(host);
        }

        connection.connect();

        if (login && !connection.isAuthenticated()) {
            if (user != null) {
                LOG.info("Logging in to XMPP as user: " + user + " on connection: " + connection);
                if (password == null) {
                    LOG.warn("No password configured for user: " + user);
                }

                if (createAccount) {
                    AccountManager accountManager = new AccountManager(connection);
                    accountManager.createAccount(user, password);
                }
                if (resource != null) {
                    connection.login(user, password, resource);
                } else {
                    connection.login(user, password);
                }
            } else {
                LOG.info("Logging in anonymously to XMPP on connection: " + connection);
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
    public String resolveRoom() throws XMPPException, CamelException {
        if (room == null) {
            throw new IllegalArgumentException("room is not specified");
        }

        if (room.indexOf('@', 0) != -1) {
            return room;
        }

        XMPPConnection conn = getConnection();
        Iterator<String> iterator = MultiUserChat.getServiceNames(conn).iterator();
        if (!iterator.hasNext()) {
            throw new CamelException("Can not find Multi User Chat service");
        }
        String chatServer = iterator.next();
        if (LOG.isInfoEnabled()) {
            LOG.info("Detected chat server: " + chatServer);
        }

        return room + "@" + chatServer;
    }

    public boolean isSingleton() {
        return true;
    }
}
