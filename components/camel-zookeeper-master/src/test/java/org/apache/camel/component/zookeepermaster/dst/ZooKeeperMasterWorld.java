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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.zookeepermaster.group.Group;
import org.apache.camel.component.zookeepermaster.group.GroupListener;
import org.apache.camel.component.zookeepermaster.group.ManagedGroupFactory;
import org.apache.camel.component.zookeepermaster.group.NodeState;
import org.apache.camel.component.zookeepermaster.group.internal.ZooKeeperGroup;
import org.apache.camel.component.zookeepermaster.policy.MasterRoutePolicy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultConsumer;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstCamel;
import org.apache.camel.test.dst.DstExecutor;
import org.apache.camel.test.dst.DstWorld;
import org.apache.camel.test.dst.Invariants;
import org.apache.curator.framework.CuratorFramework;

/**
 * The world around one node of a camel-zookeeper-master group: the real master consumer ({@code zookeeper-master:}
 * endpoint) or the real {@link MasterRoutePolicy}, with the real {@code ZooKeeperGroup} of the component, on a fake
 * ZooKeeper ensemble ({@link FakeZooKeeper}) shared through the {@code curator} bean, as an application shares one
 * CuratorFramework. The other nodes of the group are sessions of the fake (peers) that join, leave and die. The
 * consumer that only the master runs is a test consumer whose start takes a while and sometimes fails, as a consumer
 * that connects to a broker does.
 * <p>
 * The seed picks the mode (master consumer or route policy), the session timeout, the retry policy of the client, the
 * start time of the consumer, the fault rates, and the operations: connection losses shorter or longer than the session
 * timeout, peers that join, leave or die, route stops and starts (master consumer), routes removed and added again and
 * route starts from outside (route policy).
 * <p>
 * The groups are made by a {@link ManagedGroupFactory} bean, as DefaultManagedGroupFactory makes them (a ZooKeeperGroup
 * on a single thread executor of the ExecutorServiceManager), to stand in for the thread interrupt of
 * {@code ExecutorService.shutdownNow} that the harness does not do. A real close of a {@code ZooKeeperGroup} interrupts
 * its thread: a ZooKeeper call it waits in throws (a write already sent is applied), the composite operation stops, and
 * the wait of the main loop on its queue ends. The fake fails the calls of the thread of a closed group, and the world
 * offers the closed group a refresh (public {@code clearAndRefresh}), after which its loop ends. A close therefore
 * waits the 5 seconds of its {@code awaitTermination} in virtual time (a real close waits only while the thread is
 * busy), so the znode of a closed group is deleted 5 seconds after the close began, and a peer takes over that much
 * later. As the factory is a bean of the registry, the default path (DefaultGroupFactoryStrategy and
 * DefaultManagedGroupFactory) does not run; a bean is also what lets the world check that a consumer or a policy does
 * not close a group factory it did not create.
 * <p>
 * Properties: this node runs its consumer only while it is the master (allowing the time of a start in flight and of
 * the events to arrive); once the faults cleared, the master runs its consumer, and this node does when it is the
 * master; a stopped consumer or a removed route leaves no listener on the shared CuratorFramework and does not keep the
 * leadership of the group with a znode of its own; the shared CuratorFramework and group factory are not closed by a
 * consumer or a policy.
 * <p>
 * Not explored: several master endpoints or policies of one node in the same group (each ZooKeeperGroup main loop runs
 * for ever, and on the one stack of the harness a second loop nested above the first would never let it resume), route
 * suspend and resume, the CuratorFramework the component creates itself, a stop of the route policy on its own (from
 * JMX), the camel-master cluster services, and a stop while the thread of the group waits in a call of ZooKeeper (see
 * {@code mustWaitToStop}). The route policy starts its consumer at once (see {@code maxStart}).
 * <p>
 * Left out of the default set while they are looked at separately (a system property adds each): a route stopped while
 * the client is disconnected ({@code -Ddst.zookeeper.stopWhileSuspended=true}) or while the group waits for the answer
 * of a create ({@code -Ddst.zookeeper.stopDuringCreate=true}), and a route of the policy started from outside when it
 * is started already ({@code -Ddst.zookeeper.startStartedRoute=true}).
 */
