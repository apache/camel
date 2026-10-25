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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.impl.DefaultCamelContext;
import org.jivesoftware.smack.AbstractXMPPConnection;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The connection monitor of the consumer and Smack's ReconnectionManager can connect and log in the same connection at
 * the same time. Smack 4.4 throws AlreadyConnectedException from connect() and AlreadyLoggedInException from login() to
 * whichever comes second (both methods are synchronized and check the state first): the monitor must go on in the first
 * case and do nothing more in the second, instead of reporting a failed connection.
 */
class XmppConsumerConnectionRaceTest {

    private final List<XMPPTCPConnection> logins = new CopyOnWriteArrayList<>();
    private DefaultCamelContext context;

    @BeforeEach
    void startContext() {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    void stopContext() {
        context.stop();
    }

    @Test
    void connectionConnectedMeanwhileByTheReconnectionManagerIsLoggedIn() throws Exception {
        // the ReconnectionManager connected the connection between isConnected() and connect() of the monitor
        XMPPTCPConnection connection = new XMPPTCPConnection(config()) {
            @Override
            public synchronized AbstractXMPPConnection connect() throws SmackException {
                throw new SmackException.AlreadyConnectedException();
            }
        };
        XmppConsumer consumer = new XmppConsumer(endpoint(null), exchange -> {
        });
        consumer.connection = connection;

        consumer.checkConnection();

        assertEquals(List.of(connection), logins, "The monitor did not log the connection in");
    }

    @Test
    void connectionLoggedInMeanwhileByTheReconnectionManagerIsNotAFailure() throws Exception {
        XMPPTCPConnection connection = new XMPPTCPConnection(config()) {
            @Override
            public synchronized AbstractXMPPConnection connect() {
                return this;
            }
        };
        // the ReconnectionManager logged the connection in between isAuthenticated() and login() of the monitor
        XmppConsumer consumer = new XmppConsumer(endpoint(new SmackException.AlreadyLoggedInException()), exchange -> {
        });
        consumer.connection = connection;

        assertDoesNotThrow(consumer::checkConnection);
        assertEquals(List.of(connection), logins);
    }

    private XmppEndpoint endpoint(SmackException loginFailure) {
        XmppComponent component = context.getComponent("xmpp", XmppComponent.class);
        XmppEndpoint endpoint = new XmppEndpoint("xmpp://localhost:5222", component) {
            @Override
            public void login(XMPPTCPConnection connection) throws SmackException {
                logins.add(connection);
                if (loginFailure != null) {
                    throw loginFailure;
                }
            }
        };
        endpoint.setHost("localhost");
        return endpoint;
    }

    private static XMPPTCPConnectionConfiguration config() throws Exception {
        return XMPPTCPConnectionConfiguration.builder()
                .setXmppDomain("localhost")
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .build();
    }
}
