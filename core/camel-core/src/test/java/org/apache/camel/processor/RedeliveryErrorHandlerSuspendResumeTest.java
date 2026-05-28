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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Verifies that redelivery continues to work after context.suspend() + context.resume() when
 * allowRedeliveryWhileStopping is set to false.
 *
 * During suspend, DefaultShutdownStrategy sets preparingShutdown = true on RedeliveryErrorHandler via
 * getChildServices(service, includeErrorHandler=true). During resume, the error handler is excluded from the service
 * list (includeErrorHandler=false) and its status stays STARTED, so neither doStart() nor doResume() is called to reset
 * the flag. Subsequent exchanges that need redelivery are rejected because the error handler thinks a shutdown is in
 * progress.
 *
 * The default RedeliveryPolicy has allowRedeliveryWhileStopping=true, which bypasses the preparingShutdown check. This
 * test uses allowRedeliveryWhileStopping(false) to expose the bug — a configuration that is recommended in production
 * to avoid slow shutdowns caused by long redelivery cycles.
 */
public class RedeliveryErrorHandlerSuspendResumeTest extends ContextTestSupport {

    private final AtomicInteger invocationCount = new AtomicInteger();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testRedeliveryWorksAfterContextSuspendResume() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler()
                        .maximumRedeliveries(3)
                        .redeliveryDelay(0)
                        .allowRedeliveryWhileStopping(false));

                from("direct:start").routeId("redeliveryRoute")
                        .process(new FailOnceThenSucceed())
                        .to("mock:result");
            }
        });
        context.start();

        // first send — processor fails once, then succeeds on redelivery
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "first");
        assertMockEndpointsSatisfied();

        resetMocks();
        invocationCount.set(0);

        // suspend and resume the entire context
        context.suspend();
        context.resume();

        // second send — redelivery must still work after resume
        getMockEndpoint("mock:result").expectedMessageCount(1);
        try {
            template.sendBody("direct:start", "second");
        } catch (CamelExecutionException e) {
            fail("Redelivery should have retried the transient failure after context suspend/resume, "
                 + "but preparingShutdown is stuck true so redelivery was rejected: " + e.getMessage());
        }
        assertMockEndpointsSatisfied();
    }

    @Test
    public void testRedeliveryWorksAfterRouteSuspendResume() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(defaultErrorHandler()
                        .maximumRedeliveries(3)
                        .redeliveryDelay(0)
                        .allowRedeliveryWhileStopping(false));

                from("direct:start").routeId("redeliveryRoute")
                        .process(new FailOnceThenSucceed())
                        .to("mock:result");
            }
        });
        context.start();

        // first send — processor fails once, then succeeds on redelivery
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "first");
        assertMockEndpointsSatisfied();

        resetMocks();
        invocationCount.set(0);

        // suspend and resume a single route
        context.getRouteController().suspendRoute("redeliveryRoute");
        context.getRouteController().resumeRoute("redeliveryRoute");

        // second send — redelivery must still work after resume
        getMockEndpoint("mock:result").expectedMessageCount(1);
        try {
            template.sendBody("direct:start", "second");
        } catch (CamelExecutionException e) {
            fail("Redelivery should have retried the transient failure after route suspend/resume, "
                 + "but preparingShutdown is stuck true so redelivery was rejected: " + e.getMessage());
        }
        assertMockEndpointsSatisfied();
    }

    /**
     * Processor that throws on the first invocation and succeeds on the second. Redelivery should make the exchange
     * succeed.
     */
    private class FailOnceThenSucceed implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            if (invocationCount.incrementAndGet() == 1) {
                throw new IllegalArgumentException("Transient failure — should succeed on retry");
            }
        }
    }
}
