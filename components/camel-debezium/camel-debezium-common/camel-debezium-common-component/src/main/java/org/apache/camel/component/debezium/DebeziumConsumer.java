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

import java.util.concurrent.ExecutorService;

import io.debezium.embedded.Connect;
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

    private final DebeziumEndpoint endpoint;
    private final EmbeddedDebeziumConfiguration configuration;

    private ExecutorService executorService;
    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> dbzEngine;
    private volatile Throwable engineFailure;
    private volatile boolean engineStopped;

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
        super.doStart();

        engineFailure = null;
        engineStopped = false;

        // start a single threaded pool to monitor events
        executorService = endpoint.createExecutor(this);

        // create engine
        dbzEngine = createDbzEngine();

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
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

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

    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> createDbzEngine() {
        return DebeziumEngine.create(Connect.class)
                .using(configuration.createDebeziumConfiguration().asProperties())
                .using(this::onEngineCompleted)
                .notifying(this::onEventListener)
                .build();
    }

    /**
     * Called by the embedded engine once it has stopped, either because it was closed or because it failed. The engine
     * does not restart itself, so a failure means this consumer is permanently dead while the route still reports as
     * started, hence the failure is reported to the exception handler and kept for the health check.
     */
    private void onEngineCompleted(boolean success, String message, Throwable error) {
        engineStopped = true;

        if (success) {
            LOG.debug("Debezium engine stopped: {}", message);
            return;
        }

        final Throwable cause = error != null ? error : new RuntimeCamelException(message);
        engineFailure = cause;

        getExceptionHandler().handleException(
                "Debezium engine has failed and stopped, no more change events are consumed from "
                                              + URISupport.sanitizeUri(endpoint.getEndpointUri()),
                cause);
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
