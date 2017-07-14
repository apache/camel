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

package org.apache.camel.component.etcd.cloud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.EtcdClient;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.component.etcd.EtcdTestSupport;
import org.junit.Test;

public class EtcdServiceCallRouteTest extends EtcdTestSupport {
    private static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();
    private static final String SERVICE_NAME = "http-service";
    private static final int SERVICE_COUNT = 5;
    private static final int SERVICE_PORT_BASE = 8080;

    private EtcdClient client;
    private List<Map<String, Object>> servers;
    private List<String> expectedBodies;

    // *************************************************************************
    // Setup / tear down
    // *************************************************************************

    @Override
    protected void doPreSetup() throws Exception {
        client = getClient();

        servers = new ArrayList<>(SERVICE_COUNT);
        expectedBodies = new ArrayList<>(SERVICE_COUNT);

        for (int i = 0; i < SERVICE_COUNT; i++) {
            Map<String, Object> server = new HashMap<>();
            server.put("name", SERVICE_NAME);
            server.put("address", "127.0.0.1");
            server.put("port", SERVICE_PORT_BASE + i);

            client.put("/services/" + "service-" + i, MAPPER.writeValueAsString(server)).send().get();

            servers.add(Collections.unmodifiableMap(server));
            expectedBodies.add("ping on " + (SERVICE_PORT_BASE + i));
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        client.deleteDir("/services/").recursive().send().get();
    }

    // *************************************************************************
    // Test
    // *************************************************************************

    @Test
    public void testServiceCall() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(SERVICE_COUNT);
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder(expectedBodies);

        servers.forEach(s -> template.sendBody("direct:start", "ping"));

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
                        .etcdServiceDiscovery()
                            .type("on-demand")
                        .endParent()
                    .to("log:org.apache.camel.component.etcd.processor.service?level=INFO&showAll=true&multiline=true")
                    .to("mock:result");

                servers.forEach(s ->
                    fromF("jetty:http://%s:%d", s.get("address"), s.get("port"))
                        .transform().simple("${in.body} on " + s.get("port"))
                );
            }
        };
    }
}
