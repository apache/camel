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

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import io.nats.client.Connection;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.JetStreamStatusException;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.PullSubscribeOptions;
import io.nats.client.api.ApiResponse;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.ConsumerInfo;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.ServerInfo;
import io.nats.client.api.StreamInfo;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.impl.NatsMessage;
import io.nats.client.support.DateTimeUtils;
import io.nats.client.support.JsonParser;
import io.nats.client.support.Status;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The JetStream pull consumer (the default with jetstreamEnabled=true) uses an ephemeral consumer when no durableName
 * is set. The NATS server deletes an ephemeral consumer that saw no pull request for its inactive threshold (5 seconds
 * by default), for example while the client was disconnected for a few seconds, and a server restart loses it. The
 * consumer must then subscribe again, starting at the first message it did not acknowledge, instead of fetching from
 * the deleted consumer for ever.
 */
class NatsConsumerPullConsumerDeletedTest extends CamelTestSupport {

    private final FakeServer server = new FakeServer();

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getRegistry().bind("conn", server.connection());
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("nats:orders?connection=#conn&jetstreamEnabled=true&jetstreamName=ORDERS&pullFetchTimeout=100")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    void consumesAgainAfterTheServerDeletedTheEphemeralConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // the client was disconnected for longer than the inactive threshold: the server deleted the consumer
        server.deleteConsumers(false);

