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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.sanitizer.junit.SanitizerViolationError;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * How the JUnit extension fails a test: it runs test classes of its own (the nested classes below, which the build does
 * not run by themselves) and checks their results.
 */
class CamelSanitizerExtensionTest {

    @Test
    void aTestWhoseRouteBreaksTheRuleFailsWithTheReport() {
        TestExecutionSummary summary = run(BreaksTheRule.class);
        assertEquals(2, summary.getTestsSucceededCount() + summary.getTestsFailedCount());
        assertEquals(1, summary.getTestsFailedCount());
        TestExecutionSummary.Failure failure = summary.getFailures().get(0);
        assertEquals("sendsToTheBrokenStep()", failure.getTestIdentifier().getDisplayName());
        SanitizerViolationError error = assertInstanceOf(SanitizerViolationError.class, failure.getException());
        assertEquals(1, error.getViolations().size());
        assertTrue(error.getMessage().contains("process threw before calling the callback (R1)"), error.getMessage());
        assertTrue(error.getMessage().contains("step broken, processor " + BrokenUserCode.ThrowsInProcess.class.getName()),
                error.getMessage());
    }

    @Test
    void aTestThatFailsOnItsOwnKeepsTheReportAsSuppressed() {
        TestExecutionSummary summary = run(FailsOnItsOwn.class);
        assertEquals(1, summary.getTestsFailedCount());
        Throwable failure = summary.getFailures().get(0).getException();
        assertEquals("its own failure", failure.getMessage());
        assertEquals(1, failure.getSuppressed().length);
        assertInstanceOf(SanitizerViolationError.class, failure.getSuppressed()[0]);
    }

    @Test
    void anExpectedViolationPassesAndAMissingOneFails() {
        TestExecutionSummary summary = run(Expectations.class);
        assertEquals(1, summary.getTestsSucceededCount());
        assertEquals(1, summary.getTestsFailedCount());
        TestExecutionSummary.Failure failure = summary.getFailures().get(0);
        assertEquals("expectsAViolationThatDoesNotHappen()", failure.getTestIdentifier().getDisplayName());
        assertTrue(failure.getException().getMessage()
                .contains("did not report the expected violation COMPLETED_TWICE at step broken"),
                failure.getException().getMessage());
    }

    @Test
    void aViolationAfterTheTestsFailsTheClass() {
        TestExecutionSummary summary = run(LateViolation.class);
        assertEquals(1, summary.getTestsSucceededCount());
        List<TestExecutionSummary.Failure> failures = summary.getFailures();
        assertEquals(1, failures.size());
        // the failure of the class container, not of its test
        assertTrue(failures.get(0).getTestIdentifier().getDisplayName().endsWith("LateViolation"),
                failures.get(0).getTestIdentifier().getDisplayName());
        SanitizerViolationError error = assertInstanceOf(SanitizerViolationError.class, failures.get(0).getException());
        assertEquals(Violation.COMPLETED_TWICE, error.getViolations().get(0).getKind());
        assertTrue(error.getMessage().contains("outside its tests"), error.getMessage());
    }

    @Test
    void parameterizedAndRepeatedTestsAreChargedPerInvocation() {
        TestExecutionSummary summary = run(Invocations.class);
        // each invocation of the parameterized test meets its expectation, each repetition fails with its own report
        assertEquals(2, summary.getTestsSucceededCount());
        assertEquals(2, summary.getTestsFailedCount());
        for (TestExecutionSummary.Failure failure : summary.getFailures()) {
            assertTrue(failure.getTestIdentifier().isTest(), failure.getTestIdentifier().getDisplayName());
            assertInstanceOf(SanitizerViolationError.class, failure.getException());
        }
    }

    @Test
    void theThreadIsNotMarkedAfterTheClass() {
        run(BreaksTheRule.class);
        // the classes ran on this thread; their mark is gone, so a context built here now is not tied to them
        assertEquals(SanitizerTestSession.current(), markBefore);
    }

    private final SanitizerTestSession markBefore = SanitizerTestSession.current();

    private static TestExecutionSummary run(Class<?> testClass) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request().selectors(selectClass(testClass))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.execute(request, listener);
        TestExecutionSummary summary = listener.getSummary();
        assertNotNull(summary);
        assertFalse(summary.getTestsFoundCount() == 0, "no test found in " + testClass);
        return summary;
    }

    abstract static class BrokenRouteSupport extends ContextTestSupport {
        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:broken").process(new BrokenUserCode.ThrowsInProcess()).id("broken");
                    from("direct:good").to("mock:good");
                }
            };
        }
    }

    @CamelSanitizer
    static class BreaksTheRule extends BrokenRouteSupport {
        @Test
        void sendsToTheBrokenStep() {
            template.send("direct:broken", e -> e.getMessage().setBody("x"));
        }

        @Test
        void sendsToTheGoodRoute() {
            template.sendBody("direct:good", "x");
        }
    }

    @CamelSanitizer
    static class FailsOnItsOwn extends BrokenRouteSupport {
        @Test
        void sendsAndFails() {
            template.send("direct:broken", e -> e.getMessage().setBody("x"));
            throw new AssertionError("its own failure");
        }
    }

    @CamelSanitizer
    static class Expectations extends BrokenRouteSupport {
        @Test
        @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "broken")
        void expectsTheViolation() {
            template.send("direct:broken", e -> e.getMessage().setBody("x"));
        }

        @Test
        @ExpectSanitizerViolation(value = Violation.COMPLETED_TWICE, step = "broken")
        void expectsAViolationThatDoesNotHappen() {
            template.sendBody("direct:good", "x");
        }
    }

    @CamelSanitizer
    static class Invocations extends BrokenRouteSupport {
        @ParameterizedTest
        @ValueSource(strings = { "a", "b" })
        @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "broken")
        void expectsTheViolationEachTime(String body) {
            template.send("direct:broken", e -> e.getMessage().setBody(body));
        }

        @RepeatedTest(2)
        void breaksTheRuleEachTime() {
            template.send("direct:broken", e -> e.getMessage().setBody("x"));
        }
    }

    @CamelSanitizer
    static class LateViolation extends ContextTestSupport {
        static final BrokenUserCode.CompletesAgainLater LATE = new BrokenUserCode.CompletesAgainLater();

        @Override
        protected RouteBuilder createRouteBuilder() {
            return new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:late").process(LATE).id("late");
                }
            };
        }

        @Test
        void sendsAndEndsBeforeTheSecondCallback() {
            template.sendBody("direct:late", "x");
        }

        @AfterAll
        static void callsTheCallbackAgain() {
            // the test has ended; the second callback comes now, and is charged to the class
            LATE.completeAgain();
        }
    }
}
