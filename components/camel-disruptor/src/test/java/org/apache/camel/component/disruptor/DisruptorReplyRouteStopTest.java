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
package org.apache.camel.component.disruptor;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.RouteStoppingException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25502: a disruptor producer waiting for the reply when its route is stopped is cut off by the stop: a
 * RouteStoppingException, not an ExchangeTimedOutException.
 */
public class DisruptorReplyRouteStopTest extends CamelTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch slowStarted = new CountDownLatch(1);

    @AfterEach
    void releaseSlowRoute() {
        release.countDown();
    }

    @Test
    void theWaitForTheReplyIsCutOffByTheRouteStop() throws Exception {
        AtomicReference<Exchange> failure = new AtomicReference<>();
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.ExchangeFailedEvent failed
                        && "caller".equals(failed.getExchange().getFromRouteId())) {
                    failure.compareAndSet(null, failed.getExchange());
                }
            }
        });
        context.getShutdownStrategy().setTimeout(1);
        context.getShutdownStrategy().setTimeUnit(TimeUnit.SECONDS);

        template.sendBody("seda:start", "A");
        assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
        // the caller waits for the reply of the slow route; the forced stop of the caller interrupts the wait
        context.getRouteController().stopRoute("caller");

        await().atMost(5, TimeUnit.SECONDS).until(() -> failure.get() != null);
        RouteStoppingException e = assertInstanceOf(RouteStoppingException.class, failure.get().getException());
        assertTrue(e.getMessage().startsWith("Interrupted while waiting for the reply from disruptor://slow"),
                e.getMessage());
        assertInstanceOf(InterruptedException.class, e.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("seda:start").routeId("caller")
                        .to(ExchangePattern.InOut, "disruptor:slow");
                from("disruptor:slow").routeId("slow")
                        .process(e -> {
                            slowStarted.countDown();
                            release.await(10, TimeUnit.SECONDS);
                        });
            }
        };
    }
}
