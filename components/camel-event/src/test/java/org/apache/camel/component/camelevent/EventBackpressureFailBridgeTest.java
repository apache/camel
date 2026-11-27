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
package org.apache.camel.component.camelevent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With backpressurePolicy=Fail and bridgeErrorHandler=true, a full queue is reported by an exchange that goes to the
 * error handler of the route. That exchange fires exchange events too, which Exchange* accepts and which do not fit in
 * the full queue either. They must not be reported again: each report would fire more events in the same thread, until
 * a StackOverflowError.
 */
class EventBackpressureFailBridgeTest extends CamelTestSupport {

    private final AtomicInteger reported = new AtomicInteger();
    private final CountDownLatch processing = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Test
    // without the guard the sender can be left waiting forever after a StackOverflowError
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void theReportOfAFullQueueIsNotReportedAgain() throws Exception {
        // the only consumer thread takes the first event and waits in the route, and the next event fills the queue
        template.sendBody("direct:source", "one");
        assertTrue(processing.await(20, TimeUnit.SECONDS), "the first event was not processed");
        // the queue is full, so each event of this exchange is reported once
        template.sendBody("direct:source", "two");
        release.countDown();

        int count = reported.get();
        assertTrue(count > 0, "a full queue was not reported to the error handler of the route");
        // a few events for each of the two exchanges, not one more report for each event of a report
        assertTrue(count < 50, "the reports of a full queue were reported again (" + count + " reports)");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("event:Exchange*?async=true&asyncPoolSize=1&asyncQueueSize=1&backpressurePolicy=Fail"
                     + "&bridgeErrorHandler=true")
                        .routeId("events")
                        .onException(IllegalStateException.class).handled(true)
                        .process(e -> {
                            // without the guard the reports never stop while the queue is full, so the consumer
                            // thread is let go to end them and fail the test
                            if (reported.incrementAndGet() >= 50) {
                                release.countDown();
                            }
                        })
                        .end()
                        .process(e -> {
                            processing.countDown();
                            release.await(20, TimeUnit.SECONDS);
                        });

                from("direct:source").routeId("source").log("${body}");
            }
        };
    }
}
