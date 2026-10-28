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

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.cluster.ClusteredRoutePolicy;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class JGroupsRaftClusteredRoutePolicyTest extends JGroupsRaftClusterAbstractTest {

    @Test
    @SuppressWarnings("resource")
    public void test() throws Exception {
        RaftHandle handleA = createHandle("A", new NopStateMachine());
        CamelContext contextA = createContext("A", handleA);

        RaftHandle handleB = createHandle("B", new NopStateMachine());
        CamelContext contextB = createContext("B", handleB);

        RaftHandle handleC = createHandle("C", new NopStateMachine());
        CamelContext contextC = createContext("C", handleC);

        contexts.add(contextA);
        routeNames.add("route-A");
        contexts.add(contextB);
        routeNames.add("route-B");
        contexts.add(contextC);
        routeNames.add("route-C");

        contextA.start();
        contextB.start();
        contextC.start();
        waitForLeader(50, handleA, handleB, handleC);
        awaitOneActiveRoute();

        contextA.stop();
        // Ensure channel A is fully closed before checking for a new leader
        handleA.channel().close();
        waitForLeader(50, handleB, handleC);
        awaitOneActiveRoute();

        contextB.stop();
        // Ensure channel B is fully closed before creating a new channel with the same member name
        handleB.channel().close();
        waitForViewSize(handleC.channel(), 1, 30);
        handleA = createHandle("A", new NopStateMachine());
        contextA = createContext("A", handleA);
        contexts.set(0, contextA);
        contextA.start();
        waitForLeader(50, handleA, handleC);
        awaitOneActiveRoute();
    }

    private CamelContext createContext(String id, RaftHandle rh) throws Exception {
        JGroupsRaftClusterService service = new JGroupsRaftClusterService();
        service.setId(id);
        service.setRaftId(id);
        service.setRaftHandle(rh);
        service.setJgroupsClusterName("JGroupsRaftClusteredRoutePolicyTest");

        DefaultCamelContext context = new DefaultCamelContext();
        context.disableJMX();
        context.getCamelContextExtension().setName("context-" + id);
        context.addService(service);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("timer:master?delay=1000&period=1000")
                        .routeId("route-" + id)
                        .routePolicy(ClusteredRoutePolicy.forNamespace("jgr"))
                        .log("From ${routeId}");
            }
        });

        return context;
    }

    private void awaitOneActiveRoute() {
        // the policy starts and stops the routes on its own thread, shortly after the leadership change
        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, countActiveFromEndpoints(contexts, routeNames)));
    }

    private int countActiveFromEndpoints(List<CamelContext> contexts, List<String> routeNames) {
        int result = 0;
        if (contexts.size() != routeNames.size()) {
            throw new UnsupportedOperationException(
                    "CamelContext list and Route ids list must have the same number of elements!");
        }
        for (int i = 0; i < contexts.size(); i++) {
            ServiceStatus status = contexts.get(i).getRouteController().getRouteStatus(routeNames.get(i));
            if (ServiceStatus.Starting.equals(status) || ServiceStatus.Started.equals(status)) {
                result++;
            }
        }
        return result;
    }
}
