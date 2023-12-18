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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class WireTapMDCTest extends ContextTestSupport {

    @Test
    public void testMdcPreserved() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(2);

        template.sendBody("seda:a", "A");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable MDC and breadcrumb
                context.setUseMDCLogging(true);
                context.setUseBreadcrumb(true);
                context.setMDCLoggingKeysPattern("custom*,my*");

                MdcCheckerProcessor checker = new MdcCheckerProcessor("route-a", "World", "MyValue");
                MdcCheckerProcessor checker2 = new MdcCheckerProcessor("route-b", "Moon", "MyValue2");

                from("seda:a").routeId("route-a")
                        .process(e -> {
                            MDC.put("custom.hello", "World");
                            MDC.put("foo", "Bar");
                            MDC.put("myKey", "MyValue");
                        })
                        .process(checker)
                        .to("log:a")
                        .wireTap("direct:b")
                        .process(checker)
                        .to("mock:end");

                from("direct:b").routeId("route-b")
                        .process(e -> {
                            MDC.put("custom.hello", "Moon");
                            MDC.put("foo", "Bar2");
                            MDC.put("myKey", "MyValue2");
                        })
                        .process(checker2)
                        .to("log:b")
                        .to("mock:end");
            }
        };
    }

    /**
     * Stores values from the first invocation to compare them with the second invocation later.
     */
    private static class MdcCheckerProcessor implements Processor {

        private String exchangeId;
        private String messageId;
        private String breadcrumbId;
        private String contextId;
        private Long threadId;
        private String foo;

        private String expected1;
        private String expected2;
        private String expected3;

        public MdcCheckerProcessor(String expected1, String expected2, String expected3) {
            this.expected1 = expected1;
            this.expected2 = expected2;
            this.expected3 = expected3;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            // custom is propagated as its pattern matches
            assertEquals(expected2, MDC.get("custom.hello"));
            assertEquals(expected3, MDC.get("myKey"));

            if (foo != null) {
                // foo propagated because its the same thread
                assertEquals(foo, MDC.get("foo"));
            } else {
                foo = MDC.get("foo");
            }

            if (threadId != null) {
                long currId = Thread.currentThread().getId();
                assertEquals(threadId, (Object) currId);
            } else {
                threadId = Thread.currentThread().getId();
            }

            if (expected1 != null) {
                assertEquals(expected1, MDC.get("camel.routeId"));
            }

            if (exchangeId != null) {
                assertEquals(exchangeId, MDC.get("camel.exchangeId"));
            } else {
                exchangeId = MDC.get("camel.exchangeId");
                assertTrue(exchangeId != null && exchangeId.length() > 0);
            }

            if (messageId != null) {
                assertEquals(messageId, MDC.get("camel.messageId"));
            } else {
                messageId = MDC.get("camel.messageId");
                assertTrue(messageId != null && messageId.length() > 0);
            }

            if (breadcrumbId != null) {
                assertEquals(breadcrumbId, MDC.get("camel.breadcrumbId"));
            } else {
                breadcrumbId = MDC.get("camel.breadcrumbId");
                assertTrue(breadcrumbId != null && breadcrumbId.length() > 0);
            }

            if (contextId != null) {
                assertEquals(contextId, MDC.get("camel.contextId"));
            } else {
                contextId = MDC.get("camel.contextId");
                assertTrue(contextId != null && contextId.length() > 0);
            }
        }
    }

}
