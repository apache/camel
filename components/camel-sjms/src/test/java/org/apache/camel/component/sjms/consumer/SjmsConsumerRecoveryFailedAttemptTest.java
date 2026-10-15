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
package org.apache.camel.component.sjms.consumer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.jms.Connection;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import jakarta.jms.JMSContext;
import jakarta.jms.JMSException;
import jakarta.jms.Session;

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection recovery of the consumer creates a new connection and then the sessions and consumers on it. When one
 * of them cannot be created (the connection fails again, the destination is not available yet), the next attempt has to
 * create all of them again. The connections, sessions and consumers of the route are tracked through a connection
 * factory that can make one call fail.
 */
class SjmsConsumerRecoveryFailedAttemptTest extends JmsTestSupport {

    private static final String QUEUE = "sjms:queue:SjmsConsumerRecoveryFailedAttemptTest.";
    private static final int CONSUMERS = 3;

    private final List<TrackedConnection> connections = new CopyOnWriteArrayList<>();
    // what the next connection of the tracking connection factory does wrong (taken by that connection)
    private volatile Fault nextFault;
    // whether the next connection of the tracking connection factory fails when it is started (taken by it)
    private volatile boolean nextFailsOnStart;

    @BindToRegistry("trackingConnectionFactory")
    private final ConnectionFactory trackingConnectionFactory = new ConnectionFactory() {
        @Override
        public Connection createConnection() throws JMSException {
            return track(connectionFactory.createConnection());
        }

        @Override
        public Connection createConnection(String userName, String password) throws JMSException {
            return track(connectionFactory.createConnection(userName, password));
        }

        @Override
        public JMSContext createContext() {
            return connectionFactory.createContext();
        }

        @Override
        public JMSContext createContext(String userName, String password) {
            return connectionFactory.createContext(userName, password);
        }

        @Override
        public JMSContext createContext(String userName, String password, int sessionMode) {
            return connectionFactory.createContext(userName, password, sessionMode);
        }

        @Override
        public JMSContext createContext(int sessionMode) {
            return connectionFactory.createContext(sessionMode);
        }
    };

    @Test
    void testRecoveryCreatesAllConsumersAfterFirstSessionFailed() throws Exception {
        recoverWithFailingSession("first", 1);
    }

    @Test
    void testRecoveryCreatesAllConsumersAfterLastSessionFailed() throws Exception {
        recoverWithFailingSession("last", CONSUMERS);
    }

    private void recoverWithFailingSession(String routeId, int failingSession) throws Exception {
        context.getRouteController().startRoute(routeId);
        MockEndpoint mock = getMockEndpoint("mock:" + routeId);
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + routeId, "before");
        mock.assertIsSatisfied();
        assertEquals(1, connections.size());
        assertEquals(CONSUMERS, connections.get(0).openConsumers());

        // the connection that the recovery creates cannot create one of its sessions
        Fault fault = new Fault(failingSession, 0, 0, null);
        nextFault = fault;
        container(routeId).onException(new JMSException("Simulated failure of the connection"));
        await().atMost(20, TimeUnit.SECONDS).until(fault.happened::get);

