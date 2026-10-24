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

import java.lang.reflect.Field;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.component.master.MasterConsumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.awaitility.Awaitility;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JGroupsRaftMasterTest extends JGroupsRaftClusterAbstractTest {

    @Test
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
        Awaitility.await().untilAsserted(() -> assertEquals(1, countActiveFromEndpoints(contexts, routeNames)));

        contextA.stop();
        handleA.channel().close();
        waitForLeader(50, handleB, handleC);
        Awaitility.await().untilAsserted(() -> assertEquals(1, countActiveFromEndpoints(contexts, routeNames)));

        contextB.stop();
        handleB.channel().close();
        waitForViewSize(handleC.channel(), 1, 30);
        // Create a completely new channel, handle and context for A
        handleA = createHandle("A", new NopStateMachine());
        contextA = createContext("A", handleA);
        contexts.set(0, contextA);
        contextA.start();
        waitForLeader(50, handleA, handleC);
        Awaitility.await().untilAsserted(() -> assertEquals(1, countActiveFromEndpoints(contexts, routeNames)));
    }

    private CamelContext createContext(String id, RaftHandle rh) throws Exception {
        JGroupsRaftClusterService service = new JGroupsRaftClusterService();
        service.setId(id);
        service.setRaftId(id);
        service.setRaftHandle(rh);
        service.setJgroupsClusterName("JGroupsRaftMasterTest");

        DefaultCamelContext context = new DefaultCamelContext();
        context.disableJMX();
        context.getCamelContextExtension().setName("context-" + id);
        context.addService(service);
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("master:jgr:timer:master?delay=1000&period=1000")
                        .routeId("route-" + id)
                        .log("From ${routeId}");
            }
        });

        return context;
    }

    private int countActiveFromEndpoints(List<CamelContext> contexts, List<String> routeNames)
            throws NoSuchFieldException, IllegalAccessException {
        int result = 0;
        if (contexts.size() != routeNames.size()) {
            throw new UnsupportedOperationException(
                    "CamelContext list and Route ids list must have the same number of elements!");
        }
        for (int i = 0; i < contexts.size(); i++) {
            Route route = contexts.get(i).getRoute(routeNames.get(i));
            if (route != null) {
                MasterConsumer mc = (MasterConsumer) route.getConsumer();
                Field delegatedConsumer = mc.getClass().getDeclaredField("delegatedConsumer");
                delegatedConsumer.setAccessible(true);
                if (delegatedConsumer.get(mc) != null) {
                    result++;
                }
            }
        }
        return result;
    }

}
