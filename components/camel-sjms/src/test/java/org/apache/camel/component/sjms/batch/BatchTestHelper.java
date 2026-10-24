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
package org.apache.camel.component.sjms.batch;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.Destination;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.Session;
import jakarta.jms.Topic;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.consumer.SimpleMessageListenerContainer;
import org.apache.camel.component.sjms.jms.JmsConstants;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("unchecked")
public final class BatchTestHelper {

    static final String DEFAULT_MESSAGE_TEXT = "Hello World!";
    static final String BATCH_ROUTEBUILDER_MOCK_START = "mock:%s.start";
    static final String BATCH_ROUTEBUILDER_MOCK_FINISH = "mock:%s.finish";

    private BatchTestHelper() {
    }

    static void sendMessages(ProducerTemplate template, String endpoint, int count) {
        sendMessagesWithText(template, endpoint, count, DEFAULT_MESSAGE_TEXT);
    }

    static void sendMessagesWithText(ProducerTemplate template, String endpoint, int count, String text) {
        for (int i = 1; i <= count; i++) {
            template.sendBody(endpoint, format(text, i));
        }
    }

    static int receivedMessages(MockEndpoint mock) {
        return mock.getReceivedExchanges().stream()
                .mapToInt(e -> getBatchExchanges(e).size())
                .sum();
    }

    static List<String> getBatchBodiesAsString(Exchange batchExchange) {
        return BatchTestHelper.getBatchExchanges(batchExchange).stream()
                .map(e -> e.getIn().getBody(String.class))
                .toList();
    }

    /** Extracts the batch body as a List<Exchange> from a batch Exchange, asserting the type. */
    static List<Exchange> getBatchExchanges(Exchange batchExchange) {
        List<Exchange> body = batchExchange.getIn().getBody(List.class);
        assertNotNull(body, "batch exchange body was null");
        for (Object o : body) {
            assertInstanceOf(Exchange.class, o, "batch element was not an Exchange: " + o);
        }
        return body;
    }

    /**
     * Asserts the mock received exactly expectedSizes.length batch exchanges, in order, with each batch's size matching
     * the corresponding element. Requires concurrentConsumers=1 (or an otherwise deterministic single-worker setup) so
     * that arrival order is meaningful.
     */
    static void assertBatchSizesInOrder(MockEndpoint mock, int... expectedSizes) {
        List<Exchange> received = mock.getExchanges();

        assertEquals(expectedSizes.length, received.size(),
                () -> String.format("Unexpected number of batches on %s: expected %d %s but received %d %s",
                        mock.getEndpointUri(),
                        expectedSizes.length, Arrays.toString(expectedSizes),
                        received.size(), describeBatchSizes(received)));

        for (int i = 0; i < expectedSizes.length; i++) {
            assertBatchSize(received.get(i), expectedSizes[i], i, mock);
        }
    }

    static void assertBatchSize(Exchange exchange, int expectedSize, int index, MockEndpoint mock) {
        Object body = exchange.getIn().getBody();
        assertNotNull(body,
                () -> String.format("Batch #%d on %s has no body", index, mock.getEndpointUri()));
        assertInstanceOf(List.class, body,
                () -> String.format("Batch #%d on %s: expected the body to be a List but was %s",
                        index, mock.getEndpointUri(), body.getClass().getName()));

        int actualSize = ((List<?>) body).size();
        assertEquals(expectedSize, actualSize,
                () -> String.format("Batch #%d on %s has the wrong size: expected %d but was %d (exchange id %s)",
                        index, mock.getEndpointUri(), expectedSize, actualSize, exchange.getExchangeId()));
    }

    /** Renders received batch sizes, e.g. [5, 5, 3], for failure messages. */
    private static String describeBatchSizes(List<Exchange> exchanges) {
        return exchanges.stream()
                .map(e -> e.getIn().getBody() instanceof List<?> l ? String.valueOf(l.size()) : "non-list")
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * Asserts the JMSRedelivered flag of every message in the batch
     */
    static void assertAllBatchMessagesRedelivered(Exchange batchExchange) {
        assertTrue(((List<Exchange>) batchExchange.getIn().getBody()).stream().allMatch(
                e -> Boolean.TRUE.equals(e.getMessage().getHeader(JmsConstants.JMS_REDELIVERED, Boolean.class))),
                "not all messages have the JMSRedelivered=true");
    }

    /**
     * Asserts the JMSRedelivered flag of every message in the batch whose body equals the given body. Fails if no
     * message has that body, so a typo in the body cannot make the assertion pass vacuously.
     */
    static void assertBatchRedelivered(Exchange batchExchange, String bodyRegex, boolean expectedRedelivered) {
        Pattern pattern = Pattern.compile(bodyRegex);

        List<Exchange> matching = getBatchExchanges(batchExchange).stream()
                .filter(e -> pattern.matcher(e.getIn().getBody(String.class)).matches())
                .toList();

        assertFalse(matching.isEmpty(), "no message in the batch has body matching regex " + bodyRegex);
        assertTrue(
                matching.stream().allMatch(e -> Objects.equals(expectedRedelivered,
                        e.getIn().getHeader(JmsConstants.JMS_REDELIVERED, Boolean.class))),
                "unexpected JMSRedelivered for a message with body matching regex " + bodyRegex);
    }

    static RouteBuilder createBatchRoute(
            String queueName, String id, int batchSize, int batchInterval, Boolean transacted,
            String acknowledgementMode,
            int concurrentConsumers, Processor processor, String connectionFactory) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batching", true);
        params.put("batchSize", batchSize);
        params.put("batchInterval", batchInterval);
        params.put("transacted", transacted);
        params.put("acknowledgementMode", acknowledgementMode);
        params.put("concurrentConsumers", concurrentConsumers);
        params.put("connectionFactory", connectionFactory);

        String query = params.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("&"));

