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
package org.apache.camel.component.pg.replication.slot;

import java.io.EOFException;
import java.net.ConnectException;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.postgresql.PGConnection;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationConnection;
import org.postgresql.replication.PGReplicationStream;
import org.postgresql.replication.fluent.ChainedStreamBuilder;
import org.postgresql.replication.fluent.logical.ChainedLogicalStreamBuilder;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The consumer must connect again after the PostgreSQL server went away and came back. The JDBC objects are mocks that
 * fail the way the PostgreSQL JDBC driver (42.7.x) fails:
 * <ul>
 * <li>a read from the replication stream of a connection whose server went away throws "Database connection failed when
 * reading from copy" (SQL state 08006, the cause is the {@link java.io.IOException} of the socket, an
 * {@link EOFException} when the server closed the connection); the driver ends the copy operation first, so the stream
 * reports itself closed from then on. A failed status update ("Database connection failed when writing to copy") does
 * the same;</li>
 * <li>when the server ended the replication (CopyDone), the read returns null and the stream is closed;</li>
 * <li>a query on a connection whose server went away throws "An I/O error occurred while sending to the backend."
 * (08006) and the driver closes (aborts) the connection; every later use of it throws "This connection has been
 * closed." (08003), and a closed stream "This replication stream has been closed." (08003).</li>
 * </ul>
 * The polls are run by the test (startScheduler=false), so nothing depends on timing.
 */
class PgReplicationSlotConsumerReconnectTest {

    private static final int MAX_POLLS = 5;

