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
package org.apache.camel.component.resilience4j;

import java.net.ConnectException;
import java.util.List;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedProcessorMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The steps of the fallback are processors of the route, with their own statistics: they are the ones that run while
 * the protected service is down.
 */
public class ResilienceFallbackManagementTest extends CamelTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testTheFallbackStepsAreProcessorsOfTheRoute() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("from the fallback", "from the fallback");

        template.sendBody("direct:check", "CAMEL-MUG");
        template.sendBody("direct:check", "CAMEL-MUG");

        MockEndpoint.assertIsSatisfied(context);

        ManagedCamelContext mcc = context.getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        ManagedRouteMBean route = mcc.getManagedRoute("check");
        assertTrue(route.processorIds().containsAll(List.of("supplier", "fallbackChoice", "fallbackBody", "otherBody")),
                "the processors of the route: " + route.processorIds());

        ManagedProcessorMBean body = mcc.getManagedProcessor("fallbackBody");
        assertEquals("check", body.getRouteId());
        assertEquals(2, body.getExchangesTotal());
        assertEquals(2, mcc.getManagedProcessor("fallbackChoice").getExchangesTotal());
        // the otherwise branch did not run
        assertEquals(0, mcc.getManagedProcessor("otherBody").getExchangesTotal());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:check").routeId("check")
                        .circuitBreaker().id("breaker")
                            .throwException(new ConnectException("supplier unreachable")).id("supplier")
                        .onFallback()
                            .choice().id("fallbackChoice")
                                .when(body().isEqualTo("CAMEL-MUG"))
                                    .setBody(constant("from the fallback")).id("fallbackBody")
                                .otherwise()
                                    .setBody(constant("otherwise")).id("otherBody")
                            .end()
                        .end()
                        .to("mock:result");
            }
        };
    }
}
