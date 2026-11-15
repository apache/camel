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
package org.apache.camel.dsl.jbang.core.commands.process;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.common.CommandLineTestHelper;
import org.apache.camel.dsl.jbang.run.common.StringPrinter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * camel get sanitizer: the violations of each integration from the sanitizer dev console it answers with, or the report
 * with --report, as a table or as JSON; and why the sanitizer is off.
 */
@ExtendWith(MockitoExtension.class)
class ListSanitizerTest extends ProcessCommandTestSupport {

    private static final String REPORT = """
            Exchange sanitizer: callback called twice (R1)
              exchange EX-1, route orders, step lookup, processor com.acme.PriceLookup
              first call of the callback: thread price-timeout-1
                  at com.acme.PriceLookup.lambda$process$0(PriceLookup.java:41)
              second call of the callback: thread price-client-3
                  at com.acme.PriceLookup.lambda$process$1(PriceLookup.java:47)
              Usual fix: let only the first path call the callback.""";

    private static final String STILL_PENDING = """
            Exchange sanitizer: callback still pending (R1, a warning)
              exchange EX-3, route orders, step quote, processor com.acme.QuoteClient
              process returned false, and the callback has not been called""";

    @Test
    void theViolations() throws Exception {
        assertThat(run(sanitizerOn())).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("RULE", "VIOLATION", "ROUTE", "STEP", "PROCESSOR", "EXCHANGE");
        String line = lineOf(out, "EX-1");
        assertThat(line).contains("orders", "R1", "callback called twice", "lookup", "com.acme.PriceLookup");
        // the stacks are only in the report
        assertThat(out).doesNotContain("price-timeout-1");
        assertThat(printer.getStderr()).isEmpty();
    }

