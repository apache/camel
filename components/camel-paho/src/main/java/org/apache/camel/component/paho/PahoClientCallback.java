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
package org.apache.camel.component.paho;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The callback of a {@link MqttClient} used by consumers. A client has a single callback, and the same client can be
 * used by the consumers of several endpoints (the client option), so the callback is shared by all the consumers of the
 * client: a message goes to the consumers whose topic matches it, and every consumer subscribes again after a
 * reconnect. Only a client that the consumer created for itself gives every message to that consumer. A message that no
 * consumer of a shared client matches is dropped, and acknowledged when its consumers use manualAcksEnabled.
 */
final class PahoClientCallback implements MqttCallbackExtended {

    private static final Logger LOG = LoggerFactory.getLogger(PahoClientCallback.class);

    // the callbacks of the clients that have consumers, by client instance
    private static final Map<MqttClient, PahoClientCallback> CALLBACKS = new IdentityHashMap<>();

    // the number of topics whose unmatched messages are logged at WARN (the next ones at DEBUG)
    private static final int MAX_WARNED_TOPICS = 100;

    private final List<PahoConsumer> consumers = new CopyOnWriteArrayList<>();
    private final MqttClient client;
    // whether the client is the own client of its consumer, so that no other consumer ever used it
    private final boolean ownedClient;
    // whether a consumer of the client acknowledges its messages itself (manualAcksEnabled); kept after it stops, as
    // the messages of its subscription can still arrive
    private volatile boolean manualAcks;
    private final Set<String> warnedTopics = ConcurrentHashMap.newKeySet();

    private PahoClientCallback(MqttClient client, boolean ownedClient) {
        this.client = client;
        this.ownedClient = ownedClient;
    }

    /**
     * Adds the consumer to the callback of the client, and sets that callback on the client.
     *
     * @param ownedClient whether the consumer created the client for itself (no other consumer uses it)
     */
    static void register(MqttClient client, PahoConsumer consumer, boolean ownedClient) {
        synchronized (CALLBACKS) {
            PahoClientCallback callback
                    = CALLBACKS.computeIfAbsent(client, c -> new PahoClientCallback(c, ownedClient));
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
    static boolean unregister(PahoConsumer consumer) {
        String topic = consumer.getEndpoint().getTopic();
        boolean topicStillUsed = false;
        synchronized (CALLBACKS) {
            Iterator<PahoClientCallback> it = CALLBACKS.values().iterator();
            while (it.hasNext()) {
                PahoClientCallback callback = it.next();
                if (callback.consumers.remove(consumer)) {
                    for (PahoConsumer other : callback.consumers) {
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
        if (reconnect) {
            for (PahoConsumer consumer : consumers) {
                consumer.resubscribe();
            }
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        LOG.debug("Message arrived on topic: {} -> {}", topic, message);
        // a snapshot: a consumer can unregister on another thread
        PahoConsumer[] current = consumers.toArray(new PahoConsumer[0]);
        if (ownedClient && current.length == 1) {
            // the own client of a consumer: it is the only one that subscribed, so it gets every message, as before
            current[0].onMessageArrived(topic, message);
            return;
        }
        // a shared client: a stopped consumer can leave its subscription on the client (cleanStart / cleanSession
        // false), so a message goes only to the consumers whose topic filter matches it, even when one consumer is left
        boolean delivered = false;
        for (PahoConsumer consumer : current) {
            if (matches(consumer.getEndpoint().getTopic(), topic)) {
                consumer.onMessageArrived(topic, message);
                delivered = true;
            }
        }
        if (!delivered) {
            // with manual acks the client sends no PUBACK (QoS 1) or PUBCOMP (QoS 2) until the message is acknowledged,
            // and the broker sends a limited number of unacknowledged messages, so a dropped message that stays
            // unacknowledged would stop every consumer of the client
            // (the own client of a consumer is not acknowledged: it is being stopped, and a session that the broker
            // keeps sends the message again on the next connect)
            boolean acknowledge = !ownedClient && manualAcks && message.getQos() > 0;
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

    static boolean matches(String topicFilter, String topic) {
        String filter = topicFilter;
        // a shared subscription ($share/<group>/<filter>) delivers the messages of <filter>
        if (filter.startsWith("$share/")) {
            int index = filter.indexOf('/', "$share/".length());
            if (index > 0) {
                filter = filter.substring(index + 1);
            }
        }
        try {
            return MqttTopic.isMatched(filter, topic);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        LOG.debug("MQTT broker connection lost due {}", cause.getMessage(), cause);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        LOG.debug("Delivery complete. Token: {}", token);
    }
}