@DstWorld(component = "zookeeper-master",
          fake = "FakeZooKeeper: a ZooKeeper ensemble (sessions, ephemeral sequential znodes, one-time watches) with the"
                 + " other nodes of the group as sessions, behind a CuratorFramework shared as the curator bean",
          covers = {
                  "the master consumer (zookeeper-master endpoint) and the MasterRoutePolicy, on the real ZooKeeperGroup",
                  "connection losses shorter and longer than the session timeout (SUSPENDED, RECONNECTED, LOST)",
                  "other nodes that join, leave and die",
                  "a start of the consumer that fails and its retry; with the master consumer, a start that takes long",
                  "route stop and start, and a route of the policy removed and added again" },
          faults = {
                  "the connection to ZooKeeper is lost", "the session expires", "an operation answers slowly",
                  "the connection is lost before the answer of a write", "the start of the consumer fails",
                  "the start of the consumer takes long" },
          invariants = {
                  "the client is used as ZooKeeper and Curator allow",
                  "this node runs its consumer only while it is the master",
                  "once the faults cleared, the master runs its consumer",
                  "a stopped consumer leaves no listener on the shared CuratorFramework",
                  "no znode of this node is left in the group after the final stop",
                  "the shared CuratorFramework and group factory are not closed",
                  "the world itself ran as planned" },
          issues = { "CAMEL-25315", "CAMEL-26540", "CAMEL-26802", "CAMEL-26870", "CAMEL-26887" })
final class ZooKeeperMasterWorld {

    static final String ROUTE = "master";
    static final String GROUP = "dst";
    static final String GROUP_PATH = "/camel/zookeepermaster/clusters/master/" + GROUP;

    private final Dst dst;
    private final FakeZooKeeper zk;
    private final boolean policyMode;
    private final long maxStart;
    private final double startFailRate;
    private final long grace;
    private final boolean startStartedRoute = Boolean.getBoolean("dst.zookeeper.startStartedRoute");
    private final boolean stopWhileSuspended = Boolean.getBoolean("dst.zookeeper.stopWhileSuspended");
    private final boolean stopDuringCreate = Boolean.getBoolean("dst.zookeeper.stopDuringCreate");
    private final List<String> violations = new ArrayList<>();
    private final List<WorkConsumer> running = new ArrayList<>();
    private final RecordingGroupFactory groupFactory;
    private MasterRoutePolicy policy;
    private boolean faults = true;
    private boolean routeActive;
    private boolean finished;
    private int opsLeft;
    private int baselineListeners;
    private long wrongSince = -1;
    private int startsInFlight;
    private long lastStartEnd = -1;
    private String wrongWhy;
    private String finalCheck;
    private String afterStop;
    private int peerNumber;

    ZooKeeperMasterWorld(Dst dst) {
        this.dst = dst;
        this.zk = new FakeZooKeeper(dst);
        this.groupFactory = new RecordingGroupFactory();
        this.policyMode = dst.chance(0.5);
        zk.sessionTimeout = dst.pick(List.of(4000L, 10_000L));
        zk.retries = dst.pick(List.of(0, 1));
        zk.slowRate = dst.pick(List.of(0.0, 0.05));
        // a connection lost before the answer of a write: the client retries it; a create retried that way makes a second
        // znode, which the group removes as it finds it with its own uuid (prunePartialState)
        zk.responseLostRate = dst.pick(List.of(0.0, 0.05));
        // the route policy starts the consumer while it holds its leadership lock, and locks do not exclude on the one
        // stack of the harness: a start that blocks would let a disconnect or a removal run inside that lock, which
        // cannot happen for real. Its starts take no time
        this.maxStart = policyMode ? 0 : dst.pick(List.of(100L, 3000L, 8000L));
        this.startFailRate = dst.pick(List.of(0.0, 0.3, 0.6));
        // the events to arrive, once no start of the consumer is in flight (a start that began while this node was the
        // master completes, and the consumer stops it then)
        this.grace = 2000;
        dst.trace("world: " + config());
    }

    private String config() {
        return (policyMode ? "MasterRoutePolicy" : "master consumer") + " sessionTimeout=" + zk.sessionTimeout
               + " retries=" + zk.retries + " slow=" + zk.slowRate + " answerLost=" + zk.responseLostRate
               + " maxStart=" + maxStart + " startFail=" + startFailRate;
    }

