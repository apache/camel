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

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

import io.nats.client.Connection;
import io.nats.client.Connection.Status;
import io.nats.client.Dispatcher;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.StreamConfiguration;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spi.HeaderFilterStrategy;
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
    private JetStreamSubscription jetStreamSubscription;

    public NatsConsumer(NatsEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.processor = processor;
    }

    @Override
    public NatsEndpoint getEndpoint() {
        return (NatsEndpoint) super.getEndpoint();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        LOG.debug("Starting Nats Consumer");
        this.executor = this.getEndpoint().createExecutor(this);

        LOG.debug("Getting Nats Connection");
        this.connection = this.getEndpoint().getConfiguration().getConnection() != null
                ? this.getEndpoint().getConfiguration().getConnection()
                : this.getEndpoint().getConnection();

        this.executor.submit(
                new NatsConsumingTask(this.connection, this.getEndpoint().getConfiguration()));
    }

    @Override
    protected void doStop() throws Exception {
        final NatsConfiguration configuration = this.getEndpoint().getConfiguration();

        if (configuration.isFlushConnection() && ObjectHelper.isNotEmpty(this.connection)) {
            LOG.debug("Flushing Messages before stopping");
            this.connection.flush(Duration.ofMillis(configuration.getFlushTimeout()));
        }

        if (ObjectHelper.isNotEmpty(this.dispatcher)) {
            try {
                this.dispatcher.unsubscribe(configuration.getTopic());
            } catch (final Exception e) {
                this.getExceptionHandler().handleException("Error during unsubscribing", e);
            }
        }

        LOG.debug("Stopping Nats Consumer");
        if (this.executor != null) {
            if (this.getEndpoint() != null && this.getEndpoint().getCamelContext() != null) {
                this.getEndpoint().getCamelContext().getExecutorServiceManager().shutdownNow(this.executor);
            } else {
                this.executor.shutdownNow();
            }
        }
        this.executor = null;

        if (ObjectHelper.isEmpty(configuration.getConnection()) && ObjectHelper.isNotEmpty(this.connection)) {
            LOG.debug("Closing Nats Connection");
            if (!this.connection.getStatus().equals(Status.CLOSED)) {
                this.connection.close();
            }
        }
        super.doStop();
    }

    public boolean isActive() {
        return this.active;
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
                NatsConfiguration config = getEndpoint().getConfiguration();
                String topic = config.getTopic();
                String queueName = config.getQueueName();
                String maxMessagesStr = config.getMaxMessages();
                Integer maxMessages = null;
                if (ObjectHelper.isNotEmpty(maxMessagesStr)) {
                    maxMessages = Integer.parseInt(maxMessagesStr);
                }

                if (config.isJetstreamEnabled() && connection.getServerInfo().isJetStreamAvailable()) {
                    setupJetStreamConsumer(topic, queueName);
                } else {
                    setupStandardNatsConsumer(topic, queueName, maxMessages);
                }
            } catch (final Exception e) {
                NatsConsumer.this.getExceptionHandler().handleException("Error during processing", e);
            }
        }

        private void setupJetStreamConsumer(String topic, String queueName) throws IOException, JetStreamApiException {
            String streamName = this.configuration.getJetstreamName();
            String durableName = this.configuration.getDurableName();

            String subscriptionType = this.configuration.isPullSubscription() ? "PULL" : "PUSH";
            LOG.debug(
                    "Setting up JetStream {}/{} consumer for stream: '{}', subject: {}",
                    subscriptionType,
                    ObjectHelper.isNotEmpty(durableName)
                            ? String.format("DURABLE, durableName: '%s'", durableName)
                            : "EPHEMERAL",
                    streamName,
                    this.configuration.getTopic());

            JetStreamManagement jsm = connection.jetStreamManagement();
            if (streamExists(jsm, streamName)) {
                LOG.debug("JetStream stream '{}' already exists. Skipping creation.", streamName);
            } else {
                LOG.debug("Creating JetStream {}", streamName);
                StreamConfiguration streamConfig = StreamConfiguration.builder()
                        .name(streamName)
                        .subjects(topic)
                        .build();
                jsm.addStream(streamConfig);
                LOG.info("JetStream stream '{}' created successfully.", streamName);
            }

            ConsumerConfiguration cc = configuration.getConsumerConfiguration();
            if (cc == null) {
                ConsumerConfiguration.Builder ccBuilder = ConsumerConfiguration.builder();
                ccBuilder.deliverSubject(null);
                if (durableName != null) {
                    ccBuilder.durable(durableName + "-durable");
                }
                cc = ccBuilder.build();
            }

            CamelNatsMessageHandler messageHandler = new CamelNatsMessageHandler();
            NatsConsumer.this.dispatcher = this.connection.createDispatcher(messageHandler);

            if (this.configuration.isPullSubscription()) {
                PullSubscribeOptions pullOptions =
                        PullSubscribeOptions.builder().configuration(cc).build();

                NatsConsumer.this.jetStreamSubscription = this.connection
                        .jetStream()
                        .subscribe(
                                NatsConsumer.this
                                        .getEndpoint()
                                        .getConfiguration()
                                        .getTopic(),
                                dispatcher,
                                messageHandler,
                                pullOptions);
            } else {
                PushSubscribeOptions pushOptions = PushSubscribeOptions.builder()
                        .configuration(cc)
                        .deliverGroup(queueName)
                        .build();

                NatsConsumer.this.jetStreamSubscription = this.connection
                        .jetStream()
                        .subscribe(
                                NatsConsumer.this
                                        .getEndpoint()
                                        .getConfiguration()
                                        .getTopic(),
                                queueName,
                                dispatcher,
                                messageHandler,
                                true,
                                pushOptions);
            }

            NatsConsumer.this.setActive(true);
        }

        private void setupStandardNatsConsumer(String topic, String queueName, Integer maxMessages) {
            LOG.debug("Setting up standard NATS consumer for subject: {}", topic);
            NatsConsumer.this.dispatcher = connection.createDispatcher(new CamelNatsMessageHandler());
            if (ObjectHelper.isNotEmpty(queueName)) {
                NatsConsumer.this.dispatcher = NatsConsumer.this.dispatcher.subscribe(topic, queueName);
            } else {
                NatsConsumer.this.dispatcher = NatsConsumer.this.dispatcher.subscribe(topic);
            }
            if (maxMessages != null) {
                NatsConsumer.this.dispatcher.unsubscribe(topic, maxMessages);
            }
            if (NatsConsumer.this.dispatcher.isActive()) {
                NatsConsumer.this.setActive(true);
            }
        }

        class CamelNatsMessageHandler implements MessageHandler {

            @Override
            public void onMessage(Message msg) throws InterruptedException {
                LOG.debug("Received Message: {}", msg);
                final Exchange exchange = NatsConsumer.this.createExchange(false);
                try {
                    exchange.getIn().setBody(msg.getData());
                    exchange.getIn().setHeader(NatsConstants.NATS_REPLY_TO, msg.getReplyTo());
                    exchange.getIn().setHeader(NatsConstants.NATS_SID, msg.getSID());
                    exchange.getIn().setHeader(NatsConstants.NATS_SUBJECT, msg.getSubject());
                    exchange.getIn()
                            .setHeader(
                                    NatsConstants.NATS_QUEUE_NAME,
                                    msg.getSubscription().getQueueName());
                    exchange.getIn().setHeader(NatsConstants.NATS_MESSAGE_TIMESTAMP, System.currentTimeMillis());
                    if (msg.isStatusMessage() && msg.getStatus() != null) {
                        exchange.getIn()
                                .setHeader(
                                        NatsConstants.NATS_STATUS_CODE,
                                        msg.getStatus().getCode());
                        exchange.getIn()
                                .setHeader(
                                        NatsConstants.NATS_STATUS_ERROR,
                                        msg.getStatus().getMessage());
                    }
                    if (msg.getHeaders() != null) {
                        final HeaderFilterStrategy strategy = NatsConsumer.this
                                .getEndpoint()
                                .getConfiguration()
                                .getHeaderFilterStrategy();
                        msg.getHeaders().entrySet().forEach(entry -> {
                            if (!strategy.applyFilterToExternalHeaders(entry.getKey(), entry.getValue(), exchange)) {
                                if (entry.getValue().size() == 1) {
                                    // going from camel to nats add all headers in lists, so we extract them in the
                                    // opposite
                                    // way if it contains a single value
                                    exchange.getIn()
                                            .setHeader(
                                                    entry.getKey(),
                                                    entry.getValue().get(0));
                                } else {
                                    exchange.getIn().setHeader(entry.getKey(), entry.getValue());
                                }
                            } else {
                                LOG.debug("Excluding header {} as per strategy", entry.getKey());
                            }
                        });
                    }
                    NatsConsumer.this.processor.process(exchange);

                    // is there a reply?
                    if (!NatsConsumingTask.this.configuration.isReplyToDisabled()
                            && msg.getReplyTo() != null
                            && msg.getConnection() != null) {
                        final Connection con = msg.getConnection();
                        final byte[] data = exchange.getMessage().getBody(byte[].class);
                        if (data != null) {
                            LOG.debug("Publishing replyTo: {} message", msg.getReplyTo());
                            con.publish(msg.getReplyTo(), data);
                        }
                    }
                } catch (final Exception e) {
                    NatsConsumer.this.getExceptionHandler().handleException("Error during processing", exchange, e);
                } finally {
                    NatsConsumer.this.releaseExchange(exchange, false);
                }
            }
        }
    }

    private boolean streamExists(JetStreamManagement jsm, String streamName) throws IOException, JetStreamApiException {
        try {
            jsm.getStreamInfo(streamName);
            return true;
        } catch (JetStreamApiException jsae) {
            if (jsae.getErrorCode() == 404) {
                return false;
            }
            throw jsae;
        }
    }
}
