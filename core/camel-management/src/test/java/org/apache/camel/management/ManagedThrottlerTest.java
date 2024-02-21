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
package org.apache.camel.management;

import java.util.concurrent.Callable;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions")
public class ManagedThrottlerTest extends AbstractManagedThrottlerTest {

    @Test
    public void testManageThrottler() throws Exception {
        final Long total = super.runTestManageThrottler();

        // 10 * delay (100) + tolerance (200)
        assertTrue(total > 1000, "Should be around 1 sec now: was " + total);
    }

    @Test
    public void testThrottleAsyncVisibleViaJmx() throws Exception {
        super.runTestThrottleAsyncVisibleViaJmx();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final ScheduledExecutorService badService = new ScheduledThreadPoolExecutor(1) {
            @Override
            public <V> ScheduledFuture<V> schedule(Callable<V> command, long delay, TimeUnit unit) {
                throw new RejectedExecutionException();
            }
        };

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").id("route1")
                        .to("log:foo")
                        .throttle(10).totalRequestsMode().id("mythrottler")
                        .delay(100)
                        .to("mock:result");

                from("seda:throttleCount").id("route2")
                        .throttle(1).totalRequestsMode().id("mythrottler2").delay(250)
                        .to("mock:end");

                from("seda:throttleCountAsync").id("route3")
                        .throttle(1).totalRequestsMode().asyncDelayed().id("mythrottler3").delay(250)
                        .to("mock:endAsync");

                from("seda:throttleCountAsyncException").id("route4")
                        .throttle(1).totalRequestsMode().asyncDelayed().id("mythrottler4").delay(250)
                        .to("mock:endAsyncException")
                        .process(exchange -> {
                            throw new RuntimeException("Fail me");
                        });
                from("seda:throttleCountRejectExecutionCallerRuns").id("route5")
                        .onException(RejectedExecutionException.class).to("mock:rejectedExceptionEndpoint1").end()
                        .throttle(1).totalRequestsMode()
                        .asyncDelayed()
                        .executorService(badService)
                        .callerRunsWhenRejected(true)
                        .id("mythrottler5")
                        .delay(250)
                        .to("mock:endAsyncRejectCallerRuns");

                from("seda:throttleCountRejectExecution").id("route6")
                        .onException(RejectedExecutionException.class).to("mock:rejectedExceptionEndpoint1").end()
                        .throttle(1).totalRequestsMode()
                        .asyncDelayed()
                        .executorService(badService)
                        .callerRunsWhenRejected(false)
                        .id("mythrottler6")
                        .delay(250)
                        .to("mock:endAsyncReject");
            }
        };
    }

}
