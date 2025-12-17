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

public class MDCAllPropertiesTest extends ExchangeTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        mdcSvc.setCustomProperties("*");
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
            // Assert MDC values
            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
            // Assert exchange properties are correctly set
            assertEquals("Property1", exchange.getProperty("prop1"));
            assertEquals("Property2", exchange.getProperty("prop2"));
        });

        // Trigger the route
        template.request("direct:start", null);

        // Wait for assertions to pass
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
                        .setProperty("prop1", constant("Property1"))
                        .setProperty("prop2", constant("Property2"))
                        .to("mock:assertMdc")
                        .to("log:info");
            }
        };
    }

}
