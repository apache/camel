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

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.MqttClientBuilderBase;
import com.hivemq.client.mqtt.MqttClientState;

/**
 * Registers the HiveMQ #302 reconnect-cancellation workaround on an {@link MqttClientBuilderBase}, shared between
 * {@link Mqtt3ClientAdapter} and {@link Mqtt5ClientAdapter}. Everything involved - the builder base, the listener
 * types, and {@link MqttClient#getState()} - is version-neutral in the HiveMQ MQTT Client library; only the actual
 * {@code disconnect()} call is protocol-specific (MQTT 5's DISCONNECT carries reason codes/properties that MQTT 3.1.1
 * does not have), which is why it is supplied by the caller instead of being part of this class.
 */
final class MqttReconnectCancellation {

    private MqttReconnectCancellation() {
    }

    static <B extends MqttClientBuilderBase<B>> B apply(
            B builder, AtomicBoolean cancelReconnect, Supplier<? extends MqttClient> client, Runnable disconnect) {
        return builder
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
                        MqttClient started = client.get();
                        if (started != null && started.getState().isConnected()) {
                            try {
                                disconnect.run();
                            } catch (Exception e) {
                                // Already disconnecting or not connected
                            }
                        }
                    }
                });
    }
}
