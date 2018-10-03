/**
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
package org.apache.camel.component.nats;

import java.time.Duration;
import java.util.concurrent.ExecutorService;

import io.nats.client.Connection;
import io.nats.client.Connection.Status;
import io.nats.client.Dispatcher;
import io.nats.client.Message;
import io.nats.client.MessageHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.util.ObjectHelper;

public class NatsConsumer extends DefaultConsumer {

    private final Processor processor;
    private ExecutorService executor;
    private Connection connection;
    private Dispatcher dispatcher;
    private boolean active;

    public NatsConsumer(NatsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public NatsEndpoint getEndpoint() {
        return (NatsEndpoint)super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        log.debug("Starting Nats Consumer");
        executor = getEndpoint().createExecutor();

        log.debug("Getting Nats Connection");
        connection = getEndpoint().getNatsConfiguration().getConnection() != null 
            ? getEndpoint().getNatsConfiguration().getConnection() : getEndpoint().getConnection();

        executor.submit(new NatsConsumingTask(connection, getEndpoint().getNatsConfiguration()));
    }

    @Override
    protected void doStop() throws Exception {

        if (getEndpoint().getNatsConfiguration().isFlushConnection()) {
            log.debug("Flushing Messages before stopping");
            connection.flush(Duration.ofMillis(getEndpoint().getNatsConfiguration().getFlushTimeout()));
        }

        try {
            dispatcher.unsubscribe(getEndpoint().getNatsConfiguration().getTopic());
        } catch (Exception e) {
            getExceptionHandler().handleException("Error during unsubscribing", e);
        }

        log.debug("Stopping Nats Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;

        if (ObjectHelper.isEmpty(getEndpoint().getNatsConfiguration().getConnection())) {
            log.debug("Closing Nats Connection");
            if (!connection.getStatus().equals(Status.CLOSED)) {
                connection.close();
            }
        }
        super.doStop();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    class NatsConsumingTask implements Runnable {

        private final Connection connection;
        private final NatsConfiguration configuration;

        NatsConsumingTask(Connection connection, NatsConfiguration configuration) {
            this.connection = connection;
            this.configuration = configuration;
        }

        @Override
        public void run() {
            try {
                dispatcher = connection.createDispatcher(new CamelNatsMessageHandler());
                if (ObjectHelper.isNotEmpty(configuration.getQueueName())) {
                    dispatcher = dispatcher.subscribe(getEndpoint().getNatsConfiguration().getTopic(), getEndpoint().getNatsConfiguration().getQueueName());
                    if (ObjectHelper.isNotEmpty(getEndpoint().getNatsConfiguration().getMaxMessages())) {
                        dispatcher.unsubscribe(getEndpoint().getNatsConfiguration().getTopic(), Integer.parseInt(getEndpoint().getNatsConfiguration().getMaxMessages()));
                    }
                    if (dispatcher.isActive()) {
                        setActive(true);
                    }
                } else {
                    dispatcher = dispatcher.subscribe(getEndpoint().getNatsConfiguration().getTopic());
                    if (ObjectHelper.isNotEmpty(getEndpoint().getNatsConfiguration().getMaxMessages())) {
                        dispatcher.unsubscribe(getEndpoint().getNatsConfiguration().getTopic(), Integer.parseInt(getEndpoint().getNatsConfiguration().getMaxMessages()));
                    }
                    if (dispatcher.isActive()) {
                        setActive(true);
                    }
                }
            } catch (Throwable e) {
                getExceptionHandler().handleException("Error during processing", e);
            }
            
        }
        
        class CamelNatsMessageHandler implements MessageHandler {

            @Override
            public void onMessage(Message msg) throws InterruptedException {
                log.debug("Received Message: {}", msg);
                Exchange exchange = getEndpoint().createExchange();
                exchange.getIn().setBody(msg);
                exchange.getIn().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    getExceptionHandler().handleException("Error during processing", exchange, e);
                }
            }
        }
    }

}
