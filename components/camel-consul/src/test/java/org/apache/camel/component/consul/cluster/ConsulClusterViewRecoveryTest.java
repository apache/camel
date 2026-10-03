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
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Request;
import okhttp3.ResponseBody;
import org.apache.camel.CamelContext;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.impl.DefaultCamelContext;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The cluster view must keep taking part in the leader election after Consul could not be reached and after Consul
 * invalidated its session, as it happens when the Consul agent restarts. Consul is simulated by mocked clients.
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
    private volatile boolean keyExists;
    private volatile String lockHolder;

    private CamelContext context;
    private CamelClusterView view;

    @BeforeEach
    void setUp() throws Exception {
        Consul consul = mock(Consul.class);
        when(consul.sessionClient()).thenReturn(sessionClient);
        when(consul.keyValueClient()).thenReturn(keyValueClient);
        simulateConsul();

        ConsulClusterConfiguration configuration = new ConsulClusterConfiguration() {
            @Override
            public Consul createConsulClient(CamelContext camelContext) {
                return consul;
            }
        };
        configuration.setSessionRefreshInterval(1);

        ConsulClusterService service = new ConsulClusterService(configuration);
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
        // the agent cannot be reached: the pending query fails, and releasing the lock fails as well
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
    void releasesTheLockOfItsPathAfterAFailedQuery() {
        String session = lockHolder;

        deliver(() -> lastQuery().onFailure(notReachable()));

        assertFalse(view.getLocalMember().isLeader());
        verify(keyValueClient).releaseLock(PATH, session);
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

    private void simulateConsul() {
        doAnswer(inv -> {
            ensureReachable();
            String id = "session-" + createdSessions.incrementAndGet();
            sessions.add(id);
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
            if (PATH.equals(inv.getArgument(0)) && inv.getArgument(1).equals(lockHolder)) {
                lockHolder = null;
                return true;
            }
            return false;
        }).when(keyValueClient).releaseLock(anyString(), anyString());

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
