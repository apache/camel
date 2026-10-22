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
     * has no connection yet or its connection is no longer connected. The connection is shared with the other consumers
     * and producers of the account, also of other endpoints; release it with
     * {@link #releaseConnection(XmppEndpoint, Object)}.
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
            if (shared.connection == null || !shared.connection.isConnected()) {
                shared.connection = endpoint.createConnection();
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
     * Connects the shared connection of the endpoint's account again when it is not connected. The consumers and
     * producers of an account share one connection, so the reconnect is done under the account's lock; Smack's
     * {@code connect()} throws {@link SmackException.AlreadyConnectedException} to a second caller, which here only
     * means that another consumer or producer of the account connected it first.
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
            if (!connection.isConnected()) {
                LOG.debug("Reconnecting to: {}", XmppEndpoint.getConnectionMessage(connection));
                try {
                    connection.connect();
                } catch (SmackException.AlreadyConnectedException e) {
                    LOG.debug("Already connected by another consumer or producer of the account: {}",
                            XmppEndpoint.getConnectionMessage(connection));
                }
            }
        } finally {
            if (lock != null) {
                lock.unlock();
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

    private static final class SharedConnection {
        private final Lock lock = new ReentrantLock();
        private final Set<Object> users = Collections.newSetFromMap(new IdentityHashMap<>());
        private volatile XMPPTCPConnection connection;

        void disconnect() {
            XMPPTCPConnection current = connection;
            if (current != null && current.isConnected()) {
                current.disconnect();
            }
        }
    }

}
