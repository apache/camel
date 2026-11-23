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
import java.util.concurrent.CountDownLatch;
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
import org.apache.camel.Consumer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.SjmsConsumer;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The connection recovery of the listener container when the connection fails at the edges of its lifecycle: during the
 * start, after the stop, during the shutdown, and when the recovery attempt itself fails with an Error. The
 * connections, sessions and consumers are tracked through a proxying connection factory over the embedded broker.
 */
class SjmsConsumerRecoveryLifecycleTest extends JmsTestSupport {

    private static final String QUEUE = "sjms:queue:SjmsConsumerRecoveryLifecycleTest.";
    private static final int CONSUMERS = 2;

    private final List<TrackedConnection> connections = new CopyOnWriteArrayList<>();
    // the next connection reports a failure to its exception listener as soon as the listener is set
    private volatile boolean nextFailsWhenListenerSet;
    // the first createSession of the next connection throws an Error (such as a NoClassDefFoundError of the client
    // library); the connection itself is created and stays open
    private volatile boolean nextSessionThrowsError;
    // the first close() of the next connection waits for this latch
    private volatile CountDownLatch nextCloseBlocks;
    private final CountDownLatch closeEntered = new CountDownLatch(1);

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

    /**
     * The connection fails after it is created and before the consumers of the start are. The recovery replaces the
     * connection, whose close also closed those consumers, so it has to create new ones on the new connection.
     */
    @Test
    void failureReportedBeforeInitConsumersIsRecoveredWithConsumers() throws Exception {
        nextFailsWhenListenerSet = true;
        context.getRouteController().startRoute("early");

        // the recovery (initial delay 1 second) creates and starts the second connection
        await().atMost(20, TimeUnit.SECONDS).until(() -> connections.size() >= 2 && connections.get(1).started.get());

        MockEndpoint mock = getMockEndpoint("mock:early");
        mock.expectedBodiesReceived("after recovery");
        template.sendBody(QUEUE + "early", "after recovery");
        mock.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(2, connections.size(), "the first connection and the one of the recovery");
        assertTrue(connections.get(0).closed.get(), "the failed connection is closed");
        // the first consumer can receive the message while the recovery still creates the second one
        await().atMost(10, TimeUnit.SECONDS).until(() -> connections.get(1).openConsumers() == CONSUMERS);
    }

    /**
     * A failure of the old connection that is reported after the consumer was stopped, and then the same consumer
     * instance is started again (as JMX does). The late report must not replace the connection of the restart.
     */
    @Test
    void lateFailureThenRestartKeepsConsumers() throws Exception {
        context.getRouteController().startRoute("restart");
        MockEndpoint mock = getMockEndpoint("mock:restart");
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + "restart", "before");
        mock.assertIsSatisfied();
        SimpleMessageListenerContainer container = container("restart");

        Consumer consumer = context.getRoute("restart").getConsumer();
        ServiceHelper.stopService(consumer);
        container.onException(new JMSException("Simulated: late failure report of the old connection"));
        ServiceHelper.startService(consumer);
        assertSame(container, container("restart"), "the same listener container is started again");
        assertEquals(2, connections.size(), "the first connection and the one of the restart");

        // a recovery, if one was scheduled, runs after 1 second: wait until the connections are settled
        await().during(2, TimeUnit.SECONDS).atMost(20, TimeUnit.SECONDS)
                .until(() -> connections.size() == 2 || last().started.get());

