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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.hivemq.client.mqtt.datatypes.MqttQos;

/**
 * Adapts an underlying HiveMQ MQTT client, hiding whether it speaks MQTT 3.1.1 or MQTT 5 behind a single API so that
 * {@link HiveMQEndpoint}, {@link HiveMQConsumer} and {@link HiveMQProducer} do not need to know which protocol version
 * is in use.
 */
interface HiveMQClientAdapter {

    /**
     * Sends the CONNECT packet. The returned future completes when the CONNACK is received.
     */
    CompletableFuture<?> connect(boolean cleanStart);

    /**
     * Cancels automatic reconnect and disconnects if currently connected. Safe to call from any client state.
     */
    void stop();

    boolean isConnected();

    /**
     * Whether the client is connected or automatically retrying a connection, i.e. not fully settled into a
     * disconnected state. Used to verify that {@link #stop()} really stopped the automatic-reconnect loop.
     */
    boolean isConnectedOrReconnecting();

    CompletableFuture<?> subscribe(String topicFilter, MqttQos qos, Consumer<HiveMQMessage> callback);

    CompletableFuture<?> unsubscribe(String topicFilter);

    CompletableFuture<?> publish(String topic, byte[] payload, MqttQos qos, boolean retained);

    /**
     * Gives direct access to the underlying HiveMQ MQTT Client library client (e.g. {@code Mqtt5AsyncClient} or
     * {@code Mqtt3AsyncClient}), for callers who need functionality this adapter does not expose. Empty if the
     * underlying client is not an instance of {@code clazz}, e.g. because this connection uses the other protocol
     * version.
     */
    <T> Optional<T> getClient(Class<T> clazz);
}
