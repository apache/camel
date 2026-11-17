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
package org.apache.camel.component.zookeepermaster.group.internal;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.listen.ListenerManager;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryOneTime;
import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ZooKeeperServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@link ZooKeeperGroup} that is closed must not leave its ephemeral znode in the group while the session of the
 * client lives on (the client is shared, such as the CuratorFramework of the component or the curator bean), as the
 * znode would keep the leadership of the group or a place ahead of this node's next znode.
 */
class ZooKeeperGroupCloseTest {

    private static final String PATH = "/camel/zookeepermaster/clusters/master/close";

    @TempDir
    Path dataDir;

    private final int port = AvailablePortFinder.getNextAvailable();

    private ZooKeeperServer server;
    private NIOServerCnxnFactory factory;
    private CuratorFramework curator;

    @BeforeEach
    void start() throws Exception {
        startServer();
        curator = CuratorFrameworkFactory.builder()
                .connectString("127.0.0.1:" + port)
                .sessionTimeoutMs(30000)
                .connectionTimeoutMs(5000)
                .retryPolicy(new RetryOneTime(100))
                .build();
        curator.start();
        assertTrue(curator.blockUntilConnected(30, TimeUnit.SECONDS));
    }

    @AfterEach
    void stop() {
        if (curator != null) {
            curator.close();
        }
        stopServer();
    }

    private void startServer() throws Exception {
        // the same data directory: a restarted server keeps the sessions and their ephemeral znodes
        File dir = dataDir.toFile();
        server = new ZooKeeperServer(dir, dir, 2000);
        factory = new NIOServerCnxnFactory();
        factory.configure(new InetSocketAddress("127.0.0.1", port), 60);
        factory.startup(server);
    }

    private void stopServer() {
        if (factory != null) {
            factory.shutdown();
            factory = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }
    }

    private List<String> members() throws Exception {
        return curator.getChildren().forPath(PATH);
    }

    @Test
    void groupClosedWhileDisconnectedDeletesItsZnodeOnceReconnected() throws Exception {
        ZooKeeperGroup<NodeState> group = new ZooKeeperGroup<>(
                curator, PATH, NodeState.class,
                Executors.newSingleThreadExecutor());
        group.start();
        group.update(new NodeState("test", "container1"));
        await().atMost(20, TimeUnit.SECONDS).until(() -> group.getId() != null);
        assertEquals(1, members().size());
        long session = curator.getZookeeperClient().getZooKeeper().getSessionId();

        // the connection is lost (shorter than the session timeout), and the group is closed meanwhile
        stopServer();
        await().atMost(20, TimeUnit.SECONDS).until(() -> !group.isConnected());
        group.close();

        // the client reconnects to the same session: its ephemeral znodes are still there
        startServer();
        assertTrue(curator.blockUntilConnected(30, TimeUnit.SECONDS));
        assertEquals(session, curator.getZookeeperClient().getZooKeeper().getSessionId());

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(
                () -> assertEquals(List.of(), members(), "The closed group must not leave its znode in the group"));
    }

    @Test
    void groupClosedDuringACreateDeletesTheZnode() throws Exception {
        CountDownLatch inCreate = new CountDownLatch(1);
        ZooKeeperGroup<NodeState> group = new ZooKeeperGroup<>(
                interruptedCreates(curator, inCreate), PATH,
                NodeState.class, Executors.newSingleThreadExecutor());
        group.start();
        group.update(new NodeState("test", "container1"));

        // the create is applied, and the thread of the group waits for its answer when the group is closed
        assertTrue(inCreate.await(20, TimeUnit.SECONDS));
        assertEquals(1, members().size());
        group.close();

        assertEquals(List.of(), members(), "The closed group must not leave its znode in the group");
    }

    @Test
    void groupClosedDuringACreateBeforeItKnowsItIsConnectedAgainDeletesTheZnode() throws Exception {
        CountDownLatch inCreate = new CountDownLatch(1);
        ZooKeeperGroup<NodeState> group = new ZooKeeperGroup<>(
                interruptedCreates(curator, inCreate), PATH,
                NodeState.class, Executors.newSingleThreadExecutor());
        group.start();
        group.update(new NodeState("test", "container1"));
        assertTrue(inCreate.await(20, TimeUnit.SECONDS));

        // the group was told the connection was lost, and not yet that it is back (the client is connected): the close
        // removes the listener of the group, so it is not told any more
        ((ListenerManager<?, ?>) curator.getConnectionStateListenable())
                .forEach(listener -> ((ConnectionStateListener) listener).stateChanged(curator, ConnectionState.SUSPENDED));
        assertFalse(group.isConnected());
        group.close();

        assertEquals(List.of(), members(), "The closed group must not leave its znode in the group");
    }

    /**
     * The client, whose creates are applied and then wait for their answer until the thread is interrupted, as when a
     * close interrupts the thread that waits for the answer of a create the server applied: the ZooKeeper client throws
     * an InterruptedException, and the caller does not know the path of the znode.
     */
    private static CuratorFramework interruptedCreates(CuratorFramework client, CountDownLatch inCreate) {
        return (CuratorFramework) Proxy.newProxyInstance(ZooKeeperGroupCloseTest.class.getClassLoader(),
                new Class<?>[] { CuratorFramework.class }, (proxy, method, args) -> {
                    Object result = invoke(client, method, args);
                    return "create".equals(method.getName())
                            ? createBuilder(result, method.getReturnType(), inCreate) : result;
                });
    }

    // a step of the fluent create builder (create().creatingParentsIfNeeded().withMode(mode).forPath(path, data))
    private static Object createBuilder(Object target, Class<?> type, CountDownLatch inCreate) {
        return Proxy.newProxyInstance(ZooKeeperGroupCloseTest.class.getClassLoader(), new Class<?>[] { type },
                (proxy, method, args) -> {
                    Object result = invoke(target, method, args);
                    if ("forPath".equals(method.getName())) {
                        inCreate.countDown();
                        // waits for the answer until the close of the group interrupts this thread
                        new CountDownLatch(1).await();
                        return result;
                    }
                    Class<?> next = returnType(method, type);
                    return next != null && result != null ? createBuilder(result, next, inCreate) : result;
                });
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    // the interface a builder method returns: Curator's builders are generic (CreateModable<T>.withMode returns T), so a
    // type variable is bound through the generic interfaces of the builder it is called on
    private static Class<?> returnType(Method method, Class<?> on) {
        if (method.getReturnType().isInterface()) {
            return method.getReturnType();
        }
        if (method.getGenericReturnType() instanceof TypeVariable<?> variable) {
            Type bound = bind(on, variable);
            if (bound instanceof ParameterizedType parameterized) {
                bound = parameterized.getRawType();
            }
            if (bound instanceof Class<?> c && c.isInterface()) {
                return c;
            }
        }
        return null;
    }

    private static Type bind(Class<?> type, TypeVariable<?> variable) {
        for (Type generic : type.getGenericInterfaces()) {
            if (generic instanceof ParameterizedType parameterized
                    && parameterized.getRawType() == variable.getGenericDeclaration()) {
                TypeVariable<?>[] parameters = ((Class<?>) parameterized.getRawType()).getTypeParameters();
                for (int i = 0; i < parameters.length; i++) {
                    if (parameters[i].equals(variable)) {
                        return parameterized.getActualTypeArguments()[i];
                    }
                }
            }
            Class<?> raw = generic instanceof ParameterizedType parameterized
                    ? (Class<?>) parameterized.getRawType() : (Class<?>) generic;
            Type found = bind(raw, variable);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
