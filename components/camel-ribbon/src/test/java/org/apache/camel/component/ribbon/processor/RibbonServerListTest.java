/**
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
package org.apache.camel.component.ribbon.processor;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import com.netflix.loadbalancer.LoadBalancerBuilder;
import com.netflix.loadbalancer.RoundRobinRule;
import com.netflix.loadbalancer.Server;
import com.netflix.loadbalancer.ServerList;
import com.netflix.loadbalancer.ZoneAwareLoadBalancer;
import org.junit.Test;

public class RibbonServerListTest extends TestCase {

    @Test
    public void testFixedServerList() throws Exception {
        List<RibbonServer> servers = new ArrayList<>();
        servers.add(new RibbonServer("localhost", 9090));
        servers.add(new RibbonServer("localhost", 9091));

        ServerList<RibbonServer> list = new RibbonServiceCallStaticServerListStrategy(servers);

        ZoneAwareLoadBalancer<RibbonServer> lb = LoadBalancerBuilder.<RibbonServer>newBuilder()
            .withDynamicServerList(list)
            .withRule(new RoundRobinRule()).buildDynamicServerListLoadBalancer();

        Server server = lb.chooseServer();
        assertEquals("localhost", server.getHost());
        assertEquals(9091, server.getPort());

        server = lb.chooseServer();
        assertEquals("localhost", server.getHost());
        assertEquals(9090, server.getPort());
    }
}
