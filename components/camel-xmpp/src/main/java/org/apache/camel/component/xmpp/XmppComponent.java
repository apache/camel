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
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.URISupport;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("xmpp")
public class XmppComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(XmppComponent.class);

    // the connections of the endpoints, one per account: a server keeps one session per account and resource, and
    // closes the older session (conflict) when another one logs in, so the endpoints of an account share its connection
    private final Map<List<Object>, SharedConnection> connections = new HashMap<>();

    public XmppComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LOG.debug("Creating new endpoint for URI {}", URISupport.sanitizeUri(uri));
        XmppEndpoint endpoint = new XmppEndpoint(uri, this);

        URI u = new URI(uri);
        endpoint.setHost(u.getHost());
        if (u.getPort() > 0) {
            endpoint.setPort(u.getPort());
        }
        if (u.getUserInfo() != null) {
            String[] parts = u.getUserInfo().split(":");
            if (parts.length == 2) {
                endpoint.setUser(parts[0]);
                endpoint.setPassword(parts[1]);
            } else {
                endpoint.setUser(u.getUserInfo());
            }
        }
        String remainingPath = u.getPath();
        if (remainingPath != null) {
            if (remainingPath.startsWith("/")) {
                remainingPath = remainingPath.substring(1);
            }

            // assume its a participant
            if (remainingPath.length() > 0) {
                endpoint.setParticipant(remainingPath);
            }
        }

        return endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        ReconnectionManager.setEnabledPerDefault(true);
    }

    @Override
    protected void doStop() throws Exception {
        List<SharedConnection> remaining;
        synchronized (connections) {
            remaining = List.copyOf(connections.values());
            connections.clear();
        }
        for (SharedConnection shared : remaining) {
            shared.disconnect();
        }

        super.doStop();
    }

    /**
     * Gets the connection of the endpoint's account for the given consumer or producer, and creates it when the account
     * has no connection yet. A connection that is no longer connected is connected and logged in again, not replaced:
     * the consumers and producers that got it before keep using that object (and Smack's ReconnectionManager keeps
     * reconnecting it), so a second connection of the account would log in the same JID and resource again and the
     * server would close one of the two (conflict). The connection is shared with the other consumers and producers of
     * the account, also of other endpoints; release it with {@link #releaseConnection(XmppEndpoint, Object)}.
     */
    XMPPTCPConnection acquireConnection(XmppEndpoint endpoint, Object user)
            throws InterruptedException, IOException, SmackException, XMPPException {
        List<Object> key = endpoint.getConnectionKey();
        SharedConnection shared;
        synchronized (connections) {
            shared = connections.computeIfAbsent(key, k -> new SharedConnection());
            shared.users.add(user);
        }
        boolean acquired = false;
        shared.lock.lock();
        try {
            if (shared.connection == null) {
                shared.connection = endpoint.createConnection();
            } else {
                connectAndLogin(endpoint, shared.connection);
            }
            acquired = true;
            return shared.connection;
        } finally {
            shared.lock.unlock();
            if (!acquired) {
                releaseConnection(endpoint, user);
            }
        }
    }

    /**
     * Connects and logs in the shared connection of the endpoint's account again when it is not connected or not logged
     * in. The consumers and producers of an account share one connection, so the reconnect is done under the account's
     * lock.
     */
    void reconnect(XmppEndpoint endpoint, XMPPTCPConnection connection)
            throws InterruptedException, IOException, SmackException, XMPPException {
        SharedConnection shared;
        synchronized (connections) {
            shared = connections.get(endpoint.getConnectionKey());
        }
        Lock lock = shared != null ? shared.lock : null;
        if (lock != null) {
            lock.lock();
        }
        try {
            connectAndLogin(endpoint, connection);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    /**
     * Connects the connection when it is not connected, and logs it in again when it is not logged in (a connection
     * that is connected again is not logged in, and the server delivers nothing to it until it is). Smack's
     * {@code connect()} and {@code login()} throw {@link SmackException.AlreadyConnectedException} and
     * {@link SmackException.AlreadyLoggedInException} to a second caller (such as Smack's ReconnectionManager), which
     * only means that it was done already. {@code login()} logs in with the user, password and resource the connection
     * logged in with before.
     */
    private static void connectAndLogin(XmppEndpoint endpoint, XMPPTCPConnection connection)
            throws InterruptedException, IOException, SmackException, XMPPException {
        if (!connection.isConnected()) {
            LOG.debug("Reconnecting to: {}", XmppEndpoint.getConnectionMessage(connection));
            try {
                connection.connect();
            } catch (SmackException.AlreadyConnectedException e) {
                LOG.debug("Already connected again: {}", XmppEndpoint.getConnectionMessage(connection));
            }
        }
        if (!connection.isAuthenticated() && (endpoint.getUser() == null || endpoint.isLogin())) {
            LOG.debug("Logging in again to: {}", XmppEndpoint.getConnectionMessage(connection));
            try {
                connection.login();
            } catch (SmackException.AlreadyLoggedInException e) {
                LOG.debug("Already logged in again: {}", XmppEndpoint.getConnectionMessage(connection));
            }
        }
    }

    /**
     * Releases the connection that the given consumer or producer acquired, and disconnects it when no consumer or
     * producer of the account uses it any more.
     */
    void releaseConnection(XmppEndpoint endpoint, Object user) {
        List<Object> key = endpoint.getConnectionKey();
        SharedConnection unused = null;
        synchronized (connections) {
            SharedConnection shared = connections.get(key);
            if (shared != null && shared.users.remove(user) && shared.users.isEmpty()) {
                connections.remove(key);
                unused = shared;
            }
        }
        if (unused != null) {
            unused.disconnect();
        }
    }

    /**
     * The number of consumers and producers that use the connection of the endpoint's account. For tests.
     */
    int connectionUsers(XmppEndpoint endpoint) {
        synchronized (connections) {
            SharedConnection shared = connections.get(endpoint.getConnectionKey());
            return shared != null ? shared.users.size() : 0;
        }
    }

    private static final class SharedConnection {
        private final Lock lock = new ReentrantLock();
        private final Set<Object> users = Collections.newSetFromMap(new IdentityHashMap<>());
        private volatile XMPPTCPConnection connection;

        void disconnect() {
            XMPPTCPConnection current = connection;
            if (current != null) {
                // also when it is not connected: that stops Smack's ReconnectionManager from connecting it again
                current.disconnect();
            }
        }
    }

}
