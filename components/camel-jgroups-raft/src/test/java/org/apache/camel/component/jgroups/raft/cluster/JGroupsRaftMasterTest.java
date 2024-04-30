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
import java.util.ArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.Route;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jgroups.raft.utils.NopStateMachine;
import org.apache.camel.component.master.MasterConsumer;
import org.apache.camel.impl.DefaultCamelContext;
import org.jgroups.JChannel;
import org.jgroups.raft.RaftHandle;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JGroupsRaftMasterTest extends JGroupsRaftClusterAbstractTest {
    private static final Logger LOG = LoggerFactory.getLogger(JGroupsRaftMasterTest.class);

    private ArrayList<CamelContext> lcc = new ArrayList<>();
    private ArrayList<String> rn = new ArrayList<>();

    @Test
    public void test() throws Exception {
        JChannel chA = new JChannel("raftABC.xml").name("A");
        RaftHandle handleA = new RaftHandle(chA, new NopStateMachine()).raftId("A");
        CamelContext contextA = createContext("A", handleA);

        JChannel chB = new JChannel("raftABC.xml").name("B");
        RaftHandle handleB = new RaftHandle(chB, new NopStateMachine()).raftId("B");
        CamelContext contextB = createContext("B", handleB);

        JChannel chC = new JChannel("raftABC.xml").name("C");
        RaftHandle handleC = new RaftHandle(chC, new NopStateMachine()).raftId("C");
        CamelContext contextC = createContext("C", handleC);

        lcc.add(contextA);
        rn.add("route-A");
        lcc.add(contextB);
        rn.add("route-B");
        lcc.add(contextC);
        rn.add("route-C");

        contextA.start();
        contextB.start();
        contextC.start();
        waitForLeader(50, handleA, handleB, handleC);
        assertEquals(1, countActiveFromEndpoints(lcc, rn));

        contextA.stop();
        waitForLeader(50, handleA, handleB, handleC);
        assertEquals(1, countActiveFromEndpoints(lcc, rn));

        contextB.stop();
        JGroupsRaftClusterService service = new JGroupsRaftClusterService();
        service.setId("A");
        service.setRaftId("A");
        service.setRaftHandle(handleA);
        service.setJgroupsClusterName("JGroupsRaftMasterTest");
        contextA.addService(service);
        contextA.start();
        waitForLeader(50, handleA, handleB, handleC);
        assertEquals(1, countActiveFromEndpoints(lcc, rn));
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

    private int countActiveFromEndpoints(ArrayList<CamelContext> lcc, ArrayList<String> rn)
            throws NoSuchFieldException, IllegalAccessException {
        int result = 0;
        if (lcc.size() != rn.size()) {
            throw new UnsupportedOperationException(
                    "CamelContext list and Route ids list must have the same number of elements!");
        }
        for (int i = 0; i < lcc.size(); i++) {
            Route route = lcc.get(i).getRoute(rn.get(i));
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
