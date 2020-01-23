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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NatsConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NatsConsumer.class);

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
        LOG.debug("Starting Nats Consumer");
        executor = getEndpoint().createExecutor();

        LOG.debug("Getting Nats Connection");
        connection = getEndpoint().getConfiguration().getConnection() != null
            ? getEndpoint().getConfiguration().getConnection() : getEndpoint().getConnection();

        executor.submit(new NatsConsumingTask(connection, getEndpoint().getConfiguration()));
    }

    @Override
    protected void doStop() throws Exception {
        if (getEndpoint().getConfiguration().isFlushConnection()) {
            LOG.debug("Flushing Messages before stopping");
            connection.flush(Duration.ofMillis(getEndpoint().getConfiguration().getFlushTimeout()));
        }

        try {
            dispatcher.unsubscribe(getEndpoint().getConfiguration().getTopic());
        } catch (Exception e) {
            getExceptionHandler().handleException("Error during unsubscribing", e);
        }

        LOG.debug("Stopping Nats Consumer");
        if (executor != null) {
            if (getEndpoint() != null && getEndpoint().getCamelContext() != null) {
                getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            } else {
                executor.shutdownNow();
            }
        }
        executor = null;

        if (ObjectHelper.isEmpty(getEndpoint().getConfiguration().getConnection())) {
            LOG.debug("Closing Nats Connection");
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
                    dispatcher = dispatcher.subscribe(getEndpoint().getConfiguration().getTopic(), getEndpoint().getConfiguration().getQueueName());
                    if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getMaxMessages())) {
                        dispatcher.unsubscribe(getEndpoint().getConfiguration().getTopic(), Integer.parseInt(getEndpoint().getConfiguration().getMaxMessages()));
                    }
                    if (dispatcher.isActive()) {
                        setActive(true);
                    }
                } else {
                    dispatcher = dispatcher.subscribe(getEndpoint().getConfiguration().getTopic());
                    if (ObjectHelper.isNotEmpty(getEndpoint().getConfiguration().getMaxMessages())) {
                        dispatcher.unsubscribe(getEndpoint().getConfiguration().getTopic(), Integer.parseInt(getEndpoint().getConfiguration().getMaxMessages()));
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
                LOG.debug("Received Message: {}", msg);
                Exchange exchange = getEndpoint().createExchange();
                exchange.getIn().setBody(msg.getData());
                exchange.getIn().setHeader(NatsConstants.NATS_REPLY_TO, msg.getReplyTo());
                exchange.getIn().setHeader(NatsConstants.NATS_SID, msg.getSID());
                exchange.getIn().setHeader(NatsConstants.NATS_SUBJECT, msg.getSubject());
                exchange.getIn().setHeader(NatsConstants.NATS_QUEUE_NAME, msg.getSubscription().getQueueName());
                exchange.getIn().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                try {
                    processor.process(exchange);
                } catch (Exception e) {
                    getExceptionHandler().handleException("Error during processing", exchange, e);
                }

                // is there a reply?
                if (!configuration.isReplyToDisabled()
                        && msg.getReplyTo() != null && msg.getConnection() != null) {
                    Connection con = msg.getConnection();
                    byte[] data = exchange.getMessage().getBody(byte[].class);
                    if (data != null) {
                        LOG.debug("Publishing replyTo: {} message", msg.getReplyTo());
                        con.publish(msg.getReplyTo(), data);
                    }
                }

            }
        }
    }

}