    void run() throws Exception {
        DefaultCamelContext context = DstCamel.newContext(dst);
        context.getRegistry().bind("curator", zk.client());
        context.getRegistry().bind("groupFactory", groupFactory);
        groupFactory.curator = zk.client();
        groupFactory.context = context;
        context.addComponent("work", new WorkComponent());
        if (policyMode) {
            policy = new MasterRoutePolicy();
            policy.setGroupName(GROUP);
            if (dst.chance(0.5)) {
                policy.setCurator(zk.client());
            }
        }
        context.addRoutes(routes());
        context.start();
        baselineListeners = policyMode ? 0 : zk.listenerCount();
        routeActive = policyMode;
        DstExecutor operator = dst.executor("operator", 1);
        try {
            if (!policyMode) {
                operator.schedule("start route", 0, () -> startRoute(context));
            }
            planPeers(operator);
            planOperations(context, operator);
            DstExecutor monitor = dst.executor("monitor", 1);
            monitor.scheduleWithFixedDelay(this::monitor, 100, 100, TimeUnit.MILLISECONDS);
            operator.schedule("finisher", 1000, () -> finisher(context, operator, -1));
            dst.runUntil(() -> finished, "the finisher");
            dst.check(
                    Invariants.of("the client is used as ZooKeeper and Curator allow",
                            () -> zk.violations.isEmpty() ? null : zk.violations + " (" + config() + ")"),
                    Invariants.of("this node runs its consumer only while it is the master",
                            () -> violations.stream().noneMatch(v -> v.startsWith("runs"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("once the faults cleared, the master runs its consumer",
                            () -> finalCheck == null ? null : finalCheck + " (" + config() + ")"),
                    Invariants.of("a stopped consumer leaves no listener on the shared CuratorFramework",
                            () -> violations.stream().noneMatch(v -> v.startsWith("listeners"))
                                    ? null : violations + " (" + config() + ")"),
                    Invariants.of("no znode of this node is left in the group after the final stop",
                            () -> afterStop == null ? null : afterStop + " (" + config() + ")"),
                    Invariants.of("the shared CuratorFramework and group factory are not closed",
                            () -> !groupFactory.closed && !zk.isClosed()
                                    ? null : "group factory closed=" + groupFactory.closed + " client closed="
                                             + zk.isClosed() + " (" + config() + ")"),
                    Invariants.of("the world itself ran as planned",
                            () -> violations.stream().noneMatch(v -> v.startsWith("operator"))
                                    ? null : violations + " (" + config() + ")"));
        } finally {
            dst.dropPending();
            context.stop();
        }
    }

    private RouteBuilder routes() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                if (policyMode) {
                    from("work:in").routeId(ROUTE).autoStartup(false).routePolicy(policy)
                            .process(exchange -> {
                                // nothing is routed: the world looks at which node runs the consumer
                            });
                } else {
                    from("zookeeper-master:" + GROUP + ":work:in").routeId(ROUTE).autoStartup(false)
                            .process(exchange -> {
                                // nothing is routed: the world looks at which node runs the consumer
                            });
                }
            }
        };
    }

    // ------------------------------------------------------------------------------------------------------------
    // the properties, checked every 100 ms

    private boolean localIsMaster() {
        String master = zk.master(GROUP_PATH);
        String id = groupFactory.currentId();
        return master != null && master.equals(id);
    }

    private void monitor() {
        if (finished) {
            return;
        }
        if (running.size() > 1 && violations.stream().noneMatch(v -> v.startsWith("runs two"))) {
            violations.add("runs two consumers at once at t=" + dst.now());
        }
        String why = null;
        if (!running.isEmpty()) {
            if (!routeActive) {
                why = "its route is stopped";
            } else if (!zk.isClientConnected()) {
                why = "it is disconnected from ZooKeeper";
            } else if (!localIsMaster()) {
                String master = zk.master(GROUP_PATH);
                String peer = zk.peerOf(master);
                why = peer != null ? "peer " + peer + " is the master" : "the master is " + master;
            }
        }
        if (why == null) {
            wrongSince = -1;
            return;
        }
        if (wrongSince < 0) {
            wrongSince = dst.now();
            wrongWhy = why;
        } else if (dst.now() - wrongSince > grace && startsInFlight == 0 && dst.now() - lastStartEnd > grace
                && violations.stream().noneMatch(v -> v.startsWith("runs"))) {
            violations.add("runs its consumer while " + wrongWhy + " since t=" + wrongSince + " (now " + dst.now()
                           + ", last start ended at t=" + lastStartEnd + ", grace " + grace + ")");
        }
    }

    private void checkStopped(String when) {
        if (!running.isEmpty() && startsInFlight == 0
                && violations.stream().noneMatch(v -> v.startsWith("runs"))) {
            violations.add("runs its consumer " + when + " at t=" + dst.now());
        }
    }

