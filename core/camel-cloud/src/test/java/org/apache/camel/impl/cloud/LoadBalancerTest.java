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
package org.apache.camel.impl.cloud;

import java.util.Collections;
import java.util.concurrent.RejectedExecutionException;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LoadBalancerTest {

    private static StaticServiceDiscovery serviceDiscovery = new StaticServiceDiscovery();

    @BeforeAll
    public static void setUp() {
        serviceDiscovery.addServer("no-name@127.0.0.1:2001");
        serviceDiscovery.addServer("no-name@127.0.0.1:2002");
        serviceDiscovery.addServer("no-name@127.0.0.1:1001");
        serviceDiscovery.addServer("no-name@127.0.0.1:1002");
        serviceDiscovery.addServer(
                new DefaultServiceDefinition("no-name", "127.0.0.1", 1003, Collections.singletonMap("supports", "foo,bar")));
    }

    @Test
    public void testLoadBalancer() throws Exception {
        DefaultServiceLoadBalancer loadBalancer = new DefaultServiceLoadBalancer();
        CamelContext camelContext = new DefaultCamelContext();
        loadBalancer.setCamelContext(camelContext);
        loadBalancer.setServiceDiscovery(serviceDiscovery);
        loadBalancer
                .setServiceFilter(
                        (exchange, services) -> services.stream().filter(s -> s.getPort() < 2000).toList());
        loadBalancer.setServiceChooser(new RoundRobinServiceChooser());
        Exchange exchange = new DefaultExchange(camelContext);
        loadBalancer.process(exchange, "no-name", service -> {
            assertEquals(1001, service.getPort());
            return false;
        });
        loadBalancer.process(exchange, "no-name", service -> {
            assertEquals(1002, service.getPort());
            return false;
        });
    }

    @Test
    public void testLoadBalancerWithContentBasedServiceFilter() throws Exception {
        DefaultServiceLoadBalancer loadBalancer = new DefaultServiceLoadBalancer();
        loadBalancer.setCamelContext(new DefaultCamelContext());
        loadBalancer.setServiceDiscovery(serviceDiscovery);
        loadBalancer.setServiceFilter(
                (exchange, services) -> services.stream()
                        .filter(serviceDefinition -> ofNullable(serviceDefinition.getMetadata()
                                .get("supports"))
                                .orElse("")
                                .contains(exchange.getProperty("needs", String.class)))
                        .toList());
        loadBalancer.setServiceChooser(new RoundRobinServiceChooser());
        Exchange exchange = new DefaultExchange(new DefaultCamelContext());
        exchange.setProperty("needs", "foo");
        loadBalancer.process(exchange, "no-name", service -> {
            assertEquals(1003, service.getPort());
            return false;
        });
    }

    @Test
    public void testNoActiveServices() throws Exception {
        DefaultServiceLoadBalancer loadBalancer = new DefaultServiceLoadBalancer();
        DefaultCamelContext camelContext = new DefaultCamelContext();
        loadBalancer.setCamelContext(camelContext);
        loadBalancer.setServiceDiscovery(serviceDiscovery);
        loadBalancer
                .setServiceFilter(
                        (exchange, services) -> services.stream().filter(s -> s.getPort() < 1000).toList());
        loadBalancer.setServiceChooser(new RoundRobinServiceChooser());
        assertThrows(RejectedExecutionException.class, () -> {
            loadBalancer.process(new DefaultExchange(camelContext), "no-name", service -> false);
        });
    }
}
