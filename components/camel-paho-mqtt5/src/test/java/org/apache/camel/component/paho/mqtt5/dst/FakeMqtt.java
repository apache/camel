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
package org.apache.camel.component.paho.mqtt5.dst;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstExecutor;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttClientException;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

/**
 * A fake MQTT 5 broker with one topic and the session of the consumer's client id, and the Paho client of the consumer
 * ({@link Client}, a subclass of the Paho {@link MqttClient} whose network calls go to the fake). Written from the MQTT
 * 5.0 specification (OASIS) and Paho 1.2.5; the rules it follows are quoted where they are implemented and marked:
 * <ul>
 * <li>[S] MQTT Version 5.0, OASIS Standard: 3.1.2.4 Clean Start, 3.1.2.11.2 Session Expiry Interval, 3.2.2.1.1 Session
 * Present, 3.3.4 PUBLISH actions, 4.4 Message delivery retry, 4.9 Flow Control (Receive Maximum);</li>
 * <li>[P] Paho Java client 1.2.5 (MqttClient, MqttAsyncClient and CommsCallback, read with javap): callbacks on one
 * callback thread, manual acknowledgements, the automatic reconnect;</li>
 * <li>[A] an assumption, where the documents leave the behaviour open; the seed picks between the allowed choices.</li>
 * </ul>
 * <p>
 * Modelled: the session of the client id (subscriptions, QoS 1 messages sent and not acknowledged, messages queued
 * while the client is offline), Clean Start, the Session Expiry Interval, Session Present, the resend of unacknowledged
 * QoS 1 messages on a reconnect to a kept session, Receive Maximum, a connection lost by the broker (and the automatic
 * reconnect of Paho, with its delay doubled from 1 second up to maxReconnectDelay), a broker that cannot be reached for
 * a while, a connection lost during a subscribe, QoS 0, 1 and 2.
 * <p>
 * Modelled from Paho as well: a message that arrives while no callback is set is dropped (CommsCallback), the callback
 * thread that a disconnect or a lost connection stops and that the next connect starts again only when it has stopped
 * (a connect that comes while the thread is still in messageArrived hangs: CommsCallback.start waits for a thread that
 * does not start again, and the client stays connecting), a connect or a close while a connect is in progress fails
 * (32110), and a PUBACK of a disconnected client is dropped.
 * <p>
 * Not modelled: retained messages, wildcards and several topics, shared subscriptions, will messages, the QoS 2 flow
 * beyond PUBREC (Paho sends PUBREC when the message arrives and completes the flow on its own, so the broker never
 * sends a QoS 2 message again once the client has it), message expiry, topic aliases, keep alive, the persistence of
 * the client, the quiesce of a disconnect, the bound of 10 messages of the queue of the callback thread, the reconnect
 * delay that Paho keeps for all clients of the JVM (each client has its own here), the order of connectComplete and the
 * messages resent at the connect (separate tasks here), the threads that Paho calls connectComplete and disconnected on
 * (tasks of the callback executor here), and the reason codes of a SUBACK.
 * <p>
 * Timing (the one-stack rule of the harness): every call of the client takes a round trip and writes take effect at its
 * start; deliveries are tasks of the callback thread, one at a time, in order.
 */
final class FakeMqtt {

    static final String TOPIC = "dst/orders";
    static final String URI = "tcp://fake:1883";

    private final Dst dst;
    final List<String> violations = new ArrayList<>();
    boolean faults = true;
    double subscribeFailRate;
    double slowRate;
    /** [A] whether the broker keeps QoS 0 messages for an offline session (MQTT allows it, 3.1.2.4). */
    boolean queueQos0;

    private final DstExecutor callbackThread;
    private final DstExecutor reconnectThread;
    private Session session;
    private Client connected;
    private long unreachableUntil;
    private final List<Client> clients = new ArrayList<>();
    /** The QoS 1 and 2 messages that the broker accepted for a session that ended before it delivered them. */
    final List<Long> lostWithSession = new ArrayList<>();
    /**
     * The connects that came while the callback thread of the client was still in messageArrived after the connection
     * closed: they hang (see Client.connectToBroker), until the world ends the run with releaseHung.
     */
    int hungConnects;
    boolean releaseHung;

