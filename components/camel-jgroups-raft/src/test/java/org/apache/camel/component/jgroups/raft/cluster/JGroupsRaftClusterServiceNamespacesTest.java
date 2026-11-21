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
package org.apache.camel.component.jgroups.raft.cluster;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.impl.DefaultCamelContext;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The views of all namespaces of a node use the same Raft member (one raft id), so they share one RaftHandle. Each
 * namespace must get the leadership of the node, whenever its view starts, and must keep it when the view of another
 * namespace stops.
 */
class JGroupsRaftClusterServiceNamespacesTest {

    private DefaultCamelContext context;
    private RaftHandle handle;

    @AfterEach
    void cleanup() {
        if (context != null) {
            context.stop();
        }
        if (handle != null) {
            handle.channel().close();
        }
    }

    @Test
    void testNamespaceStartedAfterElectionGetsLeadership() throws Exception {
        handle = newHandle();
        JGroupsRaftClusterService service = newService();
        service.setRaftHandle(handle);
        AtomicInteger ns1 = new AtomicInteger();
        AtomicInteger ns2 = new AtomicInteger();

        startContext(service, ns1);
        await().atMost(30, TimeUnit.SECONDS).until(() -> ns1.get() > 0 && handle.isLeader());

        // a master route of another namespace starts after the node was elected leader
        context.addRoutes(masterRoute("ns2", ns2));
        await().atMost(20, TimeUnit.SECONDS).until(() -> ns2.get() > 0);
        assertTrue(service.isLeader("ns2"));
        assertTrue(service.isLeader("ns1"));
    }

    @Test
    void testStoppingOneNamespaceKeepsTheOthers() throws Exception {
        handle = newHandle();
        JGroupsRaftClusterService service = newService();
        service.setRaftHandle(handle);
        AtomicInteger ns1 = new AtomicInteger();
        AtomicInteger ns2 = new AtomicInteger();

        startContext(service, ns1, ns2);
        await().atMost(30, TimeUnit.SECONDS).until(() -> ns1.get() > 0 && ns2.get() > 0);

        context.getRouteController().stopRoute("route-ns1");
        assertTrue(handle.channel().isConnected(), "The handle should stay connected while namespace ns2 uses it");
        int before = ns2.get();
        await().atMost(20, TimeUnit.SECONDS).until(() -> ns2.get() > before + 5);
        assertTrue(service.isLeader("ns2"));

        // the handle is disconnected when the last namespace stops, and connected again when one starts again
        context.getRouteController().stopRoute("route-ns2");
        assertFalse(handle.channel().isConnected(), "The handle should be disconnected when no namespace uses it");
        int restarted = ns1.get();
        context.getRouteController().startRoute("route-ns1");
        await().atMost(30, TimeUnit.SECONDS).until(() -> ns1.get() > restarted);
        assertTrue(service.isLeader("ns1"));
    }

    @Test
    void testTwoNamespacesWithoutRaftHandle() throws Exception {
        // no RaftHandle configured: the service creates the handle of the node
        JGroupsRaftClusterService service = newService();
        service.setJgroupsConfig("raft.xml");
        AtomicInteger ns1 = new AtomicInteger();
        AtomicInteger ns2 = new AtomicInteger();

        startContext(service, ns1, ns2);
        await().atMost(30, TimeUnit.SECONDS).until(() -> ns1.get() > 0 && ns2.get() > 0);
        assertTrue(service.isLeader("ns1"));
        assertTrue(service.isLeader("ns2"));
    }

    private void startContext(JGroupsRaftClusterService service, AtomicInteger... counters) throws Exception {
        context = new DefaultCamelContext();
        context.disableJMX();
        context.addService(service);
        for (int i = 0; i < counters.length; i++) {
            context.addRoutes(masterRoute("ns" + (i + 1), counters[i]));
        }
        context.start();
    }

    private static RaftHandle newHandle() throws Exception {
        RaftHandle raftHandle = new RaftHandle(new JChannel("raft.xml").name("A"), new NopStateMachine()).raftId("A");
        raftHandle.raft().logPrefix("namespaces-" + UUID.randomUUID() + "-A");
        return raftHandle;
    }

    private static JGroupsRaftClusterService newService() {
        JGroupsRaftClusterService service = new JGroupsRaftClusterService();
        service.setId("A");
        service.setJgroupsClusterName("namespaces-" + UUID.randomUUID());
        return service;
    }

    private static RouteBuilder masterRoute(String namespace, AtomicInteger counter) {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("master:" + namespace + ":timer:" + namespace + "?period=100").routeId("route-" + namespace)
                        .process(e -> counter.incrementAndGet());
            }
        };
    }
}
