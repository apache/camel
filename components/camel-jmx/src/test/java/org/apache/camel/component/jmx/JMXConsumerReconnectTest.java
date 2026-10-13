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
package org.apache.camel.component.jmx;

import java.rmi.NoSuchObjectException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerFactory;
import javax.management.NotificationListener;
import javax.management.remote.JMXConnectionNotification;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.apache.camel.Route;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A remote JMX consumer with reconnectOnConnectionFailure=true must replace a lost connection once: one reconnect for
 * the "failed" and "closed" notifications that the connector sends for one lost connection, no reconnect for the
 * connection it closes itself, and none after it was stopped. The connector server counts the client connections that
 * it accepts.
 */
class JMXConsumerReconnectTest extends SimpleBeanFixture {

    @RegisterExtension
    AvailablePortFinder.Port portHolder = AvailablePortFinder.find();

    /**
     * How long no connection may be opened: three times the reconnect delay of one second
     */
    private static final int QUIET_SECONDS = 3;

    private final Semaphore opened = new Semaphore(0);
    private JMXServiceURL url;
    private JMXConnectorServer connector;
    private Registry registry;
    private boolean beanRegistered;

    @BeforeEach
    @Override
    public void setUp() throws Exception {
        url = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + portHolder.getPort() + "/" + DOMAIN);
        registry = LocateRegistry.createRegistry(portHolder.getPort());
        initServer();
        initBean();
        initContext();
        startContext();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (!getContext().isStopped()) {
            getContext().stop();
        }
        if (beanRegistered) {
            unregisterBean(makeObjectName("simpleBean"));
        }
        if (connector != null) {
            connector.stop();
        }
        if (registry != null) {
            try {
                UnicastRemoteObject.unexportObject(registry, true);
            } catch (NoSuchObjectException e) {
                // already unexported
            }
        }
    }

    @Override
    protected void initServer() throws Exception {
        server = MBeanServerFactory.newMBeanServer(DOMAIN);
        connector = JMXConnectorServerFactory.newJMXConnectorServer(url, Collections.<String, Object> emptyMap(), server);
        // one permit for every client connection that this connector server accepts
        connector.addNotificationListener((notification, handback) -> {
            if (JMXConnectionNotification.OPENED.equals(notification.getType())) {
                opened.release();
            }
        }, null, null);
        connector.start();
    }

    @Override
    protected void initBean() throws Exception {
        super.initBean();
        beanRegistered = true;
    }

    @Override
    protected JMXUriBuilder buildFromURI() {
        return super.buildFromURI().withServerName(url.toString()).withReconnectDelay(1)
                .withReconnectOnConnectionFailure(true);
    }

    /**
     * A lost connection: the connector reports it with "failed" and then, when it closes itself, with "closed", both
     * with the id of that connection (observed with the RMI connector of JDK 21 when the server can no longer be
     * reached). That is one lost connection, so the consumer must connect once.
     */
    @Test
    void testReconnectsOnceWhenTheConnectionFailed() throws Exception {
        MockEndpoint mock = getMockFixture().getMockEndpoint();
        awaitFirstConnection(mock);

        String connectionId = connector.getConnectionIds()[0];
        NotificationListener listener = connectionListener();
        listener.handleNotification(new JMXConnectionNotification(
                JMXConnectionNotification.FAILED, connector, connectionId, 1, "Failed to communicate with the server",
                null), null);
        listener.handleNotification(new JMXConnectionNotification(
                JMXConnectionNotification.CLOSED, connector, connectionId, 2, "Client has been closed", null), null);

        assertTrue(opened.tryAcquire(20, TimeUnit.SECONDS), "the consumer did not replace its connection");

        assertKeepsConnectionAndIsSubscribedOnce(mock);
    }

    /**
     * The server restarts and the MBean is registered a little later than the connector is back (the usual order when
     * an application restarts). Whether the connector re-establishes the connection itself or reports it as lost
     * depends on timing; the consumer must keep trying to subscribe either way, and keep the connection it subscribed
     * on.
     */
    @Test
    void testSubscribesWhenTheBeanIsRegisteredAfterTheServerIsBack() throws Exception {
        MockEndpoint mock = getMockFixture().getMockEndpoint();
        awaitFirstConnection(mock);

        // the server goes away and comes back without the MBean
        connector.stop();
        beanRegistered = false;
        initServer();

        // two connections: a connection without a subscription is not the end, the consumer connects again
        assertTrue(opened.tryAcquire(2, 30, TimeUnit.SECONDS),
                "the consumer did not keep trying to subscribe after the server came back without the MBean");
        assertFalse(server.isRegistered(makeObjectName("simpleBean")));

        // now the MBean is there: one of the next attempts subscribes
        mock.reset();
        mock.setMinimumExpectedMessageCount(1);
        initBean();
        awaitSubscribed(mock);

        // The consumer is subscribed, so it has no reason to connect again. The check does not depend on the moment
        // at which it starts: it tolerates a connection that is being opened right now and fails only when the
        // consumer keeps connecting.
        assertTrue(awaitNoConnectionFor(QUIET_SECONDS, 3),
                "the consumer kept connecting although it was subscribed on a working connection");

        assertDeliversOnce(mock);
    }

    /**
     * The consumer replaces its connection when the server reports lost notifications. It closes the old connection
     * itself, which must not count as one more lost connection.
     */
    @Test
    void testReconnectsOnceWhenNotificationsWereLost() throws Exception {
        MockEndpoint mock = getMockFixture().getMockEndpoint();
        awaitFirstConnection(mock);

        String connectionId = connector.getConnectionIds()[0];
        connectionListener().handleNotification(new JMXConnectionNotification(
                JMXConnectionNotification.NOTIFS_LOST, connector, connectionId, 1, "lost notifications", 1L), null);

        assertTrue(opened.tryAcquire(20, TimeUnit.SECONDS), "the consumer did not replace its connection");

        assertKeepsConnectionAndIsSubscribedOnce(mock);
    }

    /**
     * The connector reports that it has opened a new connection by itself (as the RMI connector does after an I/O error
     * when the server can be reached). The consumer cannot know whether its listener was registered again, so it
     * subscribes again on a connection of its own.
     */
    @Test
    void testSubscribesAgainWhenTheConnectorReconnectedByItself() throws Exception {
        MockEndpoint mock = getMockFixture().getMockEndpoint();
        awaitFirstConnection(mock);

        connectionListener().handleNotification(new JMXConnectionNotification(
                JMXConnectionNotification.OPENED, connector, "rmi://reconnected 2", 1, "Reconnected to server", null),
                null);

        assertTrue(opened.tryAcquire(20, TimeUnit.SECONDS),
                "the consumer did not subscribe again after the connector had reconnected by itself");

        assertKeepsConnectionAndIsSubscribedOnce(mock);
    }

    /**
     * A notification about a lost connection that arrives after the consumer was stopped must not make it connect
     * again.
     */
    @Test
    void testDoesNotReconnectAfterStop() throws Exception {
        MockEndpoint mock = getMockFixture().getMockEndpoint();
        awaitFirstConnection(mock);

        String connectionId = connector.getConnectionIds()[0];
        NotificationListener listener = connectionListener();
        Route route = getContext().getRoutes().get(0);
        getContext().getRouteController().stopRoute(route.getRouteId());

        listener.handleNotification(new JMXConnectionNotification(
                JMXConnectionNotification.FAILED, connector, connectionId, 1, "Failed to communicate with the server",
                null), null);

        assertFalse(opened.tryAcquire(QUIET_SECONDS, TimeUnit.SECONDS),
                "the consumer connected again although it was stopped");
    }

    private NotificationListener connectionListener() {
        JMXConsumer consumer = (JMXConsumer) getContext().getRoutes().get(0).getConsumer();
        return consumer.getConnectionNotificationListener();
    }

    private void awaitFirstConnection(MockEndpoint mock) throws Exception {
        assertTrue(opened.tryAcquire(20, TimeUnit.SECONDS), "the consumer did not connect at start");
        getSimpleMXBean().touch();
        mock.assertIsSatisfied(20000);
    }

    /**
     * The consumer is subscribed on a connection that works: it must not connect again (waits three times the reconnect
     * delay of one second for a connection that must not come), and one notification is delivered once.
     */
    private void assertKeepsConnectionAndIsSubscribedOnce(MockEndpoint mock) throws Exception {
        assertFalse(opened.tryAcquire(QUIET_SECONDS, TimeUnit.SECONDS),
                "the consumer connected again although it was subscribed on a working connection");

        assertDeliversOnce(mock);
    }

    private void assertDeliversOnce(MockEndpoint mock) throws Exception {
        mock.reset();
        mock.setExpectedMessageCount(1);
        getSimpleMXBean().touch();
        mock.assertIsSatisfied(20000);
        assertEquals(1, mock.getReceivedCounter());
    }

    /**
     * Waits until the connector server has accepted no connection for the given number of seconds; gives up after the
     * given number of periods that had a connection.
     */
    private boolean awaitNoConnectionFor(int seconds, int attempts) throws InterruptedException {
        for (int i = 0; i < attempts; i++) {
            if (!opened.tryAcquire(seconds, TimeUnit.SECONDS)) {
                return true;
            }
            opened.drainPermits();
        }
        return false;
    }

    /**
     * Touches the bean until the consumer delivers a notification (bounded: 150 rounds of at most 200 ms).
     */
    private void awaitSubscribed(MockEndpoint mock) throws Exception {
        for (int i = 0; i < 150; i++) {
            try {
                getSimpleMXBean().touch();
            } catch (Exception e) {
                if (!(e.getCause() instanceof InstanceNotFoundException)) {
                    throw e;
                }
            }
            if (mock.await(200, TimeUnit.MILLISECONDS)) {
                return;
            }
        }
        mock.assertIsSatisfied(0);
    }
}
