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
package org.apache.camel.component.pgevent;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import com.impossibl.postgres.api.jdbc.PGConnection;
import com.impossibl.postgres.api.jdbc.PGNotificationListener;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.TaskRunFailureException;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PgEvent consumer.
 */
public class PgEventConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(PgEventConsumer.class);

    private final PgEventListener listener = new PgEventListener();
    private final PgEventEndpoint endpoint;
    private PGConnection dbConnection;
    private ScheduledExecutorService reconnectPool;
    private BackgroundTask reconnectTask;
    private ExecutorService workerPool;
    private boolean shutdownWorkerPool;

    public PgEventConsumer(PgEventEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    public PgEventListener getPgEventListener() {
        return listener;
    }

    @Override
    protected void doInit() throws Exception {
        if (endpoint.getWorkerPool() != null) {
            workerPool = endpoint.getWorkerPool();
        } else {
            workerPool = endpoint.createWorkerPool(this);
            shutdownWorkerPool = true;
        }
        // used for re-connecting to the database
        reconnectPool = getEndpoint().getCamelContext().getExecutorServiceManager()
                .newSingleThreadScheduledExecutor(this, "PgEventReconnect");
        reconnectTask = Tasks.backgroundTask()
                .withScheduledExecutor(reconnectPool)
                .withBudget(Budgets.iterationTimeBudget()
                        .withInterval(Duration.ofMillis(endpoint.getReconnectDelay()))
                        .withInitialDelay(Duration.ofSeconds(1))
                        .withUnlimitedDuration()
                        .build())
                .withName("PgEventReconnect")
                .build();
    }

    @Override
    protected void doStart() throws Exception {
        listener.initConnection();
        super.doStart();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        listener.closeConnection();
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(reconnectPool);
        if (shutdownWorkerPool && workerPool != null) {
            LOG.debug("Shutting down PgEventConsumer worker threads with timeout {} millis", 10000);
            endpoint.getCamelContext().getExecutorServiceManager().shutdownGraceful(workerPool, 10000);
            workerPool = null;
        }
    }

    public class PgEventListener implements PGNotificationListener {

        public void reconnect() {
            // only submit the task if not already running
            if (!reconnectTask.isRunning()) {
                reconnectTask.run(endpoint.getCamelContext(), () -> {
                    if (isRunAllowed()) {
                        LOG.debug("Connecting attempt #{}", reconnectTask.iteration());
                        try {
                            initConnection();
                        } catch (Exception e) {
                            String message
                                    = "Failed to connect attempt #" + reconnectTask.iteration() + " due to: " + e.getMessage();
                            getExceptionHandler().handleException(message, e);
                            // make the task runner aware of the exception (will retry)
                            throw new TaskRunFailureException(message, e);
                        }
                        LOG.debug("Connecting successful");
                    }
                    return false;
                });
            }
        }

        public void initConnection() throws Exception {
            dbConnection = endpoint.initJdbc();
            String channel = endpoint.getChannel();
            if (!channel.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("Invalid channel name");
            }
            String sql = String.format("LISTEN %s", channel);
            try (PreparedStatement statement = dbConnection.prepareStatement(sql)) { // NOSONAR
                statement.execute();
            }
            dbConnection.addNotificationListener(channel, channel, listener);
        }

        public void closeConnection() throws Exception {
            if (dbConnection != null) {
                try {
                    String channel = endpoint.getChannel();
                    if (!channel.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
                        throw new IllegalArgumentException("Invalid channel name");
                    }
                    dbConnection.removeNotificationListener(channel);
                    String sql = String.format("UNLISTEN %s", channel);
                    try (PreparedStatement statement = dbConnection.prepareStatement(sql)) { // NOSONAR
                        statement.execute();
                    }
                    dbConnection.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            dbConnection = null;
        }

        @Override
        public void notification(int processId, String channel, String payload) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Notification processId: {}, channel: {}, payload: {}", processId, channel, payload);
            }

            Exchange exchange = createExchange(false);
            Message msg = exchange.getIn();
            msg.setHeader(PgEventConstants.HEADER_CHANNEL, channel);
            msg.setBody(payload);

            // use worker pool to avoid blocking notification thread
            if (workerPool != null) {
                workerPool.submit(() -> {
                    try {
                        getProcessor().process(exchange);
                    } catch (Exception e) {
                        exchange.setException(e);
                    }
                    if (exchange.getException() != null) {
                        String cause
                                = "Unable to process incoming notification from PostgreSQL: processId='" + processId
                                  + "', channel='"
                                  + channel + "', payload='" + payload + "'";
                        getExceptionHandler().handleException(cause, exchange.getException());
                    }
                    releaseExchange(exchange, false);
                });
            }
        }

        @Override
        public void closed() {
            // connection lost, so we need to re-connect
            LOG.warn("Connection to PostgreSQL lost unexpected. Re-connecting...");
            reconnect();
        }
    }

}
