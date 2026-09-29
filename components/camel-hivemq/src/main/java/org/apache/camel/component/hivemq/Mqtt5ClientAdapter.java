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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientState;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuthBuilder;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class Mqtt5ClientAdapter implements HiveMQClientAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(Mqtt5ClientAdapter.class);

    private final Mqtt5AsyncClient client;
    private final AtomicBoolean cancelReconnect = new AtomicBoolean();

    Mqtt5ClientAdapter(HiveMQConfiguration configuration) {
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

        client = builder.buildAsync();
        clientRef.set(client);
    }

    @Override
    public CompletableFuture<?> connect(boolean cleanStart) {
        return client.connectWith().cleanStart(cleanStart).send();
    }

    @Override
    public void stop() {
        cancelReconnect.set(true);
        try {
            if (client.getState().isConnected()) {
                client.disconnect().orTimeout(5, TimeUnit.SECONDS).join();
            }
        } catch (Exception e) {
            // Not connected, already disconnecting, or reconnecting: the disconnected listener cancels reconnect.
            LOG.debug("Failed to disconnect HiveMQ MQTT 5 client during shutdown", e);
        }
    }

    @Override
    public boolean isConnected() {
        return client.getState().isConnected();
    }

    @Override
    public boolean isConnectedOrReconnecting() {
        return client.getState().isConnectedOrReconnect();
    }

    @Override
    public CompletableFuture<?> subscribe(String topicFilter, MqttQos qos, Consumer<HiveMQMessage> callback) {
        return client.subscribeWith()
                .topicFilter(topicFilter)
                .qos(qos)
                .callback(publish -> callback.accept(toMessage(publish)))
                .send();
    }

    @Override
    public CompletableFuture<?> unsubscribe(String topicFilter) {
        return client.unsubscribeWith().topicFilter(topicFilter).send();
    }

    @Override
    public CompletableFuture<?> publish(String topic, byte[] payload, MqttQos qos, boolean retained) {
        return client.publish(Mqtt5Publish.builder()
                .topic(topic)
                .qos(qos)
                .retain(retained)
                .payload(payload)
                .build());
    }

    private static HiveMQMessage toMessage(Mqtt5Publish publish) {
        return new HiveMQMessage(
                publish.getTopic().toString(), publish.getPayloadAsBytes(), publish.getQos(), publish.isRetain());
    }

    @Override
    public <T> Optional<T> getClient(Class<T> clazz) {
        return clazz.isInstance(client) ? Optional.of(clazz.cast(client)) : Optional.empty();
    }
}