    private void checkListeners(String when) {
        if (zk.listenerCount() != baselineListeners
                && violations.stream().noneMatch(v -> v.startsWith("listeners"))) {
            violations.add("listeners on the shared CuratorFramework " + when + ": " + zk.listenerCount()
                           + ", expected " + baselineListeners + " at t=" + dst.now());
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the load and the operator

    private void planPeers(DstExecutor operator) {
        int initial = (int) dst.between(0, 2);
        for (int i = 0; i < initial; i++) {
            String c = "peer-" + ++peerNumber;
            operator.schedule("peer joins", dst.between(0, 2000), () -> zk.peerJoins(GROUP_PATH, c));
        }
        int changes = (int) dst.between(0, 4);
        long t = dst.between(1000, 5000);
        for (int i = 0; i < changes; i++) {
            opsLeft++;
            operator.schedule("peer change", t, () -> {
                if (!faults) {
                    opsLeft--;
                    return;
                }
                List<String> present = new ArrayList<>(zk.peers());
                if (present.isEmpty() || dst.chance(0.4)) {
                    zk.peerJoins(GROUP_PATH, "peer-" + ++peerNumber);
                } else if (dst.chance(0.5)) {
                    zk.peerLeaves(dst.pick(present));
                } else {
                    zk.peerDies(dst.pick(present));
                }
                opsLeft--;
            });
            t += dst.between(1000, 15_000);
        }
    }

    private void planOperations(DefaultCamelContext context, DstExecutor operator) {
        int ops = (int) dst.between(0, 4);
        long t = dst.between(500, 4000);
        for (int i = 0; i < ops; i++) {
            opsLeft++;
            int kind = (int) dst.between(0, 2);
            if (kind == 0) {
                long length = dst.chance(0.5)
                        ? dst.between(100, zk.sessionTimeout / 2) : dst.between(zk.sessionTimeout, 3 * zk.sessionTimeout);
                operator.schedule("connection loss", t, () -> {
                    zk.disconnect(length);
                    opsLeft--;
                });
            } else if (policyMode && kind == 1) {
                operator.schedule("remove route", t, () -> removeRoute(context, operator));
            } else if (policyMode) {
                operator.schedule("start route from outside", t, () -> {
                    // a route that is already started is started again only with -Ddst.zookeeper.startStartedRoute=true:
                    // that starts its consumer again without the onStart of the policy
                    if (startStartedRoute || context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
                        startRoute(context);
                    }
                    opsLeft--;
                });
            } else {
                operator.schedule("stop route", t, () -> stopRoute(context, operator));
            }
            t += dst.between(1000, 20_000);
        }
    }

    private void startRoute(CamelContext context) {
        try {
            context.getRouteController().startRoute(ROUTE);
            routeActive = true;
        } catch (Exception e) {
            if (policyMode && causedByStartFault(e)) {
                // the consumer of a route started from outside failed to start: the route stays stopped
                return;
            }
            violations.add("operator: start failed: " + e);
        }
    }

    private static boolean causedByStartFault(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().startsWith("dst: the consumer cannot connect")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the operator waits before it stops or removes the route:
     * <ul>
     * <li>while the thread of the group waits in a call of ZooKeeper other than a create (a read, a setData, a delete,
     * the ensure of the path): a real close interrupts that call and waits for the thread to end before it leaves the
     * group, so the thread records the znode it made first; on the one stack of the harness the thread can only end
     * after the close returned, so the close would leave the group before (a limit of the harness);</li>
     * <li>while the thread of the group waits in a create (the create is applied, and the group never records the
     * znode, which stays in the group as long as the session of the shared client lives;
     * {@code -Ddst.zookeeper.stopDuringCreate=true} adds it);</li>
     * <li>while the client is disconnected (a group closed then does not delete its znode, with the same result;
     * {@code -Ddst.zookeeper.stopWhileSuspended=true} adds it).</li>
     * </ul>
     * A stop while the thread starts the consumer is driven.
     */
    private boolean mustWaitToStop() {
        return zk.inCallOtherThanCreate("ZooKeeperGroup-") || !stopDuringCreate && zk.inCreate("ZooKeeperGroup-")
                || !stopWhileSuspended && !zk.isClientConnected();
    }

    private void stopRoute(DefaultCamelContext context, DstExecutor operator) {
        if (context.getRouteController().getRouteStatus(ROUTE) != ServiceStatus.Started) {
            // stopped already by an earlier operation, which starts it again
            opsLeft--;
            return;
        }
        if (mustWaitToStop()) {
            operator.schedule("stop route (later)", 10, () -> stopRoute(context, operator));
            return;
        }
        try {
            context.getRouteController().stopRoute(ROUTE);
        } catch (Exception e) {
            violations.add("operator: stop failed: " + e);
        }
        routeActive = false;
        checkStopped("after the route stopped");
        checkListeners("after the route stopped");
        operator.schedule("start route", dst.between(100, 10_000), () -> {
            startRoute(context);
            opsLeft--;
        });
    }

    private void removeRoute(DefaultCamelContext context, DstExecutor operator) {
        if (context.getRoute(ROUTE) == null) {
            // removed already by an earlier operation, which adds it again
            opsLeft--;
            return;
        }
        if (mustWaitToStop()) {
            operator.schedule("remove route (later)", 10, () -> removeRoute(context, operator));
            return;
        }
        try {
            if (context.getRouteController().getRouteStatus(ROUTE) == ServiceStatus.Started) {
                context.getRouteController().stopRoute(ROUTE);
            }
            context.removeRoute(ROUTE);
        } catch (Exception e) {
            violations.add("operator: remove failed: " + e);
        }
        routeActive = false;
        checkStopped("after the route was removed");
        checkListeners("after the route was removed");
        operator.schedule("add route again", dst.between(100, 10_000), () -> {
            try {
                context.addRoutes(routes());
                routeActive = true;
            } catch (Exception e) {
                violations.add("operator: add failed: " + e);
            }
            opsLeft--;
        });
    }

    private void finisher(DefaultCamelContext context, DstExecutor operator, long deadline) {
        if (deadline == -1 && opsLeft > 0) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, -1));
            return;
        }
        if (deadline == -1) {
            faults = false;
            zk.faults = false;
            dst.trace("world: faults cleared");
            // the other nodes leave sometimes, so that this node becomes the master
            if (dst.chance(0.5)) {
                new ArrayList<>(zk.peers()).forEach(zk::peerLeaves);
            }
            long d = dst.now() + 5 * 60_000L;
            operator.schedule("finisher", 500, () -> finisher(context, operator, d));
            return;
        }
        boolean expected = routeActive && zk.isClientConnected() && localIsMaster();
        String master = zk.master(GROUP_PATH);
        // the master is this node's znode or a peer that is alive: else no node runs the consumer
        boolean liveMaster = master != null
                && (master.equals(groupFactory.currentId())
                        || zk.peerOf(master) != null && zk.peers().contains(zk.peerOf(master)));
        boolean settled = zk.isNetworkUp() && zk.isClientConnected() && zk.pendingDeletes().isEmpty()
                && (expected ? running.size() == 1 : running.isEmpty())
                && (liveMaster || !routeActive && zk.peers().isEmpty());
        if (!settled && dst.now() < deadline) {
            operator.schedule("finisher", 500, () -> finisher(context, operator, deadline));
            return;
        }
        if (mustWaitToStop()) {
            operator.schedule("finisher (later)", 10, () -> finisher(context, operator, deadline));
            return;
        }
        if (!settled) {
            finalCheck = "at the deadline: route active=" + routeActive + " connected=" + zk.isClientConnected()
                         + " master=" + master + " (peer " + zk.peerOf(master) + ") this node=" + groupFactory.currentId()
                         + " local znodes=" + zk.localZnodes(GROUP_PATH) + " consumers running=" + running.size();
        }
        // the final stop: the consumer or the route of the policy leaves the group
        try {
            if (context.getRouteController().getRouteStatus(ROUTE) == ServiceStatus.Started) {
                context.getRouteController().stopRoute(ROUTE);
            }
            if (policyMode) {
                context.removeRoute(ROUTE);
            }
            routeActive = false;
        } catch (Exception e) {
            violations.add("operator: final stop failed: " + e);
        }
        checkListeners("after the final stop");
        operator.schedule("after the final stop", 2000, () -> {
            List<String> left = zk.localZnodes(GROUP_PATH);
            if (!left.isEmpty()) {
                String last = zk.master(GROUP_PATH);
                afterStop = "after the final stop this node still has the znodes " + left + " in the group (the master is "
                            + last + (left.contains(last) ? ", one of them)" : ")");
            }
            if (!running.isEmpty()) {
                violations.add("runs its consumer after the final stop");
            }
            finished = true;
        });
    }

