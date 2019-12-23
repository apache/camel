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

import io.debezium.embedded.EmbeddedEngine;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.debezium.configuration.EmbeddedDebeziumConfiguration;
import org.apache.camel.support.DefaultConsumer;
import org.apache.kafka.connect.source.SourceRecord;

public class DebeziumConsumer extends DefaultConsumer {

    private final DebeziumEndpoint endpoint;
    private final EmbeddedDebeziumConfiguration configuration;

    private ExecutorService executorService;
    private EmbeddedEngine dbzEngine;

    public DebeziumConsumer(DebeziumEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = endpoint.getConfiguration();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // start a single threaded pool to monitor events
        executorService = endpoint.createExecutor();

        // create engine
        dbzEngine = createDbzEngine();

        // submit task to the thread pool
        executorService.submit(dbzEngine);
    }

    @Override
    protected void doStop() throws Exception {
        dbzEngine.stop();

        // shutdown the thread pool gracefully
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdownGraceful(executorService);

        // shutdown camel consumer
        super.doStop();
    }

    private EmbeddedEngine createDbzEngine() {
        return EmbeddedEngine.create().using(configuration.createDebeziumConfiguration())
            .notifying(this::onEventListener).build();
    }

    private void onEventListener(final SourceRecord event) {
        final Exchange exchange = endpoint.createDbzExchange(event);

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
        }
    }
}