    @Test
    void consumesAgainAfterTheServerClosedTheConnection() throws Exception {
        FakeServer server = new FakeServer(new EOFException("Unexpected end of stream"));
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("first");
            pollUntilReceived(consumer, server, 1);

            // a restart of the server: the connection is gone, the server cannot be reached for two polls
            server.stop();
            pollIgnoringFailures(consumer, server, 2);
            server.start();
            server.publish("second");

            pollUntilReceived(consumer, server, 2);
            assertEquals(List.of("first", "second"), server.received,
                    "the consumer did not consume again after the PostgreSQL server was back");
        }
    }

    @Test
    void consumesAgainWhenTheServerWasStillDownAtTheFirstReconnect() throws Exception {
        FakeServer server = new FakeServer(new SocketException("Connection reset"));
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("first");
            pollUntilReceived(consumer, server, 1);

            server.stop();
            pollIgnoringFailures(consumer, server, 2);
            server.start();
            server.publish("second");

            pollUntilReceived(consumer, server, 2);
            assertEquals(List.of("first", "second"), server.received,
                    "the consumer did not consume again after a reconnect attempt had failed");
        }
    }

    @Test
    void consumesAgainAfterTheServerEndedTheReplication() throws Exception {
        FakeServer server = new FakeServer(new EOFException("Unexpected end of stream"));
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("first");
            pollUntilReceived(consumer, server, 1);

            // a restart of the server: the server ends the replication (the read returns null and the stream is
            // closed) and closes the connection, so the query of the next poll fails with an I/O error
            server.endReplication();
            server.stop();
            pollIgnoringFailures(consumer, server, 2);
            server.start();
            server.publish("second");

            pollUntilReceived(consumer, server, 2);
            assertEquals(List.of("first", "second"), server.received,
                    "the consumer did not consume again after the server had ended the replication");
        }
    }

    @Test
    void deliversAMessageWhoseProcessingFailedOnceAfterTheReconnect() throws Exception {
        FakeServer server = new FakeServer(new EOFException("Unexpected end of stream"));
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("first");
            pollUntilReceived(consumer, server, 1);

            // the processing of "second" fails; while it runs, the server goes away and the status update that the
            // consumer sends during a long processing fails, which closes the stream
            server.failFirstDelivery("second", () -> {
                server.stop();
                server.sendStatusUpdate();
            });
            server.publish("second");
            pollIgnoringFailures(consumer, server, 1);
            assertEquals(1, server.failedDeliveries, "the first delivery of the message did not fail");

            pollIgnoringFailures(consumer, server, 2);
            server.start();

            pollUntilReceived(consumer, server, 2);
            // more polls: a message that was delivered again from the old connection's buffer would show up here
            pollIgnoringFailures(consumer, server, 2);
            assertEquals(List.of("first", "second"), server.received,
                    "the message whose processing failed was not delivered exactly once after the reconnect");
        }
    }

    @Test
    void keepsTheConnectionAfterAnErrorThatIsNotAConnectionLoss() throws Exception {
        FakeServer server = new FakeServer(new EOFException("Unexpected end of stream"));
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            PgReplicationSlotConsumer consumer = startRoute(context, server);

            server.publish("first");
            pollUntilReceived(consumer, server, 1);

            // an error of the server that leaves the connection usable (42704: undefined object)
            server.nextReadError = new PSQLException(
                    "ERROR: replication slot \"slot\" does not exist",
                    PSQLState.UNDEFINED_OBJECT);
            pollIgnoringFailures(consumer, server, 1);
            server.publish("second");

            pollUntilReceived(consumer, server, 2);
            assertEquals(1, server.connections.size(), "the consumer replaced a connection that was not lost");
        }
    }

    private static PgReplicationSlotConsumer startRoute(DefaultCamelContext context, FakeServer server) throws Exception {
        PgReplicationSlotComponent component = new PgReplicationSlotComponent();
        context.addComponent("pg-replication-slot", component);
        PgReplicationSlotEndpoint endpoint
                = new PgReplicationSlotEndpoint("pg-replication-slot://localhost:5432/db/slot:test_decoding", component) {
                    @Override
                    Connection newDbConnection() throws SQLException {
                        return server.connect();
                    }
                };
        endpoint.setCamelContext(context);
        endpoint.setStartScheduler(false);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(endpoint).routeId("slot")
                        .process(e -> server.deliver(new String(e.getIn().getBody(byte[].class), StandardCharsets.UTF_8)));
            }
        });
        context.start();
        return (PgReplicationSlotConsumer) context.getRoute("slot").getConsumer();
    }

    private static void pollIgnoringFailures(PgReplicationSlotConsumer consumer, FakeServer server, int polls) {
        for (int i = 0; i < polls; i++) {
            try {
                consumer.poll();
            } catch (Exception e) {
                // the server is down: failing polls are expected
                server.pollFailures.add(e.getMessage());
            }
        }
    }

    private static void pollUntilReceived(PgReplicationSlotConsumer consumer, FakeServer server, int count) {
        for (int i = 0; i < MAX_POLLS && server.received.size() < count; i++) {
            try {
                consumer.poll();
            } catch (Exception e) {
                server.pollFailures.add(e.getMessage());
            }
        }
        assertTrue(server.received.size() >= count,
                "the consumer did not consume again after the PostgreSQL server was back; received " + server.received
                                                    + ", poll failures " + server.pollFailures);
    }

    /**
     * A PostgreSQL server with one logical replication slot. Messages that were not flushed stay in the slot and are
     * sent again on the next replication stream, as the server does (the stream starts at the slot's confirmed flush
     * position).
     */
    static final class FakeServer {
        final List<String> received = new CopyOnWriteArrayList<>();
        final List<String> pollFailures = new ArrayList<>();
        private final List<String> slot = new ArrayList<>();
        final List<FakeConnection> connections = new ArrayList<>();
        private final Exception ioFailureCause;
        private final Map<String, Runnable> failFirstDelivery = new HashMap<>();
        private boolean up = true;
        int failedDeliveries;
        SQLException nextReadError;

        FakeServer(Exception ioFailureCause) {
            this.ioFailureCause = ioFailureCause;
        }

        void publish(String message) {
            slot.add(message);
        }

        void stop() {
            up = false;
            connections.forEach(c -> c.serverGone = true);
        }

        void start() {
            up = true;
        }

        /**
         * The server ends the replication of every open stream (CopyDone).
         */
        void endReplication() {
            connections.forEach(c -> c.replicationEnded = true);
        }

        /**
         * Sends a status update on the newest stream, as the consumer's status-update task does while an exchange is
         * processed; a failure is ignored, as that task does (it logs it).
         */
        void sendStatusUpdate() {
            try {
                connections.get(connections.size() - 1).stream.forceUpdateStatus();
            } catch (SQLException e) {
                // the task logs it
            }
        }

        /**
         * The first delivery of the given message runs the action and then fails.
         */
        void failFirstDelivery(String message, Runnable action) {
            failFirstDelivery.put(message, action);
        }

        void deliver(String message) {
            Runnable action = failFirstDelivery.remove(message);
            if (action != null) {
                failedDeliveries++;
                action.run();
                throw new IllegalStateException("processing of " + message + " failed");
            }
            received.add(message);
        }

        Connection connect() throws SQLException {
            if (!up) {
                throw new PSQLException(
                        "Connection to localhost:5432 refused.", PSQLState.CONNECTION_UNABLE_TO_CONNECT,
                        new ConnectException("Connection refused"));
            }
            FakeConnection c = new FakeConnection(this);
            connections.add(c);
            return c.connection;
        }
    }

    static final class FakeConnection {
        final Connection connection = mock(Connection.class);
        final PGReplicationStream stream = mock(PGReplicationStream.class);
        private final FakeServer server;
        boolean closed;
        boolean serverGone;
        boolean replicationEnded;
        private boolean streamClosed = true;
        private int sent;

        FakeConnection(FakeServer server) throws SQLException {
            this.server = server;
            PGConnection pg = mock(PGConnection.class);
            PGReplicationConnection replication = mock(PGReplicationConnection.class);
            ChainedStreamBuilder streamBuilder = mock(ChainedStreamBuilder.class);
            ChainedLogicalStreamBuilder logical = mock(ChainedLogicalStreamBuilder.class, RETURNS_SELF);

            when(connection.unwrap(PGConnection.class)).thenReturn(pg);
            when(connection.isClosed()).thenAnswer(i -> closed);
            doAnswer(i -> closed = true).when(connection).close();
            when(connection.prepareStatement(anyString())).thenAnswer(i -> {
                checkConnectionOpen();
                String sql = i.getArgument(0);
                ResultSet rs = mock(ResultSet.class);
                when(rs.next()).thenReturn(true);
                // the slot exists and is not used by another connection
                when(rs.getInt(1)).thenReturn(sql.contains("active = true") ? 0 : 1);
                PreparedStatement ps = mock(PreparedStatement.class);
                when(ps.executeQuery()).thenAnswer(q -> {
                    query();
                    return rs;
                });
                return ps;
            });

            when(pg.getReplicationAPI()).thenReturn(replication);
            when(replication.replicationStream()).thenReturn(streamBuilder);
            when(streamBuilder.logical()).thenReturn(logical);
            when(logical.start()).thenAnswer(i -> {
                query();
                // the server sends again everything that was not flushed
                sent = 0;
                streamClosed = false;
                replicationEnded = false;
                return stream;
            });

            when(stream.isClosed()).thenAnswer(i -> streamClosed || closed);
            when(stream.readPending()).thenAnswer(i -> {
                checkStreamOpen();
                if (replicationEnded) {
                    streamClosed = true;
                    return null;
                }
                if (serverGone) {
                    streamClosed = true;
                    throw new PSQLException(
                            "Database connection failed when reading from copy", PSQLState.CONNECTION_FAILURE,
                            server.ioFailureCause);
                }
                if (server.nextReadError != null) {
                    SQLException e = server.nextReadError;
                    server.nextReadError = null;
                    // the error response ends the replication; the connection stays usable
                    streamClosed = true;
                    throw e;
                }
                if (sent < server.slot.size()) {
                    return ByteBuffer.wrap(server.slot.get(sent++).getBytes(StandardCharsets.UTF_8));
                }
                return null;
            });
            when(stream.getLastReceiveLSN()).thenAnswer(i -> LogSequenceNumber.valueOf(sent));
            doAnswer(i -> {
                checkStreamOpen();
                if (serverGone) {
                    streamClosed = true;
                    throw new PSQLException(
                            "Database connection failed when writing to copy", PSQLState.CONNECTION_FAILURE,
                            server.ioFailureCause);
                }
                // the server forgets what was flushed
                for (int n = 0; n < sent; n++) {
                    server.slot.remove(0);
                }
                sent = 0;
                return null;
            }).when(stream).forceUpdateStatus();
        }

        private void checkConnectionOpen() throws SQLException {
            if (closed) {
                throw new PSQLException("This connection has been closed.", PSQLState.CONNECTION_DOES_NOT_EXIST);
            }
        }

        private void checkStreamOpen() throws SQLException {
            if (streamClosed || closed) {
                throw new PSQLException("This replication stream has been closed.", PSQLState.CONNECTION_DOES_NOT_EXIST);
            }
        }

        private void query() throws SQLException {
            checkConnectionOpen();
            if (serverGone) {
                // the driver closes (aborts) a connection whose query failed with an I/O error
                closed = true;
                throw new PSQLException(
                        "An I/O error occurred while sending to the backend.", PSQLState.CONNECTION_FAILURE,
                        server.ioFailureCause);
            }
        }
    }
}
