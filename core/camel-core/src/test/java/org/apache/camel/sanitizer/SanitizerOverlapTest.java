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
package org.apache.camel.sanitizer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.AsyncCallback;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.support.AsyncProcessorSupport;
import org.apache.camel.support.ExchangeHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule R3, its second part: a processor of a user that lets two threads use the same exchange at the same time is
 * reported, with both threads and their stacks. Each point opens a watch window here (sampling 1), long enough that the
 * two threads meet in one whichever comes first; a processor that waits for its task, or that sends a copy with the
 * same id aside, is not reported.
 */
@CamelSanitizer(overlapSampling = 1)
class SanitizerOverlapTest extends ContextTestSupport {

    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> new Thread(r, "user-aside"));
    // the other route started: the route goes to its next point then, while the other route goes to its own
    private final CountDownLatch auditStarted = new CountDownLatch(1);
    private BrokenUserCode.SendsTheExchangeAside aside;
    private SendsACopyAside copier;

    @AfterEach
    void shutdownPool() {
        pool.shutdownNow();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // a window much longer than the thread of the pool takes to start: the two threads meet in it
        context.getPropertiesComponent().addOverrideProperty("camel.sanitizer.overlapWindow", "200ms");
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                aside = new BrokenUserCode.SendsTheExchangeAside(pool, template, "direct:aside");
                from("direct:start").routeId("orders")
                        .process(aside).id("handOff")
                        // both threads then come to their next point at the same time
                        .process(e -> assertTrue(auditStarted.await(10, TimeUnit.SECONDS))).id("meet")
                        // the route goes on when the other route is done, so the two do not change the routes of the
                        // unit of work at the same time (only their steps overlap)
                        .process(e -> assertTrue(aside.asideDone.await(10, TimeUnit.SECONDS))).id("next")
                        .to("mock:result");
                from("direct:aside").routeId("audit")
                        .process(e -> {
                            auditStarted.countDown();
                            e.setProperty("audited", true);
                        }).id("auditStep");

                copier = new SendsACopyAside(pool, template, "direct:aside");
                from("direct:copy").routeId("copier")
                        .process(copier).id("handOffCopy")
                        .process(e -> assertTrue(auditStarted.await(10, TimeUnit.SECONDS))).id("meetCopy")
                        .process(e -> assertTrue(copier.asideDone.await(10, TimeUnit.SECONDS))).id("nextCopy")
                        .to("mock:result");

                from("direct:waits").routeId("caller")
                        .process(new BrokenUserCode.WaitsForItsTask(pool, template, "direct:task")).id("breaker")
                        .to("mock:result");
                from("direct:task").routeId("task")
                        .setHeader("task", constant(true)).id("taskStep");
            }
        };
    }

    @Test
    @ExpectSanitizerViolation(Violation.USED_BY_TWO_THREADS)
    void aProcessorThatSendsTheExchangeAsideIsReportedWithBothThreads() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:start", "order");
        assertMockEndpointsSatisfied();

        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        await().atMost(Duration.ofSeconds(10))
                .until(() -> sanitizer.getViolationCount(Violation.USED_BY_TWO_THREADS) > 0);
        // one report for the exchange, also when the two threads met at more than one point
        List<SanitizerViolation> found = sanitizer.getViolations();
        assertEquals(1, found.size(), found::toString);
        SanitizerViolation v = found.get(0);
        assertEquals(Violation.USED_BY_TWO_THREADS, v.getKind());
        String text = v.getText();
        assertTrue(text.startsWith("Exchange sanitizer: exchange used by two threads at the same time (R3)"), text);
        // both threads, each with what it did then: the route and the other route
        assertTrue(text.contains("thread user-aside"), text);
        assertTrue(text.contains("thread " + Thread.currentThread().getName()), text);
        assertTrue(text.contains(" of route audit "), text);
        assertTrue(text.contains(" of route orders "), text);
        assertTrue(text.contains("  watched while "), text);
        assertTrue(text.contains("  meanwhile "), text);
        assertTrue(text.contains("must never be used by two threads at the same time (see AsyncProcessor)"), text);
        assertTrue(text.contains("Usual fix: let one thread at a time use the exchange"), text);
        // the stack of the thread of the pool goes through the template that the processor of the user called
        assertTrue(text.contains("at " + BrokenUserCode.SendsTheExchangeAside.class.getName() + ".lambda$process$"),
                text);
        assertTrue(sanitizer.getOverlaps().getWindowCount() > 0);
        assertTrue(sanitizer.report().contains("watch windows opened (R3, at 1 in 1 points)"), sanitizer.report());
    }

    @Test
    void aCopyWithTheIdOfTheExchangeIsNotReported() throws Exception {
        // the same routes as the exchange sent aside, with a copy that keeps the id: another exchange
        getMockEndpoint("mock:result").expectedMessageCount(1);
        template.sendBody("direct:copy", "order");
        assertMockEndpointsSatisfied();

        Sanitizer sanitizer = Sanitizer.get(context);
        assertEquals(List.of(), sanitizer.getViolations());
        assertTrue(sanitizer.getOverlaps().getWindowCount() > 0, () -> sanitizer.report());
    }

    @Test
    void aCallerThatWaitsForItsTaskIsNotReported() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);
        getMockEndpoint("mock:result").expectedHeaderReceived("task", true);
        template.sendBody("direct:waits", "x");
        assertMockEndpointsSatisfied();

        Sanitizer sanitizer = Sanitizer.get(context);
        assertEquals(List.of(), sanitizer.getViolations());
        // the steps of both routes were watched: the caller only waited while the task used the exchange
        assertTrue(sanitizer.getOverlaps().getWindowCount() >= 4, () -> sanitizer.report());
    }

    /**
     * Correct: sends a copy of the exchange that keeps its id to another route on a thread of its pool, and goes on, as
     * a transport in the same JVM that rebuilds the exchange with the id of the sender does.
     */
    static final class SendsACopyAside extends AsyncProcessorSupport {

        final CountDownLatch asideDone = new CountDownLatch(1);
        private final ExecutorService pool;
        private final ProducerTemplate template;
        private final String uri;

        SendsACopyAside(ExecutorService pool, ProducerTemplate template, String uri) {
            this.pool = pool;
            this.template = template;
            this.uri = uri;
        }

        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            Exchange copy = ExchangeHelper.createCorrelatedCopy(exchange, false);
            copy.setExchangeId(exchange.getExchangeId());
            pool.execute(() -> {
                try {
                    template.send(uri, copy);
                } finally {
                    asideDone.countDown();
                }
            });
            callback.done(true);
            return true;
        }
    }
}
