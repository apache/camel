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
package org.apache.camel.component.telegram;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.telegram.util.TelegramMockRoutes;
import org.apache.camel.component.telegram.util.TelegramTestSupport;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.health.HealthCheckRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TelegramConsumerHealthCheckErrorTest extends TelegramTestSupport {

    @EndpointInject("mock:telegram")
    private MockEndpoint endpoint;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        // enabling routes health check is a bit cumbersome via low-level Java code
        HealthCheckRegistry hcr = context.getExtension(HealthCheckRegistry.class);
        HealthCheckRepository repo = hcr.getRepository("routes").orElse((HealthCheckRepository) hcr.resolveById("routes"));
        repo.setEnabled(true);
        hcr.register(repo);

        return context;
    }

    @Test
    public void testReceptionOfTwoMessages() throws Exception {
        HealthCheckRegistry hcr = context.getExtension(HealthCheckRegistry.class);
        HealthCheckRepository repo = hcr.getRepository("routes").get();

        // should not be DOWN from the start
        boolean down = repo.stream().anyMatch(h -> h.call().getState().equals(HealthCheck.State.DOWN));
        Assertions.assertFalse(down, "None health-check should be DOWN");

        // wait until HC is DOWN
        Awaitility.waitAtMost(5, TimeUnit.SECONDS).until(
                () -> repo.stream().anyMatch(h -> h.call().getState().equals(HealthCheck.State.DOWN)));
    }

    @Override
    protected RoutesBuilder[] createRouteBuilders() throws Exception {
        return new RoutesBuilder[] {
                getMockRoutes(),
                new RouteBuilder() {
                    @Override
                    public void configure() throws Exception {
                        from("telegram:bots?authorizationToken=mock-token")
                                .convertBodyTo(String.class)
                                .to("mock:telegram");
                    }
                } };
    }

    @Override
    protected TelegramMockRoutes createMockRoutes() {
        return new TelegramMockRoutes(port)
                .addErrorEndpoint(
                        "getUpdates",
                        "GET",
                        401);
    }
}
