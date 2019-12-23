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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.AsyncCallbackToCompletableFutureAdapter;
import org.junit.Test;
import org.slf4j.MDC;

public class MDCAsyncTest extends ContextTestSupport {

    @Test
    public void testMdcPreservedAfterAsyncEndpoint() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:end");
        mock.expectedMessageCount(1);

        template.sendBody("direct:a", "Hello World");

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

                MdcCheckerProcessor checker = new MdcCheckerProcessor();

                from("direct:a").routeId("route-async").process(e -> {
                    // custom is propagated
                    MDC.put("custom.hello", "World");
                    // foo is not propagated
                    MDC.put("foo", "Bar");
                    // myKey is propagated
                    MDC.put("myKey", "Baz");
                }).process(checker).to("log:foo").process(new MyAsyncProcessor()).process(checker).to("mock:end");

            }
        };
    }

    private static class MyAsyncProcessor implements AsyncProcessor {

        private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(1);

        MyAsyncProcessor() {
            // submit a Runnable that does nothing just to initialise the
            // threads
            EXECUTOR.submit(new Runnable() {
                @Override
                public void run() {
                    // do nothing
                }
            });
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            throw new RuntimeCamelException("This processor does not support the sync pattern.");
        }

        @Override
        public CompletableFuture<Exchange> processAsync(Exchange exchange) {
            AsyncCallbackToCompletableFutureAdapter<Exchange> callback = new AsyncCallbackToCompletableFutureAdapter<>(exchange);
            process(exchange, callback);
            return callback.getFuture();
        }

        @Override
        public boolean process(Exchange exchange, final AsyncCallback callback) {
            EXECUTOR.submit(() -> callback.done(false));
            return false;
        }
    }

    /**
     * Stores values from the first invocation to compare them with the second
     * invocation later.
     */
    private static class MdcCheckerProcessor implements Processor {

        private String routeId = "route-async";
        private String exchangeId;
        private String messageId;
        private String breadcrumbId;
        private String contextId;
        private Long threadId;
        private String foo;

        @Override
        public void process(Exchange exchange) throws Exception {
            // custom is propagated as its pattern matches
            assertEquals("World", MDC.get("custom.hello"));
            assertEquals("Baz", MDC.get("myKey"));

            if (foo != null) {
                // foo is not propagated
                assertNotEquals(foo, MDC.get("foo"));
            } else {
                foo = MDC.get("foo");
            }

            if (threadId != null) {
                Long currId = Thread.currentThread().getId();
                assertNotEquals(threadId, currId);
            } else {
                threadId = Thread.currentThread().getId();
            }

            if (routeId != null) {
                assertEquals(routeId, MDC.get("camel.routeId"));
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