        // the attempt after the failed one (it creates the third connection) must bring the consumers back
        await().atMost(20, TimeUnit.SECONDS).until(() -> connections.size() == 3);
        mock.reset();
        mock.expectedBodiesReceived("after");
        template.sendBody(QUEUE + routeId, "after");
        mock.assertIsSatisfied();

        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(CONSUMERS, last().openConsumers(), "consumers on the new connection"));
        assertEquals(3, connections.size(), "connections: the first, the one of the failed attempt, the one in use");
        assertTrue(connections.get(1).closed.get(), "the connection of the failed attempt is closed");
    }

    /**
     * The new connection fails while its last session is created, and the JMS provider tells the container at once.
     * Another recovery is not scheduled then (one is running), so the running one has to try again. This test passes
     * without the fix as well: there the sets that onException drops are the ones the loop adds to, and the
     * NullPointerException fails the attempt. It is here because the loop no longer fails that way.
     */
    @Test
    void testRecoveryTriesAgainWhenConnectionFailsWhileConsumersAreCreated() throws Exception {
        context.getRouteController().startRoute("during");
        MockEndpoint mock = getMockEndpoint("mock:during");
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + "during", "before");
        mock.assertIsSatisfied();

        SimpleMessageListenerContainer container = container("during");
        Fault fault = new Fault(
                0, 0, CONSUMERS, () -> container.onException(new JMSException("Simulated second failure")));
        nextFault = fault;
        container.onException(new JMSException("Simulated failure of the connection"));
        await().atMost(20, TimeUnit.SECONDS).until(fault.happened::get);

        // the first, the one that failed again, the one in use
        await().atMost(20, TimeUnit.SECONDS).until(() -> connections.size() == 3);
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(CONSUMERS, last().openConsumers(), "consumers on the new connection"));
        assertTrue(connections.get(1).closed.get(), "the connection that failed again is closed");

        mock.reset();
        mock.expectedBodiesReceived("after");
        template.sendBody(QUEUE + "during", "after");
        mock.assertIsSatisfied();
        assertEquals(3, connections.size());
    }

    /**
     * The new connection reports its failure to the container (its exception listener) when the recovery starts it,
     * that is before the consumers are created, and it delivers no message; the JMS provider still creates the sessions
     * and consumers on it. The recovery has to notice the failure and try again with another connection, although
     * onException found no consumers to drop and none of the create calls failed.
     */
    @Test
    void testRecoveryTriesAgainWhenConnectionFailsBeforeConsumersAreCreated() throws Exception {
        context.getRouteController().startRoute("beforeconsumers");
        MockEndpoint mock = getMockEndpoint("mock:beforeconsumers");
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + "beforeconsumers", "before");
        mock.assertIsSatisfied();

        nextFailsOnStart = true;
        container("beforeconsumers").onException(new JMSException("Simulated failure of the connection"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> connections.size() >= 2 && connections.get(1).failed.get());

        // the first, the one that failed when it was started, the one in use
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(3, connections.size(),
                "connections: the recovery must try again, the connection failed before the consumers were created"));
        await().atMost(20, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(CONSUMERS, last().openConsumers(), "consumers on the new connection"));
        assertTrue(connections.get(1).closed.get(), "the connection that failed when it was started is closed");

        mock.reset();
        mock.expectedBodiesReceived("after");
        template.sendBody(QUEUE + "beforeconsumers", "after");
        mock.assertIsSatisfied();
        assertEquals(3, connections.size());
    }

    /**
     * A listener container that fails to start (the second consumer cannot be created) closes the session and consumer
     * it did create, and the session whose consumer failed. The connection is kept for the next start.
     */
    @Test
    void testFailedStartClosesItsSessionsAndSecondStartCreatesAllConsumers() throws Exception {
        SjmsConsumer consumer = (SjmsConsumer) context.getRoute("manual").getConsumer();
        assertEquals(0, connections.size());

        Fault fault = new Fault(0, 2, 0, null);
        nextFault = fault;
        assertThrows(Exception.class, consumer::startListenerContainer);
        assertTrue(fault.happened.get());
        assertEquals(1, connections.size());
        TrackedConnection connection = connections.get(0);
        assertEquals(2, connection.sessions.size(), "sessions created by the failed start");
        assertEquals(0, connection.openConsumers(), "open consumers after the failed start");
        assertEquals(0, connection.openSessions(), "open sessions after the failed start");

        consumer.startListenerContainer();
        MockEndpoint mock = getMockEndpoint("mock:manual");
        mock.expectedBodiesReceived("Hello World");
        template.sendBody(QUEUE + "manual", "Hello World");
        mock.assertIsSatisfied();
        assertEquals(1, connections.size());
        assertEquals(2, connection.openConsumers(), "open consumers after the second start");
        assertEquals(2, connection.openSessions(), "open sessions after the second start");
    }

    private SimpleMessageListenerContainer container(String routeId) {
        return (SimpleMessageListenerContainer) ((SjmsConsumer) context.getRoute(routeId).getConsumer())
                .getListenerContainer();
    }

    private TrackedConnection last() {
        return connections.get(connections.size() - 1);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // each test starts its own route, so that the tracked connections are the ones of that route
                for (String routeId : List.of("first", "last", "during", "beforeconsumers")) {
                    from(QUEUE + routeId + "?concurrentConsumers=" + CONSUMERS + "&recoveryInterval=200"
                         + "&connectionFactory=#trackingConnectionFactory")
                            .routeId(routeId).autoStartup(false).to("mock:" + routeId);
                }
                from(QUEUE + "manual?autoStartup=false&concurrentConsumers=2"
                     + "&connectionFactory=#trackingConnectionFactory")
                        .routeId("manual").to("mock:manual");
            }
        };
    }

    private Connection track(Connection delegate) {
        TrackedConnection tracked = new TrackedConnection();
        tracked.fault = nextFault;
        nextFault = null;
        tracked.failsOnStart = nextFailsOnStart;
        nextFailsOnStart = false;
        connections.add(tracked);
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if ("setExceptionListener".equals(method.getName())) {
                        tracked.exceptionListener = (ExceptionListener) args[0];
                    }
                    if ("start".equals(method.getName()) && tracked.failsOnStart) {
                        // the connection is not started, so its consumers get no message, and the failure is reported
                        // on the thread that starts it
                        tracked.failed.set(true);
                        tracked.exceptionListener.onException(new JMSException("Simulated: the connection failed"));
                        return null;
                    }
                    if ("createSession".equals(method.getName())) {
                        Fault fault = tracked.fault;
                        int number = tracked.sessionCalls.incrementAndGet();
                        if (fault != null && fault.failingSession == number) {
                            fault.happened.set(true);
                            throw new JMSException("Simulated: session " + number + " cannot be created");
                        }
                        Session session = track(tracked, (Session) invoke(delegate, method, args));
                        if (fault != null && fault.actionAtSession == number) {
                            fault.action.run();
                            fault.happened.set(true);
                        }
                        return session;
                    }
                    if ("close".equals(method.getName())) {
                        tracked.closed.set(true);
                    }
                    return invoke(delegate, method, args);
                });
    }

    private Session track(TrackedConnection connection, Session delegate) {
        TrackedSession tracked = new TrackedSession();
        connection.sessions.add(tracked);
        int number = connection.sessions.size();
        return (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Session.class },
                (proxy, method, args) -> {
                    if ("createConsumer".equals(method.getName())) {
                        Fault fault = connection.fault;
                        if (fault != null && fault.failingConsumer == number) {
                            fault.happened.set(true);
                            throw new JMSException("Simulated: consumer " + number + " cannot be created");
                        }
                        Object consumer = invoke(delegate, method, args);
                        tracked.consumers.incrementAndGet();
                        return consumer;
                    }
                    if ("close".equals(method.getName())) {
                        tracked.closed.set(true);
                    }
                    return invoke(delegate, method, args);
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /**
     * What one connection does wrong: the number (from 1) of the session that cannot be created, of the session whose
     * consumer cannot be created, and of the session after whose creation the action runs; 0 for none.
     */
    private record Fault(int failingSession, int failingConsumer, int actionAtSession, Runnable action,
            AtomicBoolean happened) {
        Fault(int failingSession, int failingConsumer, int actionAtSession, Runnable action) {
            this(failingSession, failingConsumer, actionAtSession, action, new AtomicBoolean());
        }
    }

    private static final class TrackedConnection {
        final AtomicInteger sessionCalls = new AtomicInteger();
        final List<TrackedSession> sessions = new CopyOnWriteArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean failed = new AtomicBoolean();
        volatile Fault fault;
        volatile boolean failsOnStart;
        volatile ExceptionListener exceptionListener;

        // a closed connection has closed its sessions, and a closed session its consumer
        int openSessions() {
            return closed.get() ? 0 : (int) sessions.stream().filter(s -> !s.closed.get()).count();
        }

        int openConsumers() {
            return closed.get()
                    ? 0 : sessions.stream().filter(s -> !s.closed.get()).mapToInt(s -> s.consumers.get()).sum();
        }
    }

    private static final class TrackedSession {
        final AtomicInteger consumers = new AtomicInteger();
        final AtomicBoolean closed = new AtomicBoolean();
    }
}
