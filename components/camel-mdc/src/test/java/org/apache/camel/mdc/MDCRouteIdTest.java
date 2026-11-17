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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.LogListener;
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * camel.routeId is the id of the route that runs the processor, also when a route calls another route.
 */
public class MDCRouteIdTest extends ExchangeTestSupport {

    private final List<String> logged = new CopyOnWriteArrayList<>();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(mdcSvc, context);
        mdcSvc.init(context);
        // added after the MDC log listener, so it sees the MDC set for the log
        context.getCamelContextExtension().addLogListener(new LogListener() {
            @Override
            public String onLog(Exchange exchange, CamelLogger camelLogger, String message) {
                logged.add(message + "=" + MDC.get(MDCService.MDC_ROUTE_ID));
                return message;
            }
        });
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:a").routeId("a")
                        .process(e -> e.getMessage().setHeader("before", MDC.get(MDCService.MDC_ROUTE_ID)))
                        .log("in a")
                        .to("direct:b")
                        .process(e -> e.getMessage().setHeader("after", MDC.get(MDCService.MDC_ROUTE_ID)))
                        .log("back in a")
                        .to("mock:a");

                from("direct:b").routeId("b")
                        .process(e -> e.getMessage().setHeader("inB", MDC.get(MDCService.MDC_ROUTE_ID)))
                        .log("in b")
                        .to("mock:b");
            }
        };
    }

    @Test
    void testRouteIdIsTheRouteBeingRun() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);
        a.expectedHeaderReceived("before", "a");
        a.expectedHeaderReceived("inB", "b");
        a.expectedHeaderReceived("after", "a");
        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(1);
        b.expectedHeaderReceived("inB", "b");

        template.sendBody("direct:a", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(List.of("in a=a", "in b=b", "back in a=a"), logged);
    }

    @Test
    void testRouteIdOfASingleRoute() throws Exception {
        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(1);
        b.expectedHeaderReceived("inB", "b");

        template.sendBody("direct:b", "Hello");

        MockEndpoint.assertIsSatisfied(context);
        assertEquals(List.of("in b=b"), logged);
    }
}
