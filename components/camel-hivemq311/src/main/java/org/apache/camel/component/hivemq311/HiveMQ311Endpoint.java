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
package org.apache.camel.component.hivemq311;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuth;
import com.hivemq.client.mqtt.mqtt3.message.auth.Mqtt3SimpleAuthBuilder;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UriEndpoint(firstVersion = "4.23.0", scheme = "hivemq311", title = "HiveMQ MQTT 3.1.1", syntax = "hivemq311:topic",
             category = { Category.MESSAGING, Category.IOT }, headersClass = HiveMQ311Constants.class)
public class HiveMQ311Endpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(HiveMQ311Endpoint.class);

    /**
     * The MQTT topic name or pattern to subscribe to or publish on.
     */
    @UriPath
    @Metadata(required = true)
    private String topic;

    /**
     * The HiveMQ component configuration options.
     */
    @UriParam
    @Metadata(description = "To use a custom HiveMQ311Configuration")
    private HiveMQ311Configuration configuration;

    private final Map<Mqtt3AsyncClient, AtomicBoolean> reconnectCancellations = new ConcurrentHashMap<>();

    public HiveMQ311Endpoint(String uri, HiveMQ311Component component, HiveMQ311Configuration configuration, String topic) {
        super(uri, component);
        this.configuration = configuration;
        this.topic = topic;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HiveMQ311Producer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HiveMQ311Consumer consumer = new HiveMQ311Consumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Mqtt3AsyncClient createClient() {
        AtomicBoolean cancelReconnect = new AtomicBoolean();
        AtomicReference<Mqtt3AsyncClient> clientRef = new AtomicReference<>();
        Mqtt3ClientBuilder builder = MqttClient.builder()
                .serverHost(configuration.getHost())
                .serverPort(configuration.getPort())
                .automaticReconnectWithDefaultConfig()
                .addDisconnectedListener(context -> {
                    // Initial connect() does not complete while auto-reconnect keeps retrying (HiveMQ #302).
                    // Also honour an explicit stop so DISCONNECTED_RECONNECT / CONNECTING_RECONNECT are cancelled.
                    if (cancelReconnect.get() || context.getClientConfig().getState() == MqttClientState.CONNECTING) {
                        context.getReconnector().reconnect(false);
                    }
                })
                .addConnectedListener(context -> {
                    // HiveMQ schedules reconnect after listeners return; cancelReconnect cannot abort that delay.
                    // If a reconnect succeeds after Camel stop, disconnect immediately (USER source skips auto-reconnect).
                    if (cancelReconnect.get()) {
                        Mqtt3AsyncClient started = clientRef.get();
                        if (started != null && started.getState().isConnected()) {
                            try {
                                started.disconnect();
                            } catch (Exception e) {
                                // Already disconnecting or not connected
                            }
                        }
                    }
                })
                .useMqttVersion3();

        if (configuration.getClientId() != null) {
            builder.identifier(configuration.getClientId());
        }

        if (configuration.isSsl()) {
            builder.sslWithDefaultConfig();
        }

        if (configuration.getUsername() != null) {
            Mqtt3SimpleAuthBuilder.Complete authBuilder
                    = Mqtt3SimpleAuth.builder().username(configuration.getUsername());
            if (configuration.getPassword() != null) {
                authBuilder.password(configuration.getPassword().getBytes(StandardCharsets.UTF_8));
            }
            builder.simpleAuth(authBuilder.build());
        }

        Mqtt3AsyncClient client = builder.buildAsync();
        clientRef.set(client);
        reconnectCancellations.put(client, cancelReconnect);
        return client;
    }

    public void connect(Mqtt3AsyncClient client) {
        try {
            client.connectWith()
                    .cleanSession(configuration.isCleanStart())
                    .send()
                    .orTimeout(HiveMQ311Constants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            stopClient(client);
            throw unwrapConnectFailure(e);
        }
    }

    /**
     * Stops automatic reconnect and disconnects if currently connected. Safe to call from any client state.
     */
    public void stopClient(Mqtt3AsyncClient client) {
        if (client == null) {
            return;
        }
        AtomicBoolean cancelReconnect = reconnectCancellations.remove(client);
        if (cancelReconnect != null) {
            cancelReconnect.set(true);
        }
        try {
            if (client.getState().isConnected()) {
                client.disconnect().orTimeout(5, TimeUnit.SECONDS).join();
            }
        } catch (Exception e) {
            // Not connected, already disconnecting, or reconnecting: the disconnected listener cancels reconnect.
            LOG.debug("Failed to disconnect HiveMQ client during shutdown", e);
        }
    }

    private static RuntimeCamelException unwrapConnectFailure(CompletionException e) {
        Throwable cause = e.getCause() != null ? e.getCause() : e;
        if (cause instanceof TimeoutException) {
            return new RuntimeCamelException("Timed out connecting to the HiveMQ broker", cause);
        }
        return new RuntimeCamelException("Failed to connect to the HiveMQ broker", cause);
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public HiveMQ311Configuration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HiveMQ311Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String getServiceUrl() {
        return configuration.getHost() + ":" + configuration.getPort();
    }

    @Override
    public String getServiceProtocol() {
        return "mqtt";
    }
}
