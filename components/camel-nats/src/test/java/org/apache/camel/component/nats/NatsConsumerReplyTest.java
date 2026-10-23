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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.nats.client.Connection;
import io.nats.client.Message;
import io.nats.client.Subscription;
import io.nats.client.impl.NatsJetStreamMetaData;
import io.nats.client.impl.NatsMessage;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExceptionHandler;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reply a nats consumer with exchangePattern=InOut publishes (no server: the connection and the messages are
 * stubs).
 */
public class NatsConsumerReplyTest extends CamelTestSupport {

    private static final String JETSTREAM_ACK_SUBJECT = "$JS.ACK.ORDERS.c1-durable.1.1.1.1700000000000000000.0";

    private final List<String> publishedSubjects = new ArrayList<>();
    private final List<byte[]> publishedData = new ArrayList<>();
    private final List<Throwable> handledErrors = new ArrayList<>();
    private final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[] { Connection.class }, (proxy, method, args) -> {
                if ("publish".equals(method.getName()) && args.length == 2 && args[1] instanceof byte[] data) {
                    publishedSubjects.add((String) args[0]);
                    publishedData.add(data);
                }
                return null;
            });

    @Test
    void textBodyIsReplied() throws Exception {
        onMessage("nats:test?exchangePattern=InOut", coreMessage(), e -> e.getMessage().setBody("Hello"));

        assertEquals(List.of("reply-inbox"), publishedSubjects);
        assertArrayEquals("Hello".getBytes(StandardCharsets.UTF_8), publishedData.get(0));
        assertTrue(handledErrors.isEmpty());
    }

    @Test
    void noBodyIsRepliedWithEmptyMessage() throws Exception {
        onMessage("nats:test?exchangePattern=InOut", coreMessage(), e -> e.getMessage().setBody(null));

        assertEquals(List.of("reply-inbox"), publishedSubjects);
        assertEquals(0, publishedData.get(0).length);
        assertTrue(handledErrors.isEmpty());
    }

    @Test
    void bodyThatIsNotBytesIsNotRepliedAndIsReported() throws Exception {
        onMessage("nats:test?exchangePattern=InOut", coreMessage(), e -> e.getMessage().setBody(Map.of("a", 1)));

        assertTrue(publishedSubjects.isEmpty());
        assertEquals(1, handledErrors.size());
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, handledErrors.get(0));
        assertTrue(cause.getMessage().contains("Map"), cause.getMessage());
    }

    @Test
    void jetStreamMessageIsNotReplied() throws Exception {
        onMessage("nats:test?exchangePattern=InOut&jetstreamEnabled=true&jetstreamName=ORDERS",
                jetStreamMessage(), e -> e.getMessage().setBody("reply-body"));

        assertTrue(publishedSubjects.isEmpty(), "published to " + publishedSubjects);
        assertTrue(handledErrors.isEmpty(), "errors " + handledErrors);
    }

    private void onMessage(String uri, Message message, Processor route) throws Exception {
        NatsEndpoint endpoint = context.getEndpoint(uri, NatsEndpoint.class);
        NatsConsumer consumer = (NatsConsumer) endpoint.createConsumer(route);
        consumer.setExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handledErrors.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handledErrors.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handledErrors.add(exception);
            }
        });
        consumer.new NatsConsumingTask(connection, endpoint.getConfiguration()).new CamelNatsMessageHandler()
                .onMessage(message);
    }

    private Message coreMessage() {
        return message("reply-inbox", false);
    }

    private Message jetStreamMessage() {
        return message(JETSTREAM_ACK_SUBJECT, true);
    }

    private Message message(String replyTo, boolean jetStream) {
        Subscription subscription = (Subscription) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] { Subscription.class }, (proxy, method, args) -> null);
        return (Message) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Message.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getSubject" -> "test";
                    case "getReplyTo" -> replyTo;
                    case "getData" -> "request".getBytes(StandardCharsets.UTF_8);
                    case "getSubscription" -> subscription;
                    case "getSID" -> "1";
                    case "getConnection" -> connection;
                    case "isJetStream" -> jetStream;
                    case "isStatusMessage", "hasHeaders", "isUtf8mode" -> false;
                    case "metaData" -> jetStream
                            ? new NatsJetStreamMetaData(new JetStreamNatsMessage(replyTo)) : null;
                    case "toString" -> "Message[" + replyTo + "]";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /**
     * The metadata of a JetStream message is parsed from its ack subject.
     */
    private static final class JetStreamNatsMessage extends NatsMessage {

        JetStreamNatsMessage(String replyTo) {
            super("test", replyTo, new byte[0]);
        }

        @Override
        public boolean isJetStream() {
            return true;
        }
    }
}
