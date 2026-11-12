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

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The still pending warning of R1 on routes: a call of a step whose callback does not come is reported once, at its
 * step, naming the processor of the user, after camel.sanitizer.pendingTimeout without anything happening in the
 * exchange. It is a warning: these tests pass with {@link CamelSanitizer}, although their exchanges hang.
 */
@CamelSanitizer
class SanitizerStillPendingTest extends ContextTestSupport {

    private final CountDownLatch release = new CountDownLatch(1);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addOverrideProperty("camel.sanitizer.pendingTimeout", "200ms");
        return context;
    }

    @Override
    protected int getShutdownTimeout() {
        // the exchanges that hang on purpose are not waited for long when the context stops
        return 1;
    }

    @AfterEach
    void releaseBlocked() {
        release.countDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:lost").routeId("lostRoute").process(new BrokenUserCode.NeverCallsBack()).id("lost")
                        .to("mock:lost");
                from("direct:outer").routeId("outer").to("direct:inner").id("toInner");
                from("direct:inner").routeId("inner").process(new BrokenUserCode.NeverCallsBack()).id("innerLost");
                from("direct:blocked").routeId("blockedRoute").process(e -> release.await(20, TimeUnit.SECONDS)).id("blocked");
                from("direct:throws").routeId("throwsRoute").process(new BrokenUserCode.ThrowsInProcess()).id("throws");
                from("direct:splitThrows").routeId("splitThrowsRoute").split(body().tokenize(","))
                        .process(new BrokenUserCode.ThrowsInProcess()).id("partThrows").end();
                from("direct:sharedPartThrows").routeId("sharedPartThrowsRoute").split(body().tokenize(","))
                        .shareUnitOfWork().process(new BrokenUserCode.ThrowsInProcess()).id("sharedPartThrows").end();
                from("direct:partLost").routeId("partLostRoute").split(body().tokenize(","))
                        .process(new BrokenUserCode.NeverCallsBack()).id("partLost").end();
                from("direct:splitSeda").routeId("splitSedaRoute").split(body().tokenize(",")).to("seda:audit").id("toAudit")
                        .end().to("mock:splitSeda");
                from("seda:audit").routeId("auditRoute").process(new BrokenUserCode.NeverCallsBack()).id("auditLost");
                from("direct:fine").routeId("fine").split(body().tokenize(",")).to("mock:part").end().to("mock:fine");
            }
        };
    }

    @Test
    void aCallbackThatDoesNotComeIsAWarningThatNamesTheProcessor() {
        template.asyncSendBody("direct:lost", "x");

        String text = awaitWarnings(1).get(0);
        assertTrue(text.startsWith("Exchange sanitizer: callback still pending (R1, a warning)\n"), text);
        assertTrue(text.contains(", route lostRoute, step lost, processor " + BrokenUserCode.NeverCallsBack.class.getName()),
                text);
        assertTrue(text.contains("process returned false, and the callback has not been called"), text);
        // no violation: the test passes
        assertEquals(0, sanitizer().getViolations().size());
    }

    @Test
    void onlyTheInnermostCallIsReported() {
        template.asyncSendBody("direct:outer", "x");

        String text = awaitWarnings(1).get(0);
        assertTrue(text.contains(", route inner, step innerLost, processor "), text);
        assertTrue(text.contains("enclosing calls still pending: route outer step toInner"), text);
        // the to step that waits for it is not reported, also later
        await().pollDelay(600, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, sanitizer().getStillPendingCount()));
    }

    @Test
    void aStepThatHasNotReturnedShowsWhereItWaits() {
        template.asyncSendBody("direct:blocked", "x");

        String text = awaitWarnings(1).get(0);
        assertTrue(text.contains(", route blockedRoute, step blocked, processor "), text);
        assertTrue(text.contains("process has not returned: thread "), text);
        // the stack of the thread that is still in process
        assertTrue(text.contains("at " + SanitizerStillPendingTest.class.getName()), text);
        release.countDown();
        await().atMost(5, TimeUnit.SECONDS).until(() -> sanitizer().getPendingCalls().size() == 0);
    }

    @Test
    void theCallsStillPendingAreTakenWhenTheContextStops() {
        template.asyncSendBody("direct:outer", "x");
        await().atMost(5, TimeUnit.SECONDS).until(() -> sanitizer().getPendingCalls().size() == 2);

        Sanitizer sanitizer = sanitizer();
        context.stop();
        // listed in the log, the innermost first, with the processor of the user, and no longer watched
        String list = sanitizer.getStillPendingAtStop();
        assertNotNull(list);
        assertTrue(list.contains("2 calls of steps still pending when CamelContext " + context.getName() + " stopped"),
                list);
        String inner = ", route inner, step innerLost, processor " + BrokenUserCode.NeverCallsBack.class.getName()
                       + ": called ";
        assertTrue(list.contains(inner), list);
        assertTrue(list.indexOf(inner) < list.indexOf(", route outer, step toInner, processor "), list);
        assertEquals(0, sanitizer.getPendingCalls().size());
    }

    @Test
    void aRouteThatCompletesLeavesNothingPending() throws Exception {
        getMockEndpoint("mock:part").expectedMessageCount(3);
        getMockEndpoint("mock:fine").expectedMessageCount(1);

        template.sendBody("direct:fine", "a,b,c");

        assertMockEndpointsSatisfied();
        assertEquals(0, sanitizer().getPendingCalls().size());
        assertEquals(0, sanitizer().getStillPendingCount());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void aCallThatThrewIsNoLongerPendingOnceTheExchangeIsDone() {
        // Camel catches the throw and fails the exchange: R1 reports the throw, and the exchange is done
        Exchange out = template.send("direct:throws", e -> e.getMessage().setBody("x"));
        assertNotNull(out.getException());

        // told by the notifier that rule R2 (on by default in a test) shares
        assertNotNull(sanitizer().getCommits());
        assertEquals(0, sanitizer().getPendingCalls().size());
        await().pollDelay(400, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, sanitizer().getStillPendingCount()));
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "partThrows")
    void aPartWhoseStepThrewIsNoLongerPendingOnceThePartIsDone() {
        Exchange out = template.send("direct:splitThrows", e -> e.getMessage().setBody("a"));
        assertNotNull(out.getException());

        assertEquals(0, sanitizer().getPendingCalls().size());
        await().pollDelay(400, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, sanitizer().getStillPendingCount()));
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "sharedPartThrows")
    void aPartThatSharesTheUnitOfWorkIsNoLongerPendingOnceItsExchangeIsDone() {
        // the parts of a split with shareUnitOfWork() have no done event of their own: the done event of the exchange
        // drops them
        Exchange out = template.send("direct:sharedPartThrows", e -> e.getMessage().setBody("a"));
        assertNotNull(out.getException());

        assertEquals(0, sanitizer().getPendingCalls().size());
        await().pollDelay(400, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(0, sanitizer().getStillPendingCount()));
    }

    @Test
    void aStepOfAPartThatDoesNotCallBackIsReportedNotTheSplit() {
        template.asyncSendBody("direct:partLost", "a");

        String text = awaitWarnings(1).get(0);
        assertTrue(text.contains(", route partLostRoute, step partLost, processor "), text);
        await().pollDelay(600, TimeUnit.MILLISECONDS).atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> assertEquals(1, sanitizer().getStillPendingCount()));
    }

    @Test
    void theCopyThatASedaProducerQueuesIsAnExchangeOfItsOwn() throws Exception {
        getMockEndpoint("mock:splitSeda").expectedMessageCount(1);

        template.sendBody("direct:splitSeda", "a");

        // the split is done; the copy queued to seda hangs in its own route, and is reported there
        assertMockEndpointsSatisfied();
        String text = awaitWarnings(1).get(0);
        assertTrue(text.contains(", route auditRoute, step auditLost, processor "), text);
    }

    @Test
    void aHangAfterTheContextIsStartedAgainIsReported() {
        context.stop();
        context.start();
        template.asyncSendBody("direct:lost", "x");

        String text = awaitWarnings(1).get(0);
        assertTrue(text.contains(", route lostRoute, step lost, processor "), text);
    }

    private Sanitizer sanitizer() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer);
        return sanitizer;
    }

    private List<String> awaitWarnings(int n) {
        await().atMost(10, TimeUnit.SECONDS).until(() -> sanitizer().getStillPendingCount() >= n);
        return sanitizer().getStillPendingReports();
    }
}