    /** Whether a client connects (its connect or its automatic reconnect is in progress), other than a hung one. */
    boolean anyConnecting() {
        return clients.stream().anyMatch(c -> c.isConnecting() && !c.isHung());
    }

    /** Whether a client hangs in a connect now (until the world ends the run with releaseHung). */
    boolean anyHanging() {
        return clients.stream().anyMatch(c -> c.isHung() && c.isConnecting());
    }

    /** Whether the client the consumer made last hangs in a connect. */
    boolean currentHung() {
        return !clients.isEmpty() && clients.get(clients.size() - 1).isHung();
    }

    private void reachable() throws MqttException {
        if (dst.now() < unreachableUntil) {
            throw new MqttException(MqttClientException.REASON_CODE_SERVER_CONNECT_ERROR);
        }
    }

    /**
     * The QoS 0 and 2 messages that the client received and dropped from its queue: when a connect cleared it, or when
     * the client was closed.
     */
    final List<Long> lostInClient = new ArrayList<>();
    Consumer<Long> onAck = n -> {
    };
    Consumer<Long> onDropped = n -> {
    };
    Consumer<Long> onDelivered = n -> {
    };
    Runnable onSubscribeFailed = () -> {
    };
    Runnable onSubscribed = () -> {
    };
    /** Whether the connection may be lost during a subscribe now (see the world). */
    BooleanSupplier subscribeFaultAllowed = () -> true;

    /** A message of the topic. */
    static final class Msg {
        final long n;
        final int qos;

        Msg(long n, int qos) {
            this.n = n;
            this.qos = qos;
        }
    }

    /** [S] 4.1 the session state of the server. */
    private final class Session {
        int subscribedQos = -1;
        final List<Msg> queued = new ArrayList<>();
        final Map<Integer, Msg> inflight = new LinkedHashMap<>();
        long expiryInterval;
        long expiresAt = Long.MAX_VALUE;
        int nextPacketId = 1;

        int packetId() {
            int id = nextPacketId;
            do {
                nextPacketId = nextPacketId % 65535 + 1;
            } while (inflight.containsKey(nextPacketId));
            return id;
        }
    }

    FakeMqtt(Dst dst) {
        this.dst = dst;
        this.callbackThread = dst.executor("paho-callback", 1);
        this.reconnectThread = dst.executor("paho-reconnect", 1);
    }

    /** A new Paho client, as PahoMqtt5Consumer.createClient makes one. */
    MqttClient newClient() {
        try {
            Client c = new Client();
            clients.add(c);
            return c;
        } catch (MqttException e) {
            throw new IllegalStateException(e);
        }
    }

