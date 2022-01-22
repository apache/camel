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

import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

public class MDCErrorHandlerTest extends ContextTestSupport {

    @Test
    public void testMDC() throws Exception {
        template.sendBody("direct:start", "Hello World");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setUseMDCLogging(true);
                context.setUseBreadcrumb(true);

                errorHandler(deadLetterChannel("direct:dead").onExceptionOccurred(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Map<String, String> m = MDC.getCopyOfContextMap();
                        Assertions.assertEquals(5, m.size());
                        Assertions.assertEquals(exchange.getMessage().getHeader(Exchange.BREADCRUMB_ID),
                                m.get("camel.breadcrumbId"));
                        Assertions.assertEquals("start", m.get("camel.routeId"));
                    }
                }));

                from("direct:start").routeId("start")
                        .to("log:before")
                        .throwException(new IllegalArgumentException("Forced"));

                from("direct:dead").routeId("dead")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Map<String, String> m = MDC.getCopyOfContextMap();
                                Assertions.assertEquals(5, m.size());
                                Assertions.assertEquals(exchange.getMessage().getHeader(Exchange.BREADCRUMB_ID),
                                        m.get("camel.breadcrumbId"));
                                Assertions.assertEquals("dead", m.get("camel.routeId"));
                            }
                        })
                        .to("log:dead");
            }
        };
    }
}
