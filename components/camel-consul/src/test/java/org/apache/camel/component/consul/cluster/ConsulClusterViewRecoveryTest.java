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
package org.apache.camel.component.consul.cluster;

import java.math.BigInteger;
import java.net.ConnectException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.Consul;
import org.kiwiproject.consul.ConsulException;
import org.kiwiproject.consul.KeyValueClient;
import org.kiwiproject.consul.SessionClient;
import org.kiwiproject.consul.async.ConsulResponseCallback;
import org.kiwiproject.consul.model.ConsulResponse;
import org.kiwiproject.consul.model.kv.ImmutableValue;
import org.kiwiproject.consul.model.kv.Value;
import org.kiwiproject.consul.model.session.ImmutableSessionCreatedResponse;
import org.kiwiproject.consul.model.session.Session;
import org.kiwiproject.consul.model.session.SessionInfo;
import org.kiwiproject.consul.option.QueryOptions;
import retrofit2.Call;
import retrofit2.Response;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cluster view must keep taking part in the leader election after Consul could not be reached and after Consul
 * invalidated its session, as it happens when the Consul agent restarts, and a stopped view must not be leader. Consul
 * is simulated by mocked clients.
 */
class ConsulClusterViewRecoveryTest {

    private static final String NAMESPACE = "my-ns";
    private static final String PATH = "/camel/" + NAMESPACE;

    private final SessionClient sessionClient = mock(SessionClient.class);
    private final KeyValueClient keyValueClient = mock(KeyValueClient.class);
    private final SessionInfo sessionInfo = mock(SessionInfo.class);
    private final List<ConsulResponseCallback<Optional<Value>>> queries = new CopyOnWriteArrayList<>();

    // the state of the simulated Consul
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();
    private final AtomicInteger createdSessions = new AtomicInteger();
    private volatile boolean reachable = true;
    private volatile boolean unreachableAfterCreatingASession;
    private volatile boolean lockCannotBeReleased;
    private volatile boolean keyExists;
    private volatile String lockHolder;

    private ConsulClusterConfiguration configuration;
    private ConsulClusterService service;
    private CamelContext context;
    private CamelClusterView view;

