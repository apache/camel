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
import org.apache.camel.support.LifecycleStrategySupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos and the watch windows of rule R3 of the exchange sanitizer together, with the tracer, the backlog tracer and
 * the backlog debugger on as well: a watch window is opened at every point where the routing engine uses an exchange
 * ({@code overlapSampling = 1}), and the chaos rules that fail, delay and duplicate steps and sends cause no report.
 * The duplicate rule is the interesting case: the producer is called twice within one call of the step, also when the
 * first send completes on another thread.
 */
@CamelSanitizer(overlapSampling = 1)
class ChaosWithSanitizerWatchWindowsTest extends ChaosTestSupport {

    private static final Pattern WINDOWS = Pattern.compile("(\\d+) watch windows? opened \\(R3, at 1 in 1 points\\)");

    private final AtomicInteger work = new AtomicInteger();
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

                from("direct:twice").routeId("twice").to("direct:receiver").id("toReceiver");
                from("direct:receiver").routeId("receiver").to("mock:received").id("toReceived");
                from("direct:twiceToMock").routeId("twiceToMock").to("mock:duplicated").id("toMock");

                // the send that chaos duplicates runs on a thread of the pool, not on the thread of the caller
                from("direct:pooled").routeId("pooled").threads(2).id("pool").to("direct:pooledReceiver").id("toPooled");
                from("direct:pooledReceiver").routeId("pooledReceiver").to("mock:pooled").id("toPooledMock");
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
        String report = sanitizer().report();
        Matcher m = WINDOWS.matcher(report);
        assertThat(m.find()).as(report).isTrue();
        assertThat(Long.parseLong(m.group(1))).as(report).isPositive();
        assertThat(sanitizer().getViolationCount(Violation.USED_BY_TWO_THREADS)).as(report).isZero();
        assertThat(sanitizer().getViolations()).as(report).isEmpty();
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
        getMockEndpoint("mock:received").expectedBodiesReceived("x", "x");

        Exchange out = template.send("direct:twice", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
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
        assertWatchedAndQuiet();
    }
}
