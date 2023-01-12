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
package org.apache.camel.component.consul.cloud;

import java.util.UUID;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.ConsulTestSupport;
import org.apache.camel.impl.cloud.ServiceRegistrationRoutePolicy;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ConsulServiceCallWithRegistrationIT extends ConsulTestSupport {
    private static final String SERVICE_HOST = "localhost";

    // ******************************
    // Setup / tear down
    // ******************************

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();

        ConsulServiceRegistry registry = new ConsulServiceRegistry();
        registry.setId(context.getUuidGenerator().generateUuid());
        registry.setCamelContext(context());
        registry.setUrl(service.getConsulUrl());
        registry.setServiceHost(SERVICE_HOST);
        registry.setOverrideServiceHost(true);

        context.addService(registry, true, false);

        return context;
    }

    // ******************************
    // Test
    // ******************************

    @Test
    public void testServiceCallSuccess() throws Exception {
        final int port = AvailablePortFinder.getNextAvailable();
        final String serviceId = UUID.randomUUID().toString();
        final String serviceName = UUID.randomUUID().toString();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // context path is derived from the jetty endpoint.
                from("direct:start")
                        .serviceCall()
                            .name(serviceName).component("undertow").defaultLoadBalancer()
                            .consulServiceDiscovery().url(service.getConsulUrl()).end()
                        .end()
                        .log("${body}");

                fromF("undertow:http://%s:%d/service/path", SERVICE_HOST, port).routeId(serviceId).routeGroup(serviceName)
                        .routePolicy(new ServiceRegistrationRoutePolicy())
                        .transform().simple("${in.body} on " + port);
            }
        });

        context.start();

        assertEquals("ping on " + port, template.requestBody("direct:start", "ping", String.class));
    }

    @Test
    public void testServiceCallFailure() throws Exception {
        final int port = AvailablePortFinder.getNextAvailable();
        final String serviceId = UUID.randomUUID().toString();
        final String serviceName = UUID.randomUUID().toString();

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // context path is hard coded so it should fail as it not exposed
                // by jetty
                from("direct:start")
                        .serviceCall()
                            .name(serviceName + "/bad/path").component("http")
                            .defaultLoadBalancer().consulServiceDiscovery().url(service.getConsulUrl()).end()
                        .end()
                        .log("${body}");

                fromF("undertow:http://%s:%d/service/path", SERVICE_HOST, port).routeId(serviceId).routeGroup(serviceName)
                        .routePolicy(new ServiceRegistrationRoutePolicy())
                        .transform().simple("${in.body} on " + port);
            }
        });

        context.start();

        assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:start", "ping", String.class));
    }
}