    List<Client> clients() {
        return clients;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the publisher and the operator

    /** [S] 3.3.4: the broker sends the message to the subscribed session, at the lower of the two QoS. */
    void publish(long n, int qos) {
        expireSession();
        if (session == null || session.subscribedQos < 0) {
            dst.trace("mqtt: message " + n + " has no subscriber");
            return;
        }
        Msg m = new Msg(n, Math.min(qos, session.subscribedQos));
        if (m.qos == 0 && connected == null && !queueQos0) {
            dst.trace("mqtt: QoS 0 message " + n + " dropped, the client is offline");
            return;
        }
        session.queued.add(m);
        pump();
    }

    /** The broker closes the network connection of the client (a broker restart, a network fault). */
    void dropConnection() {
        Client c = connected;
        if (c != null) {
            dst.trace("mqtt: connection of the client lost");
            closeConnection(c);
            c.connectionLost();
        }
    }

    /** The broker cannot be reached for {@code millis}. */
    void unreachable(long millis) {
        unreachableUntil = Math.max(unreachableUntil, dst.now() + millis);
        dst.trace("mqtt: broker unreachable until t=" + unreachableUntil);
        dropConnection();
    }

    /** The QoS 1 messages sent to the client and not acknowledged, and the messages queued for it. */
    int pending() {
        expireSession();
        return session == null ? 0 : session.inflight.size() + session.queued.size();
    }

    boolean isConnected() {
        return connected != null;
    }

    boolean isSubscribed() {
        expireSession();
        return session != null && session.subscribedQos >= 0;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the broker

    private void expireSession() {
        if (session != null && connected == null && dst.now() >= session.expiresAt) {
            dst.trace("mqtt: session expired");
            endSession();
        }
    }

    private void endSession() {
        for (Msg m : session.inflight.values()) {
            lostWithSession.add(m.n);
        }
        for (Msg m : session.queued) {
            if (m.qos > 0) {
                lostWithSession.add(m.n);
            }
        }
        session = null;
    }

    /** [S] 4.9 the server sends no more than Receive Maximum QoS 1 and 2 PUBLISH packets that are not acknowledged. */
    private void pump() {
        Client c = connected;
        if (c == null || session == null) {
            return;
        }
        while (!session.queued.isEmpty()) {
            Msg m = session.queued.get(0);
            if (m.qos > 0 && session.inflight.size() >= c.receiveMaximum) {
                return;
            }
            session.queued.remove(0);
            int id = 0;
            if (m.qos > 0) {
                // [S] 4.3.2 / 4.3.3: a QoS 1 message is in flight until its PUBACK, a QoS 2 message until the client
                // has it (PUBREC; Paho then completes the flow on its own)
                id = session.packetId();
                session.inflight.put(id, m);
            }
            c.deliver(m, id, false);
        }
    }

    private boolean connect(Client c, MqttConnectionOptions options) throws MqttException {
        reachable();
        if (connected != null && connected != c) {
            // [S] 3.1.4: a second connection with the same client id makes the server disconnect the first one
            Client old = connected;
            closeConnection(old);
            old.connectionLost();
        }
        expireSession();
        boolean present;
        if (options.isCleanStart() || session == null) {
            // [S] 3.1.2.4: Clean Start 1 discards any existing session and starts a new one
            if (session != null) {
                endSession();
            }
            session = new Session();
            present = false;
        } else {
            present = true;
        }
        Long expiry = options.getSessionExpiryInterval();
        // [S] 3.1.2.11.2: an absent Session Expiry Interval is 0, the session ends when the connection closes
        session.expiryInterval = expiry == null ? 0 : expiry;
        session.expiresAt = Long.MAX_VALUE;
        connected = c;
        c.sessionPresent = present;
        dst.trace("mqtt: connected, session present=" + present);
        // [S] 4.4: "When a Client reconnects with Clean Start set to 0 and a session is present, both the Client and
        // Server MUST resend any unacknowledged PUBLISH packets (where QoS > 0) and PUBREL packets using their original
        // Packet Identifiers"
        for (Map.Entry<Integer, Msg> e : session.inflight.entrySet()) {
            c.deliver(e.getValue(), e.getKey(), true);
        }
        pump();
        return present;
    }

    private void closeConnection(Client c) {
        if (connected != c) {
            return;
        }
        connected = null;
        c.epoch++;
        c.connectedFlag = false;
        c.stopCallback();
        if (session != null) {
            if (session.expiryInterval == 0) {
                endSession();
            } else {
                session.expiresAt = dst.now() + session.expiryInterval * 1000;
            }
        }
    }

    private long latency() {
        if (faults && dst.chance(slowRate)) {
            return dst.between(200, 3000);
        }
        return dst.between(1, 20);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the Paho client

    /** The Paho MqttClient of the consumer: its network calls go to the fake broker. */
    final class Client extends MqttClient {
        private MqttCallback callback;
        private boolean manualAcks;
        private boolean closed;
        boolean connectedFlag;
        boolean sessionPresent;
        int epoch;
        int receiveMaximum = 65535;
        private MqttConnectionOptions options;
        private boolean reconnectCycle;
        private boolean everReconnected;
        private boolean disconnectedByUser;
        private boolean connecting;
        private boolean hung;
        // [P] CommsCallback: the callback thread, its target and current state, and its queue of received messages
        private boolean callbackTarget;
        private boolean callbackRunning;
        private boolean busy;
        private boolean workerScheduled;
        private final List<Object[]> inbox = new ArrayList<>();

        Client() throws MqttException {
            super(URI, "dst-consumer", new MemoryPersistence());
        }

        private void checkOpen() throws MqttException {
            if (closed) {
                violations.add("a call on a closed client");
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_CLOSED);
            }
        }

        @Override
        public void setCallback(MqttCallback callback) {
            this.callback = callback;
        }

        @Override
        public void setManualAcks(boolean manualAcks) {
            this.manualAcks = manualAcks;
        }

        @Override
        public boolean isConnected() {
            return connectedFlag;
        }

        @Override
        public void connect(MqttConnectionOptions options) throws MqttException {
            connectWithResult(options);
        }

        @Override
        public IMqttToken connectWithResult(MqttConnectionOptions options) throws MqttException {
            checkOpen();
            if (connecting) {
                // [P] MqttAsyncClient.connect: a connect while one is in progress fails
                throw new MqttException(MqttClientException.REASON_CODE_CONNECT_IN_PROGRESS);
            }
            if (connectedFlag) {
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_CONNECTED);
            }
            this.options = options;
            Integer rm = options.getReceiveMaximum();
            receiveMaximum = rm == null ? 65535 : rm;
            connecting = true;
            boolean present;
            try {
                dst.block(latency(), "connect");
                present = connectToBroker();
            } finally {
                connecting = false;
            }
            connectedFlag = true;
            disconnectedByUser = false;
            // [P] the connect after an automatic reconnect reports reconnect=true: the reconnecting flag that the
            // reconnect cycle sets is not cleared (MqttAsyncClient 1.2.5)
            boolean reconnect = everReconnected;
            callbackTask("connectComplete", () -> callback.connectComplete(reconnect, URI));
            return token(present);
        }

        @Override
        public IMqttToken subscribe(String topic, int qos) throws MqttException {
            checkOpen();
            if (!connectedFlag) {
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_NOT_CONNECTED);
            }
            if (!TOPIC.equals(topic)) {
                violations.add("subscribe to an unexpected topic " + topic);
            }
            int e = epoch;
            dst.block(latency(), "subscribe");
            // [P] MqttClient waits for the SUBACK without a time limit (timeToWait -1): the call fails only when the
            // connection is lost while it waits; [A] the broker closes the connection now and then during a subscribe
            if (e == epoch && faults && subscribeFaultAllowed.getAsBoolean() && dst.chance(subscribeFailRate)) {
                dst.trace("mqtt: the connection is lost during a subscribe");
                dropConnection();
            }
            if (e != epoch) {
                onSubscribeFailed.run();
                throw new MqttException(MqttClientException.REASON_CODE_CONNECTION_LOST);
            }
            session.subscribedQos = qos;
            onSubscribed.run();
            dst.trace("mqtt: subscribed with QoS " + qos);
            pump();
            return token(false);
        }

        @Override
        public void unsubscribe(String topic) throws MqttException {
            checkOpen();
            if (!connectedFlag) {
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_NOT_CONNECTED);
            }
            if (session != null) {
                session.subscribedQos = -1;
            }
            dst.block(latency(), "unsubscribe");
        }

