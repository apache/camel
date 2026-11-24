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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * With testConnectionOnStartup=false the consumer starts in the background when the server cannot be reached at start.
 * That delayed start waits for the account's lock while another consumer or producer of the account connects or logs
 * in. When the route is stopped meanwhile, the delayed start must not start the stopped consumer when it gets the lock:
 * no listener on the connection, no connection monitor, and the account's connection is closed once its other users
 * released it.
 */
class XmppConsumerStopDuringDelayedStartTest {

    private static final String ROOM = "dev@conference." + FakeXmppServer.DOMAIN;

    private DefaultCamelContext context;
    private FakeXmppServer server;

    @AfterEach
    void stop() throws Exception {
        if (context != null) {
            context.stop();
        }
        if (server != null) {
            server.close();
        }
    }

    @Test
    void chatConsumerStoppedWhileItsDelayedStartWaitsForTheLockIsNotStarted() throws Exception {
        int port = freePort();
        stopWhileTheDelayedStartWaitsForTheLock(port, "xmpp://127.0.0.1:" + port + "/bob@" + FakeXmppServer.DOMAIN
                                                      + "?connectionConfig=#connectionConfig&connectionPollDelay=3"
                                                      + "&testConnectionOnStartup=false");
    }

    @Test
    void roomConsumerStoppedWhileItsDelayedStartWaitsForTheLockIsNotStarted() throws Exception {
        int port = freePort();
        stopWhileTheDelayedStartWaitsForTheLock(port, "xmpp://127.0.0.1:" + port + "?room=" + ROOM + "&nickname=camel"
                                                      + "&connectionConfig=#connectionConfig&connectionPollDelay=3"
                                                      + "&testConnectionOnStartup=false");
        assertEquals(0, server.occupants(ROOM), "the stopped consumer entered its room");
    }

    private void stopWhileTheDelayedStartWaitsForTheLock(int port, String uri) throws Exception {
        startRoute(port, uri);
        XmppConsumer consumer = (XmppConsumer) context.getRoute("in").getConsumer();
        XmppEndpoint endpoint = (XmppEndpoint) consumer.getEndpoint();
        XmppComponent component = endpoint.getComponent();

        // the server is reachable now; a producer of the account has the connection, and holds the account's lock (as
        // while it logs in again) when the delayed start (connectionPollDelay=3) runs
        server = new FakeXmppServer(port);
        Object producer = new Object();
        component.acquireConnection(endpoint, producer);
        ReentrantLock lock = component.connectionLock(endpoint);
        lock.lock();
        try {
            await().atMost(10, TimeUnit.SECONDS).until(lock::hasQueuedThreads);
            context.getRouteController().stopRoute("in");
        } finally {
            lock.unlock();
        }
        // the producer stops too
        component.releaseConnection(endpoint, producer);

        // the delayed start gets the lock and the connection, and gives the connection back
        await().atMost(10, TimeUnit.SECONDS).until(() -> !lock.isLocked() && !lock.hasQueuedThreads()
                && component.connectionUsers(endpoint) == 0);
        await().atMost(10, TimeUnit.SECONDS).until(() -> server.boundSessions() == 0);
        await().atMost(10, TimeUnit.SECONDS).until(() -> Thread.getAllStackTraces().keySet().stream()
                .noneMatch(t -> t.isAlive() && t.getName().contains("connectionPoll")));
        assertNull(consumer.connection, "the stopped consumer holds a connection");
        assertEquals(ServiceStatus.Stopped, context.getRouteController().getRouteStatus("in"));
    }

    private void startRoute(int port, String uri) throws Exception {
        context = new DefaultCamelContext();
        // the test server has no TLS
        context.getRegistry().bind("connectionConfig", XMPPTCPConnectionConfiguration.builder()
                .setHostAddress(InetAddress.getLoopbackAddress())
                .setPort(port)
                .setXmppDomain(FakeXmppServer.DOMAIN)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .performSaslAnonymousAuthentication()
                .build());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("in").to("mock:out");
            }
        });
        // nothing listens on the port yet, so the consumer starts in the background
        context.start();
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }
}
