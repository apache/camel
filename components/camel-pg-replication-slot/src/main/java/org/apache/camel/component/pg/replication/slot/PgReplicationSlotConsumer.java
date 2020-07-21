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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.ExtendedExchange;
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
        super.doStart();

        this.connect();

        if (this.scheduledExecutor == null) {
            this.scheduledExecutor = this.getEndpoint().getCamelContext().getExecutorServiceManager()
                    .newSingleThreadScheduledExecutor(this, "PgReplicationStatusUpdateSender");
        }
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
        PGReplicationStream stream = getStream();

        // If the stream is null, this means the slot is active, i.e. used by another connection. We'll try
        // again on the next poll.
        if (stream == null) {
            return 0;
        }

        try {
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
            // If the cause of the exception is that connection is lost, we'll try to reconnect so in the next poll a
            // new connection will be available.
            if (e.getCause() instanceof SocketException) {
                LOG.info("Connection to PosgreSQL server has been lost, trying to reconnect.");
                this.connect();
            }
            throw e;
        }

        Exchange exchange = this.endpoint.createExchange();
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

        exchange.adapt(ExtendedExchange.class).addOnCompletion(new Synchronization() {
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
            getExceptionHandler().handleException("Error during processing exchange. Will attempt to process the message on next poll.", exchange, cause);
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
        String sql = String.format("SELECT count(*) FROM pg_replication_slots WHERE slot_name = '%s';", this.endpoint.getSlot());

        try (Statement statement = this.connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
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
        String sql = String.format("SELECT count(*) FROM pg_replication_slots where slot_name = '%s' AND active = true;",
                this.endpoint.getSlot());

        try (Statement statement = this.connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }

    private void connect() throws SQLException {
        if (this.connection != null) {
            this.connection.close();
        }

        this.connection = this.endpoint.newDbConnection();
        this.pgConnection = this.connection.unwrap(PGConnection.class);
        this.replicationStream = null;

        if (this.endpoint.getAutoCreateSlot() && !this.isSlotCreated()) {
            this.createSlot();
        }
    }
}
