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
package org.apache.camel.component.paho.mqtt5;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callback of a {@link MqttClient} used by consumers. A client has a single callback, and the same client can be
 * used by the consumers of several endpoints (the client option), so the callback is shared by all the consumers of the
 * client: a message goes to the consumers whose topic matches it, and every consumer subscribes again after a
 * reconnect. Only a client that the consumer created for itself gives every message to that consumer. A message that no
 * consumer of a shared client matches is dropped, and acknowledged when its consumers use manualAcksEnabled.
 */
final class PahoMqtt5ClientCallback implements MqttCallback {

    private static final Logger LOG = LoggerFactory.getLogger(PahoMqtt5ClientCallback.class);

    // the callbacks of the clients that have consumers, by client instance
    private static final Map<MqttClient, PahoMqtt5ClientCallback> CALLBACKS = new IdentityHashMap<>();

    // the number of topics whose unmatched messages are logged at WARN (the next ones at DEBUG)
    private static final int MAX_WARNED_TOPICS = 100;

    private final List<PahoMqtt5Consumer> consumers = new CopyOnWriteArrayList<>();
    private final MqttClient client;
    // whether the client is the own client of its consumer, so that no other consumer ever used it
    private final boolean ownedClient;
    // whether a consumer of the client acknowledges its messages itself (manualAcksEnabled); kept after it stops, as
    // the messages of its subscription can still arrive
    private volatile boolean manualAcks;
    private final Set<String> warnedTopics = ConcurrentHashMap.newKeySet();

    private PahoMqtt5ClientCallback(MqttClient client, boolean ownedClient) {
        this.client = client;
        this.ownedClient = ownedClient;
    }

    /**
     * Adds the consumer to the callback of the client, and sets that callback on the client.
     *
     * @param ownedClient whether the consumer created the client for itself (no other consumer uses it)
     */
    static void register(MqttClient client, PahoMqtt5Consumer consumer, boolean ownedClient) {
        synchronized (CALLBACKS) {
            PahoMqtt5ClientCallback callback
                    = CALLBACKS.computeIfAbsent(client, c -> new PahoMqtt5ClientCallback(c, ownedClient));
            if (consumer.getEndpoint().getConfiguration().isManualAcksEnabled()) {
                callback.manualAcks = true;
            }
            if (!callback.consumers.contains(consumer)) {
                callback.consumers.add(consumer);
            }
            client.setCallback(callback);
        }
    }

