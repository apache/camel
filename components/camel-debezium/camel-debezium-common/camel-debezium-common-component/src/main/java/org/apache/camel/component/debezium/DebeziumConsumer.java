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
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.support.DefaultConsumer;
import org.apache.kafka.connect.source.SourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DebeziumConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(DebeziumConsumer.class);

    private final DebeziumEndpoint endpoint;
    private final EmbeddedDebeziumConfiguration configuration;

    private ExecutorService executorService;
    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> dbzEngine;

    public DebeziumConsumer(DebeziumEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

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
                        LOG.error("Debezium engine has failed: {}", e.getMessage(), e);
                    }
                });
    }

    @Override
    protected void doStop() throws Exception {
        if (dbzEngine != null) {
            dbzEngine.close();
        }

        // shutdown the thread pool gracefully
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

        // shutdown camel consumer
        super.doStop();
    }

    private DebeziumEngine<ChangeEvent<SourceRecord, SourceRecord>> createDbzEngine() {
        return DebeziumEngine.create(Connect.class)
                .using(configuration.createDebeziumConfiguration().asProperties())
                .notifying(this::onEventListener)
                .build();
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