        mock.reset();
        mock.expectedBodiesReceived("after");
        template.sendBody(QUEUE + "restart", "after");
        mock.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
        assertEquals(CONSUMERS, last().openConsumers(), "consumers on the connection in use");
    }

    /**
     * A failure of the old connection that is reported after the route was stopped must not open a connection (with
     * consumers) that nothing closes any more.
     */
    @Test
    void lateFailureAfterStopOpensNoConnection() throws Exception {
        context.getRouteController().startRoute("leak");
        MockEndpoint mock = getMockEndpoint("mock:leak");
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + "leak", "before");
        mock.assertIsSatisfied();
        SimpleMessageListenerContainer container = container("leak");

        context.getRouteController().stopRoute("leak");
        assertTrue(connections.get(0).closed.get(), "the connection is closed by the stop");
        container.onException(new JMSException("Simulated: late failure report of the old connection"));

        // a recovery, if one was scheduled, runs after 1 second: wait until it has created its consumers, if any
        await().during(2, TimeUnit.SECONDS).atMost(20, TimeUnit.SECONDS)
                .until(() -> connections.size() == 1 || last().openConsumers() == CONSUMERS);
        assertAllClosed();
    }

    /**
     * A failure reported while the container closes its connection in doShutdown. Nothing may replace the connection
     * between the close and the end of the shutdown, as that connection would stay open with nothing referencing it.
     */
    @Test
    void failureDuringShutdownLeavesNoConnectionOpen() throws Exception {
        nextCloseBlocks = new CountDownLatch(1);
        CountDownLatch release = nextCloseBlocks;
        context.getRouteController().startRoute("orphan");
        MockEndpoint mock = getMockEndpoint("mock:orphan");
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + "orphan", "before");
        mock.assertIsSatisfied();
        SimpleMessageListenerContainer container = container("orphan");

        Thread stopper = new Thread(() -> {
            try {
                context.getRouteController().stopRoute("orphan");
            } catch (Exception e) {
                log.warn("Stopping the route failed", e);
            }
        }, "route-stopper");
        stopper.start();
        // doShutdown is closing the connection
        assertTrue(closeEntered.await(20, TimeUnit.SECONDS), "doShutdown closes the connection");
        // the provider reports the failure on a thread of its own
        Thread notifier = new Thread(
                () -> container.onException(new JMSException("Simulated: failure report during the shutdown")),
                "provider-exception-listener");
        notifier.start();

        // a recovery, if one was scheduled, runs after 1 second: wait until it has created its consumers, if any
        await().during(2, TimeUnit.SECONDS).atMost(20, TimeUnit.SECONDS)
                .until(() -> connections.size() == 1 || last().openConsumers() == CONSUMERS);
        release.countDown();
        stopper.join(30000);
        notifier.join(30000);
        assertAllClosed();
    }

    /**
     * An Error (not an Exception) in a recovery attempt ends that recovery. It comes from the creation of a session on
     * the new connection, which stays open (and started) without consumers: a later failure of that connection must
     * still be recovered.
     */
    @Test
    void errorInRecoveryDoesNotBlockLaterRecovery() throws Exception {
        context.getRouteController().startRoute("error");
        MockEndpoint mock = getMockEndpoint("mock:error");
        mock.expectedBodiesReceived("before");
        template.sendBody(QUEUE + "error", "before");
        mock.assertIsSatisfied();
        SimpleMessageListenerContainer container = container("error");

        nextSessionThrowsError = true;
        container.onException(new JMSException("Simulated failure of the connection"));
        // the recovery attempt created the second connection and took the Error from its first session, and the
        // failed recovery task left the task registry
        await().atMost(20, TimeUnit.SECONDS)
                .until(() -> connections.size() == 2 && !connections.get(1).sessionThrowsError);
        await().atMost(20, TimeUnit.SECONDS).until(() -> PluginHelper
                .getTaskManagerRegistry(context.getCamelContextExtension()).getTasks().stream()
                .noneMatch(t -> "SjmsConnectionRecovery".equals(t.getName())));
        assertFalse(connections.get(1).closed.get(), "the connection of the failed attempt is still open");
        assertEquals(0, connections.get(1).openConsumers(), "the failed attempt created no consumer");

        // the client library works again, and the connection of the failed attempt fails
        container.onException(new JMSException("Simulated failure of the connection of the failed attempt"));
        mock.reset();
        mock.expectedBodiesReceived("after");
        template.sendBody(QUEUE + "error", "after");
        mock.assertIsSatisfied(context, 20, TimeUnit.SECONDS);
        assertEquals(3, connections.size(), "the first connection and the ones of the two recoveries");
        assertTrue(connections.get(1).closed.get(), "the connection of the failed attempt is closed");
        // the first consumer can receive the message while the recovery still creates the second one
        await().atMost(10, TimeUnit.SECONDS).until(() -> last().openConsumers() == CONSUMERS);
    }

    private void assertAllClosed() {
        for (int i = 0; i < connections.size(); i++) {
            assertTrue(connections.get(i).closed.get(), "connection " + (i + 1) + " of " + connections.size()
                                                        + " is closed after the stop");
        }
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
                for (String routeId : List.of("early", "restart", "leak", "orphan", "error")) {
                    from(QUEUE + routeId + "?concurrentConsumers=" + CONSUMERS + "&recoveryInterval=200"
                         + "&connectionFactory=#trackingConnectionFactory")
                            .routeId(routeId).autoStartup(false).to("mock:" + routeId);
                }
            }
        };
    }

    private Connection track(Connection delegate) {
        TrackedConnection tracked = new TrackedConnection();
        tracked.failsWhenListenerSet = nextFailsWhenListenerSet;
        nextFailsWhenListenerSet = false;
        tracked.closeBlocks = nextCloseBlocks;
        nextCloseBlocks = null;
        tracked.sessionThrowsError = nextSessionThrowsError;
        nextSessionThrowsError = false;
        connections.add(tracked);
        return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setExceptionListener".equals(name)) {
                        Object answer = invoke(delegate, method, args);
                        if (tracked.failsWhenListenerSet) {
                            ((ExceptionListener) args[0]).onException(new JMSException("Simulated: the connection failed"));
                        }
                        return answer;
                    }
                    if ("start".equals(name)) {
                        tracked.started.set(true);
                    }
                    if ("createSession".equals(name)) {
                        if (tracked.sessionThrowsError) {
                            tracked.sessionThrowsError = false;
                            throw new NoClassDefFoundError("Simulated: a class of the JMS client cannot be loaded");
                        }
                        return track(tracked, (Session) invoke(delegate, method, args));
                    }
                    if ("close".equals(name)) {
                        CountDownLatch latch = tracked.closeBlocks;
                        if (latch != null) {
                            tracked.closeBlocks = null;
                            closeEntered.countDown();
                            latch.await(30, TimeUnit.SECONDS);
                        }
                        tracked.closed.set(true);
                    }
                    return invoke(delegate, method, args);
                });
    }

    private Session track(TrackedConnection connection, Session delegate) {
        TrackedSession tracked = new TrackedSession();
        connection.sessions.add(tracked);
        return (Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Session.class },
                (proxy, method, args) -> {
                    if ("createConsumer".equals(method.getName())) {
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

    private static final class TrackedConnection {
        final List<TrackedSession> sessions = new CopyOnWriteArrayList<>();
        final AtomicBoolean closed = new AtomicBoolean();
        final AtomicBoolean started = new AtomicBoolean();
        volatile boolean failsWhenListenerSet;
        volatile boolean sessionThrowsError;
        volatile CountDownLatch closeBlocks;

        // a closed connection has closed its sessions, and a closed session its consumer
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
