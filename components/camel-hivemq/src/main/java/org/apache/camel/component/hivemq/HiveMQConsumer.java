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
package org.apache.camel.component.hivemq;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HiveMQConsumer extends DefaultConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(HiveMQConsumer.class);
    private final HiveMQEndpoint endpoint;
    private HiveMQClientAdapter client;
    private ExecutorService executor;

    public HiveMQConsumer(HiveMQEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = endpoint.getCamelContext().getExecutorServiceManager().newDefaultThreadPool(this, "HiveMQConsumer");
        client = endpoint.createClient();
        endpoint.connect(client);

        client.subscribe(endpoint.getTopic(), endpoint.getConfiguration().getQos(), this::onMessage)
                .orTimeout(HiveMQConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .join();
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null && client.isConnected()) {
            try {
                client.unsubscribe(endpoint.getTopic())
                        .orTimeout(5, TimeUnit.SECONDS).join();
            } catch (Exception e) {
                // Best-effort unsubscribe before cancelling reconnect / disconnect
                LOG.debug("Failed to unsubscribe from topic {} during shutdown", endpoint.getTopic(), e);
            }
        }
        if (client != null) {
            client.stop();
        }
        client = null;
        if (executor != null) {
            endpoint.getCamelContext().getExecutorServiceManager().shutdownNow(executor);
            executor = null;
        }
        super.doStop();
    }

    /**
     * Gives direct access to the underlying HiveMQ MQTT Client library client (e.g. {@code Mqtt5AsyncClient} or
     * {@code Mqtt3AsyncClient}, depending on the {@code mqttVersion} this consumer is connected with) for use cases
     * this component does not cover. Empty before the consumer has started, or if {@code clazz} does not match the
     * protocol version in use.
     */
    public <T> Optional<T> getClient(Class<T> clazz) {
        return client == null ? Optional.empty() : client.getClient(clazz);
    }

    private void onMessage(HiveMQMessage message) {
        Exchange exchange = createExchange(false);
        exchange.getIn().setBody(message.payload());
        exchange.getIn().setHeader(HiveMQConstants.MQTT_TOPIC, message.topic());
        exchange.getIn().setHeader(HiveMQConstants.MQTT_QOS, message.qos());
        exchange.getIn().setHeader(HiveMQConstants.MQTT_RETAINED, message.retained());

        ExecutorService worker = executor;
        if (worker == null) {
            processExchange(exchange);
            return;
        }
        worker.submit(() -> processExchange(exchange));
    }

    private void processExchange(Exchange exchange) {
        AtomicBoolean released = new AtomicBoolean();
        try {
            getAsyncProcessor().process(exchange, doneSync -> finishExchange(exchange, released, exchange.getException()));
        } catch (Exception e) {
            finishExchange(exchange, released, e);
        }
    }

    private void finishExchange(Exchange exchange, AtomicBoolean released, Exception error) {
        if (!released.compareAndSet(false, true)) {
            return;
        }
        if (error != null) {
            getExceptionHandler().handleException("Error processing HiveMQ message", exchange, error);
        }
        releaseExchange(exchange, false);
    }
}
