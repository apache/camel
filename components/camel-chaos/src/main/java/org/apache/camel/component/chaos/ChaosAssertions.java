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

import java.time.Duration;
import java.util.Objects;

import org.apache.camel.CamelContext;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * Assertions for chaos in a test (CAMEL-26724): a finding of a chaos session becomes a regression test. They throw an
 * {@link AssertionError} with the report, so they work with any test framework.
 * <p/>
 * Chaos is switched on in a test with {@code camel.chaos.enabled=true} (and {@code camel.chaos.seed} for the same
 * faults each time), with the rules of {@code camel.chaos.rules} or {@code camel.chaos.scenario}, or added with
 * {@link ChaosManager#addRule(String)}.
 *
 * <pre>
 * ChaosAssertions.assertVerdict(context, ChaosReport.PASS, Duration.ofSeconds(10));
 * ChaosAssertions.assertExpectationMet(context, "r1", Duration.ofSeconds(10));
 * ChaosAssertions.assertFired(context, "r1", 3);
 * </pre>
 *
 * @since 4.24
 */
public final class ChaosAssertions {

    // how often a report is read while waiting for it to be final
    private static final long POLL_MILLIS = 50;

    private ChaosAssertions() {
    }

    /**
     * Waits until the report is final, which is when no expectation is pending any more (an expectation of at least so
     * many exchanges is decided when they completed; one of at most so many, when its time is up).
     *
     * @param  camelContext   the context, with chaos switched on
     * @param  timeout        how long to wait at most
     * @return                the final report
     * @throws AssertionError when chaos is off, or the report is still pending after the timeout
     */
    public static ChaosReport awaitFinalReport(CamelContext camelContext, Duration timeout) {
        ChaosManager chaos = manager(camelContext);
        long deadline = System.nanoTime() + timeout.toNanos();
        ChaosReport report = chaos.report();
        while (!report.isFinal()) {
            if (System.nanoTime() - deadline >= 0) {
                throw new AssertionError(
                        "The chaos report is still pending after " + timeout + ": an expectation is not decided yet\n"
                                         + report.toText());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the chaos report to be final", e);
            }
            report = chaos.report();
        }
        return report;
    }

    /**
     * Asserts the verdict of the report as it is now, which must be final.
     *
     * @param camelContext the context, with chaos switched on
     * @param verdict      {@link ChaosReport#PASS} or {@link ChaosReport#FAIL}
     */
    public static void assertVerdict(CamelContext camelContext, String verdict) {
        ChaosReport report = manager(camelContext).report();
        if (!report.isFinal()) {
            throw new AssertionError("The chaos report is not final: an expectation is pending\n" + report.toText());
        }
        checkVerdict(report, verdict);
    }

    /**
     * Waits until the report is final, and asserts its verdict.
     *
     * @param camelContext the context, with chaos switched on
     * @param verdict      {@link ChaosReport#PASS} or {@link ChaosReport#FAIL}
     * @param timeout      how long to wait at most for the report to be final
     */
    public static void assertVerdict(CamelContext camelContext, String verdict, Duration timeout) {
        checkVerdict(awaitFinalReport(camelContext, timeout), verdict);
    }

    /**
     * Waits until the expectation of a rule is decided, and asserts that it is met.
     *
     * @param camelContext the context, with chaos switched on
     * @param ruleId       the id of the rule with the expectation, such as {@code r1}
     * @param timeout      how long to wait at most for the expectation to be decided
     */
    public static void assertExpectationMet(CamelContext camelContext, String ruleId, Duration timeout) {
        ChaosManager chaos = manager(camelContext);
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            ChaosReport report = chaos.report();
            JsonObject rule = rule(report, ruleId);
            if (!(rule.get("expectation") instanceof JsonObject expectation)) {
                throw new AssertionError("Chaos rule " + ruleId + " has no expectation (expect=)\n" + report.toText());
            }
            String outcome = expectation.getString("outcome");
            if ("met".equals(outcome)) {
                return;
            }
            if (!"pending".equals(outcome) || System.nanoTime() - deadline >= 0) {
                throw new AssertionError(
                        "The expectation of chaos rule " + ruleId + " is " + outcome
                                         + ("pending".equals(outcome) ? " after " + timeout : "") + ", not met\n"
                                         + report.toText());
            }
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the expectation of chaos rule " + ruleId, e);
            }
        }
    }

    /**
     * Asserts that a rule fired at least so many faults, as the report says now: it does not wait, so send the messages
     * first (a fault fires as a message is sent).
     *
     * @param camelContext the context, with chaos switched on
     * @param ruleId       the id of the rule, such as {@code r1}
     * @param atLeast      the least number of faults
     */
    public static void assertFired(CamelContext camelContext, String ruleId, long atLeast) {
        ChaosReport report = manager(camelContext).report();
        JsonObject rule = rule(report, ruleId);
        long fired = 0;
        if (rule.get("fired") instanceof JsonObject perHook) {
            for (Object count : perHook.values()) {
                fired += count instanceof Number n ? n.longValue() : 0;
            }
        }
        if (fired < atLeast) {
            throw new AssertionError(
                    "Chaos rule " + ruleId + " fired " + fired + (fired == 1 ? " fault" : " faults") + ", expected at least "
                                     + atLeast + "\n" + report.toText());
        }
    }

    private static void checkVerdict(ChaosReport report, String verdict) {
        if (!Objects.equals(verdict, report.getVerdict())) {
            throw new AssertionError(
                    "The chaos verdict is " + report.getVerdict() + ", expected " + verdict
                                     + (report.getReasons().isEmpty() ? "" : ": " + String.join("; ", report.getReasons()))
                                     + "\n" + report.toText());
        }
    }

    /** The latest version of a rule in the report. */
    private static JsonObject rule(ChaosReport report, String ruleId) {
        // the report lists the versions of an id from the latest
        if (report.toJson().get("rules") instanceof JsonArray rules) {
            for (Object o : rules) {
                if (o instanceof JsonObject rule && ruleId.equals(rule.getString("id"))) {
                    return rule;
                }
            }
        }
        throw new AssertionError("There is no chaos rule " + ruleId + "\n" + report.toText());
    }

    private static ChaosManager manager(CamelContext camelContext) {
        ChaosManager chaos = ChaosManager.get(camelContext);
        if (chaos == null) {
            throw new AssertionError(
                    "Chaos is off in this CamelContext: switch it on with camel.chaos.enabled=true (and add camel-chaos)");
        }
        return chaos;
    }
}
