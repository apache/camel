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
package org.apache.camel.component.consul;

import java.util.Map;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.consul.endpoint.ConsulAgentActions;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.kiwiproject.consul.model.health.ImmutableService;
import org.kiwiproject.consul.model.health.Service;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

import static org.apache.camel.builder.Builder.constant;

public class MockAgentTest extends CamelTestSupport {

    @Test
    public void testMockAgent() throws Exception {
        MockEndpoint mockConsulAgent = getMockEndpoint("mock:consul:agent");

        AdviceWith.adviceWith(context, "servicesRoute", a -> {
            a.mockEndpointsAndSkip("consul:agent*");
        });
        mockConsulAgent.returnReplyBody(constant(ImmutableMap.of("foo-1", ImmutableService.builder()
                .id("foo-1")
                .service("foo")
                .address("localhost")
                .port(80)
                .build())));

        @SuppressWarnings("unchecked")
        Map<String, Service> result = fluentTemplate.to("direct:start").request(Map.class);
        Assertions.assertEquals(1, result.size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("servicesRoute").to("consul:agent?action=" + ConsulAgentActions.SERVICES);
            }
        };
    }
}
