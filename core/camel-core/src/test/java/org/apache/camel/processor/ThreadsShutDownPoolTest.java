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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The Threads EIP with a thread pool that its owner shut down: the exchange must fail with a
 * {@link RejectedExecutionException}, instead of never being completed.
 */
class ThreadsShutDownPoolTest extends ContextTestSupport {

    private ExecutorService camelPool;
    private ExecutorService jdkPool;

    @Test
    void testCamelThreadPoolShutDown() throws Exception {
        // a thread pool of Camel, with the CallerRuns policy
        camelPool.shutdown();
        assertRejected("direct:camel");
    }

    @Test
    void testJdkThreadPoolShutDown() throws Exception {
        // a thread pool of the JDK, with the AbortPolicy: the CallerRuns policy of the EIP takes over
        jdkPool.shutdown();
        assertRejected("direct:jdk");
    }

    private void assertRejected(String uri) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Future<Exchange> future = template.asyncSend(uri, e -> e.getIn().setBody("Hello"));
        Exchange exchange = future.get(10, TimeUnit.SECONDS);

        assertInstanceOf(RejectedExecutionException.class, exchange.getException());
        assertMockEndpointsSatisfied();
        assertEquals(0, context.getInflightRepository().size());
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        camelPool = context.getExecutorServiceManager().newDefaultThreadPool(this, "shared");
        jdkPool = Executors.newFixedThreadPool(1);
        context.getRegistry().bind("camelPool", camelPool);
        context.getRegistry().bind("jdkPool", jdkPool);
        return context;
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        jdkPool.shutdownNow();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:camel").threads().executorService("camelPool").to("mock:result");
                from("direct:jdk").threads().executorService("jdkPool").to("mock:result");
            }
        };
    }
}
