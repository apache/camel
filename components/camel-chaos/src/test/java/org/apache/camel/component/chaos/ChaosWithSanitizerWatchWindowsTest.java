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
package org.apache.camel.component.chaos;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.debugger.DefaultBacklogDebugger;
import org.apache.camel.sanitizer.Sanitizer;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.spi.BacklogDebugger;
import org.apache.camel.spi.BacklogTracer;
import org.apache.camel.support.LifecycleStrategySupport;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos and the watch windows of rule R3 of the exchange sanitizer together, with the tracer, the backlog tracer and
 * the backlog debugger on as well: a watch window is opened at every point where the routing engine uses an exchange
 * ({@code overlapSampling = 1}), and the chaos rules that fail, delay and duplicate steps and sends cause no report.
 * The duplicate rule is the interesting case: the producer is called twice within one call of the step. The tests cover
 * the three ways the two sends run:
 * <ul>
 * <li>the endpoint completes synchronously: the thread of the caller makes both sends;</li>
 * <li>the duplicated send comes after a {@code threads} EIP: a thread of the pool makes both sends;</li>
 * <li>the endpoint completes asynchronously: the thread that completed the first send makes the second send while the
 * thread of the caller unwinds.</li>
 * </ul>
 * The tests record the thread that makes each send, so they check which case ran.
 */
