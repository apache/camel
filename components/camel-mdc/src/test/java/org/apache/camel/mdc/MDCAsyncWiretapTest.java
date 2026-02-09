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
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.ExchangeTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class MDCAsyncWiretapTest extends ExchangeTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MDCAsyncWiretapTest.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        mdcSvc.setCustomHeaders("*");
        mdcSvc.setCustomProperties("*");
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(mdcSvc, context);
        mdcSvc.init(context);
        return context;
    }

    @Test
    void testRouteSingleRequest() throws IOException, InterruptedException {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);
        mock.setAssertPeriod(5000);
        context.createProducerTemplate().sendBody("direct:start", null);
        mock.assertIsSatisfied(1000);

        // NOTE: more assertions directly in process as it was simpler to verify the condition while executing the async process.
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .setBody()
                        .simple("start")
                        .log("start: ${exchangeId}")
                        .to("direct:a")
                        // MUST be any async component
                        .wireTap("direct:b");

                from("direct:a")
                        .setProperty("prop1", simple("Property1"))
                        .setHeader("head", simple("Header1"))
                        .process(exchange -> {
                            LOG.info("Direct:a process");
                            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
                            assertNotNull(MDC.get(MDCService.MDC_CAMEL_THREAD_ID));
                            assertEquals("Header1", MDC.get("head"));
                            assertEquals("Property1", MDC.get("prop1"));
                            assertNull(MDC.get("prop2"));
                            // We store the threadId of this execution in a property
                            // as we will use it to assert the thread is different in the direct:b execution
                            exchange.setProperty("thread-a", MDC.get(MDCService.MDC_CAMEL_THREAD_ID));
                        })
                        .setBody()
                        .simple("Direct a")
                        .log("directa: ${exchangeId}");

                from("direct:b")
                        .setProperty("prop2", simple("Property2"))
                        .setHeader("head", simple("Header2"))
                        .process(exchange -> {
                            LOG.info("Direct:b process");
                            // Make sure this execution is spanned in a different thread
                            // but still the context (in this case the properties) is propagated
                            assertNotEquals(exchange.getProperty("thread-a"), MDC.get(MDCService.MDC_CAMEL_THREAD_ID));
                            assertEquals("Header2", MDC.get("head"));
                            assertEquals("Property1", MDC.get("prop1"));
                            assertEquals("Property2", MDC.get("prop2"));
                        })
                        .delay(2000)
                        .setBody()
                        .simple("Direct b")
                        .log("directb: ${exchangeId}")
                        .to("mock:end");
            }
        };
    }

}