    // ------------------------------------------------------------------------------------------------------------
    // the consumer that only the master runs

    private final class WorkComponent extends DefaultComponent {
        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
            return new WorkEndpoint(uri, this);
        }
    }

    private final class WorkEndpoint extends DefaultEndpoint {
        WorkEndpoint(String uri, WorkComponent component) {
            super(uri, component);
        }

        @Override
        public Producer createProducer() {
            throw new UnsupportedOperationException("work: consumer only");
        }

        @Override
        public Consumer createConsumer(Processor processor) {
            return new WorkConsumer(this, processor);
        }
    }

    /** A consumer whose start connects to a broker: it takes a while and fails sometimes. */
    private final class WorkConsumer extends DefaultConsumer {
        WorkConsumer(Endpoint endpoint, Processor processor) {
            super(endpoint, processor);
        }

        @Override
        protected void doStart() throws Exception {
            startsInFlight++;
            try {
                if (maxStart > 0) {
                    dst.block(dst.between(0, maxStart), "start of the consumer");
                }
                if (faults && dst.chance(startFailRate)) {
                    throw Dst.injected(new IOException("dst: the consumer cannot connect to its broker"));
                }
                super.doStart();
                running.add(this);
                dst.trace("world: consumer started");
            } finally {
                startsInFlight--;
                lastStartEnd = dst.now();
            }
        }

        @Override
        protected void doStop() throws Exception {
            if (running.remove(this)) {
                dst.trace("world: consumer stopped");
            }
            super.doStop();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // the group factory

    /**
     * A group factory as a bean of the registry. It makes the groups as DefaultManagedGroupFactory does (a
     * ZooKeeperGroup on a single thread executor of the ExecutorServiceManager), with a thread name of its own per
     * group, so that the fake can tell the thread of a closed group, which a real close interrupts.
     */
    private final class RecordingGroupFactory implements ManagedGroupFactory {
        CuratorFramework curator;
        CamelContext context;
        boolean closed;
        private int groups;
        private ClosingGroup<?> current;

        String currentId() {
            ClosingGroup<?> g = current;
            return g != null && !g.closed ? g.delegate.getId() : null;
        }

        @Override
        public CuratorFramework getCurator() {
            return curator;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public <T extends NodeState> Group<T> createGroup(String path, Class<T> clazz) {
            String name = "ZooKeeperGroup-" + path + "-" + ++groups;
            ExecutorService executor = context.getExecutorServiceManager().newSingleThreadExecutor(this, name);
            ClosingGroup<T> g = new ClosingGroup<>(new ZooKeeperGroup<>(curator, path, clazz, executor), name);
            current = g;
            return g;
        }

        @Override
        public <T extends NodeState> Group<T> createGroup(String path, Class<T> clazz, ThreadFactory threadFactory) {
            return createGroup(path, clazz);
        }

        @Override
        public <T extends NodeState> Group<T> createMultiGroup(String path, Class<T> clazz) {
            violations.add("operator: a multi group is not modelled");
            throw new UnsupportedOperationException("not modelled");
        }

        @Override
        public <T extends NodeState> Group<T> createMultiGroup(String path, Class<T> clazz, ThreadFactory threadFactory) {
            return createMultiGroup(path, clazz);
        }
    }

    /**
     * The real ZooKeeperGroup. Its close interrupts the thread of the group in the fake, and ends the wait of the
     * group's main loop (see the class Javadoc).
     */
    private final class ClosingGroup<T extends NodeState> implements Group<T> {
        final ZooKeeperGroup<T> delegate;
        final String thread;
        boolean closed;

        ClosingGroup(ZooKeeperGroup<T> delegate, String thread) {
            this.delegate = delegate;
            this.thread = thread;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            zk.interrupt(thread);
            delegate.close();
            try {
                delegate.clearAndRefresh();
            } catch (Exception e) {
                // the refresh only wakes the main loop of the closed group
                dst.trace("world: refresh of a closed group failed: " + e);
            }
        }

        @Override
        public boolean isConnected() {
            return delegate.isConnected();
        }

        @Override
        public void start() {
            delegate.start();
        }

        @Override
        public void add(GroupListener<T> listener) {
            delegate.add(listener);
        }

        @Override
        public void remove(GroupListener<T> listener) {
            delegate.remove(listener);
        }

        @Override
        public void update(T state) {
            delegate.update(state);
        }

        @Override
        public Map<String, T> members() {
            return delegate.members();
        }

        @Override
        public boolean isMaster() {
            return delegate.isMaster();
        }

        @Override
        public T master() {
            return delegate.master();
        }

        @Override
        public List<T> slaves() {
            return delegate.slaves();
        }

        @Override
        public T getLastState() {
            return delegate.getLastState();
        }
    }
}