        mock.reset();
        mock.expectedBodiesReceived("order-2", "order-3");
        server.publish("order-2");
        server.publish("order-3");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
    }

    @Test
    void consumesAgainAfterAWaitingPullGotConsumerDeleted() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        // the consumer is deleted while a pull request waits: that request gets 409 Consumer Deleted
        server.deleteConsumers(true);

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        server.publish("order-2");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        assertEquals(2, server.consumersCreated(), "one consumer created again");
    }

    @Test
    void consumesAStreamCreatedAgainFromItsStart() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1", "order-2");
        server.publish("order-1");
        server.publish("order-2");
        mock.assertIsSatisfied();

        // a memory stream after a server restart: the consumer is gone and order-3 is sequence 1 of the new stream
        server.createStreamAgain();

        mock.reset();
        mock.expectedBodiesReceived("order-3");
        server.publish("order-3");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        ConsumerConfiguration again = server.subscribed().get(1);
        assertEquals(DeliverPolicy.All, again.getDeliverPolicy(), "the consumer configuration, not a start sequence");
    }

    @Test
    void workQueueStreamIsSubscribedAgainWithDeliverPolicyAll() throws Exception {
        server.retention(RetentionPolicy.WorkQueue);
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("order-1");
        server.publish("order-1");
        mock.assertIsSatisfied();

        server.deleteConsumers(false);

        mock.reset();
        mock.expectedBodiesReceived("order-2");
        server.publish("order-2");
        mock.setResultWaitTime(20_000);
        mock.assertIsSatisfied();
        assertEquals(2, server.consumersCreated(), "one consumer created again");
        assertEquals(DeliverPolicy.All, server.subscribed().get(1).getDeliverPolicy(),
                "a work queue stream accepts only the deliver policy All");
    }

    /**
     * A JetStream server with one stream, faked at the level of the jnats interfaces the consumer calls. A consumer
     * delivers from its start sequence; once deleted, a fetch on it gets nothing at once (jnats ends the fetch on the
     * 503 "no responders" status), and getConsumerInfo answers null, as jnats does when the server answers consumer not
     * found (10014). A live consumer answers its info. A stream with WorkQueue retention removes the acknowledged
     * messages and refuses a consumer whose deliver policy is not All (400, 10101), and a stream created again starts
     * its sequences at 1 again with a new create time.
     */
    static final class FakeServer {
        final List<byte[]> stream = new ArrayList<>();
        final Set<Long> removed = new HashSet<>();
        final List<FakeConsumer> consumers = new ArrayList<>();
        final List<ConsumerConfiguration> subscribed = new ArrayList<>();
        RetentionPolicy retention = RetentionPolicy.Limits;
        ZonedDateTime created = DateTimeUtils.gmtNow().minusHours(1);
        int consumersCreated;
        int fetches;
        int consumerInfoRequests;
        RuntimeException failNextSubscribe;
        Status fetchStatus;

        final class FakeConsumer {
            long next;
            boolean deleted;
            boolean tellDeleted;
            final List<Long> redeliver = new ArrayList<>();
        }

        synchronized int consumersCreated() {
            return consumersCreated;
        }

        synchronized int fetches() {
            return fetches;
        }

        synchronized int consumerInfoRequests() {
            return consumerInfoRequests;
        }

        synchronized void retention(RetentionPolicy retention) {
            this.retention = retention;
        }

        /**
         * Every fetch fails with this status, for example 409 Consumer is push based.
         */
        synchronized void failFetches(Status status) {
            fetchStatus = status;
        }

        /**
         * The stream is created again, for example a memory stream after a server restart: its consumers are gone and
         * its sequences start again at 1.
         */
        synchronized void createStreamAgain() {
            stream.clear();
            removed.clear();
            created = created.plusMinutes(1);
            deleteConsumers(false);
        }

        synchronized void publish(String body) {
            stream.add(body.getBytes(StandardCharsets.UTF_8));
            notifyAll();
        }

        synchronized void deleteConsumers(boolean waitingRequest) {
            for (FakeConsumer c : consumers) {
                c.deleted = true;
                c.tellDeleted = waitingRequest;
            }
            notifyAll();
        }

        /**
         * The deleted consumers are created again with the same name (a durable created again by an administrator).
         */
        synchronized void restoreConsumers() {
            for (FakeConsumer c : consumers) {
                c.deleted = false;
            }
            notifyAll();
        }

        synchronized void failNextSubscribe(RuntimeException e) {
            failNextSubscribe = e;
        }

        synchronized List<ConsumerConfiguration> subscribed() {
            return new ArrayList<>(subscribed);
        }

        Connection connection() {
            JetStreamManagement jsm = proxy(JetStreamManagement.class, (name, args) -> switch (name) {
                case "getStreamInfo", "addStream" -> streamInfo();
                default -> null;
            });
            JetStream js = proxy(JetStream.class, (name, args) -> {
                if ("subscribe".equals(name) && args.length == 2 && args[1] instanceof PullSubscribeOptions o) {
                    return subscribe(o.getConsumerConfiguration());
                }
                throw new UnsupportedOperationException(name);
            });
            ServerInfo info = new ServerInfo("INFO {\"server_id\":\"fake\",\"jetstream\":true}");
            return proxy(Connection.class, (name, args) -> switch (name) {
                case "getServerInfo" -> info;
                case "jetStreamManagement" -> jsm;
                case "jetStream" -> js;
                case "getStatus" -> Connection.Status.CONNECTED;
                default -> null;
            });
        }

        private synchronized StreamInfo streamInfo() {
            return new StreamInfo(
                    JsonParser.parseUnchecked("{\"config\":{\"name\":\"ORDERS\",\"subjects\":[\"orders\"],"
                                              + "\"retention\":\"" + retention + "\"},\"created\":\""
                                              + DateTimeUtils.toRfc3339(created) + "\",\"state\":{}}"));
        }

        private synchronized JetStreamSubscription subscribe(ConsumerConfiguration cc) throws JetStreamApiException {
            if (failNextSubscribe != null) {
                RuntimeException e = failNextSubscribe;
                failNextSubscribe = null;
                throw e;
            }
            if (retention == RetentionPolicy.WorkQueue && cc.getDeliverPolicy() != DeliverPolicy.All) {
                // the server's answer as jnats reads it (ApiResponse is abstract)
                throw new JetStreamApiException(
                        new ApiResponse<Void>(
                                JsonParser.parseUnchecked(
                                        "{\"error\":{\"code\":400,\"err_code\":10101,"
                                                          + "\"description\":\"consumer must be deliver all on workqueue stream\"}}")) {
                        });
            }
            subscribed.add(cc);
            FakeConsumer c = new FakeConsumer();
            c.next = cc != null && cc.getDeliverPolicy() == DeliverPolicy.ByStartSequence ? cc.getStartSequence() : 1;
            consumers.add(c);
            consumersCreated++;
            JetStreamSubscription[] self = new JetStreamSubscription[1];
            self[0] = proxy(JetStreamSubscription.class, (name, args) -> switch (name) {
                case "fetch" -> fetch(c, self[0], (Integer) args[0], (Duration) args[1]);
                case "getConsumerInfo" -> consumerInfo(c);
                case "isActive" -> true;
                default -> null;
            });
            return self[0];
        }

        private synchronized ConsumerInfo consumerInfo(FakeConsumer c) {
            consumerInfoRequests++;
            if (c.deleted) {
                return null;
            }
            return new ConsumerInfo(JsonParser.parseUnchecked("{\"stream_name\":\"ORDERS\",\"name\":\"c\",\"config\":{}}"));
        }

        private synchronized List<Message> fetch(FakeConsumer c, JetStreamSubscription sub, int batch, Duration timeout) {
            fetches++;
            long deadline = System.nanoTime() + timeout.toNanos();
            List<Message> out = new ArrayList<>();
            if (fetchStatus != null) {
                throw new JetStreamStatusException(fetchStatus, sub);
            }
            while (true) {
                if (c.deleted) {
                    if (c.tellDeleted) {
                        c.tellDeleted = false;
                        throw new JetStreamStatusException(new Status(409, Status.CONSUMER_DELETED), sub);
                    }
                    return out;
                }
                while (out.size() < batch && (!c.redeliver.isEmpty() || c.next <= stream.size())) {
                    long seq = c.redeliver.isEmpty() ? c.next++ : c.redeliver.remove(0);
                    if (!removed.contains(seq)) {
                        out.add(message(c, sub, seq));
                    }
                }
                long left = deadline - System.nanoTime();
                if (!out.isEmpty() || left <= 0) {
                    return out;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, left);
                } catch (InterruptedException e) {
                    // as jnats: an interrupted fetch keeps the interrupt and returns what it has
                    Thread.currentThread().interrupt();
                    return out;
                }
            }
        }

        private Message message(FakeConsumer c, JetStreamSubscription sub, long seq) {
            byte[] data = stream.get((int) seq - 1);
            // a JetStream message carries its metadata in the reply subject of the acknowledgement
            NatsJetStreamMetaData meta = new NatsJetStreamMetaData(
                    new NatsMessage("orders", "$JS.ACK.ORDERS.c.1." + seq + "." + seq + ".0.0", data) {
                        @Override
                        public boolean isJetStream() {
                            return true;
                        }
                    });
            return proxy(Message.class, (name, args) -> switch (name) {
                case "getData" -> data;
                case "getSubject" -> "orders";
                case "getSID" -> "1";
                case "getSubscription" -> sub;
                case "isJetStream" -> true;
                case "isStatusMessage" -> false;
                case "metaData" -> meta;
                case "nak" -> {
                    synchronized (this) {
                        c.redeliver.add(seq);
                    }
                    yield null;
                }
                case "ack", "term" -> {
                    synchronized (this) {
                        if (retention == RetentionPolicy.WorkQueue) {
                            removed.add(seq);
                        }
                    }
                    yield null;
                }
                default -> null;
            });
        }

        @FunctionalInterface
        interface Handler {
            Object handle(String method, Object[] args) throws Exception;
        }

        @SuppressWarnings("unchecked")
        static <T> T proxy(Class<T> type, Handler handler) {
            return (T) Proxy.newProxyInstance(FakeServer.class.getClassLoader(), new Class<?>[] { type },
                    (p, m, a) -> {
                        if (m.getDeclaringClass() == Object.class) {
                            return switch (m.getName()) {
                                case "hashCode" -> System.identityHashCode(p);
                                case "equals" -> p == a[0];
                                default -> type.getSimpleName() + "@fake";
                            };
                        }
                        Object r = handler.handle(m.getName(), a == null ? new Object[0] : a);
                        if (r == null && m.getReturnType() == boolean.class) {
                            return false;
                        }
                        return r;
                    });
        }
    }
}
