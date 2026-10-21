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

import java.net.SocketException;
import java.nio.ByteBuffer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.support.ScheduledPollConsumer;
import org.postgresql.PGConnection;
import org.postgresql.replication.PGReplicationStream;
import org.postgresql.replication.fluent.logical.ChainedLogicalStreamBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The pg-replication-slot consumer.
 */
public class PgReplicationSlotConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PgReplicationSlotConsumer.class);

    private final PgReplicationSlotEndpoint endpoint;

    private Connection connection;
    private PGConnection pgConnection;
    private PGReplicationStream replicationStream;

    private ScheduledExecutorService scheduledExecutor;

    private byte[] payload;

    PgReplicationSlotConsumer(PgReplicationSlotEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        this.connect();

        if (this.scheduledExecutor == null) {
            this.scheduledExecutor = this.getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "PgReplicationStatusUpdateSender");
        }

        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }

        if (this.scheduledExecutor != null) {
            this.getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(this.scheduledExecutor);
            this.scheduledExecutor = null;
        }
    }

    @Override
    protected int poll() throws Exception {
        PGReplicationStream stream;

        try {
            // The connection was lost in an earlier poll (or connecting again failed): connect again.
            if (this.connection == null) {
                LOG.debug("Connecting to PostgreSQL server again.");
                this.connect();
            }

            stream = getStream();

            // If the stream is null, this means the slot is active, i.e. used by another connection. We'll try
            // again on the next poll.
            if (stream == null) {
                return 0;
            }

            // The same payload will be sent again and again until the processing is completed successfully.
            // We should not read another payload before that to guarantee the order of processing.
            if (this.payload == null) {
                ByteBuffer msg = stream.readPending();

                if (msg == null) {
                    return 0;
                }

                int offset = msg.arrayOffset();
                byte[] source = msg.array();
                int length = source.length - offset;

                this.payload = new byte[length];
                System.arraycopy(source, offset, this.payload, 0, length);
            }
        } catch (SQLException e) {
            // If the connection is lost, we drop it so that the next poll connects again. A connection is lost when
            // the server went away (the driver reports the I/O error of the socket, SQL state class 08, also when
            // the server closed the connection) or shut the session down (SQL state class 57P, e.g. a server that
            // is restarted), and when the driver closed the connection.
            if (isConnectionLost(e)) {
                if (this.disconnect()) {
                    LOG.info("Connection to PostgreSQL server has been lost, will connect again at the next poll: {}",
                            e.getMessage());
                } else {
                    LOG.debug("Not connected to PostgreSQL server, will connect again at the next poll: {}", e.getMessage());
                }
            }
            throw e;
        }

        Exchange exchange = createExchange(true);
        exchange.setExchangeId(stream.getLastReceiveLSN().asString());

        Message message = exchange.getIn();
        message.setBody(this.payload);

        final long delay = this.endpoint.getStatusInterval();
        ScheduledFuture<?> scheduledFuture = this.scheduledExecutor.scheduleAtFixedRate(() -> {
            try {
                LOG.debug("Processing took too long. Sending status update to avoid disconnect.");
                stream.forceUpdateStatus();
            } catch (SQLException e) {
                LOG.error(e.getMessage(), e);
            }
        }, delay, delay, TimeUnit.SECONDS);

        exchange.getExchangeExtension().addOnCompletion(new Synchronization() {
            @Override
            public void onComplete(Exchange exchange) {
                processCommit(exchange);
                scheduledFuture.cancel(true);
            }

            @Override
            public void onFailure(Exchange exchange) {
                processRollback(exchange);
                scheduledFuture.cancel(true);
            }
        });

        getProcessor().process(exchange);

        return 1;
    }

    private void processCommit(Exchange exchange) {
        try {
            // Reset the `payload` buffer first because it's already processed, and in case of losing the connection
            // while updating the status, the next poll will try to reconnect again instead of processing the stale payload.
            this.payload = null;

            PGReplicationStream stream = getStream();

            if (stream == null) {
                return;
            }

            stream.setAppliedLSN(stream.getLastReceiveLSN());
            stream.setFlushedLSN(stream.getLastReceiveLSN());
            stream.forceUpdateStatus();
        } catch (SQLException e) {
            getExceptionHandler().handleException("Exception while sending feedback to PostgreSQL.", exchange, e);
        }
    }

    private void processRollback(Exchange exchange) {
        Exception cause = exchange.getException();
        if (cause != null) {
            getExceptionHandler().handleException(
                    "Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
        }
    }

    private void createSlot() throws SQLException {
        this.pgConnection.getReplicationAPI()
                .createReplicationSlot()
                .logical()
                .withSlotName(this.endpoint.getSlot())
                .withOutputPlugin(this.endpoint.getOutputPlugin())
                .make();
    }

    private boolean isSlotCreated() throws SQLException {
        String sql = "SELECT count(*) FROM pg_replication_slots WHERE slot_name = ?";

        try (PreparedStatement ps = this.connection.prepareStatement(sql)) {
            ps.setString(1, this.endpoint.getSlot());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    private PGReplicationStream getStream() throws SQLException {
        if (this.replicationStream != null && !this.replicationStream.isClosed()) {
            return this.replicationStream;
        }

        if (isSlotActive()) {
            LOG.debug(String.format("Slot: %s is active. Waiting for it to be available.", this.endpoint.getSlot()));
            return null;
        }

        ChainedLogicalStreamBuilder streamBuilder = this.pgConnection.getReplicationAPI()
                .replicationStream()
                .logical()
                .withSlotName(this.endpoint.getSlot())
                .withStatusInterval(this.endpoint.getStatusInterval(), TimeUnit.SECONDS);

        Properties slotOptions = new Properties();
        slotOptions.putAll(this.endpoint.getSlotOptions());
        streamBuilder.withSlotOptions(slotOptions);

        this.replicationStream = streamBuilder.start();

        return this.replicationStream;
    }

    private boolean isSlotActive() throws SQLException {
        String sql = "SELECT count(*) FROM pg_replication_slots WHERE slot_name = ? AND active = true";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, endpoint.getSlot());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        }
        return false;
    }

    private boolean isConnectionLost(SQLException e) {
        String state = e.getSQLState();
        if (e.getCause() instanceof SocketException
                || (state != null && (state.startsWith("08") || state.startsWith("57P")))) {
            return true;
        }
        try {
            return this.connection == null || this.connection.isClosed();
        } catch (SQLException ex) {
            return true;
        }
    }

    /**
     * Closes the connection, if any, and forgets it and its stream. The payload that was read but not processed
     * successfully is forgotten too: it was not flushed, so the server sends it again on the next connection (the
     * stream starts at the slot's confirmed flush position).
     *
     * @return true if there was a connection to close
     */
    private boolean disconnect() {
        Connection previous = this.connection;
        this.connection = null;
        this.pgConnection = null;
        this.replicationStream = null;
        this.payload = null;
        if (previous == null) {
            return false;
        }
        try {
            previous.close();
        } catch (SQLException e) {
            LOG.debug("Error closing the lost connection to PostgreSQL server. This exception is ignored.", e);
        }
        return true;
    }

    private void connect() throws SQLException {
        disconnect();

        this.connection = this.endpoint.newDbConnection();
        try {
            this.pgConnection = this.connection.unwrap(PGConnection.class);

            if (Boolean.TRUE.equals(this.endpoint.getAutoCreateSlot()) && !this.isSlotCreated()) {
                this.createSlot();
            }
        } catch (SQLException | RuntimeException e) {
            // leave no half-set-up connection behind, so that the next attempt starts from the beginning
            disconnect();
            throw e;
        }
    }
}
