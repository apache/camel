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
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuthBuilder;
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

@UriEndpoint(firstVersion = "4.23.0", scheme = "hivemq", title = "HiveMQ", syntax = "hivemq:topic",
             category = { Category.MESSAGING, Category.IOT }, headersClass = HiveMQConstants.class)
public class HiveMQEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    private static final Logger LOG = LoggerFactory.getLogger(HiveMQEndpoint.class);

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
    @Metadata(description = "To use a custom HiveMQConfiguration")
    private HiveMQConfiguration configuration;

    private final Map<Mqtt5AsyncClient, AtomicBoolean> reconnectCancellations = new ConcurrentHashMap<>();

    public HiveMQEndpoint(String uri, HiveMQComponent component, HiveMQConfiguration configuration, String topic) {
        super(uri, component);
        this.configuration = configuration;
        this.topic = topic;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HiveMQProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        HiveMQConsumer consumer = new HiveMQConsumer(this, processor);
        configureConsumer(consumer);
        return consumer;
    }

    public Mqtt5AsyncClient createClient() {
        AtomicBoolean cancelReconnect = new AtomicBoolean();
        AtomicReference<Mqtt5AsyncClient> clientRef = new AtomicReference<>();
        Mqtt5ClientBuilder builder = MqttClient.builder()
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
                        Mqtt5AsyncClient started = clientRef.get();
                        if (started != null && started.getState().isConnected()) {
                            try {
                                started.disconnect();
                            } catch (Exception e) {
                                // Already disconnecting or not connected
                            }
                        }
                    }
                })
                .useMqttVersion5();

        if (configuration.getClientId() != null) {
            builder.identifier(configuration.getClientId());
        }

        if (configuration.isSsl()) {
            builder.sslWithDefaultConfig();
        }

        if (configuration.getUsername() != null) {
            Mqtt5SimpleAuthBuilder.Complete authBuilder
                    = Mqtt5SimpleAuth.builder().username(configuration.getUsername());
            if (configuration.getPassword() != null) {
                authBuilder.password(configuration.getPassword().getBytes(StandardCharsets.UTF_8));
            }
            builder.simpleAuth(authBuilder.build());
        }

        Mqtt5AsyncClient client = builder.buildAsync();
        clientRef.set(client);
        reconnectCancellations.put(client, cancelReconnect);
        return client;
    }

    public void connect(Mqtt5AsyncClient client) {
        try {
            client.connectWith()
                    .cleanStart(configuration.isCleanStart())
                    .send()
                    .orTimeout(HiveMQConstants.DEFAULT_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .join();
        } catch (CompletionException e) {
            stopClient(client);
            throw unwrapConnectFailure(e);
        }
    }

    /**
     * Stops automatic reconnect and disconnects if currently connected. Safe to call from any client state.
     */
    public void stopClient(Mqtt5AsyncClient client) {
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

    public HiveMQConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(HiveMQConfiguration configuration) {
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
