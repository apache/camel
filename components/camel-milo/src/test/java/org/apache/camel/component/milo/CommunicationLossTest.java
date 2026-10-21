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
package org.apache.camel.component.milo;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.client.MiloClientConnection;
import org.apache.camel.component.milo.client.MiloClientEndpoint;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A consumer reports its nodes with {@code Bad_NoCommunication} when communication with the server is lost, and
 * delivers their current values again once it is restored. The client reaches the server through a TCP relay of the
 * test, which drops the connection: the client then activates its session again, and the server resends the values.
 */
public class CommunicationLossTest extends AbstractMiloServerTest {

    private static final String DIRECT_START_1 = "direct:start1";

    private static final String MILO_SERVER_ITEM_1 = "milo-server:myitem1";

    private static final String MILO_CLIENT_BASE = "milo-client:opc.tcp://foo:bar@localhost:@@relay@@"
                                                   + "?allowedSecurityPolicies=None&overrideHost=true&overridePort=true"
                                                   + "&samplingInterval=50&requestedPublishingInterval=100";

    private static final String MILO_CLIENT_ITEM_1
            = MILO_CLIENT_BASE + "&node=" + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "myitem1");

    private static final String MOCK_ITEM_1 = "mock:item1";

    private static final String MOCK_LATE = "mock:late";

    private static final String MOCK_RECONNECT_TIMEOUT = "mock:reconnectTimeout";

    private static final String MOCK_UNKNOWN = "mock:unknown";

    private static final String MILO_CLIENT_UNKNOWN
            = MILO_CLIENT_BASE + "&node=" + NodeIds.nodeValue(MiloServerComponent.DEFAULT_NAMESPACE_URI, "unknownItem");

    @EndpointInject(MOCK_ITEM_1)
    protected MockEndpoint item1Endpoint;

    @EndpointInject(MOCK_LATE)
    protected MockEndpoint lateEndpoint;

    @EndpointInject(MOCK_RECONNECT_TIMEOUT)
    protected MockEndpoint reconnectTimeoutEndpoint;

    @EndpointInject(MOCK_UNKNOWN)
    protected MockEndpoint unknownEndpoint;

    @Produce(DIRECT_START_1)
    protected ProducerTemplate producer1;

    @RegisterExtension
    AvailablePortFinder.Port relayPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port lateRelayPort = AvailablePortFinder.find();

    @RegisterExtension
    AvailablePortFinder.Port reconnectTimeoutRelayPort = AvailablePortFinder.find();

    private final Relay relay = new Relay();

    private final Relay lateRelay = new Relay();

    private final Relay reconnectTimeoutRelay = new Relay();

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(DIRECT_START_1).to(MILO_SERVER_ITEM_1);

                // the consumers deliver the current values while they start, so the tests start these routes
                from(throughRelay(MILO_CLIENT_ITEM_1)).routeId("item1").autoStartup(false).to(MOCK_ITEM_1);

                // a node the server refuses, on the same connection
                from(throughRelay(MILO_CLIENT_UNKNOWN)).routeId("unknown").autoStartup(false).to(MOCK_UNKNOWN);

                // its own relay, so its own connection, which the test starts while the relay is down
                from(resolve(MILO_CLIENT_ITEM_1).replace("@@relay@@", Integer.toString(lateRelayPort.getPort())))
                        .routeId("late").autoStartup(false).to(MOCK_LATE);

                // the same, with a delay between two connection attempts shorter than the default
                from(resolve(MILO_CLIENT_ITEM_1).replace("@@relay@@", Integer.toString(reconnectTimeoutRelayPort.getPort()))
                     + "&reconnectTimeout=1000")
                        .routeId("reconnectTimeout").autoStartup(false).to(MOCK_RECONNECT_TIMEOUT);
            }
        };
    }

    /**
     * The URI of a consumer that reaches the server through the relay. These consumers share one connection, which
     * creates the refused node again every second.
     */
    private String throughRelay(final String uri) {
        return resolve(uri).replace("@@relay@@", Integer.toString(relayPort.getPort())) + "&reconnectTimeout=1000";
    }

    @Override
    protected void doPostSetup() throws Exception {
        relay.start(relayPort.getPort(), getServerPort());
    }

    @Override
    protected void doPostTearDown() throws Exception {
        // after the context, so that its clients disconnect through the relays
        relay.stop();
        lateRelay.stop();
        reconnectTimeoutRelay.stop();
    }

    @Test
    void lostSessionReportsTheNodeAndResendsItsValue() throws Exception {
        producer1.sendBody("Foo");

        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        // the refused node gets its refusal once, and keeps it through the losses below
        unknownEndpoint.expectedMessageCount(1);
        testBody(unknownEndpoint.message(0),
                value -> assertEquals(StatusCodes.Bad_NodeIdUnknown, value.getStatusCode().getValue()));
        context.getRouteController().startRoute("unknown");
        unknownEndpoint.assertIsSatisfied();
        unknownEndpoint.reset();
        unknownEndpoint.expectedMessageCount(0);
        unknownEndpoint.setSleepForEmptyTest(500);

        final MiloClientEndpoint endpoint = context.getEndpoint(throughRelay(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final NodeId sessionId = client.getSession().getSessionId();

            // the connection drops: the node is reported, then the reactivated session resends its unchanged value.
            // The test server answers the Publish requests it still queues for the dropped channel first, one per
            // keep-alive interval, so the resent value reaches the client through its republish of the missed
            // notification, 10 to 20 seconds later
            item1Endpoint.reset();
            item1Endpoint.expectedMessageCount(2);
            item1Endpoint.setResultWaitTime(30_000);
            item1Endpoint.setAssertPeriod(2_000);
            testBody(item1Endpoint.message(0), assertNoCommunication());
            testBody(item1Endpoint.message(1), assertGoodValue("Foo"));
            relay.dropConnections();
            item1Endpoint.assertIsSatisfied();

            // through the same client and session: the connection was not rebuilt, the session not replaced
            assertSame(client, connection.getOpcUaClient());
            assertEquals(sessionId, client.getSession().getSessionId());

            // changes arrive as before
            item1Endpoint.reset();
            item1Endpoint.expectedMessageCount(1);
            testBody(item1Endpoint.message(0), assertGoodValue("Bar"));
            producer1.sendBody("Bar");
            item1Endpoint.assertIsSatisfied();

            // a second loss reports the node once more
            item1Endpoint.reset();
            item1Endpoint.expectedMessageCount(2);
            item1Endpoint.setResultWaitTime(30_000);
            item1Endpoint.setAssertPeriod(2_000);
            testBody(item1Endpoint.message(0), assertNoCommunication());
            testBody(item1Endpoint.message(1), assertGoodValue("Bar"));
            relay.dropConnections();
            item1Endpoint.assertIsSatisfied();

            assertSame(client, connection.getOpcUaClient());
            assertEquals(sessionId, client.getSession().getSessionId());
            unknownEndpoint.assertIsSatisfied();
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    void refusedNodeIsCreatedOnceTheSessionIsActiveAgain() throws Exception {
        producer1.sendBody("Foo");

        item1Endpoint.expectedMessageCount(1);
        testBody(item1Endpoint.message(0), assertGoodValue("Foo"));
        context.getRouteController().startRoute("item1");
        item1Endpoint.assertIsSatisfied();

        unknownEndpoint.expectedMessageCount(1);
        testBody(unknownEndpoint.message(0),
                value -> assertEquals(StatusCodes.Bad_NodeIdUnknown, value.getStatusCode().getValue()));
        context.getRouteController().startRoute("unknown");
        unknownEndpoint.assertIsSatisfied();

        final MiloClientEndpoint endpoint = context.getEndpoint(throughRelay(MILO_CLIENT_ITEM_1), MiloClientEndpoint.class);
        final MiloClientConnection connection = endpoint.createConnection();
        try {
            final OpcUaClient client = connection.getOpcUaClient();
            final NodeId sessionId = client.getSession().getSessionId();

            // the refused node is not reported, and delivers its value once it is created after the session is back
            unknownEndpoint.reset();
            unknownEndpoint.expectedMessageCount(1);
            unknownEndpoint.setResultWaitTime(60_000);
            unknownEndpoint.setAssertPeriod(2_000);
            testBody(unknownEndpoint.message(0), assertGoodValue("Baz"));
            item1Endpoint.reset();
            item1Endpoint.expectedMessageCount(2);
            item1Endpoint.setResultWaitTime(60_000);
            item1Endpoint.setAssertPeriod(2_000);
            testBody(item1Endpoint.message(0), assertNoCommunication());
            testBody(item1Endpoint.message(1), assertGoodValue("Foo"));

            // the server stays unreachable for longer than the retry interval of the refused node
            relay.stop();
            await().atMost(10, TimeUnit.SECONDS).until(() -> item1Endpoint.getReceivedCounter() >= 1);
            await().during(3, TimeUnit.SECONDS).atMost(5, TimeUnit.SECONDS)
                    .until(() -> unknownEndpoint.getReceivedCounter() == 0);
            // an attempt while the session is lost is skipped: it does not hold the connection until the session is back
            assertSame(client, CompletableFuture.supplyAsync(connection::getOpcUaClient).get(2, TimeUnit.SECONDS));
            template.sendBody("milo-server:unknownItem", "Baz");
            relay.start(relayPort.getPort(), getServerPort());

            unknownEndpoint.assertIsSatisfied();
            item1Endpoint.assertIsSatisfied();

            // the attempts while the session was lost did not rebuild the connection
            assertSame(client, connection.getOpcUaClient());
            assertEquals(sessionId, client.getSession().getSessionId());
        } finally {
            endpoint.releaseConnection(connection);
        }
    }

    @Test
    void consumerStartedWithoutTheServerReportsTheNodeThenReceivesItsValue() throws Exception {
        producer1.sendBody("Foo");

        // the relay is down: the consumer cannot connect and reports its node while it starts
        lateEndpoint.expectedMessageCount(1);
        lateEndpoint.setAssertPeriod(5_000);
        testBody(lateEndpoint.message(0), assertNoCommunication());
        context.getRouteController().startRoute("late");
        lateEndpoint.assertIsSatisfied();

        // the server becomes reachable: the next connection attempt delivers the current value
        lateEndpoint.reset();
        lateEndpoint.expectedMessageCount(1);
        lateEndpoint.setResultWaitTime(60_000);
        lateEndpoint.setAssertPeriod(2_000);
        testBody(lateEndpoint.message(0), assertGoodValue("Foo"));
        lateRelay.start(lateRelayPort.getPort(), getServerPort());
        lateEndpoint.assertIsSatisfied();
    }

    @Test
    void consumerStartedWithoutTheServerConnectsAfterTheConfiguredReconnectTimeout() throws Exception {
        producer1.sendBody("Foo");

        // the relay is down: the consumer reports its node once, while it keeps trying to connect every second
        reconnectTimeoutEndpoint.expectedMessageCount(1);
        reconnectTimeoutEndpoint.setAssertPeriod(3_000);
        testBody(reconnectTimeoutEndpoint.message(0), assertNoCommunication());
        context.getRouteController().startRoute("reconnectTimeout");
        reconnectTimeoutEndpoint.assertIsSatisfied();

        // the server becomes reachable: the next attempt, at most a second later, delivers the current value, long
        // before the default delay of 10 seconds after the first attempt would have passed
        reconnectTimeoutEndpoint.reset();
        reconnectTimeoutEndpoint.expectedMessageCount(1);
        reconnectTimeoutEndpoint.setResultWaitTime(5_000);
        testBody(reconnectTimeoutEndpoint.message(0), assertGoodValue("Foo"));
        reconnectTimeoutRelay.start(reconnectTimeoutRelayPort.getPort(), getServerPort());
        reconnectTimeoutEndpoint.assertIsSatisfied();
    }

    /**
     * A TCP relay to the server, whose connections the test can drop while it keeps listening, and which can be down
     * while the client tries to connect.
     */
    private static final class Relay {

        private volatile ServerSocket listener;

        private final List<Socket> sockets = new ArrayList<>();

        void start(final int port, final int serverPort) throws IOException {
            final ServerSocket listener = new ServerSocket(port);
            this.listener = listener;
            final Thread acceptor = new Thread(() -> {
                while (!listener.isClosed()) {
                    Socket client = null;
                    try {
                        client = listener.accept();
                        final Socket server = new Socket("localhost", serverPort);
                        synchronized (sockets) {
                            sockets.add(client);
                            sockets.add(server);
                        }
                        pump(client, server);
                        pump(server, client);
                    } catch (final IOException e) {
                        // the listener was closed, or the server refused: nothing to relay
                        if (client != null) {
                            close(client);
                        }
                    }
                }
            }, "relay-" + port);
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private static void pump(final Socket from, final Socket to) {
            final Thread thread = new Thread(() -> {
                final byte[] buffer = new byte[8192];
                try (InputStream in = from.getInputStream(); OutputStream out = to.getOutputStream()) {
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                        out.flush();
                    }
                } catch (final IOException e) {
                    // the connection was dropped
                } finally {
                    close(from);
                    close(to);
                }
            }, "relay-pump");
            thread.setDaemon(true);
            thread.start();
        }

        void dropConnections() {
            synchronized (sockets) {
                sockets.forEach(Relay::close);
                sockets.clear();
            }
        }

        void stop() {
            final ServerSocket listener = this.listener;
            this.listener = null;
            if (listener != null) {
                try {
                    listener.close();
                } catch (final IOException e) {
                    // nothing left to close
                }
            }
            dropConnections();
        }

        private static void close(final Socket socket) {
            try {
                socket.close();
            } catch (final IOException e) {
                // already closed
            }
        }
    }
}
