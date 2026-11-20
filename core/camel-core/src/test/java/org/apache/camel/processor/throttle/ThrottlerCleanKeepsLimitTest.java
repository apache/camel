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
package org.apache.camel.processor.throttle;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.RuntimeExchangeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.processor.Throttler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The throttler removes the state of a key some time after it was last used. When the limit comes from a header, a
 * message without the header uses the old value (throttle-eip.adoc): also after the state was removed, and in both
 * throttling modes.
 */
class ThrottlerCleanKeepsLimitTest extends ContextTestSupport {

    private final CapturingExecutor executor = new CapturingExecutor();

    @AfterEach
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    @ParameterizedTest
    @ValueSource(strings = { "total", "concurrent" })
    void testNoHeaderUsesOldValueAfterClean(String mode) throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("first", "second");

        template.sendBodyAndHeader("direct:" + mode, "first", "max", 3);
        Throttler throttler = throttler(mode);
        assertEquals(3, throttler.getCurrentMaximumRequests());

        // the key was idle long enough: the clean removes its state
        executor.runScheduled();
        assertEquals(0, throttler.getCurrentMaximumRequests());

        // no header: the old value is used
        template.sendBody("direct:" + mode, "second");
        assertEquals(3, throttler.getCurrentMaximumRequests());

        assertMockEndpointsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "total", "concurrent" })
    void testNoHeaderWithoutOldValueFails(String mode) throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(0);

        Exception e = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:" + mode, "first"));
        assertInstanceOf(RuntimeExchangeException.class, e.getCause());

        assertMockEndpointsSatisfied();
    }

    @ParameterizedTest
    @ValueSource(strings = { "total", "concurrent" })
    void testCorrelatedKeyHasNoOldValueAfterClean(String mode) throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("first");

        // with a correlation expression the limit of a removed state is not kept, as there can be any number of keys
        template.sendBodyAndHeaders("direct:" + mode + "-key", "first", Map.<String, Object> of("max", 3, "key", "a"));
        executor.runScheduled();
        Exception e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader("direct:" + mode + "-key", "second", "key", "a"));
        assertInstanceOf(RuntimeExchangeException.class, e.getCause());

        assertMockEndpointsSatisfied();
    }

    private Throttler throttler(String mode) {
        return context.getProcessor(mode, Throttler.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:total")
                        .throttle(header("max")).timePeriodMillis(TimeUnit.HOURS.toMillis(1)).executorService(executor)
                        .id("total")
                        .to("mock:result");
                from("direct:concurrent")
                        .throttle(header("max")).concurrentRequestsMode().executorService(executor).id("concurrent")
                        .to("mock:result");
                from("direct:total-key")
                        .throttle(header("max")).correlationExpression(header("key"))
                        .timePeriodMillis(TimeUnit.HOURS.toMillis(1)).executorService(executor)
                        .to("mock:result");
                from("direct:concurrent-key")
                        .throttle(header("max")).correlationExpression(header("key")).concurrentRequestsMode()
                        .executorService(executor)
                        .to("mock:result");
            }
        };
    }

    /**
     * Keeps the scheduled clean tasks so the test decides when they run.
     */
    private static final class CapturingExecutor extends ScheduledThreadPoolExecutor {
        private final List<Runnable> scheduled = new CopyOnWriteArrayList<>();

        CapturingExecutor() {
            super(1);
            setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            scheduled.add(command);
            return super.schedule(command, TimeUnit.DAYS.toMillis(1), TimeUnit.MILLISECONDS);
        }

        void runScheduled() {
            for (Runnable task : scheduled) {
                task.run();
            }
            scheduled.clear();
        }
    }
}
