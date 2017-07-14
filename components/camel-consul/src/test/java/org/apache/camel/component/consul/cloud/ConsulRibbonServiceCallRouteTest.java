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

package org.apache.camel.component.consul.cloud;

import java.util.ArrayList;
import java.util.List;

import com.orbitz.consul.AgentClient;
import com.orbitz.consul.model.agent.ImmutableRegistration;
import com.orbitz.consul.model.agent.Registration;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.ConsulTestSupport;
import org.junit.Test;

public class ConsulRibbonServiceCallRouteTest extends ConsulTestSupport {
    private static final String SERVICE_NAME = "http-service";
    private static final int SERVICE_COUNT = 5;
    private static final int SERVICE_PORT_BASE = 8080;

    private AgentClient client;
    private List<Registration> registrations;
    private List<String> expectedBodies;

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

    @Override
    protected void doPreSetup() throws Exception {
        client = getConsul().agentClient();

        registrations = new ArrayList<>(SERVICE_COUNT);
        expectedBodies = new ArrayList<>(SERVICE_COUNT);

        for (int i = 0; i < SERVICE_COUNT; i++) {
            Registration r = ImmutableRegistration.builder()
                .id("service-" + i)
                .name(SERVICE_NAME)
                .address("127.0.0.1")
                .port(SERVICE_PORT_BASE + i)
                .build();

            client.register(r);

            registrations.add(r);
            expectedBodies.add("ping on " + r.getPort().get());
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();


        registrations.forEach(r -> client.deregister(r.getId()));
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(SERVICE_COUNT);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder(expectedBodies);

        registrations.forEach(r -> template.sendBody("direct:start", "ping"));

        assertMockEndpointsSatisfied();
    }

    // *************************************************************************
    // Route
    // *************************************************************************

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .serviceCall()
                        .name(SERVICE_NAME)
                        .component("jetty")
                        .consulServiceDiscovery()
                        .endParent()
                    .to("log:org.apache.camel.component.consul.processor.service?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");

                registrations.forEach(r ->
                    fromF("jetty:http://%s:%d", r.getAddress().get(), r.getPort().get())
                        .transform().simple("${in.body} on " + r.getPort().get())
                );
            }
        };
    }
}