    /**
     * Removes the consumer from the callback of its client, if any.
     *
     * @return whether another consumer of the client still uses the topic filter of the consumer, so that the topic
     *         must stay subscribed
     */
    static boolean unregister(PahoMqtt5Consumer consumer) {
        String topic = consumer.getEndpoint().getTopic();
        boolean topicStillUsed = false;
        synchronized (CALLBACKS) {
            Iterator<PahoMqtt5ClientCallback> it = CALLBACKS.values().iterator();
            while (it.hasNext()) {
                PahoMqtt5ClientCallback callback = it.next();
                if (callback.consumers.remove(consumer)) {
                    for (PahoMqtt5Consumer other : callback.consumers) {
                        if (topic.equals(other.getEndpoint().getTopic())) {
                            topicStillUsed = true;
                        }
                    }
                }
                if (callback.consumers.isEmpty()) {
                    it.remove();
                }
            }
        }
        return topicStillUsed;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        for (PahoMqtt5Consumer consumer : consumers) {
            consumer.connectComplete(reconnect);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        LOG.debug("Message arrived on topic: {} -> {}", topic, message);
        // a snapshot: a consumer can unregister on another thread
        PahoMqtt5Consumer[] current = consumers.toArray(new PahoMqtt5Consumer[0]);
        if (ownedClient && current.length == 1) {
            // the own client of a consumer: it is the only one that subscribed, so it gets every message, as before
            current[0].onMessageArrived(topic, message);
            return;
        }
        // a shared client: a stopped consumer can leave its subscription on the client (cleanStart / cleanSession
        // false), so a message goes only to the consumers whose topic filter matches it, even when one consumer is left
        boolean delivered = false;
        for (PahoMqtt5Consumer consumer : current) {
            if (matches(consumer.getEndpoint().getTopic(), topic)) {
                consumer.onMessageArrived(topic, message);
                delivered = true;
            }
        }
        if (!delivered) {
            // with manual acks the client sends no PUBACK for a QoS 1 message until it is acknowledged, and the broker
            // sends no more than receiveMaximum unacknowledged messages, so a dropped message that stays unacknowledged
            // would stop every consumer of the client; the client completes the QoS 2 flow on its own
            // (the own client of a consumer is not acknowledged: it is being stopped, and a session that the broker
            // keeps sends the message again on the next connect)
            boolean acknowledge = !ownedClient && manualAcks && message.getQos() == 1;
            String dropped = acknowledge ? "dropped and acknowledged" : "dropped";
            if (warnedTopics.size() < MAX_WARNED_TOPICS && warnedTopics.add(topic)) {
                LOG.warn("Message arrived on topic: {} that no running consumer of the client subscribed to; it is {}"
                         + " (further messages of this topic are logged at DEBUG)",
                        topic, dropped);
            } else {
                LOG.debug("Message arrived on topic: {} that no running consumer of the client subscribed to; it is {}",
                        topic, dropped);
            }
            if (acknowledge) {
                acknowledge(topic, message);
            }
        }
    }

    private void acknowledge(String topic, MqttMessage message) {
        try {
            client.messageArrivedComplete(message.getId(), message.getQos());
        } catch (MqttException e) {
            LOG.warn("Failed to acknowledge the dropped MQTT message with ID: {} of topic: {}: {}",
                    message.getId(), topic, e.toString(), e);
        }
    }

    /**
     * Whether the topic filter of a subscription matches the topic name of a message, following the topic matching
     * rules of MQTT 5 section 4.7 (which MQTT 3.1.1 shares). Package-private for tests.
     */
    static boolean matches(String topicFilter, String topic) {
        String filter = topicFilter;
        // a shared subscription ($share/<group>/<filter>) delivers the messages of <filter>
        if (filter.startsWith("$share/")) {
            int index = filter.indexOf('/', "$share/".length());
            if (index > 0) {
                filter = filter.substring(index + 1);
            }
        }
        // MQTT 4.7.2: a filter that starts with a wildcard does not match a topic that starts with $
        if (topic.startsWith("$") && (filter.startsWith("+") || filter.startsWith("#"))) {
            return false;
        }
        // split with -1 keeps the empty levels, which + matches (MQTT 4.7.1.3)
        String[] filterLevels = filter.split("/", -1);
        String[] topicLevels = topic.split("/", -1);
        for (int i = 0; i < filterLevels.length; i++) {
            String level = filterLevels[i];
            if ("#".equals(level)) {
                // # matches the parent level and any number of child levels
                return i == filterLevels.length - 1;
            }
            if (i >= topicLevels.length || !("+".equals(level) || level.equals(topicLevels[i]))) {
                return false;
            }
        }
        return filterLevels.length == topicLevels.length;
    }

    @Override
    public void authPacketArrived(int reasonCode, MqttProperties properties) {
        LOG.debug("Auth packet arrived {} {}", reasonCode, properties);
    }

    @Override
    public void disconnected(MqttDisconnectResponse response) {
        LOG.debug("MQTT broker disconnected due {}", response.getReasonString(), response.getException());
    }

    @Override
    public void mqttErrorOccurred(MqttException exception) {
        LOG.debug("Error occurred {}", exception.getMessage(), exception);
    }

    @Override
    public void deliveryComplete(IMqttToken token) {
        LOG.debug("Delivery complete. Token: {}", token);
    }
}
