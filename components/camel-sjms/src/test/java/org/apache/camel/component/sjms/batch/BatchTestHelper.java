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

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConstants;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.jms.JmsConstants;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisVMService;

import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SuppressWarnings("unchecked")
public final class BatchTestHelper {

    static final String DEFAULT_MESSAGE_TEXT = "Hello World!";
    static final String BATCH_ROUTEBUILDER_MOCK_START = "mock:%s.start";
    static final String BATCH_ROUTEBUILDER_MOCK_FINISH = "mock:%s.complete";

    private BatchTestHelper() {
    }

    static void sendMessages(ProducerTemplate template, String endpoint, int count) {
        sendMessagesWithText(template, endpoint, count, DEFAULT_MESSAGE_TEXT);
    }

    static void sendMessagesWithText(ProducerTemplate template, String endpoint, int count, String text) {
        for (int i = 0; i < count; i++) {
            template.sendBody(endpoint, format(text, i));
        }
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

    /** Asserts a single batch exchange has exactly the given number of messages. */
    static void assertBatchSize(Exchange batchExchange, int expectedSize) {
        List<Exchange> batch = getBatchExchanges(batchExchange);
        assertEquals(expectedSize, batch.size(), "unexpected batch size");
        assertEquals(expectedSize,
                batchExchange.getIn().getHeader(SjmsConstants.SJMS_BATCH_SIZE_HEADER, Integer.class),
                "CamelSjmsBatchSize header did not match actual batch size");
    }

    /**
     * Asserts the mock received exactly expectedSizes.length batch exchanges, in order, with each batch's size matching
     * the corresponding element. Requires concurrentConsumers=1 (or an otherwise deterministic single-worker setup) so
     * that arrival order is meaningful.
     */
    static void assertBatchSizesInOrder(MockEndpoint mock, int... expectedSizes) {
        assertEquals(expectedSizes.length, mock.getExchanges().size(),
                "Number of expected sizes ddoes not match the number of exchanges");
        List<Exchange> received = mock.getExchanges();
        for (int i = 0; i < expectedSizes.length; i++) {
            assertBatchSize(received.get(i), expectedSizes[i]);
        }
    }

    static RouteBuilder createBatchRoute(
            String queueName, String id, int batchSize, int batchInterval, Boolean transacted,
            String acknowledgementMode,
            int concurrentConsumers, Processor processor) {

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("batching", true);
        params.put("batchSize", batchSize);
        params.put("batchInterval", batchInterval);
        params.put("transacted", transacted);
        params.put("acknowledgementMode", acknowledgementMode);
        params.put("concurrentConsumers", concurrentConsumers);

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

    /**
     * Asserts the JMSRedelivered flag of every message in the batch whose body equals the given body. Fails if no
     * message has that body, so a typo in the body cannot make the assertion pass vacuously.
     */
    static void assertBatchRedelivered(Exchange batchExchange, String body, boolean expectedRedelivered) {
        List<Exchange> matching = getBatchExchanges(batchExchange).stream()
                .filter(e -> body.equals(e.getIn().getBody(String.class)))
                .toList();

        assertFalse(matching.isEmpty(), "no message in the batch has body " + body);
        for (Exchange e : matching) {
            assertEquals(expectedRedelivered,
                    e.getIn().getHeader(JmsConstants.JMS_REDELIVERED, Boolean.class),
                    "unexpected JMSRedelivered for a message with body " + body);
        }
    }

    static void triggerConnectionFailure(CamelContext context, String routeId) throws Exception {
        SjmsConsumer consumer = (SjmsConsumer) context
                .getRoute(routeId)
                .getConsumer();

        Field field = SjmsConsumer.class.getDeclaredField("listenerContainer");

        field.setAccessible(true);

        ExceptionListener listener = (ExceptionListener) field.get(consumer);

        listener.onException(
                new JMSException("Simulated connection failure"));
    }

    static class DoNothingProcessor implements org.apache.camel.Processor {

        @Override
        public void process(Exchange exchange) {
            //NOOP
        }
    }

    static boolean closeSessionForQueue(
            ArtemisService service,
            String queueName)
            throws Exception {

        if (service instanceof ArtemisVMService vmService) {
            ActiveMQServer server = vmService.getEmbeddedBrokerService().getActiveMQServer();
            for (ServerSession session : server.getSessions()) {
                for (ServerConsumer consumer : session.getServerConsumers()) {
                    if (queueName.equals(consumer.getQueue().getName().toString())) {
                        consumer.close(true);
                        session.getRemotingConnection().close();
                        session.close(true);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static class CountingConnectionFactory implements ConnectionFactory {
        private final ConnectionFactory delegate;
        final AtomicInteger createCount = new AtomicInteger();

        public CountingConnectionFactory(ConnectionFactory delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection createConnection() throws JMSException {
            createCount.incrementAndGet();
            return delegate.createConnection();
        }

        @Override
        public Connection createConnection(String userName, String password) throws JMSException {
            createCount.incrementAndGet();
            return delegate.createConnection(userName, password);
        }

        @Override
        public JMSContext createContext() {
            return delegate.createContext();
        }

        @Override
        public JMSContext createContext(String userName, String password) {
            return delegate.createContext(userName, password);
        }

        @Override
        public JMSContext createContext(String userName, String password, int sessionMode) {
            return delegate.createContext(userName, password, sessionMode);
        }

        @Override
        public JMSContext createContext(int sessionMode) {
            return delegate.createContext(sessionMode);
        }

        public int getCreateCount() {
            return createCount.get();
        }
    }
}
