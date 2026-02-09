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
package org.apache.camel.mdc;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MDCSelectedHeadersTest extends ExchangeTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        mdcSvc.setCustomHeaders("head1,head2,head3");
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(mdcSvc, context);
        mdcSvc.init(context);
        return context;
    }

    @Test
    void testRouteSingleRequest() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:assertMdc");
        mock.expectedMessageCount(1);
        mock.whenAnyExchangeReceived(exchange -> {
            // Required MDC entries
            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID), "MDC_MESSAGE_ID should be set");
            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID), "MDC_EXCHANGE_ID should be set");
            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID), "MDC_ROUTE_ID should be set");
            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID), "MDC_CAMEL_CONTEXT_ID should be set");

            // Headers propagated to MDC
            assertEquals("Header1", MDC.get("head1"));
            assertEquals("Header2", MDC.get("head2"));

            // Headers that were not set remain null
            assertNull(MDC.get("head3"));
            assertNull(MDC.get("head4")); // note: intentionally not included
        });

        // Trigger the route
        template.request("direct:start", null);

        // Wait for expectations
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .setHeader("head1", constant("Header1"))
                        .setHeader("head2", constant("Header2"))
                        // head3 is missing on purpose
                        .setHeader("head4", constant("Header4"))
                        .to("mock:assertMdc")
                        .to("log:info");
            }
        };
    }

}