@CamelSanitizer(overlapSampling = 1)
class ChaosWithSanitizerWatchWindowsTest extends ChaosTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ChaosWithSanitizerWatchWindowsTest.class);
    private static final Pattern WINDOWS = Pattern.compile("(\\d+) watch windows? opened \\(R3, at 1 in 1 points\\)");

    private final AtomicInteger work = new AtomicInteger();
    // the threads that made the sends to direct:receiver (a direct consumer runs on the thread of the send), in order
    private final List<Thread> receiverSenders = new CopyOnWriteArrayList<>();
    // the threads that made the sends to direct:pooledReceiver, in order, per exchange: the two threads of the pool
    // can route two exchanges at once, so the sends of different exchanges can interleave
    private final Map<String, List<Thread>> pooledSenders = new ConcurrentHashMap<>();
    private BacklogDebugger debugger;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setTracing(true);
        context.setBacklogTracing(true);
        context.setDebugging(true);
        // no breakpoint is set, so the debugger never suspends an exchange
        debugger = DefaultBacklogDebugger.createDebugger(context);
        context.addLifecycleStrategy(new LifecycleStrategySupport() {
            @Override
            public void onContextStarted(CamelContext context) {
                debugger.enableDebugger();
            }
        });
        context.addService(debugger);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:retry").routeId("retry")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false))
                        .process(e -> work.incrementAndGet()).id("work")
                        .to("mock:retry").id("toRetry");

                // the receiver completes asynchronously: threads(1) hands the exchange to its thread, so the first
                // send returns false, and that thread completes it and makes the second send
                from("direct:twice").routeId("twice").to("direct:receiver").id("toReceiver");
                from("direct:receiver").routeId("receiver")
                        .process(e -> receiverSenders.add(Thread.currentThread())).id("receiverSender")
                        .threads(1).id("receiverPool")
                        .to("mock:received").id("toReceived");
                from("direct:twiceToMock").routeId("twiceToMock").to("mock:duplicated").id("toMock");

                // the send that chaos duplicates runs on a thread of the pool, not on the thread of the caller
                from("direct:pooled").routeId("pooled").threads(2).id("pool").to("direct:pooledReceiver").id("toPooled");
                from("direct:pooledReceiver").routeId("pooledReceiver")
                        .process(e -> pooledSenders.computeIfAbsent(e.getExchangeId(), k -> new CopyOnWriteArrayList<>())
                                .add(Thread.currentThread()))
                        .id("pooledSender")
                        .to("mock:pooled").id("toPooledMock");
            }
        };
    }

    private Sanitizer sanitizer() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertThat(sanitizer).as("the sanitizer is installed").isNotNull();
        return sanitizer;
    }

    private void assertWatchedAndQuiet() {
        assertThat(context.isTracing()).isTrue();
        assertThat(context.isBacklogTracing()).isTrue();
        assertThat(context.isDebugging()).isTrue();
        assertThat(debugger.isEnabled()).isTrue();
        // the tracer and the backlog tracer recorded the routing, so they work with the watch windows on
        assertThat(context.getTracer().getTraceCounter()).as("events of the tracer").isPositive();
        BacklogTracer backlogTracer = context.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        assertThat(backlogTracer).as("the backlog tracer").isNotNull();
        assertThat(backlogTracer.isEnabled()).isTrue();
        assertThat(backlogTracer.getTraceCounter()).as("events of the backlog tracer").isPositive();
        String report = sanitizer().report();
        Matcher m = WINDOWS.matcher(report);
        assertThat(m.find()).as(report).isTrue();
        assertThat(Long.parseLong(m.group(1))).as(report).isPositive();
        assertThat(sanitizer().getViolationCount(Violation.USED_BY_TWO_THREADS)).as(report).isZero();
        assertThat(sanitizer().getViolations()).as(report).isEmpty();
        LOG.info("{} watch windows opened, no overlap reported, {} events of the tracer, {} of the backlog tracer",
                m.group(1), context.getTracer().getTraceCounter(), backlogTracer.getTraceCounter());
    }

    @Test
    void aStepThatChaosFails() throws Exception {
        String id = chaos().addRule("step=work error until=1");
        getMockEndpoint("mock:retry").expectedMessageCount(1);

        Exchange out = template.send("direct:retry", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(1);
        assertThat(work.get()).isEqualTo(1);
        assertWatchedAndQuiet();
    }

    @Test
    void aDelayedStep() throws Exception {
        String id = chaos().addRule("step=work delay duration=5ms");
        getMockEndpoint("mock:retry").expectedMessageCount(5);

        for (int i = 0; i < 5; i++) {
            template.sendBody("direct:retry", "x" + i);
        }

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(5);
        assertWatchedAndQuiet();
    }

    @Test
    void aDuplicatedSendThatCompletesAsynchronously() throws Exception {
        String id = chaos().addRule("endpoint=direct:receiver duplicate");
        int sends = 20;
        getMockEndpoint("mock:received").expectedMessageCount(2 * sends);

        for (int i = 0; i < sends; i++) {
            Exchange out = template.send("direct:twice", e -> e.getMessage().setBody("x"));
            assertThat(out.getException()).isNull();
        }

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(sends);
        // the caller made each first send, and the thread of threads(1), which completed it, made the second
        Thread caller = Thread.currentThread();
        assertThat(receiverSenders).hasSize(2 * sends);
        for (int i = 0; i < sends; i++) {
            Thread first = receiverSenders.get(2 * i);
            Thread second = receiverSenders.get(2 * i + 1);
            assertThat(first).as("the first send of exchange %d", i).isSameAs(caller);
            assertThat(second).as("the second send of exchange %d", i).isNotSameAs(first);
            assertThat(second.getName()).as("the second send of exchange %d", i).contains("Threads");
        }
        LOG.info("The first sends were made on {}, the second sends on {}",
                receiverSenders.stream().filter(t -> t == caller).map(Thread::getName).distinct().toList(),
                receiverSenders.stream().filter(t -> t != caller).map(Thread::getName).distinct().toList());
        assertWatchedAndQuiet();
    }

    @Test
    void aDuplicatedSendThatCompletesSynchronously() throws Exception {
        String id = chaos().addRule("endpoint=mock:duplicated duplicate");
        getMockEndpoint("mock:duplicated").expectedBodiesReceived("x", "x");

        Exchange out = template.send("direct:twiceToMock", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
        assertWatchedAndQuiet();
    }

    @Test
    void aDuplicatedSendOnAThreadOfAPool() throws Exception {
        String id = chaos().addRule("endpoint=direct:pooledReceiver duplicate");
        getMockEndpoint("mock:pooled").expectedMessageCount(20);

        for (int i = 0; i < 10; i++) {
            Exchange out = template.send("direct:pooled", e -> e.getMessage().setBody("x"));
            assertThat(out.getException()).isNull();
        }

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(10);
        // pooledReceiver completes synchronously, so the thread of the pool made both sends of each exchange
        assertThat(pooledSenders).hasSize(10);
        pooledSenders.forEach((exchangeId, senders) -> {
            assertThat(senders).as("the sends of exchange %s", exchangeId).hasSize(2);
            assertThat(senders.get(0)).as("the first send of exchange %s", exchangeId).isNotSameAs(Thread.currentThread());
            assertThat(senders.get(1)).as("the second send of exchange %s", exchangeId).isSameAs(senders.get(0));
        });
        assertWatchedAndQuiet();
    }
}
