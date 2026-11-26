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
package org.apache.camel.component.debezium;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import io.debezium.config.Configuration;
import io.debezium.embedded.Connect;
import io.debezium.embedded.async.AsyncEngineConfig;
import io.debezium.engine.ChangeEvent;
import io.debezium.engine.DebeziumEngine;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.URISupport;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebeziumConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DebeziumConsumer.class);

    private static final String TASKS_MAX = "tasks.max";

    private final DebeziumEndpoint endpoint;
    private final EmbeddedDebeziumConfiguration configuration;

    private ExecutorService executorService;
    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> dbzEngine;
    private volatile Throwable engineFailure;
    private volatile boolean engineStopped;
    private volatile boolean engineReady;

    // guards waitingForStart and startFailure, so that an engine failure is either thrown by doStart or handed to the
    // exception handler, never both and never neither
    private final Object startLock = new Object();
    private boolean waitingForStart;
    private Throwable startFailure;
    private volatile CountDownLatch startLatch;

    public DebeziumConsumer(DebeziumEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doBuild() throws Exception {
        if (getHealthCheck() == null) {
            setHealthCheck(new DebeziumConsumerHealthCheck(this, "consumer:" + getRouteId()));
        }
        super.doBuild();
    }

    @Override
    protected void doStart() throws Exception {
        engineReady = false;
        engineFailure = null;
        engineStopped = false;

        super.doStart();

        final Configuration dbzConfiguration = configuration.createDebeziumConfiguration();
        final CountDownLatch latch = new CountDownLatch(1);
        synchronized (startLock) {
            startLatch = latch;
            startFailure = null;
            waitingForStart = true;
        }

        boolean started = false;
        try {
            // start a single threaded pool to monitor events
            executorService = endpoint.createExecutor(this);

            // create engine, which reports an invalid configuration to the completion callback and throws
            try {
                dbzEngine = createDbzEngine(dbzConfiguration);
            } catch (RuntimeException e) {
                final Throwable failure = endWaitingForStart();
                throw failedToStart(failure != null ? failure : e);
            }

            // submit task to the thread pool
            executorService.submit(
                    () -> {
                        try {
                            dbzEngine.run();
                        } catch (Throwable e) {
                            // the engine reports its own failures through the completion callback and is not
                            // expected to throw, so this is only a safety net
                            onEngineCompleted(false, e.getMessage(), e);
                        }
                    });

            awaitEngineStarted(latch, startTimeout(dbzConfiguration));
            started = true;
        } finally {
            if (!started) {
                endWaitingForStart();
                stopExecutor();
            }
        }
    }

    /**
     * Ends the start: from now on a failure of the engine goes to the exception handler.
     *
     * @return the failure of the engine that the start has to throw, or <tt>null</tt>
     */
    private Throwable endWaitingForStart() {
        synchronized (startLock) {
            waitingForStart = false;
            final Throwable answer = startFailure;
            startFailure = null;
            return answer;
        }
    }

    /**
     * Waits until the engine has started its connector tasks or has stopped, and fails the start of this consumer when
     * the engine failed meanwhile. The engine calls pollingStarted once all its tasks have started, which is before an
     * initial snapshot is done, as the snapshot runs while the engine polls, so a long snapshot does not delay the
     * start. The engine bounds the start of each task by its <tt>internal.task.management.timeout.ms</tt>, so the wait
     * only ends by timeout when the engine hangs before that, for example while it opens its offset store; the consumer
     * then starts as it did before: the health check stays at its initial state until the engine polls, and a later
     * failure goes to the exception handler and the health check.
     */
    private void awaitEngineStarted(CountDownLatch latch, long timeoutMillis) {
        boolean done = false;
        boolean interrupted = false;
        try {
            done = latch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            interrupted = true;
            Thread.currentThread().interrupt();
        }

        final Throwable failure = endWaitingForStart();
        if (failure != null) {
            throw failedToStart(failure);
        }
        if (interrupted) {
            LOG.debug("Interrupted while waiting for the Debezium engine to start, route: {}", getRouteId());
        } else if (!done) {
            LOG.warn("Debezium engine has not reported that it started within {} ms, route: {} ({}) starts anyway, and a"
                     + " later failure of the engine is reported to the exception handler and the health check",
                    timeoutMillis, getRouteId(), URISupport.sanitizeUri(endpoint.getEndpointUri()));
        } else if (engineStopped) {
            LOG.debug("Debezium engine stopped while starting, without a failure, route: {}", getRouteId());
        }
    }

    private RuntimeCamelException failedToStart(Throwable failure) {
        return new RuntimeCamelException(
                "Debezium engine failed to start, route: " + getRouteId() + " ("
                                         + URISupport.sanitizeUri(endpoint.getEndpointUri()) + "): " + describe(failure),
                failure);
    }

    /**
     * How long the start waits for the engine: the engine waits up to <tt>internal.task.management.timeout.ms</tt> for
     * each task to start, and the same again is given to starting the connector and its offset store before that.
     */
    static long startTimeout(Configuration dbzConfiguration) {
        final long taskTimeout = dbzConfiguration.getLong(AsyncEngineConfig.TASK_MANAGEMENT_TIMEOUT_MS.name(),
                AsyncEngineConfig.DEFAULT_TASK_MANAGEMENT_TIMEOUT_MS);
        final int tasks = Math.max(1, dbzConfiguration.getInteger(TASKS_MAX, 1));
        return Math.max(1, taskTimeout) * (tasks + 1L);
    }

    private static String describe(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        final String message = failure.getMessage() != null ? failure.getMessage() : failure.getClass().getName();
        if (root != failure && root.getMessage() != null && !message.contains(root.getMessage())) {
            return message + " (caused by: " + root.getMessage() + ")";
        }
        return message;
    }

    private void stopExecutor() {
        if (executorService != null) {
            getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);
            executorService = null;
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (dbzEngine != null && !engineStopped) {
            try {
                dbzEngine.close();
            } catch (IllegalStateException e) {
                // close() rejects three states: the engine stopped on its own between the check above and
                // this call, which leaves nothing to close, but also tasks still starting and a shutdown
                // already in progress, and those two are worth seeing
                if (engineStopped) {
                    LOG.debug("Debezium engine was already stopped: {}", e.getMessage());
                } else {
                    LOG.warn("Debezium engine could not be closed: {}", e.getMessage());
                }
            }
        }

        // shutdown the thread pool gracefully
        stopExecutor();

        // shutdown camel consumer
        super.doStop();
    }

    /**
     * The failure that stopped the embedded engine, or <tt>null</tt> while the engine is starting, running, or was
     * stopped on request. Used by {@link DebeziumConsumerHealthCheck}.
     */
    Throwable getEngineFailure() {
        return engineFailure;
    }

    boolean isEngineReady() {
        return engineReady;
    }

    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> createDbzEngine(Configuration dbzConfiguration) {
        return DebeziumEngine.create(Connect.class)
                .using(dbzConfiguration.asProperties())
                .using(this::onEngineCompleted)
                .using(new DebeziumEngine.ConnectorCallback() {
                    @Override
                    public void pollingStarted() {
                        // called once all the tasks have started, before an initial snapshot is done; the start
                        // has succeeded, so a failure from now on goes to the exception handler
                        synchronized (startLock) {
                            waitingForStart = false;
                        }
                        engineReady = true;
                        startLatch.countDown();
                    }

                    @Override
                    public void pollingStopped() {
                        // the engine is no longer polling, so it consumes no further change event, even when it
                        // completed on its own without reporting a failure
                        engineReady = false;
                    }
                })
                .notifying(this::onEventListener)
                .build();
    }

    /**
     * Called by the embedded engine once it has stopped, either because it was closed or because it failed. The engine
     * does not restart itself. A failure while the consumer starts fails that start; a later failure means this
     * consumer is permanently dead while the route still reports as started, hence the failure is reported to the
     * exception handler and kept for the health check.
     */
    private void onEngineCompleted(boolean success, String message, Throwable error) {
        final boolean reportToExceptionHandler;
        final Throwable cause;
        synchronized (startLock) {
            engineStopped = true;
            if (success) {
                cause = null;
                reportToExceptionHandler = false;
            } else {
                cause = error != null ? error : new RuntimeCamelException(message);
                engineFailure = cause;
                if (waitingForStart) {
                    // the consumer is starting, so doStart throws the failure instead
                    startFailure = cause;
                    reportToExceptionHandler = false;
                } else {
                    reportToExceptionHandler = true;
                }
            }
        }
        startLatch.countDown();

        if (success) {
            LOG.debug("Debezium engine stopped: {}", message);
        } else if (reportToExceptionHandler) {
            getExceptionHandler().handleException(
                    "Debezium engine has failed and stopped, no more change events are consumed from "
                                                  + URISupport.sanitizeUri(endpoint.getEndpointUri()),
                    cause);
        }
    }

    private void onEventListener(final ChangeEvent<SourceRecord, SourceRecord> event) {
        final Exchange exchange = endpoint.createDbzExchange(this, event.value());

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
        } catch (Exception ex) {
            exchange.setException(ex);
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange,
                        exchange.getException());
            }
            releaseExchange(exchange, false);
        }
    }
}
