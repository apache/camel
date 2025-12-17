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

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MDCAllHeadersTest extends ExchangeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MDCSelectedPropertiesTest.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        mdcSvc.setCustomHeaders("*");
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(mdcSvc, context);
        mdcSvc.init(context);
        return context;
    }

    @Test
    void testRouteSingleRequest() throws IOException {
        template.request("direct:start", null);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .setHeader("head1", simple("Header1"))
                        .setHeader("head2", simple("Header2"))
                        .process(exchange -> {
                            LOG.info("A process");
                            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
                            assertEquals("Header1", MDC.get("head1"));
                            assertEquals("Header2", MDC.get("head2"));
                        })
                        .to("log:info");
            }
        };
    }

}
