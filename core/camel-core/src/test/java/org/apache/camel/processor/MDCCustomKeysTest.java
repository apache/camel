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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MDCCustomKeysTest extends ContextTestSupport {

    private MdcCheckerProcessor checker1 = new MdcCheckerProcessor("N/A");
    private MdcCheckerProcessor checker2 = new MdcCheckerProcessor("World");

    @Test
    public void testMdcPreserved() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("A");

        checker1.reset();
        checker2.reset();
        template.sendBody("direct:a", "A");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testMdcPreservedTwo() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedBodiesReceived("A", "B");

        checker1.reset();
        checker2.reset();
        template.sendBody("direct:a", "A");

        checker1.reset();
        checker2.reset();
        template.sendBody("direct:a", "B");

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

                from("direct:a").process(e -> {

                    // custom should be empty
                    String hello = MDC.get("custom.hello");
                    assertNull(hello);

                    // custom is propagated
                    MDC.put("custom.hello", "N/A");
                    // foo is propagated due we use the same thread
                    MDC.put("foo", "Bar");
                    // myKey is propagated
                    MDC.put("myKey", "Baz");
                }).process(checker1)
                        .to("log:foo")
                        .process(e -> {
                            MDC.put("custom.hello", "World");
                        })
                        .process(checker2)
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

        private final String expected;

        public MdcCheckerProcessor(String expected) {
            this.expected = expected;
        }

        public void reset() {
            exchangeId = null;
            messageId = null;
            breadcrumbId = null;
            contextId = null;
            threadId = null;
            foo = null;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            // custom is propagated as its pattern matches
            assertEquals(expected, MDC.get("custom.hello"));
            assertEquals("Baz", MDC.get("myKey"));

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
            if (exchangeId != null) {
                assertNotEquals(exchangeId, MDC.get("camel.exchangeId"));
            } else {
                exchangeId = MDC.get("camel.exchangeId");
                assertTrue(exchangeId != null && exchangeId.length() > 0);
            }

            if (messageId != null) {
                assertNotEquals(messageId, MDC.get("camel.messageId"));
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