    @BeforeEach
    void setUp() throws Exception {
        Consul consul = mock(Consul.class);
        when(consul.sessionClient()).thenReturn(sessionClient);
        when(consul.keyValueClient()).thenReturn(keyValueClient);
        simulateConsul();

        configuration = new ConsulClusterConfiguration() {
            @Override
            public Consul createConsulClient(CamelContext camelContext) {
                return consul;
            }
        };
        configuration.setSessionRefreshInterval(1);

        service = new ConsulClusterService(configuration);
        service.setId("node-1");

        context = new DefaultCamelContext();
        context.addService(service);
        context.start();

        view = service.getView(NAMESPACE);
        assertTrue(view.getLocalMember().isLeader());
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    void keepsWatchingAfterConsulCouldNotBeReached() {
        // the agent cannot be reached: the pending query fails
        reachable = false;
        deliver(() -> lastQuery().onFailure(notReachable()));

        // the leadership cannot be confirmed anymore (the session is not renewed): give it up
        assertFalse(view.getLocalMember().isLeader());

        // and query again, the agent is back
        reachable = true;
        verify(keyValueClient, timeout(5000).times(2)).getValue(eq(PATH), any(QueryOptions.class), any());

        // the lock was held by the session all along: the node is leader again
        deliver(() -> lastQuery().onComplete(keyValue()));
        assertTrue(view.getLocalMember().isLeader());
    }

    @Test
    void keepsTheLockAfterAFailedQuery() {
        String session = lockHolder;

        deliver(() -> lastQuery().onFailure(notReachable()));

        // the node steps down locally, but keeps the lock: an explicit release would skip the lock-delay of Consul and
        // let another node take the leadership while this node is still stopping its clustered routes
        assertFalse(view.getLocalMember().isLeader());
        verify(keyValueClient, never()).releaseLock(anyString(), anyString());
        assertEquals(session, lockHolder);
        assertFalse(keyValueClient.acquireLock(PATH, "session-of-another-node"));

        // the next answer confirms that the session still holds the lock: the node is leader again
        verify(keyValueClient, timeout(5000).times(2)).getValue(eq(PATH), any(QueryOptions.class), any());
        deliver(() -> lastQuery().onComplete(keyValue()));
        assertTrue(view.getLocalMember().isLeader());
    }

    @Test
    void doesNotLeaveAnExecutorBehindWhenTheSessionCannotBeCreated() throws Exception {
        CamelContext otherContext = new DefaultCamelContext();
        ExecutorServiceManager executorServiceManager = spy(otherContext.getExecutorServiceManager());
        otherContext.setExecutorServiceManager(executorServiceManager);
        try {
            ConsulClusterService service = new ConsulClusterService(configuration);
            service.setId("node-2");
            otherContext.addService(service);
            otherContext.start();

            // the session cannot be created: the view fails to start
            reachable = false;
            assertThrows(Exception.class, () -> service.getView(NAMESPACE));

            verify(executorServiceManager, never()).newSingleThreadScheduledExecutor(any(), anyString());
        } finally {
            reachable = true;
            otherContext.stop();
        }
    }

    @Test
    void createsANewSessionWhenConsulInvalidatedIt() {
        // the agent restarts: Consul invalidates the session and releases its lock
        sessions.clear();
        lockHolder = null;

        deliver(() -> lastQuery().onComplete(keyValue()));
        assertFalse(view.getLocalMember().isLeader());

        // the next answer: the key is still free and the node takes the leadership with a new session
        deliver(() -> lastQuery().onComplete(keyValue()));
        assertEquals(2, createdSessions.get());
        assertEquals("session-2", lockHolder);
        assertTrue(view.getLocalMember().isLeader());
    }

    @Test
    void acquiresTheLockWhenTheKeyDoesNotExist() {
        // Consul restarts without its data: no session and no key anymore
        sessions.clear();
        lockHolder = null;
        keyExists = false;

        // the node does not hold a lock anymore
        deliver(() -> lastQuery().onComplete(keyValue()));
        assertFalse(view.getLocalMember().isLeader());

        // the next answer: the node creates the key and takes the leadership with a new session
        deliver(() -> lastQuery().onComplete(keyValue()));
        assertEquals("session-2", lockHolder);
        assertTrue(view.getLocalMember().isLeader());
    }

    @Test
    void givesUpTheLeadershipWhenStoppedWhileConsulCannotBeReached() throws Exception {
        // the view is stopped while the agent cannot be reached: the lock cannot be released nor the session destroyed
        reachable = false;
        assertDoesNotThrow(() -> service.stopView(NAMESPACE));

        // the stopped view is not leader anymore
        assertFalse(view.getLocalMember().isLeader());
        assertFalse(service.isLeader(NAMESPACE));

        // Consul invalidates the session once its TTL expired, as it is not renewed anymore, and releases the lock
        sessions.clear();
        lockHolder = null;

        // the agent is back and the view is started again: it takes part in the leader election with a new session
        reachable = true;
        service.startView(NAMESPACE);
        assertEquals("session-2", lockHolder);
        assertTrue(view.getLocalMember().isLeader());
        verify(keyValueClient, times(2)).getValue(eq(PATH), any(QueryOptions.class), any());
    }

    @Test
    void destroysTheSessionWhenTheLockCannotBeReleased() throws Exception {
        lockCannotBeReleased = true;
        assertDoesNotThrow(() -> service.stopView(NAMESPACE));

        // Consul releases the lock of the destroyed session
        verify(sessionClient).destroySession("session-1");
        assertNull(lockHolder);
        assertFalse(view.getLocalMember().isLeader());
    }

    @Test
    void createsANewSessionWhenStartedAgainAfterAFailedStart() throws Exception {
        CamelContext otherContext = new DefaultCamelContext();
        try {
            ConsulClusterService otherService = new ConsulClusterService(configuration);
            otherService.setId("node-2");
            otherContext.addService(otherService);
            otherContext.start();

            // the agent goes away right after the session was created: the view fails to start, and is stopped
            unreachableAfterCreatingASession = true;
            assertThrows(Exception.class, () -> otherService.getView(NAMESPACE));
            assertEquals(2, createdSessions.get());

            // the agent is back: a new start takes part in the leader election with a new session
            unreachableAfterCreatingASession = false;
            reachable = true;
            otherService.startView(NAMESPACE);
            CamelClusterView otherView = otherService.getView(NAMESPACE);
            assertEquals("session-3", otherView.getLocalMember().getId());
            assertFalse(otherView.getLocalMember().isLeader());
            verify(keyValueClient, times(2)).getValue(eq(PATH), any(QueryOptions.class), any());
        } finally {
            unreachableAfterCreatingASession = false;
            reachable = true;
            otherContext.stop();
        }
    }

    @Test
    void ignoresTheFailureOfAQueryFromBeforeARestart() throws Exception {
        ConsulResponseCallback<Optional<Value>> queryOfFirstStart = lastQuery();

        service.stopView(NAMESPACE);
        service.startView(NAMESPACE);
        assertTrue(view.getLocalMember().isLeader());

        // the query of the first start fails only now: the restarted view keeps the leadership of its own session
        deliver(() -> queryOfFirstStart.onFailure(notReachable()));
        assertTrue(view.getLocalMember().isLeader());
    }

    @Test
    void ignoresTheAnswerOfAQueryFromBeforeARestart() throws Exception {
        // Consul answered the query of the first start (the key is held by session-1), but the answer is handled
        // only after the view was restarted
        ConsulResponseCallback<Optional<Value>> queryOfFirstStart = lastQuery();
        ConsulResponse<Optional<Value>> answer = keyValue();

        service.stopView(NAMESPACE);
        service.startView(NAMESPACE);
        assertEquals("session-2", lockHolder);
        assertTrue(view.getLocalMember().isLeader());
        int queriesAfterRestart = queries.size();

        // the answer is about a session of the first start: it must neither change the leadership of the restarted
        // view nor keep the watch of the first start going next to the one of the restart
        deliver(() -> queryOfFirstStart.onComplete(answer));
        assertTrue(view.getLocalMember().isLeader());
        assertEquals(queriesAfterRestart, queries.size());
    }

    @Test
    void doesNotTakeTheLeadershipAfterTheViewWasStopped() throws Exception {
        // another node holds the lock
        lockHolder = "session-of-another-node";
        deliver(() -> lastQuery().onComplete(keyValue()));
        assertFalse(view.getLocalMember().isLeader());

        // the other node releases the lock, and the view is stopped while it handles that answer: right after it
        // acquired the lock (it logs the result), and before it took the leadership
        lockHolder = null;
        ConsulResponseCallback<Optional<Value>> query = lastQuery();
        ConsulResponse<Optional<Value>> answer = keyValue();
        Thread stop = new Thread(() -> {
            try {
                service.stopView(NAMESPACE);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, "stop-view");
        Thread callback = new Thread(() -> deliver(() -> query.onComplete(answer)), "consul-callback");

        onDebugMessage("Try to acquire lock", () -> {
            stop.start();
            // until the view is stopped, or the stop waits for this callback
            await().atMost(10, TimeUnit.SECONDS).until(
                    () -> stop.getState() == Thread.State.TERMINATED
                            || stop.getState() == Thread.State.WAITING && ((ServiceSupport) view).isStopping());
        }, () -> {
            callback.start();
            callback.join(TimeUnit.SECONDS.toMillis(10));
            stop.join(TimeUnit.SECONDS.toMillis(10));
        });
        assertFalse(callback.isAlive());
        assertFalse(stop.isAlive());

        // the view is stopped and gave up its session: it is not leader, and the lock it acquired is free again
        assertFalse(view.getLocalMember().isLeader());
        assertFalse(service.isLeader(NAMESPACE));
        assertNull(lockHolder);
    }

    /**
     * Runs the action with a hook that runs on the thread that logs a debug message of the view starting with the given
     * text, the first time.
     */
    private static void onDebugMessage(String prefix, Runnable hook, ThrowingRunnable action) throws Exception {
        String name = ConsulClusterView.class.getName();
        AtomicBoolean done = new AtomicBoolean();
        AbstractAppender appender = new AbstractAppender("hook", null, null, false, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getMessage().getFormattedMessage().startsWith(prefix) && done.compareAndSet(false, true)) {
                    hook.run();
                }
            }
        };
        appender.start();
        LoggerContext loggerContext = LoggerContext.getContext(false);
        Configuration config = loggerContext.getConfiguration();
        LoggerConfig loggerConfig = new LoggerConfig(name, Level.DEBUG, false);
        loggerConfig.addAppender(appender, Level.DEBUG, null);
        config.addLogger(name, loggerConfig);
        loggerContext.updateLoggers();
        try {
            action.run();
        } finally {
            config.removeLogger(name);
            loggerContext.updateLoggers();
            appender.stop();
        }
        assertTrue(done.get(), "the hook did not run");
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void simulateConsul() {
        doAnswer(inv -> {
            ensureReachable();
            String id = "session-" + createdSessions.incrementAndGet();
            sessions.add(id);
            if (unreachableAfterCreatingASession) {
                reachable = false;
            }
            return ImmutableSessionCreatedResponse.builder().id(id).build();
        }).when(sessionClient).createSession(any(Session.class));

        doAnswer(inv -> {
            ensureReachable();
            return sessions.contains(inv.<String> getArgument(0)) ? Optional.of(sessionInfo) : Optional.empty();
        }).when(sessionClient).getSessionInfo(anyString());

        doAnswer(inv -> {
            ensureReachable();
            String id = inv.getArgument(0);
            if (!sessions.contains(id)) {
                // what Consul answers for a session that does not exist
                throw new ConsulException(
                        renewCall(id), Response.error(404, ResponseBody.create("Session id '" + id + "' not found", null)));
            }
            return Optional.of(sessionInfo);
        }).when(sessionClient).renewSession(anyString());

        doAnswer(inv -> {
            ensureReachable();
            String id = inv.getArgument(1);
            if (sessions.contains(id) && (lockHolder == null || lockHolder.equals(id))) {
                lockHolder = id;
                keyExists = true;
                return true;
            }
            return false;
        }).when(keyValueClient).acquireLock(eq(PATH), anyString());

        doAnswer(inv -> {
            ensureReachable();
            if (lockCannotBeReleased) {
                throw new ConsulException("Permission denied", new IllegalStateException("403"));
            }
            if (PATH.equals(inv.getArgument(0)) && inv.getArgument(1).equals(lockHolder)) {
                lockHolder = null;
                return true;
            }
            return false;
        }).when(keyValueClient).releaseLock(anyString(), anyString());

        doAnswer(inv -> {
            ensureReachable();
            // Consul releases the locks of a destroyed session
            String id = inv.getArgument(0);
            sessions.remove(id);
            if (id.equals(lockHolder)) {
                lockHolder = null;
            }
            return null;
        }).when(sessionClient).destroySession(anyString());

        doAnswer(inv -> {
            ensureReachable();
            return Optional.ofNullable(lockHolder);
        }).when(keyValueClient).getSession(PATH);

        // blocking queries: answered by the test
        doAnswer(inv -> {
            queries.add(inv.getArgument(2));
            return null;
        }).when(keyValueClient).getValue(eq(PATH), any(QueryOptions.class), any());
    }

    private void ensureReachable() {
        if (!reachable) {
            throw notReachable();
        }
    }

    private static Call<?> renewCall(String id) {
        Call<?> call = mock(Call.class);
        when(call.request()).thenReturn(new Request.Builder().url("http://localhost:8500/v1/session/renew/" + id).build());
        return call;
    }

    private static ConsulException notReachable() {
        return new ConsulException("Error connecting to Consul", new ConnectException("Connection refused"));
    }

    private ConsulResponseCallback<Optional<Value>> lastQuery() {
        return queries.get(queries.size() - 1);
    }

    private ConsulResponse<Optional<Value>> keyValue() {
        Optional<Value> value = Optional.empty();
        if (keyExists) {
            value = Optional.of(ImmutableValue.builder()
                    .key(PATH)
                    .session(Optional.ofNullable(lockHolder))
                    .createIndex(1)
                    .modifyIndex(queries.size())
                    .lockIndex(1)
                    .flags(0)
                    .build());
        }
        return new ConsulResponse<>(value, 0, true, BigInteger.valueOf(queries.size()), (String) null, (String) null);
    }

    /**
     * Calls the callback of a query like the HTTP client does: an exception thrown by the callback is only logged.
     */
    private static void deliver(Runnable callback) {
        try {
            callback.run();
        } catch (RuntimeException e) {
            // ignored, as by the HTTP client
        }
    }
}
