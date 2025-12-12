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
import org.apache.camel.test.junit6.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MDCAllPropertiesTest extends ExchangeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MDCSelectedPropertiesTest.class);

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
    void testRouteSingleRequest() throws IOException {
        template.request("direct:start", null);
        // We should get no MDC after the route has been executed
        assertEquals(0, MDC.getCopyOfContextMap().size());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .routeId("start")
                        .log("A message")
                        .setProperty("prop1", simple("Property1"))
                        .setProperty("prop2", simple("Property2"))
                        .process(exchange -> {
                            LOG.info("A process");
                            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
                            assertEquals("Property1", MDC.get("prop1"));
                            assertEquals("Property2", MDC.get("prop2"));
                        })
                        .to("log:info");
            }
        };
    }

}
