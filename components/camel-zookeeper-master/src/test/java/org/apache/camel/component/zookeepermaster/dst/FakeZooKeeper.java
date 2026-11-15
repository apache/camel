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
package org.apache.camel.component.zookeepermaster.dst;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;

import org.apache.camel.component.zookeepermaster.CamelNodeState;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.curator.CuratorZookeeperClient;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.listen.Listenable;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.curator.retry.RetryOneTime;
import org.apache.curator.utils.EnsurePath;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * A fake ZooKeeper ensemble, the {@link CuratorFramework} of this node on it, and the other nodes of the group (peers)
 * as sessions of their own. Written from the ZooKeeper Programmer's Guide (3.9) and the Curator documentation (5.x);
 * the rules it follows are given (in the words of the documents where possible, not verbatim) where they are
 * implemented and marked:
 * <ul>
 * <li>[Z] ZooKeeper Programmer's Guide, "ZooKeeper Sessions", "ZooKeeper Watches", "Ephemeral Nodes", "Sequence
 * Nodes";</li>
 * <li>[R] ZooKeeper Recipes, "Leader Election";</li>
 * <li>[C] Curator, "Error Handling" (connection states), the Javadoc of {@code ConnectionState}, {@code RetryLoop} and
 * {@code DeleteBuilder.guaranteed()};</li>
 * <li>[A] an assumption, where the documents leave the behaviour open; the seed picks between the allowed choices.</li>
 * </ul>
 * <p>
 * Modelled: persistent, ephemeral and ephemeral sequential znodes; one-time child and data watches, held while the
 * client is disconnected and delivered after it reconnected to the same session; a session that expires when the client
 * cannot reach the ensemble for longer than the session timeout, with its ephemeral znodes; the connection states
 * SUSPENDED, LOST and RECONNECTED, delivered on a connection state thread; watch events on an event thread; the wait of
 * an operation for the connection (up to the connection timeout) and its foreground retry after a connection loss (the
 * retry policy of the client); a create that the ensemble applied but whose answer was lost (a retry then makes a
 * second znode); guaranteed deletes, done again in the background after a reconnect.
 * <p>
 * Not modelled: ACLs, containers and TTL znodes, background (asynchronous) operations, transactions, the state events
 * of type None that ZooKeeper gives every watcher (the watchers of the group ignore them), read-only mode, the order of
 * the children in a getChildren answer (sorted here).
 * <p>
 * The client notices a connection loss after a seeded delay below two thirds of the session timeout (as the ZooKeeper
 * client does with its read timeout), or at once when one of its calls fails on it (a reset connection).
 * <p>
 * Timing (the one-stack rule of the harness): writes take effect at the start of their latency and reads at the end;
 * watch and connection state events are tasks of their own threads and never block.
 */
final class FakeZooKeeper {

    private static final ObjectMapper MAPPER = JsonMapper.builderWithJackson2Defaults()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private final Dst dst;
    final List<String> violations = new ArrayList<>();
    boolean faults = true;
    /** The negotiated session timeout. */
    long sessionTimeout = 6000;
    /** Foreground retries of an operation that failed with a connection loss, and the sleep between two. */
    int retries = 1;
    long retrySleep = 1000;
    /** [C] CuratorFrameworkFactory: the connection timeout defaults to 15 seconds. */
    long connectionTimeout = 15_000;
    double slowRate;
    double responseLostRate;

    private final DstExecutor eventThread;
    private final DstExecutor stateThread;
    private final Map<String, Znode> tree = new TreeMap<>();
    private long zxid;
    private long nextSession = 1;

