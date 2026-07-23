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
package org.apache.camel.component.resilience4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.CamelContext;
import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit6.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test that using a non-ScheduledExecutorService with async + timeout throws at startup.
 */
public class ResilienceAsyncInvalidExecutorTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext ctx = super.createCamelContext();
        ExecutorService plainExecutor = Executors.newFixedThreadPool(2);
        ctx.getRegistry().bind("myExecutor", plainExecutor);
        return ctx;
    }

    @Test
    public void testInvalidExecutorThrows() {
        Exception exception = assertThrows(Exception.class, () -> {
            context.addRoutes(createTestRouteBuilder());
            context.start();
        });
        assertIsInstanceOf(FailedToCreateRouteException.class, exception);
        assertIsInstanceOf(IllegalArgumentException.class, exception.getCause());
        assertTrue(exception.getCause().getMessage().contains("ScheduledExecutorService"),
                "Should mention ScheduledExecutorService");
        assertTrue(exception.getCause().getMessage().contains("asynchronous"),
                "Should mention asynchronous");
    }

    private RoutesBuilder createTestRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .circuitBreaker()
                            .resilience4jConfiguration()
                                .asynchronous(true)
                                .timeoutEnabled(true)
                                .timeoutDuration(2000)
                                .timeoutExecutorService("myExecutor")
                            .end()
                            .to("direct:foo")
                        .end()
                        .to("mock:result");

                from("direct:foo").transform().constant("Bye World");
            }
        };
    }

}
