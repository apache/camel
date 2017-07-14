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
package org.apache.camel.impl.cloud;

import java.util.stream.Collectors;

import org.apache.camel.ContextTestSupport;
import org.junit.Test;

public class LoadBalancerTest extends ContextTestSupport {
    @Test
    public void testLoadBalancer() throws Exception {
        StaticServiceDiscovery serviceDiscovery = new StaticServiceDiscovery();
        serviceDiscovery.addServer("no-name", "127.0.0.1", 2001);
        serviceDiscovery.addServer("no-name", "127.0.0.1", 2002);
        serviceDiscovery.addServer("no-name", "127.0.0.1", 1001);
        serviceDiscovery.addServer("no-name", "127.0.0.1", 1002);

        DefaultServiceLoadBalancer loadBalancer = new DefaultServiceLoadBalancer();
        loadBalancer.setCamelContext(context);
        loadBalancer.setServiceDiscovery(serviceDiscovery);
        loadBalancer.setServiceFilter(services -> services.stream().filter(s -> s.getPort() < 2000).collect(Collectors.toList()));
        loadBalancer.setServiceChooser(new RoundRobinServiceChooser());
        loadBalancer.process("no-name", service -> {
            assertEquals(1001, service.getPort());
            return false;
        });
        loadBalancer.process("no-name", service -> {
            assertEquals(1002, service.getPort());
            return false;
        });
    }
}
