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
package org.apache.camel.processor;

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.saga.InMemorySagaService;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SagaSharedServiceRouteStopTest extends ContextTestSupport {

    @Test
    public void testStoppingOneSagaRouteKeepsSharedServiceRunning() throws Exception {
        InMemorySagaService sagaService = context.hasService(InMemorySagaService.class);

        assertTrue(ServiceHelper.isStarted(sagaService), "Saga service should be running");

        context.getRouteController().stopRoute("route-a");

        assertTrue(ServiceHelper.isStarted(sagaService),
                "Saga service should still be running after stopping one route");
    }

    @Test
    public void testCompensationOnOtherRouteStillWorksAfterStoppingOneSagaRoute() throws Exception {
        MockEndpoint compensated = getMockEndpoint("mock:compensate-b");
        compensated.expectedMessageCount(1);

        context.getRouteController().stopRoute("route-a");

        try {
            template.sendBody("direct:route-b", "trigger-fail");
        } catch (Exception e) {
            // expected — the saga processor throws after compensation is triggered
        }

        MockEndpoint.assertIsSatisfied(context, 10, TimeUnit.SECONDS);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                InMemorySagaService sagaService = new InMemorySagaService();
                context.addService(sagaService);

                from("direct:route-a").routeId("route-a")
                        .saga().compensation("direct:compensate-a")
                        .log("Route A");

                from("direct:compensate-a")
                        .log("Compensating A");

                from("direct:route-b").routeId("route-b")
                        .saga().compensation("direct:compensate-b")
                        .process(e -> {
                            throw new RuntimeException("forced failure");
                        });

                from("direct:compensate-b")
                        .to("mock:compensate-b");
            }
        };
    }
}