    // the session of this node's client, and what the client believes about its connection
    private long session;
    private boolean network = true;
    private boolean clientConnected = true;
    private boolean sessionExpired;
    private boolean lostPosted;
    private int outage;
    private final List<ConnectionStateListener> listeners = new ArrayList<>();
    private final Map<String, Set<Watcher>> childWatches = new HashMap<>();
    private final Map<String, Set<Watcher>> dataWatches = new HashMap<>();
    private final List<Runnable> held = new ArrayList<>();
    private final Set<String> failedDeletes = new LinkedHashSet<>();
    private boolean closed;
    private final CuratorFramework client;
    private final FakeZookeeperClient zookeeperClient;

    // the other nodes of the group: container -> session
    private final Map<String, Long> peers = new TreeMap<>();

    private static final class Znode {
        byte[] data;
        int version;
        int cversion;
        long owner;
        long czxid;
        long mzxid;
        long ctime;
        long mtime;
    }

    FakeZooKeeper(Dst dst) {
        this.dst = dst;
        this.eventThread = dst.executor("zookeeper-event-thread", 1);
        this.stateThread = dst.executor("curator-connection-state", 1);
        this.session = nextSession++;
        tree.put("/", new Znode());
        this.client = (CuratorFramework) Proxy.newProxyInstance(CuratorFramework.class.getClassLoader(),
                new Class<?>[] { CuratorFramework.class }, this::invokeClient);
        this.zookeeperClient = new FakeZookeeperClient();
    }

    /** The CuratorFramework of this node, as an application shares it (bound as the curator bean). */
    CuratorFramework client() {
        return client;
    }

    boolean isClosed() {
        return closed;
    }

    int listenerCount() {
        return listeners.size();
    }

    boolean isNetworkUp() {
        return network;
    }