        @Override
        public void disconnect() throws MqttException {
            checkOpen();
            if (callbackThread.label().equals(dst.currentExecutor())) {
                // [P] a disconnect from a callback is not allowed
                violations.add("disconnect from the callback thread");
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_DISCONNECT_PROHIBITED);
            }
            if (!connectedFlag) {
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_ALREADY_DISCONNECTED);
            }
            disconnectedByUser = true;
            reconnectCycle = false;
            closeConnection(this);
            dst.block(latency(), "disconnect");
        }

        boolean isClosed() {
            return closed;
        }

        boolean isConnecting() {
            return connecting;
        }

        @Override
        public void close(boolean force) throws MqttException {
            if (connecting) {
                // [P] ClientComms.close: a close while a connect is in progress fails, also with force
                throw new MqttException(MqttClientException.REASON_CODE_CONNECT_IN_PROGRESS);
            }
            if (connectedFlag) {
                // [P] close of a connected client fails
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_CONNECTED);
            }
            closed = true;
            reconnectCycle = false;
            // the messages still in the queue of the callback thread are gone with the client (a QoS 1 message stays in
            // flight at the broker; a QoS 0 or QoS 2 message is lost)
            for (Object[] item : inbox) {
                Msg m = (Msg) item[0];
                if (m.qos != 1) {
                    lostInClient.add(m.n);
                }
            }
            inbox.clear();
        }

        @Override
        public void close() throws MqttException {
            close(false);
        }

        @Override
        public void messageArrivedComplete(int id, int qos) throws MqttException {
            dst.trace("mqtt: messageArrivedComplete(" + id + ", " + qos + ")");
            checkOpen();
            if (qos != 1) {
                // [P] QoS 2: Paho completed the flow on its own; QoS 0 has nothing to acknowledge
                return;
            }
            if (!connectedFlag) {
                // [P] the PUBACK is queued and dropped with the closed connection
                dst.trace("mqtt: PUBACK for packet id " + id + " dropped, the client is disconnected");
                return;
            }
            ack(id);
        }

        private void ack(int id) {
            Msg m = session != null ? session.inflight.remove(id) : null;
            if (m == null) {
                // [A] the broker ignores a PUBACK for a packet id that has no message in flight
                dst.trace("mqtt: PUBACK for packet id " + id + " ignored, no message in flight");
                return;
            }
            dst.trace("mqtt: message " + m.n + " acknowledged");
            onAck.accept(m.n);
            pump();
        }

        /**
         * [P] the client receives a PUBLISH: CommsCallback.messageArrived(MqttPublish) runs on the receiver thread.
         * Without a callback set at that moment the message is not queued and no PUBACK is sent (a QoS 1 message stays
         * in flight at the broker); a QoS 2 message gets its PUBREC all the same (ClientState.notifyReceivedMsg), so
         * the broker never sends it again. Otherwise it goes to the queue of the callback thread.
         */
        void deliver(Msg m, int id, boolean dup) {
            if (m.qos == 2 && session != null) {
                session.inflight.remove(id);
            }
            if (callback == null) {
                dst.trace("mqtt: message " + m.n + " dropped, no callback");
                onDropped.accept(m.n);
            } else {
                inbox.add(new Object[] { m, id, dup, epoch });
                runCallback();
            }
            if (m.qos == 2) {
                pump();
            }
        }

        /**
         * [P] ConnectBG: the TCP connect (it fails when the broker cannot be reached), then CommsCallback.start, then
         * the CONNECT. start() restarts the thread only when it is STOPPED, and then waits until it runs; a thread that
         * is still in messageArrived after the connection closed is not STOPPED, so start() sets nothing and its wait
         * never ends: the connect hangs, and the client stays connecting (a close fails with 32110). The fake waits
         * until the world ends the run.
         */
        private boolean connectToBroker() throws MqttException {
            reachable();
            if (callbackRunning && !callbackTarget) {
                hung = true;
                hungConnects++;
                dst.trace("mqtt: connect while the callback thread is still in messageArrived: the connect hangs");
                dst.awaitUntil(() -> releaseHung, 24L * 3600 * 1000 - 1, "connect of a client whose callback thread does not"
                                                                         + " start again");
                throw new MqttException(MqttClientException.REASON_CODE_CLIENT_TIMEOUT);
            }
            startCallback();
            return FakeMqtt.this.connect(this, options);
        }

        boolean isHung() {
            return hung;
        }

        /** [P] CommsCallback.start: restarts the thread only when it is STOPPED. */
        private void startCallback() {
            if (!callbackRunning) {
                // [P] start() clears the queue: the messages of an earlier connection that were not delivered are gone
                for (Object[] item : inbox) {
                    Msg m = (Msg) item[0];
                    if (m.qos != 1) {
                        lostInClient.add(m.n);
                    }
                }
                inbox.clear();
                callbackRunning = true;
                callbackTarget = true;
                runCallback();
            }
        }

        /** [P] CommsCallback.stop (on a disconnect and on a lost connection): returns at once. */
        void stopCallback() {
            callbackTarget = false;
            if (!busy) {
                callbackRunning = false;
            }
        }

        private void runCallback() {
            if (!callbackRunning || workerScheduled || inbox.isEmpty()) {
                return;
            }
            workerScheduled = true;
            callbackThread.execute(() -> {
                workerScheduled = false;
                if (!callbackTarget) {
                    callbackRunning = false;
                    return;
                }
                if (inbox.isEmpty()) {
                    return;
                }
                Object[] item = inbox.remove(0);
                busy = true;
                try {
                    handle((Msg) item[0], (int) item[1], (boolean) item[2], (int) item[3]);
                } finally {
                    busy = false;
                }
                if (!callbackTarget) {
                    callbackRunning = false;
                    dst.trace("mqtt: the callback thread stops");
                    return;
                }
                runCallback();
            });
        }

        /** [P] CommsCallback.handleMessage: delivers the message, then sends the PUBACK unless the acks are manual. */
        private void handle(Msg m, int id, boolean dup, int e) {
            MqttMessage message = new MqttMessage(("m" + m.n).getBytes(StandardCharsets.UTF_8));
            message.setQos(m.qos);
            message.setId(id);
            message.setDuplicate(dup);
            onDelivered.accept(m.n);
            try {
                callback.messageArrived(TOPIC, message);
            } catch (Exception ex) {
                // [P] an exception from messageArrived closes the connection
                violations.add("messageArrived threw " + ex);
            }
            if (e == epoch && m.qos == 1 && !manualAcks) {
                ack(id);
            }
        }

        private void callbackTask(String what, Runnable r) {
            callbackThread.execute(() -> {
                if (callback != null || what.startsWith("deliver")) {
                    r.run();
                }
            });
        }

        /** [P] the connection was lost: the callback is told, and the automatic reconnect starts. */
        void connectionLost() {
            connectedFlag = false;
            if (callback != null) {
                MqttCallback cb = callback;
                callbackTask("disconnected", () -> cb.disconnected(new MqttDisconnectResponse(
                        new MqttException(MqttClientException.REASON_CODE_CONNECTION_LOST))));
            }
            if (options != null && options.isAutomaticReconnect() && !disconnectedByUser && !closed) {
                reconnectCycle = true;
                scheduleReconnect(1000);
            }
        }

        private void scheduleReconnect(long delay) {
            reconnectThread.schedule("automatic reconnect", delay, () -> {
                if (!reconnectCycle || connectedFlag || closed || disconnectedByUser || connecting) {
                    return;
                }
                connecting = true;
                try {
                    dst.block(latency(), "reconnect");
                    if (connectedFlag || closed || disconnectedByUser) {
                        return;
                    }
                    connectToBroker();
                    connectedFlag = true;
                    reconnectCycle = false;
                    everReconnected = true;
                    callbackTask("connectComplete", () -> callback.connectComplete(true, URI));
                } catch (MqttException e) {
                    long max = Math.max(1000, options.getMaxReconnectDelay());
                    scheduleReconnect(Math.min(delay * 2, max));
                } finally {
                    connecting = false;
                }
            });
        }

        private IMqttToken token(boolean present) {
            return (IMqttToken) Proxy.newProxyInstance(IMqttToken.class.getClassLoader(), new Class<?>[] { IMqttToken.class },
                    (p, method, args) -> switch (method.getName()) {
                        case "getSessionPresent" -> present;
                        case "isComplete" -> true;
                        case "toString" -> "FakeMqtt.token";
                        case "hashCode" -> System.identityHashCode(p);
                        case "equals" -> p == args[0];
                        default -> {
                            violations.add("not modelled: IMqttToken." + method.getName());
                            throw new UnsupportedOperationException(method.getName());
                        }
                    });
        }
    }
}
