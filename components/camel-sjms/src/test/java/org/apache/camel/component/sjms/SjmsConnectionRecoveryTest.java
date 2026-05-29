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
package org.apache.camel.component.sjms;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.artemis.services.ArtemisContainer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Reproduces a scenario observed with Oracle AQ where, after a brief connectivity interruption, the SJMS consumer
 * enters an infinite recovery loop: it successfully recreates the JMS connection on each attempt, but immediately
 * re-enters recovery ~recoveryInterval later, blocking normal message consumption indefinitely.
 * <p>
 * Root cause: {@code SimpleMessageListenerContainer.recoverConnection()} returns {@code false} on success, but the
 * {@code BackgroundTask} framework interprets {@code false} as "not done, keep scheduling." This causes the task to
 * keep running — each iteration destroys the working connection via {@code refreshConnection()} while
 * {@code initConsumers()} skips re-creation (because {@code consumers != null}), leaving consumers attached to closed
 * sessions.
 * <p>
 * Uses a real Artemis broker over TCP (via Testcontainers) so that connection close truly kills sessions and consumers,
 * matching the behavior of Oracle AQ and other remote JMS providers.
 */
public class SjmsConnectionRecoveryTest extends CamelTestSupport {

    private static final String SJMS_QUEUE_NAME = "sjms:queue:SjmsConnectionRecoveryTest";
    private static final String MOCK_RESULT = "mock:result";
    private static final int RECOVERY_INTERVAL_MS = 1000;

    private static ArtemisContainer broker;
    private CountingConnectionFactory countingFactory;

    @BeforeAll
    static void startBroker() {
        broker = new ArtemisContainer();
        broker.start();
    }

    @AfterAll
    static void stopBroker() {
        if (broker != null) {
            broker.stop();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(
                "tcp://" + broker.getHost() + ":" + broker.defaultAcceptorPort(),
                broker.username(), broker.password());
        connectionFactory.setReconnectAttempts(0);

        countingFactory = new CountingConnectionFactory(connectionFactory);

        SjmsComponent sjms = new SjmsComponent();
        sjms.setConnectionFactory(countingFactory);
        camelContext.addComponent("sjms", sjms);

        return camelContext;
    }

    /**
     * Verifies that after a transient JMS connection interruption, recovery succeeds once and the recovery loop stops —
     * the consumer does not re-enter recovery again.
     * <p>
     * This directly reproduces the customer scenario where logs show:
     *
     * <pre>
     * Recovering from JMS Connection exception (attempt: 125)
     * Created JMS Connection
     * Successfully recovered JMS Connection (attempt: 125)
     *
     * Recovering from JMS Connection exception (attempt: 126)
     * Created JMS Connection
     * Successfully recovered JMS Connection (attempt: 126)
     * ... repeats indefinitely ...
     * </pre>
     *
     * The test uses a {@link CountingConnectionFactory} to track how many JMS connections are created during recovery.
     * After triggering the connection exception, it waits for multiple recovery intervals and asserts:
     * <ul>
     * <li>Exactly one new connection was created (recovery happened once and stopped)</li>
     * <li>Messages are consumed normally after recovery</li>
     * </ul>
     * <p>
     * With the bug: multiple connections are created (recovery loops indefinitely), and messages sent after the
     * recovery window are never consumed because consumers are attached to closed sessions.
     * <p>
     * With the fix: exactly one connection is created, the recovery task stops, consumers remain alive, and messages
     * are consumed normally.
     */
    @Test
    public void testRecoveryStopsAfterSuccessfulReconnection() throws Exception {
        MockEndpoint mock = getMockEndpoint(MOCK_RESULT);

        // Phase 1: verify normal consumption (also confirms consumer is fully started)
        mock.expectedMessageCount(1);
        template.sendBody(SJMS_QUEUE_NAME, "before-failure");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> mock.assertIsSatisfied());
        mock.reset();

        // Phase 2: simulate a transient JMS connection exception.
        // Record connection count before triggering the exception.
        int connectionsBefore = countingFactory.createCount.get();
        SjmsConsumer sjmsConsumer = (SjmsConsumer) context.getRoute("recovery-route").getConsumer();
        Field containerField = SjmsConsumer.class.getDeclaredField("listenerContainer");
        containerField.setAccessible(true);
        ExceptionListener container = (ExceptionListener) containerField.get(sjmsConsumer);
        container.onException(new JMSException("Simulated transient connection failure"));

        // Phase 3: wait for recovery to complete and for multiple recovery intervals to pass.
        // With recoveryInterval=1000ms and BackgroundTask initial delay=1s:
        //   t=0s: onException fires, consumers/sessions nullified, recovery scheduled
        //   t=1s: recovery iteration 1 — creates new connection + consumers
        //   t=2s: BUG: iteration 2 — creates another connection, skips consumer re-creation
        //   t=3s: BUG: iteration 3 — creates yet another connection
        // By t=4s, if the bug is present, 3+ connections were created.
        await().pollDelay(4 * RECOVERY_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .atMost(5 * RECOVERY_INTERVAL_MS, TimeUnit.MILLISECONDS)
                .until(() -> true);

        // Phase 4: assert recovery happened exactly once and stopped.
        int connectionsAfter = countingFactory.createCount.get();
        int recoveryConnections = connectionsAfter - connectionsBefore;
        assertEquals(1, recoveryConnections,
                "Recovery should create exactly one new connection and stop, "
                                             + "but created " + recoveryConnections
                                             + " — the recovery loop did not stop after successful reconnection");

        // Phase 5: verify messages are consumed after recovery.
        mock.expectedMessageCount(1);
        template.sendBody(SJMS_QUEUE_NAME, "after-failure");
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> mock.assertIsSatisfied());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(SJMS_QUEUE_NAME
                     + "?acknowledgementMode=CLIENT_ACKNOWLEDGE"
                     + "&asyncStartListener=true"
                     + "&concurrentConsumers=5"
                     + "&recoveryInterval=" + RECOVERY_INTERVAL_MS)
                        .routeId("recovery-route")
                        .to(MOCK_RESULT);
            }
        };
    }

    /**
     * A ConnectionFactory wrapper that counts how many JMS connections are created. Used to verify that the recovery
     * mechanism creates exactly one new connection and stops, rather than looping indefinitely.
     */
    static class CountingConnectionFactory implements ConnectionFactory {
        private final ConnectionFactory delegate;
        final AtomicInteger createCount = new AtomicInteger();

        CountingConnectionFactory(ConnectionFactory delegate) {
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
    }
}