    @Test
    void theViolationsAsJson() throws Exception {
        assertThat(run(sanitizerOn(), "--json")).isZero();

        JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getStdout());
        assertThat(rows).hasSize(1);
        JsonObject row = rows.getMap(0);
        assertThat(row.getLong("pid")).isEqualTo(TEST_PID);
        assertThat(row.getString("name")).isEqualTo("orders");
        assertThat(row.getString("rule")).isEqualTo("R1");
        assertThat(row.getString("kind")).isEqualTo("COMPLETED_TWICE");
        assertThat(row.getString("routeId")).isEqualTo("orders");
        assertThat(row.getString("stepId")).isEqualTo("lookup");
        assertThat(row.getString("processor")).isEqualTo("com.acme.PriceLookup");
        assertThat(row.getString("exchangeId")).isEqualTo("EX-1");
        assertThat(row).doesNotContainKey("report");
    }

    @Test
    void theReportWithTheCountsAndTheStacks() throws Exception {
        assertThat(run(sanitizerOn(), "--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("orders (pid " + TEST_PID + "): 1 violations in 42 calls of steps checked, R2 not checked"
                                 + " (camel.sanitizer.checks=callback,commit)");
        // the kinds at the level of the rules, so a kind of R1 does not look as if it were of the rule above it
        assertThat(out).contains("  R1: 1").contains("\n  COMPLETED_TWICE: 1").doesNotContain("    COMPLETED_TWICE")
                .doesNotContain("THREW_BEFORE_CALLBACK");
        // without the commits followed, R2 is not checked: no count of 0 for it
        assertThat(out).doesNotContain("  R2:");
        assertThat(out).contains("first call of the callback: thread price-timeout-1",
                "at com.acme.PriceLookup.lambda$process$1(PriceLookup.java:47)");
    }

    @Test
    void theReportAsJson() throws Exception {
        assertThat(run(sanitizerOn(), "--report", "--json")).isZero();

        JsonArray answer = (JsonArray) Jsoner.deserialize(printer.getStdout());
        JsonObject report = answer.getMap(0);
        assertThat(report.getString("name")).isEqualTo("orders");
        assertThat(report.getLong("calls")).isEqualTo(42);
        assertThat(report.getLong("total")).isEqualTo(1);
        assertThat(((JsonObject) report.get("rules")).getLong("R1")).isEqualTo(1);
        JsonObject violation = (JsonObject) ((JsonArray) report.get("violations")).get(0);
        assertThat(violation.getString("report")).isEqualTo(REPORT);
    }

    @Test
    void aViolationOfR2WithoutAStep() throws Exception {
        assertThat(run(withCommitAndStillPending())).isZero();

        String line = lineOf(printer.getStdout(), "EX-2");
        assertThat(line).contains("R2", "commit never ran", "files", "com.acme.FileCommit");
        assertThat(printer.getStderr())
                .contains("orders (pid " + TEST_PID + "): 1 calls reported still pending (see --report)");

        assertThat(run(withCommitAndStillPending(), "--json")).isZero();
        JsonArray rows = (JsonArray) Jsoner.deserialize(printer.getStdout());
        assertThat(rows).hasSize(2);
        JsonObject row = rows.getMap(1);
        assertThat(row.getString("rule")).isEqualTo("R2");
        assertThat(row.getString("kind")).isEqualTo("COMMIT_NEVER_RUN");
        assertThat(row.getString("processor")).isEqualTo("com.acme.FileCommit");
        assertThat(row).doesNotContainKey("stepId");
    }

    @Test
    void theReportWithTheCommitsAndTheStillPendingWarnings() throws Exception {
        assertThat(run(withCommitAndStillPending(), "--report")).isZero();

        String out = printer.getStdout();
        assertThat(out).contains("orders (pid " + TEST_PID + "): 2 violations in 42 calls of steps checked, 7 commits"
                                 + " followed, 1 calls reported still pending (warnings), 1 pending now");
        assertThat(out).contains("  R1: 1", "  R2: 1", "\n  COMMIT_NEVER_RUN: 1").doesNotContain("    COMMIT_NEVER_RUN");
        assertThat(out).contains(STILL_PENDING);

        assertThat(run(withCommitAndStillPending(), "--report", "--json")).isZero();
        JsonObject report = ((JsonArray) Jsoner.deserialize(printer.getStdout())).getMap(0);
        assertThat(report.getLong("commits")).isEqualTo(7);
        assertThat(report.getLong("stillPending")).isEqualTo(1);
        assertThat(report.getLong("pendingCalls")).isEqualTo(1);
        assertThat((JsonArray) report.get("stillPendingWarnings")).containsExactly(STILL_PENDING);
    }

    @Test
    void stillPendingWarningsWithoutViolationsAreSaid() throws Exception {
        JsonObject sanitizer = withCommitAndStillPending();
        sanitizer.put("total", 0L);
        sanitizer.put("violations", new JsonArray());

        assertThat(run(sanitizer)).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("orders (pid " + TEST_PID + "): no violations (42 calls of steps"
                                                 + " checked), 1 calls reported still pending (see --report)");
    }

    @Test
    void theViolationsCountedButNotListedAreSaid() throws Exception {
        JsonObject sanitizer = sanitizerOn();
        sanitizer.put("total", 1003L);

        assertThat(run(sanitizer)).isZero();
        assertThat(printer.getStderr()).contains("orders (pid " + TEST_PID + "): 1002 more violations are counted");

        assertThat(run(sanitizer, "--report")).isZero();
        assertThat(printer.getStdout()).contains("(1002 more violations are counted, but not listed)");
    }

    @Test
    void noViolationsSaysSo() throws Exception {
        JsonObject sanitizer = sanitizerOn();
        sanitizer.put("total", 0L);
        sanitizer.put("violations", new JsonArray());

        assertThat(run(sanitizer)).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("orders (pid " + TEST_PID + "): no violations (42 calls of steps checked)");
    }

    @Test
    void sanitizerOff() throws Exception {
        assertThat(run(new JsonObject(
                Map.of("enabled", false, "reason", "The sanitizer is off: it stays off with profile prod")))).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr())
                .contains("orders (pid " + TEST_PID + "): The sanitizer is off: it stays off with profile prod");
    }

    @Test
    void sanitizerOffReportAsJson() throws Exception {
        assertThat(run(new JsonObject(Map.of("enabled", false, "reason", "off")), "--report", "--json")).isZero();

        assertThat((JsonArray) Jsoner.deserialize(printer.getStdout())).isEmpty();
    }

    @Test
    void withoutCamelSanitizer() throws Exception {
        // the integration answers with nothing: it has no camel-sanitizer, or does not know the action
        assertThat(run(new JsonObject())).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr())
                .contains("No running integration has camel-sanitizer: run it with camel run --sanitizer");
    }

    @Test
    void anIntegrationThatDidNotAnswerIsSaid() throws Exception {
        assertThat(run(null)).isZero();

        assertThat(printer.getStdout()).isEmpty();
        assertThat(printer.getStderr()).contains("orders (pid " + TEST_PID + "): did not answer in time")
                .doesNotContain("No running integration has camel-sanitizer");
    }

    @Test
    void anIntegrationBeforeCamel424IsNotAsked() throws Exception {
        printer = new StringPrinter();
        ListSanitizer command = new ListSanitizer(new CamelJBangMain().withPrinter(printer)) {
            @Override
            JsonObject loadSanitizer(long pid) {
                throw new AssertionError("an integration before Camel 4.24 cannot have camel-sanitizer");
            }
        };
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "orders", "version", "4.23.0")));

        assertThat(run(command, root)).isZero();

        // camel run --sanitizer would not help an integration before Camel 4.24
        assertThat(printer.getStderr())
                .contains("No running integration has camel-sanitizer: it is new in Camel 4.24, and they are older")
                .doesNotContain("camel run --sanitizer");
    }

    @Test
    void theTableAsksWithoutTheReports() throws Exception {
        ListSanitizer command = new ListSanitizer(new CamelJBangMain().withPrinter(new StringPrinter()));
        CommandLineTestHelper.parse(command);
        assertThat(command.sanitizerRequest().getString("action")).isEqualTo("sanitizer");
        // the table and its JSON do not show the reports of the violations or the still pending warnings
        assertThat(command.sanitizerRequest().getString("report")).isEqualTo("false");

        command = new ListSanitizer(new CamelJBangMain().withPrinter(new StringPrinter()));
        CommandLineTestHelper.parse(command, "--report");
        assertThat(command.sanitizerRequest()).doesNotContainKey("report");
    }

    private int run(JsonObject answer, String... args) throws Exception {
        // a fresh output for each run of a test
        printer = new StringPrinter();
        // as the action reply carries it: JSON text, so the numbers are BigDecimal
        JsonObject reply = answer != null ? (JsonObject) Jsoner.deserialize(answer.toJson()) : null;
        ListSanitizer command = new ListSanitizer(new CamelJBangMain().withPrinter(printer)) {
            @Override
            JsonObject loadSanitizer(long pid) {
                return reply;
            }
        };
        CommandLineTestHelper.parse(command, args);
        JsonObject root = new JsonObject();
        root.put("context", new JsonObject(Map.of("name", "orders")));
        return run(command, root);
    }

    private static int run(ListSanitizer command, JsonObject status) throws Exception {
        writeStatusFile(TEST_PID, status);
        try (MockedStatic<ProcessHandle> mocked = mockStatic(ProcessHandle.class)) {
            ProcessHandle ph = mockProcessHandle(TEST_PID);
            ProcessHandle currentHandle = mockCurrentHandle();
            mocked.when(ProcessHandle::current).thenReturn(currentHandle);
            mocked.when(ProcessHandle::allProcesses).thenAnswer(inv -> Stream.of(ph));
            return command.doCall();
        }
    }

    private static String lineOf(String out, String text) {
        return out.lines().filter(l -> l.contains(text)).findFirst().orElseThrow();
    }

    /**
     * The sanitizer dev console of an integration with R2 and the still pending warning on: a violation of R1, one of
     * R2 found where no step ran, and one still pending warning.
     */
    private static JsonObject withCommitAndStillPending() {
        JsonObject sanitizer = sanitizerOn();
        JsonObject violation = new JsonObject();
        violation.put("rule", "R2");
        violation.put("kind", "COMMIT_NEVER_RUN");
        violation.put("title", "commit never ran");
        violation.put("exchangeId", "EX-2");
        violation.put("routeId", "files");
        violation.put("processor", "com.acme.FileCommit");
        violation.put("report", "Exchange sanitizer: commit never ran (R2)");
        ((JsonArray) sanitizer.get("violations")).add(violation);
        ((JsonObject) sanitizer.get("kinds")).put("COMMIT_NEVER_RUN", 1L);
        sanitizer.put("total", 2L);
        sanitizer.put("rules", new JsonObject(Map.of("R1", 1L, "R2", 1L)));
        sanitizer.put("commits", 7L);
        sanitizer.put("pendingCalls", 1L);
        sanitizer.put("stillPending", 1L);
        sanitizer.put("stillPendingWarnings", new JsonArray(List.of(STILL_PENDING)));
        return sanitizer;
    }

    /** The sanitizer dev console of an integration with one violation, as camel-sanitizer makes it. */
    private static JsonObject sanitizerOn() {
        JsonObject violation = new JsonObject();
        violation.put("rule", "R1");
        violation.put("kind", "COMPLETED_TWICE");
        violation.put("title", "callback called twice");
        violation.put("exchangeId", "EX-1");
        violation.put("routeId", "orders");
        violation.put("stepId", "lookup");
        violation.put("processor", "com.acme.PriceLookup");
        violation.put("report", REPORT);
        JsonObject kinds = new JsonObject();
        kinds.put("COMPLETED_TWICE", 1L);
        kinds.put("THREW_BEFORE_CALLBACK", 0L);
        JsonObject sanitizer = new JsonObject();
        sanitizer.put("enabled", true);
        sanitizer.put("calls", 42L);
        sanitizer.put("total", 1L);
        // as the console lists it with R2 not checked (the default outside a test): every rule, and no commits
        sanitizer.put("rules", new JsonObject(Map.of("R1", 1L, "R2", 0L)));
        sanitizer.put("kinds", kinds);
        sanitizer.put("violations", new JsonArray(List.of(violation)));
        return sanitizer;
    }
}
