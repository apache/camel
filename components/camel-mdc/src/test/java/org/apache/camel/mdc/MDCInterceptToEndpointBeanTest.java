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
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class MDCInterceptToEndpointBeanTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger("MyRoute.myBean");

    private Processor myBean = new Processor() {
        @Override
        public void process(Exchange exchange) throws Exception {
            LOG.info("MDC Values present");

            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
        }
    };

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MDCService mdcSvc = new MDCService();
        CamelContext context = super.createCamelContext();
        CamelContextAware.trySetCamelContext(mdcSvc, context);
        mdcSvc.init(context);
        return context;
    }

    @Test
    public void testMDC() throws Exception { // NOSONAR
        template.sendBody("direct:start", "Hello World");
        // NOTE: asserts are done directly in process to make it easier understand the test
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                context.getRegistry().bind("myMapper", myBean);

                interceptSendToEndpoint("bean*").to("direct:beforeSend");

                from("direct:start")
                        .log(LoggingLevel.INFO, "MyRoute.logBefore", "MDC Values present")
                        .process(e -> {
                            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
                        })
                        .to("bean:myMapper")
                        .log(LoggingLevel.INFO, "MyRoute.logAfter", "MDC Values present")
                        .process(e -> {
                            assertNotNull(MDC.get(MDCService.MDC_MESSAGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_EXCHANGE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_ROUTE_ID));
                            assertNotNull(MDC.get(MDCService.MDC_CAMEL_CONTEXT_ID));
                        });

                from("direct:beforeSend")
                        .log(LoggingLevel.INFO, "MyRoute.beforeSend", "MDC Values present");

            }
        };
    }
}
