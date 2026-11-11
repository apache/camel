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
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rule R1 on processors and strategies of a user that break the contract of AsyncProcessor: each test fails with a
 * report that names the step and the class, and says so with {@link ExpectSanitizerViolation}. The tests without it
 * check what Camel handles so that no rule is broken, such as an Error (not a VirtualMachineError) of a plain
 * Processor.
 */
@CamelSanitizer
class SanitizerUserCodeTest extends ContextTestSupport {

    private final BrokenUserCode.CompletesTwice completesTwice = new BrokenUserCode.CompletesTwice();
    private final BrokenUserCode.ThrowsThenCallsBack throwsThenCallsBack = new BrokenUserCode.ThrowsThenCallsBack();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addEndpoint("twice:x", new BrokenUserCode.TwiceEndpoint("twice:x", context));
        context.getRegistry().bind("throwsByRef", new BrokenUserCode.ThrowsInProcess());
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:twice").routeId("twice").process(completesTwice).id("lookup").to("mock:twice");
                from("direct:producer").to("twice:x").id("send");
                from("direct:byRef").process("throwsByRef").id("byRef");
                from("direct:throws").process(new BrokenUserCode.ThrowsInProcess()).id("throws").to("mock:throws");
                from("direct:after").process(new BrokenUserCode.ThrowsAfterCallback()).id("after");
                // no id: the step has the id Camel generates, as process1
                from("direct:autoId").routeId("autoId").process(new BrokenUserCode.ThrowsInProcess());
                from("direct:plainError").process(new BrokenUserCode.ThrowsError()).id("plainError");
                from("direct:beanError").routeId("beanRoute").bean(new BrokenUserCode.ThrowsError()).id("beanError");
                from("direct:late").process(throwsThenCallsBack).id("late");
                from("direct:aggregateError")
                        .aggregate(constant(true), new BrokenUserCode.ErrorStrategy()).completionSize(2).id("aggregate")
                        .to("mock:aggregated");
                from("direct:splitRuntime")
                        .split(body().tokenize(","), new BrokenUserCode.RuntimeExceptionStrategy()).id("split")
                        .to("mock:part");
            }
        };
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.COMPLETED_TWICE, step = "lookup")
    void aCallbackCalledTwiceIsReportedWithBothThreads() throws Exception {
        template.sendBody("direct:twice", "price?");
        assertTrue(completesTwice.secondDone.await(10, TimeUnit.SECONDS));

        SanitizerViolation v = awaitOne(Violation.COMPLETED_TWICE);
        assertEquals("lookup", v.getStepId());
        assertEquals("twice", v.getRouteId());
        // the report looks through the delegate Camel puts around an AsyncProcessor that is not a Service
        assertEquals(BrokenUserCode.CompletesTwice.class.getName(), v.getProcessor());
        assertTrue(v.getText().contains("first call of the callback: thread user-timeout"), v.getText());
        assertTrue(v.getText().contains("second call of the callback: thread user-reply"), v.getText());
        // each stack starts at the code of the user that called the callback
        assertTrue(v.getText().contains("at " + BrokenUserCode.CompletesTwice.class.getName() + ".lambda$process$"),
                v.getText());
        assertTrue(v.getText().contains("compareAndSet"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.COMPLETED_TWICE, step = "send")
    void aProducerOfACustomComponentThatCompletesTwiceIsReportedWithItsEndpoint() {
        template.send("direct:producer", e -> e.getMessage().setBody("x"));

        SanitizerViolation v = awaitOne(Violation.COMPLETED_TWICE);
        assertEquals("send", v.getStepId());
        assertEquals("org.apache.camel.processor.SendProcessor", v.getProcessor());
        // the producer is behind the endpoint the step sends to
        assertTrue(
                v.getText()
                        .matches("(?s).*endpoint twice:(//)?x \\("
                                 + BrokenUserCode.TwiceEndpoint.class.getName().replace("$", "\\$") + "\\).*"),
                v.getText());
        assertTrue(v.getText().contains("at " + BrokenUserCode.TwiceEndpoint.class.getName()), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void aProcessorThatThrowsIsReported() {
        Exchange out = template.send("direct:throws", e -> e.getMessage().setBody("x"));
        // Camel fails the exchange with what was thrown; the processor still broke the contract
        assertNotNull(out.getException());

        SanitizerViolation v = awaitOne(Violation.THREW_BEFORE_CALLBACK);
        assertEquals("throws", v.getStepId());
        assertEquals(BrokenUserCode.ThrowsInProcess.class.getName(), v.getProcessor());
        assertTrue(v.getText().contains("java.lang.IllegalStateException: Forced by ThrowsInProcess"), v.getText());
        assertTrue(v.getText().contains("at " + BrokenUserCode.ThrowsInProcess.class.getName() + ".process"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "byRef")
    void aProcessorGivenByReferenceIsNamed() {
        template.send("direct:byRef", e -> e.getMessage().setBody("x"));

        SanitizerViolation v = awaitOne(Violation.THREW_BEFORE_CALLBACK);
        assertEquals(BrokenUserCode.ThrowsInProcess.class.getName(), v.getProcessor());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_AFTER_CALLBACK, step = "after")
    void aProcessorThatThrowsAfterItsCallbackIsReported() {
        template.send("direct:after", e -> e.getMessage().setBody("x"));

        SanitizerViolation v = awaitOne(Violation.THREW_AFTER_CALLBACK);
        assertEquals(BrokenUserCode.ThrowsAfterCallback.class.getName(), v.getProcessor());
        assertTrue(v.getText().contains("Forced by ThrowsAfterCallback"), v.getText());
    }

    @Test
    @ExpectSanitizerViolation(Violation.THREW_BEFORE_CALLBACK)
    void aStepWithAGeneratedIdIsNamed() {
        template.send("direct:autoId", e -> e.getMessage().setBody("x"));

        SanitizerViolation v = awaitOne(Violation.THREW_BEFORE_CALLBACK);
        assertEquals("autoId", v.getRouteId());
        // the id Camel gave the step when the route was added, which the processor of the user is found by
        assertTrue(v.getStepId().matches("process\\d+"), v.getStepId());
        assertEquals(context.getRouteDefinition("autoId").getOutputs().get(0).getId(), v.getStepId());
        assertEquals(BrokenUserCode.ThrowsInProcess.class.getName(), v.getProcessor());
    }

    @Test
    void anErrorOfAPlainProcessorFailsTheExchange() {
        // Camel's adapter around a plain Processor sets what it throws on the exchange, also an Error, and then calls the
        // callback: no rule is broken
        assertFailedWithTheError(template.send("direct:plainError", e -> e.getMessage().setBody("x")));
        assertEquals(0, Sanitizer.get(context).getViolations().size());
    }

    @Test
    void anErrorOfABeanThatIsAProcessorFailsTheExchange() {
        assertFailedWithTheError(template.send("direct:beanError", e -> e.getMessage().setBody("x")));
        assertEquals(0, Sanitizer.get(context).getViolations().size());
        // a report at this step would name the bean, not Camel's BeanProcessor that calls it
        assertEquals(BrokenUserCode.ThrowsError.class.getName(),
                Sanitizer.get(context).processorOf("beanRoute", "beanError"));
    }

    private static void assertFailedWithTheError(Exchange out) {
        // the exchange keeps the Error wrapped in a CamelExecutionException
        assertInstanceOf(CamelExecutionException.class, out.getException());
        assertInstanceOf(BrokenUserCode.StrategyBug.class, out.getException().getCause());
        assertEquals("Forced by ThrowsError", out.getException().getCause().getMessage());
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "late")
    @ExpectSanitizerViolation(value = Violation.CALLBACK_AFTER_THROW, step = "late")
    void aCallbackAfterTheProcessorThrewIsReported() throws Exception {
        Exchange out = template.send("direct:late", e -> e.getMessage().setBody("x"));
        assertNotNull(out.getException());
        throwsThenCallsBack.release.countDown();
        assertTrue(throwsThenCallsBack.calledBack.await(10, TimeUnit.SECONDS));

        SanitizerViolation v = awaitOne(Violation.CALLBACK_AFTER_THROW);
        assertEquals(BrokenUserCode.ThrowsThenCallsBack.class.getName(), v.getProcessor());
        assertTrue(v.getText().contains("then the callback was called: thread user-late-callback"), v.getText());
        assertEquals(1, Sanitizer.get(context).getViolationCount(Violation.THREW_BEFORE_CALLBACK));
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "aggregate")
    void anAggregationStrategyThatThrowsAnErrorIsReported() {
        template.send("direct:aggregateError", e -> e.getMessage().setBody("a"));
        // the second message completes the group, and the strategy throws an Error, which the aggregator does not catch
        template.send("direct:aggregateError", e -> e.getMessage().setBody("b"));

        SanitizerViolation v = awaitOne(Violation.THREW_BEFORE_CALLBACK);
        assertEquals("aggregate", v.getStepId());
        assertTrue(v.getText().contains(BrokenUserCode.StrategyBug.class.getName() + ": Forced by ErrorStrategy"),
                v.getText());
        // the stack of what was thrown starts in the strategy
        assertTrue(v.getText().contains("at " + BrokenUserCode.ErrorStrategy.class.getName() + ".aggregate"), v.getText());
    }

    @Test
    void anAggregationStrategyThatThrowsAnExceptionIsHandledByCamel() {
        Exchange out = template.send("direct:splitRuntime", e -> e.getMessage().setBody("a,b"));
        // the splitter catches what the strategy throws and fails the exchange: no rule is broken
        assertNotNull(out.getException());
        assertEquals(0, Sanitizer.get(context).getViolations().size());
    }

    private SanitizerViolation awaitOne(Violation kind) {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        await().atMost(Duration.ofSeconds(10)).until(() -> sanitizer.getViolationCount(kind) > 0);
        List<SanitizerViolation> found = sanitizer.getViolations().stream().filter(v -> v.getKind() == kind).toList();
        assertEquals(1, found.size(), found::toString);
        return found.get(0);
    }
}
