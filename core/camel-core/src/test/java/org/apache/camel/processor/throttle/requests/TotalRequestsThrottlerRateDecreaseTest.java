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
package org.apache.camel.processor.throttle.requests;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A decrease of the maximum requests waits for permits while other exchanges wait for permits too: the exchanges that
 * get the permits must be able to return them, so the decrease completes.
 */
public class TotalRequestsThrottlerRateDecreaseTest extends ContextTestSupport {

    private static final long PERIOD = 2000;

    private final List<Throwable> failures = new CopyOnWriteArrayList<>();

    @Test
    public void testDecreaseWhileExchangesWaitForPermits() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(5);

        // both permits are taken, and delayed for one period
        template.sendBodyAndHeader("direct:start", "a", "max", 2);
        template.sendBodyAndHeader("direct:start", "b", "max", 2);

        CountDownLatch done = new CountDownLatch(3);
        // two exchanges wait for the delayed permits (the first one gets the first permit, the second one the next)
        Thread first = send("c", 2, done);
        await().atMost(5, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS)
                .until(() -> first.getState() == Thread.State.TIMED_WAITING);
        Thread second = send("d", 2, done);
        await().atMost(5, TimeUnit.SECONDS).pollInterval(10, TimeUnit.MILLISECONDS)
                .until(() -> second.getState() == Thread.State.WAITING);
        // then an exchange decreases the maximum to 1, which waits for a permit to discard
        send("e", 1, done);

        assertTrue(done.await(20, TimeUnit.SECONDS), "All exchanges should get through the throttler");
        assertEquals(List.of(), failures);
        assertMockEndpointsSatisfied();
    }

    private Thread send(String body, int max, CountDownLatch done) {
        Thread thread = new Thread(() -> {
            try {
                template.sendBodyAndHeader("direct:start", body, "max", max);
            } catch (Exception e) {
                failures.add(e);
            } finally {
                done.countDown();
            }
        }, "send-" + body);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").throttle(header("max")).timePeriodMillis(PERIOD).to("mock:result");
            }
        };
    }
}