    boolean isClientConnected() {
        return clientConnected;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the operator: connection losses and peers

    /**
     * The client loses its connection to the ensemble for {@code millis}. [Z] The client will see a disconnected event
     * when it is disconnected from the ensemble; [Z] Expirations happens when the cluster does not hear from the client
     * within the specified session timeout period; [C] SUSPENDED: There has been a loss of connection; LOST: The
     * connection is confirmed to be lost ... Curator will set the LOST state when it believes that the ZooKeeper
     * session has expired (after the negotiated session timeout without a reconnect, Curator injects the expiry itself,
     * so the old session is not resumed after that).
     */
    void disconnect(long millis) {
        if (!network) {
            return;
        }
        network = false;
        int k = ++outage;
        dst.trace("zk: connection lost for " + millis + " ms (session " + session + ")");
        // [A] the client notices the loss within two thirds of the session timeout (the read timeout of the client), or
        // at once when one of its calls fails on it
        long detect = dst.between(10, Math.max(10, sessionTimeout * 2 / 3));
        stateThread.schedule("detect connection loss", detect, () -> notice(k));
        // the ensemble expires the session when it does not hear from the client within the session timeout
        eventThread.schedule("session expiry on the ensemble", sessionTimeout, () -> {
            if (outage == k && !network) {
                expireLocalSession();
            }
        });
        dst.executor("network", 1).schedule("connection back", millis, () -> reconnect(k));
    }

    /**
     * The client notices the connection loss: [Z] a ConnectionLoss comes with the Disconnected event of the client ([C]
     * SUSPENDED).
     */
    private void notice(int k) {
        if (outage == k && !network && clientConnected) {
            clientConnected = false;
            post(ConnectionState.SUSPENDED);
            stateThread.schedule("session timeout of the client", sessionTimeout, () -> {
                if (outage == k && !network && !lostPosted) {
                    lostPosted = true;
                    post(ConnectionState.LOST);
                }
            });
        }
    }

    private void reconnect(int k) {
        if (outage != k || network) {
            return;
        }
        network = true;
        if (sessionExpired || lostPosted) {
            // [Z] the client will be told that the session has expired; [C] a new session is made, RECONNECTED
            if (!sessionExpired) {
                expireLocalSession();
            }
            if (!lostPosted) {
                clientConnected = false;
                post(ConnectionState.LOST);
            }
            session = nextSession++;
            sessionExpired = false;
            lostPosted = false;
            held.clear();
            failedDeletes.clear();
            clientConnected = true;
            dst.trace("zk: reconnected with a new session " + session);
            post(ConnectionState.RECONNECTED);
            return;
        }
        dst.trace("zk: reconnected to session " + session);
        if (!clientConnected) {
            clientConnected = true;
            post(ConnectionState.RECONNECTED);
        }
        // [Z] When a client reconnects, any previously registered watches will be reregistered and triggered if
        // needed"
        List<Runnable> events = new ArrayList<>(held);
        held.clear();
        events.forEach(Runnable::run);
        // [C] guaranteed(): Curator will record failed node deletions and attempt to delete them in the background
        // until successful"
        for (String path : new ArrayList<>(failedDeletes)) {
            retryDelete(path);
        }
    }

    private void retryDelete(String path) {
        long s = session;
        eventThread.schedule("guaranteed delete of " + path, latency(), () -> {
            if (network && session == s && failedDeletes.remove(path) && tree.containsKey(path)) {
                dst.trace("zk: guaranteed delete of " + path + " done in the background");
                remove(path);
            }
        });
    }

    private void expireLocalSession() {
        long expired = session;
        sessionExpired = true;
        dst.trace("zk: session " + expired + " expired");
        // [Z] the watches of the session are lost; ephemeral nodes ... are removed when the session ends
        childWatches.clear();
        dataWatches.clear();
        held.clear();
        removeEphemeralsOf(expired);
    }

    private void post(ConnectionState state) {
        dst.trace("zk: connection state " + state);
        stateThread.execute(() -> {
            // [C] ConnectionStateManager calls the listeners registered when it dispatches the state
            List<ConnectionStateListener> snapshot = new ArrayList<>(listeners);
            dst.trace("zk: connection state " + state + " to " + snapshot.size() + " listeners");
            for (ConnectionStateListener l : snapshot) {
                try {
                    l.stateChanged(client, state);
                } catch (RuntimeException e) {
                    // Curator logs it
                    dst.trace("zk: listener failed: " + e);
                }
            }
        });
    }

    /** A peer joins the group: its session makes an ephemeral sequential znode, as ZooKeeperGroup does. */
    void peerJoins(String groupPath, String container) {
        if (peers.containsKey(container)) {
            return;
        }
        long s = nextSession++;
        peers.put(container, s);
        ensureParents(groupPath + "/0");
        CamelNodeState state = new CamelNodeState("dst", container);
        String path = createNode(groupPath + "/0", encode(state), CreateMode.EPHEMERAL_SEQUENTIAL, s);
        dst.trace("zk: peer " + container + " joins as " + path);
    }

    /** A peer leaves the group: it closes its session, so its znode is removed at once. */
    void peerLeaves(String container) {
        Long s = peers.remove(container);
        if (s != null) {
            dst.trace("zk: peer " + container + " leaves");
            removeEphemeralsOf(s);
        }
    }

    /** A peer dies: the ensemble removes its znode once its session timed out. */
    void peerDies(String container) {
        Long s = peers.remove(container);
        if (s != null) {
            dst.trace("zk: peer " + container + " dies");
            deadPeerSessions.add(s);
            eventThread.schedule("session expiry of " + container, sessionTimeout, () -> removeEphemeralsOf(s));
        }
    }

    /** The guaranteed deletes that the client still has to do again in the background. */
    Set<String> pendingDeletes() {
        return failedDeletes;
    }

    Set<String> peers() {
        return peers.keySet();
    }

    // ------------------------------------------------------------------------------------------------------------
    // what the members of the group see

    /** The znodes under the group path that the sessions of this node own (the current or an expired one). */
    List<String> localZnodes(String groupPath) {
        List<String> answer = new ArrayList<>();
        for (Map.Entry<String, Znode> e : tree.entrySet()) {
            if (isChild(groupPath, e.getKey()) && e.getValue().owner != 0 && !peers.containsValue(e.getValue().owner)
                    && !isDeadPeer(e.getValue().owner)) {
                answer.add(e.getKey());
            }
        }
        return answer;
    }

    private final Set<Long> deadPeerSessions = new HashSet<>();

    private boolean isDeadPeer(long owner) {
        return deadPeerSessions.contains(owner);
    }

    /**
     * The znode of the master, as every member of the group computes it from the znodes: one znode per container (the
     * one with the greatest path), and of those the one with the lowest sequence number ([R] the client with the
     * smallest sequence number is the leader). Null when the group has no members.
     */
    String master(String groupPath) {
        Map<String, String> latest = new HashMap<>();
        for (Map.Entry<String, Znode> e : tree.entrySet()) {
            if (isChild(groupPath, e.getKey())) {
                String container = container(e.getValue().data);
                String cur = latest.get(container);
                if (cur == null || cur.compareTo(e.getKey()) < 0) {
                    latest.put(container, e.getKey());
                }
            }
        }
        return latest.values().stream().sorted().findFirst().orElse(null);
    }

    /** The container of the peer that owns the znode, or null when it is not a peer's. */
    String peerOf(String path) {
        Znode z = path != null ? tree.get(path) : null;
        if (z == null) {
            return null;
        }
        for (Map.Entry<String, Long> e : peers.entrySet()) {
            if (e.getValue() == z.owner) {
                return e.getKey();
            }
        }
        return isDeadPeer(z.owner) ? "dead peer" : null;
    }

    private static boolean isChild(String parent, String path) {
        return path.startsWith(parent + "/") && path.indexOf('/', parent.length() + 1) < 0;
    }

    private static String container(byte[] data) {
        try {
            return MAPPER.readValue(data, CamelNodeState.class).getContainer();
        } catch (RuntimeException e) {
            return "undecodable";
        }
    }

    private static byte[] encode(CamelNodeState state) {
        return MAPPER.writeValueAsString(state).getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------------------------------------------------
    // the ensemble

    private long latency() {
        if (faults && dst.chance(slowRate)) {
            return dst.between(200, 2000);
        }
        return dst.between(1, 20);
    }

    private void ensureParents(String path) {
        int i = path.indexOf('/', 1);
        while (i > 0) {
            String p = path.substring(0, i);
            if (!tree.containsKey(p)) {
                createNode(p, new byte[0], CreateMode.PERSISTENT, 0);
            }
            i = path.indexOf('/', i + 1);
        }
    }

    private static String parentOf(String path) {
        int i = path.lastIndexOf('/');
        return i <= 0 ? "/" : path.substring(0, i);
    }

    private String createNode(String path, byte[] data, CreateMode mode, long owner) {
        Znode parent = tree.get(parentOf(path));
        String name = path;
        if (mode.isSequential()) {
            // [Z] a monotonically increasing counter will be appended to the name ... formatted as %010d, the
            // counter of the parent
            name = path + String.format("%010d", parent.cversion);
        }
        Znode z = new Znode();
        z.data = data;
        z.owner = mode.isEphemeral() ? owner : 0;
        z.czxid = ++zxid;
        z.mzxid = z.czxid;
        z.ctime = dst.currentTimeMillis();
        z.mtime = z.ctime;
        tree.put(name, z);
        parent.cversion++;
        fireChildWatches(parentOf(name));
        return name;
    }

    private void remove(String path) {
        if (tree.remove(path) != null) {
            Znode parent = tree.get(parentOf(path));
            if (parent != null) {
                parent.cversion++;
            }
            fireDataWatches(path, Watcher.Event.EventType.NodeDeleted);
            fireChildWatches(parentOf(path));
        }
    }

    private void removeEphemeralsOf(long owner) {
        for (String path : new ArrayList<>(tree.keySet())) {
            Znode z = tree.get(path);
            if (z != null && z.owner == owner) {
                remove(path);
            }
        }
    }

    // [Z] Watches are one time triggers; a watch object ... will only be triggered once for a given notification
    private void fireChildWatches(String path) {
        Set<Watcher> ws = childWatches.remove(path);
        if (ws != null) {
            deliver(ws, Watcher.Event.EventType.NodeChildrenChanged, path);
        }
    }

    private void fireDataWatches(String path, Watcher.Event.EventType type) {
        Set<Watcher> ws = dataWatches.remove(path);
        if (ws != null) {
            deliver(ws, type, path);
        }
        if (type == Watcher.Event.EventType.NodeDeleted) {
            // [Z] a delete triggers the child watches of the deleted znode too
            Set<Watcher> cs = childWatches.remove(path);
            if (cs != null) {
                deliver(cs, type, path);
            }
        }
    }

    private void deliver(Set<Watcher> watchers, Watcher.Event.EventType type, String path) {
        long s = session;
        Runnable r = () -> {
            for (Watcher w : watchers) {
                eventThread.execute(() -> {
                    if (session == s && !sessionExpired) {
                        w.process(new WatchedEvent(type, Watcher.Event.KeeperState.SyncConnected, path));
                    }
                });
            }
        };
        if (network && clientConnected) {
            r.run();
        } else {
            // [Z] Watches will not be received while disconnected from a server
            held.add(r);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the operations of the client

    // the threads of the closed groups (executor labels), which a real close interrupts
    private final Set<String> interrupted = new HashSet<>();

    /**
     * The thread with this executor label is interrupted, as ExecutorService.shutdownNow does when a group closes. A
     * ZooKeeper call that it waits in, or makes from now on, throws: the real one throws InterruptedException (Curator
     * sets the interrupt flag again, so the next call throws too). The fake throws an IllegalStateException instead, as
     * an InterruptedException would make the code under test set the interrupt flag of the one thread of the harness. A
     * write that was sent before the interrupt is applied by the ensemble (ClientCnxn queues the packet before it
     * waits); only its answer is lost.
     */
    void interrupt(String executorLabel) {
        interrupted.add(executorLabel);
    }

    private void checkInterrupted(String what, boolean applied) {
        if (interrupted.contains(dst.currentExecutor())) {
            dst.trace("zk: " + what + " is interrupted" + (applied ? " (applied, its answer is lost)" : ""));
            throw new IllegalStateException("dst: " + what + " of an interrupted thread (a closed group)");
        }
    }

    // the calls in flight per thread (executor label), and the creates among them
    private final Map<String, Integer> inFlight = new HashMap<>();
    private final Map<String, Integer> createsInFlight = new HashMap<>();

    /**
     * Whether a thread whose executor label starts with the prefix waits in a call of the client other than a create.
     */
    boolean inCallOtherThanCreate(String threadPrefix) {
        return inFlight.entrySet().stream().anyMatch(
                e -> e.getKey().startsWith(threadPrefix) && e.getValue() > createsInFlight.getOrDefault(e.getKey(), 0));
    }

    /** Whether a thread whose executor label starts with the prefix waits in a create. */
    boolean inCreate(String threadPrefix) {
        return createsInFlight.entrySet().stream().anyMatch(e -> e.getKey().startsWith(threadPrefix) && e.getValue() > 0);
    }

    private <T> T op(String what, boolean write, Callable<T> apply) throws Exception {
        String thread = dst.currentExecutor();
        boolean create = what.startsWith("create ");
        inFlight.merge(thread, 1, Integer::sum);
        if (create) {
            createsInFlight.merge(thread, 1, Integer::sum);
        }
        try {
            return call(what, write, apply);
        } finally {
            inFlight.merge(thread, -1, Integer::sum);
            if (create) {
                createsInFlight.merge(thread, -1, Integer::sum);
            }
        }
    }

    private <T> T call(String what, boolean write, Callable<T> apply) throws Exception {
        checkInterrupted(what, false);
        if (closed) {
            violations.add(what + " on a closed client");
            throw new IllegalStateException("Expected state [STARTED] was [STOPPED]");
        }
        if (!clientConnected) {
            // [C] RetryLoop.callWithRetry first waits until the client is connected, up to the connection timeout, once
            // before its retries
            dst.awaitUntil(() -> clientConnected, connectionTimeout, what + " waits for the connection");
            checkInterrupted(what, false);
        }
        int attempt = 0;
        while (true) {
            try {
                return attempt(what, write, apply);
            } catch (KeeperException.ConnectionLossException e) {
                checkInterrupted(what, false);
                // [C] RetryLoop: a connection loss is retried as the retry policy allows
                if (attempt++ < retries) {
                    dst.block(retrySleep, "retry of " + what);
                    continue;
                }
                throw e;
            }
        }
    }

    private <T> T attempt(String what, boolean write, Callable<T> apply) throws Exception {
        long l = latency();
        if (!network) {
            dst.block(l, what + " (no connection)");
            throw connectionLoss();
        }
        if (write) {
            int k = outage;
            T answer = apply.call();
            dst.block(l, what);
            checkInterrupted(what, true);
            // [Z] the client will not know whether the operation succeeded: the change is made, and the connection is
            // lost before its answer came; the client reconnects soon after
            if (outage == k && network && faults && dst.chance(responseLostRate)) {
                dst.trace("zk: the connection is lost before the answer of " + what);
                disconnect(dst.between(10, 1000));
            }
            if (outage != k || !network) {
                dst.trace("zk: the answer of " + what + " is lost");
                throw connectionLoss();
            }
            return answer;
        }
        dst.block(l, what);
        checkInterrupted(what, false);
        if (!network) {
            throw connectionLoss();
        }
        return apply.call();
    }

    private KeeperException.ConnectionLossException connectionLoss() {
        notice(outage);
        return new KeeperException.ConnectionLossException();
    }

    private Object execute(Builder b, Object[] args) throws Exception {
        String path = (String) args[0];
        switch (b.kind) {
            case "create": {
                byte[] data = args.length > 1 ? (byte[]) args[1] : new byte[0];
                CreateMode mode = b.mode != null ? b.mode : CreateMode.PERSISTENT;
                return op("create " + path, true, () -> {
                    if (!tree.containsKey(parentOf(path))) {
                        if (!b.creatingParents) {
                            throw new KeeperException.NoNodeException(parentOf(path));
                        }
                        ensureParents(path);
                    }
                    if (!mode.isSequential() && tree.containsKey(path)) {
                        throw new KeeperException.NodeExistsException(path);
                    }
                    String created = createNode(path, data, mode, session);
                    dst.trace("zk: created " + created + " (session " + session + ")");
                    return created;
                });
            }
            case "setData": {
                byte[] data = (byte[]) args[1];
                return op("setData " + path, true, () -> {
                    Znode z = tree.get(path);
                    if (z == null) {
                        throw new KeeperException.NoNodeException(path);
                    }
                    z.data = data;
                    z.version++;
                    z.mzxid = ++zxid;
                    z.mtime = dst.currentTimeMillis();
                    fireDataWatches(path, Watcher.Event.EventType.NodeDataChanged);
                    return stat(z, new Stat());
                });
            }
            case "delete": {
                try {
                    return op("delete " + path, true, () -> {
                        if (!tree.containsKey(path)) {
                            throw new KeeperException.NoNodeException(path);
                        }
                        remove(path);
                        dst.trace("zk: deleted " + path);
                        return null;
                    });
                } catch (KeeperException.ConnectionLossException e) {
                    if (b.guaranteed && failedDeletes.add(path)) {
                        // [C] the failed delete is recorded and done again in the background (at the latest after the
                        // reconnect, below)
                        retryDelete(path);
                    }
                    throw e;
                }
            }
            case "getChildren":
                return op("getChildren " + path, false, () -> {
                    if (!tree.containsKey(path)) {
                        throw new KeeperException.NoNodeException(path);
                    }
                    if (b.watcher != null) {
                        childWatches.computeIfAbsent(path, p -> new LinkedHashSet<>()).add(b.watcher);
                    }
                    List<String> names = new ArrayList<>();
                    for (String p : tree.keySet()) {
                        if (isChild(path, p)) {
                            names.add(p.substring(path.length() + 1));
                        }
                    }
                    return names;
                });
            case "getData":
                return op("getData " + path, false, () -> {
                    Znode z = tree.get(path);
                    if (z == null) {
                        // [Z] no watch is left for a znode that does not exist (getData)
                        throw new KeeperException.NoNodeException(path);
                    }
                    if (b.watcher != null) {
                        dataWatches.computeIfAbsent(path, p -> new LinkedHashSet<>()).add(b.watcher);
                    }
                    if (b.stat != null) {
                        stat(z, b.stat);
                    }
                    return z.data;
                });
            default:
                violations.add("not modelled: " + b.kind + " " + b.calls);
                throw new UnsupportedOperationException("not modelled: " + b.kind);
        }
    }

    private Stat stat(Znode z, Stat stat) {
        stat.setVersion(z.version);
        stat.setCversion(z.cversion);
        stat.setCzxid(z.czxid);
        stat.setMzxid(z.mzxid);
        stat.setCtime(z.ctime);
        stat.setMtime(z.mtime);
        stat.setEphemeralOwner(z.owner);
        stat.setDataLength(z.data != null ? z.data.length : 0);
        return stat;
    }

    // ------------------------------------------------------------------------------------------------------------
    // the proxies

    private Object invokeClient(Object proxy, Method method, Object[] args) {
        switch (method.getName()) {
            case "create":
            case "delete":
            case "getData":
            case "setData":
            case "getChildren":
                return builder(new Builder(method.getName()), method.getReturnType());
            case "getConnectionStateListenable":
                return listenable;
            case "getZookeeperClient":
                return zookeeperClient;
            case "newNamespaceAwareEnsurePath":
                return new FakeEnsurePath((String) args[0]);
            case "clearWatcherReferences":
                // Curator 5.9 does nothing here (CuratorFrameworkImpl.clearWatcherReferences is empty)
                return null;
            case "close":
                violations.add("the shared CuratorFramework was closed");
                closed = true;
                return null;
            case "getState":
                return closed ? CuratorFrameworkState.STOPPED : CuratorFrameworkState.STARTED;
            case "isStarted":
                return !closed;
            case "toString":
                return "FakeZooKeeper.client";
            case "hashCode":
                return System.identityHashCode(proxy);
            case "equals":
                return proxy == args[0];
            default:
                violations.add("not modelled: CuratorFramework." + method.getName());
                throw new UnsupportedOperationException("not modelled: CuratorFramework." + method.getName());
        }
    }

    private final Listenable<ConnectionStateListener> listenable = new Listenable<>() {
        @Override
        public void addListener(ConnectionStateListener listener) {
            if (!listeners.contains(listener)) {
                listeners.add(listener);
            }
        }

        @Override
        public void addListener(ConnectionStateListener listener, Executor executor) {
            addListener(listener);
        }

        @Override
        public void removeListener(ConnectionStateListener listener) {
            listeners.remove(listener);
        }
    };

    /** The calls of a fluent Curator builder, up to the forPath that runs it. */
    private static final class Builder {
        final String kind;
        final List<String> calls = new ArrayList<>();
        boolean creatingParents;
        boolean guaranteed;
        CreateMode mode;
        Watcher watcher;
        Stat stat;

        Builder(String kind) {
            this.kind = kind;
        }
    }

    private Object builder(Builder b, Class<?> type) {
        InvocationHandler h = (proxy, method, args) -> {
            String name = method.getName();
            switch (name) {
                case "forPath":
                    return execute(b, args);
                case "creatingParentsIfNeeded":
                    b.creatingParents = true;
                    break;
                case "guaranteed":
                    b.guaranteed = true;
                    break;
                case "withMode":
                    b.mode = (CreateMode) args[0];
                    break;
                case "usingWatcher":
                    if (args[0] instanceof Watcher w) {
                        b.watcher = w;
                    } else {
                        violations.add("not modelled: a CuratorWatcher");
                        throw new UnsupportedOperationException("not modelled: a CuratorWatcher");
                    }
                    break;
                case "storingStatIn":
                    b.stat = (Stat) args[0];
                    break;
                case "toString":
                    return b.kind + b.calls;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    violations.add("not modelled: " + b.kind + "()." + name);
                    throw new UnsupportedOperationException("not modelled: " + b.kind + "()." + name);
            }
            b.calls.add(name);
            Class<?> next = returnType(method, type);
            if (next == null || !next.isInterface()) {
                violations.add("not modelled: " + b.kind + "()." + name + " returning " + next);
                throw new UnsupportedOperationException(name);
            }
            return builder(b, next);
        };
        return Proxy.newProxyInstance(CuratorFramework.class.getClassLoader(), new Class<?>[] { type }, h);
    }

    /**
     * The interface a builder method returns: the builders of Curator are generic (Watchable&lt;T&gt; returns T), so
     * the type variable is bound through the generic interfaces of the builder the method is called on.
     */
    private static Class<?> returnType(Method method, Class<?> on) {
        Type t = method.getGenericReturnType();
        if (t instanceof TypeVariable<?> tv) {
            t = bind(on, new HashMap<>(), tv);
        }
        if (t instanceof Class<?> c) {
            return c;
        }
        if (t instanceof ParameterizedType p) {
            return (Class<?>) p.getRawType();
        }
        return null;
    }

    private static Type bind(Class<?> c, Map<TypeVariable<?>, Type> env, TypeVariable<?> target) {
        for (Type gi : c.getGenericInterfaces()) {
            Map<TypeVariable<?>, Type> inner = new HashMap<>();
            Class<?> raw;
            if (gi instanceof ParameterizedType p) {
                raw = (Class<?>) p.getRawType();
                TypeVariable<?>[] vars = raw.getTypeParameters();
                Type[] actual = p.getActualTypeArguments();
                for (int i = 0; i < vars.length; i++) {
                    Type a = actual[i];
                    if (a instanceof TypeVariable<?> av && env.containsKey(av)) {
                        a = env.get(av);
                    }
                    inner.put(vars[i], a);
                }
            } else {
                raw = (Class<?>) gi;
            }
            if (inner.containsKey(target)) {
                return inner.get(target);
            }
            Type found = bind(raw, inner, target);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** isConnected is what the client believes: it turns false when the client noticed the connection loss. */
    private final class FakeZookeeperClient extends CuratorZookeeperClient {
        FakeZookeeperClient() {
            super("fake:2181", 60000, 15000, null, new RetryOneTime(1000));
        }

        @Override
        public boolean isConnected() {
            return clientConnected && !closed;
        }
    }

    /**
     * Makes the persistent parents of a path, as EnsurePath does, retried as the client retries: once, as after a
     * success EnsurePath does nothing any more (its helper becomes a no-op, EnsurePath$1 in Curator 5.9).
     */
    private final class FakeEnsurePath extends EnsurePath {
        private boolean done;

        FakeEnsurePath(String path) {
            super(path);
        }

        @Override
        public void ensure(CuratorZookeeperClient c) throws Exception {
            if (done) {
                return;
            }
            String path = getPath();
            op("ensure " + path, true, () -> {
                if (!tree.containsKey(path)) {
                    ensureParents(path + "/x");
                }
                return null;
            });
            done = true;
        }
    }
}
