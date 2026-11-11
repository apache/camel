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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.sanitizer.Sanitizer;
import org.apache.camel.sanitizer.Violation;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos and the exchange sanitizer together (CAMEL-26714): real chaos rules fail, delay and duplicate the steps and
 * sends of the routes, and the sanitizer, on for the same contexts, reports only real breaks of the contract of
 * AsyncProcessor. A step that a chaos rule fails is not a call of its processor, so the sanitizer neither checks nor
 * reports it; a processor that does break the contract is still reported, once, also when chaos acts on its step.
 */
@CamelSanitizer
class ChaosWithSanitizerTest extends ChaosTestSupport {

    private final AtomicInteger work = new AtomicInteger();
    private final AtomicInteger plain = new AtomicInteger();
    private final AtomicInteger thrown = new AtomicInteger();

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:retry").routeId("retry")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false))
                        .process(e -> work.incrementAndGet()).id("work")
                        .to("mock:retry").id("toRetry");

                from("direct:plain").routeId("plain")
                        .errorHandler(noErrorHandler())
                        .process(e -> plain.incrementAndGet()).id("unhandled");

                from("direct:broken").routeId("broken")
                        .errorHandler(defaultErrorHandler().maximumRedeliveries(1).redeliveryDelay(0).logStackTrace(false))
                        .process(new AsyncProcessorSupport() {
                            @Override
                            public boolean process(Exchange exchange, AsyncCallback callback) {
                                thrown.incrementAndGet();
                                throw new IllegalStateException("Forced by the test");
                            }
                        }).id("throws");

                from("direct:twice").routeId("twice").to("direct:receiver").id("toReceiver");
                from("direct:receiver").routeId("receiver").to("mock:received").id("toReceived");
                from("direct:twiceToMock").routeId("twiceToMock").to("mock:duplicated").id("toMock");
            }
        };
    }

    private Sanitizer sanitizer() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertThat(sanitizer).as("the sanitizer is installed").isNotNull();
        return sanitizer;
    }

    @Test
    void aStepThatChaosFailsIsNotACallOfItsProcessor() throws Exception {
        // the error handler redelivers the attempt that the rule failed, and that one calls the processor
        String id = chaos().addRule("step=work error until=1");
        getMockEndpoint("mock:retry").expectedMessageCount(1);

        Exchange out = template.send("direct:retry", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(1);
        assertThat(work.get()).isEqualTo(1);
        // checked: the redelivered call of work and the send to mock:retry; not the attempt that chaos failed
        assertThat(sanitizer().getCallCount()).isEqualTo(2);
        assertThat(sanitizer().getViolations()).isEmpty();
    }

    @Test
    void aStepThatChaosFailsWithoutAnErrorHandler() {
        // no error handler: the channel of the step asks the chaos hook
        String id = chaos().addRule("step=unhandled error");

        Exchange out = template.send("direct:plain", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isInstanceOf(ChaosException.class);
        assertThat(fired(id, "step")).isEqualTo(1);
        assertThat(plain.get()).isZero();
        assertThat(sanitizer().getCallCount()).isZero();
        assertThat(sanitizer().getViolations()).isEmpty();
    }

    @Test
    void aDelayedStepIsOneCall() throws Exception {
        String id = chaos().addRule("step=work delay duration=5ms");
        getMockEndpoint("mock:retry").expectedMessageCount(1);

        template.sendBody("direct:retry", "x");

        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "step")).isEqualTo(1);
        assertThat(work.get()).isEqualTo(1);
        assertThat(sanitizer().getCallCount()).isEqualTo(2);
        assertThat(sanitizer().getViolations()).isEmpty();
    }

    @Test
    void aSendThatChaosDuplicatesCallsBackOnce() throws Exception {
        // the producer of direct:receiver is called twice within one call of the step toReceiver, whose callback is
        // called once; the first send completes asynchronously (it routes through receiver), so the thread that
        // completes it makes the second send
        String id = chaos().addRule("endpoint=direct:receiver duplicate");
        getMockEndpoint("mock:received").expectedBodiesReceived("x", "x");

        Exchange out = template.send("direct:twice", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
        // checked: toReceiver once, and toReceived once for each of the two sends
        assertThat(sanitizer().getCallCount()).isEqualTo(3);
        assertThat(sanitizer().getViolations()).isEmpty();
    }

    @Test
    void aSendThatChaosDuplicatesAndThatCompletesSynchronouslyCallsBackOnce() throws Exception {
        // the mock producer completes each send synchronously, so the thread of the step makes the second send
        String id = chaos().addRule("endpoint=mock:duplicated duplicate");
        getMockEndpoint("mock:duplicated").expectedBodiesReceived("x", "x");

        Exchange out = template.send("direct:twiceToMock", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isNull();
        MockEndpoint.assertIsSatisfied(context);
        assertThat(fired(id, "send")).isEqualTo(1);
        assertThat(sanitizer().getCallCount()).isEqualTo(1);
        assertThat(sanitizer().getViolations()).isEmpty();
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void aRealBreakIsStillReportedOnceWhenChaosFailsTheFirstAttempt() {
        // the rule fails the first attempt; the redelivery calls the processor, which throws instead of calling its
        // callback
        String id = chaos().addRule("step=throws error until=1");

        Exchange out = template.send("direct:broken", e -> e.getMessage().setBody("x"));

        assertThat(out.getException()).isInstanceOf(IllegalStateException.class).hasMessage("Forced by the test");
        assertThat(fired(id, "step")).isEqualTo(1);
        assertThat(thrown.get()).isEqualTo(1);
        assertThat(sanitizer().getCallCount()).isEqualTo(1);
        assertThat(sanitizer().getViolationCount(Violation.THREW_BEFORE_CALLBACK)).isEqualTo(1);
        assertThat(sanitizer().getViolations()).singleElement().satisfies(v -> {
            assertThat(v.getStepId()).isEqualTo("throws");
            assertThat(v.getRouteId()).isEqualTo("broken");
            assertThat(v.getText()).contains("java.lang.IllegalStateException: Forced by the test");
        });
    }
}