        String fromJmsEndpoint = "sjms:queue:" + format(queueName, id) + (query.isEmpty() ? "" : "?" + query);

        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fromJmsEndpoint)
                        .id(id)
                        .toF(BATCH_ROUTEBUILDER_MOCK_START, id)
                        .process(processor)
                        .toF(BATCH_ROUTEBUILDER_MOCK_FINISH, id);
            }
        };
    }

    static void triggerConnectionFailure(CamelContext context, String routeId) throws Exception {
        SjmsConsumer consumer = (SjmsConsumer) context
                .getRoute(routeId)
                .getConsumer();

        ((SimpleMessageListenerContainer) ((SjmsConsumer) context.getRoute(routeId).getConsumer())
                .getListenerContainer()).onException(new JMSException("Simulated failure of the connection"));
    }

    static long countWorkerThreads(String queue) {
        return Thread.getAllStackTraces().keySet().stream()
                .filter(Thread::isAlive)
                .map(Thread::getName)
                .filter(n -> n.contains("SjmsBatchConsumer") && n.contains(queue))
                .count();
    }

    static class DoNothingProcessor implements Processor {

        @Override
        public void process(Exchange exchange) {
            //NOOP
        }
    }

    abstract static class Tracked<T> implements InvocationHandler {
        final AtomicBoolean closed = new AtomicBoolean();
        private final Class<T> type;
        private final T target;

        Tracked(Class<T> type, T target) {
            this.type = type;
            this.target = target;
        }

        /** The object handed to the code under test. */
        @SuppressWarnings("unchecked")
        final T proxy() {
            return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, this);
        }

        @Override
        public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result;
            try {
                result = method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
            if (method.getName().equals("close")) {
                closed.set(true);
            }
            return after(method.getName(), args, result);
        }

        /** Record the call; return the result, or a wrapped replacement for it. */
        Object after(String method, Object[] args, Object result) throws JMSException {
            return result;
        }
    }

    static final class TrackedConnectionFactory extends Tracked<ConnectionFactory> {
        final List<TrackedConnection> connections = new CopyOnWriteArrayList<>();

        TrackedConnectionFactory(ConnectionFactory delegate) {
            super(ConnectionFactory.class, delegate);
        }

        @Override
        Object after(String method, Object[] args, Object result) {
            if (result instanceof Connection c) {
                TrackedConnection tc = new TrackedConnection(c);
                connections.add(tc);
                return tc.proxy();
            }
            return result;
        }

        int getCreateCount() {
            return connections.size();
        }

        int messages(String destination) {
            return sumConsumers(destination, c -> c.messages.get());
        }

        private int sumConsumers(String destination, ToIntFunction<TrackedConsumer> f) {
            return connections.stream().flatMap(c -> c.sessions.stream())
                    .flatMap(s -> s.consumers.stream())
                    .filter(c -> c.destination.equals(destination))
                    .mapToInt(f).sum();
        }
    }

    static final class TrackedConnection extends Tracked<Connection> {
        final List<TrackedSession> sessions = new CopyOnWriteArrayList<>();

        TrackedConnection(Connection delegate) {
            super(Connection.class, delegate);
        }

        @Override
        Object after(String method, Object[] args, Object result) {
            if (result instanceof Session s) {
                TrackedSession ts = new TrackedSession(s);
                sessions.add(ts);
                return ts.proxy();
            }
            return result;
        }

        int openSessions() {
            return closed.get() ? 0 : (int) sessions.stream().filter(s -> !s.closed.get()).count();
        }

        int openConsumers() {
            return closed.get()
                    ? 0 : sessions.stream()
                            .filter(s -> !s.closed.get())
                            .mapToInt(TrackedSession::openConsumers).sum();
        }
    }

    static final class TrackedSession extends Tracked<Session> {
        final List<TrackedConsumer> consumers = new CopyOnWriteArrayList<>();
        final AtomicInteger commits = new AtomicInteger();

        TrackedSession(Session delegate) {
            super(Session.class, delegate);
        }

        @Override
        Object after(String method, Object[] args, Object result) throws JMSException {
            if (method.equals("commit")) {
                commits.incrementAndGet();
            }
            if (result instanceof MessageConsumer mc && args != null && args[0] instanceof Destination d) {
                TrackedConsumer tc = new TrackedConsumer(nameOf(d), mc);
                consumers.add(tc);
                return tc.proxy();
            }
            return result;
        }

        int openConsumers() {
            return (int) consumers.stream().filter(c -> !c.closed.get()).count();
        }
    }

    static final class TrackedConsumer extends Tracked<MessageConsumer> {
        final String destination;
        final AtomicInteger messages = new AtomicInteger();
        final AtomicInteger empty = new AtomicInteger();

        TrackedConsumer(String destination, MessageConsumer delegate) {
            super(MessageConsumer.class, delegate);
            this.destination = destination;
        }

        @Override
        Object after(String method, Object[] args, Object result) {
            if (method.startsWith("receive")) {
                (result != null ? messages : empty).incrementAndGet();
            }
            return result;
        }
    }

    private static String nameOf(Destination d) throws JMSException {
        if (d instanceof jakarta.jms.Queue q)
            return q.getQueueName();
        if (d instanceof Topic t)
            return t.getTopicName();
        return d.toString();
    }
}
