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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.console.DevConsole;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.engine.DefaultDevConsoleResolver;
import org.apache.camel.sanitizer.junit.CamelSanitizer;
import org.apache.camel.sanitizer.junit.ExpectSanitizerViolation;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.SynchronizationAdapter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sanitizer dev console: the violations of R1 and R2 with their stacks, the counts per rule, the commits followed
 * and the still pending warnings, as JSON and as text, without the body or the headers of the exchange; and why it is
 * off.
 */
@CamelSanitizer
class SanitizerDevConsoleTest extends ContextTestSupport {

    private static final String BODY = "body-of-the-order-4711";
    private static final String HEADER = "header-of-the-order-4712";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getPropertiesComponent().addOverrideProperty("camel.sanitizer.pendingTimeout", "200ms");
        return context;
    }

    @Override
    protected int getShutdownTimeout() {
        // the exchange that hangs on purpose is not waited for long when the context stops
        return 1;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:throws").routeId("orders").process(new BrokenUserCode.ThrowsInProcess()).id("throws");
                from("direct:ok").routeId("ok").to("mock:ok").id("toOk");
                from("direct:lost").routeId("lostRoute").process(new BrokenUserCode.NeverCallsBack()).id("lost");
                from("direct:dropCommit").routeId("dropping")
                        .process(e -> {
                            // hands the commit over through the unit of work itself to a copy that is never routed: it
                            // leaves the unit of work and never runs
                            Exchange copy = ExchangeHelper.createCorrelatedCopy(e, false);
                            e.getUnitOfWork().handoverSynchronization(copy);
                        }).id("dropCommit");
            }
        };
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void theViolationsAndTheCountsAsJson() {
        template.sendBody("direct:ok", "x");
        template.send("direct:throws", e -> {
            e.getMessage().setBody(BODY);
            e.getMessage().setHeader("order", HEADER);
        });
        awaitViolation();

        JsonObject json = (JsonObject) console().call(DevConsole.MediaType.JSON);

        assertEquals(true, json.get("enabled"));
        assertTrue(json.getLong("calls") >= 2, json::toJson);
        assertEquals(1L, json.getLong("total"));
        assertEquals(Map.of("R1", 1L, "R2", 0L), json.getMap("rules"));
        JsonObject kinds = json.getMap("kinds");
        // every kind, also those not reported
        assertEquals(Violation.values().length, kinds.size());
        assertEquals(1L, kinds.getLong("THREW_BEFORE_CALLBACK"));
        assertEquals(0L, kinds.getLong("COMPLETED_TWICE"));
        JsonArray violations = (JsonArray) json.get("violations");
        assertEquals(1, violations.size());
        JsonObject v = violations.getMap(0);
        assertEquals("R1", v.getString("rule"));
        assertEquals("THREW_BEFORE_CALLBACK", v.getString("kind"));
        assertEquals("process threw before calling the callback", v.getString("title"));
        assertEquals("orders", v.getString("routeId"));
        assertEquals("throws", v.getString("stepId"));
        assertEquals(BrokenUserCode.ThrowsInProcess.class.getName(), v.getString("processor"));
        assertNotNull(v.getString("exchangeId"));
        // the report with the thread, the stack, and the class and message of what was thrown
        String report = v.getString("report");
        assertTrue(report.contains("java.lang.IllegalStateException: Forced by ThrowsInProcess"), report);
        assertTrue(report.contains("at " + BrokenUserCode.ThrowsInProcess.class.getName() + ".process"), report);
        // never the body or the headers
        String all = json.toJson();
        assertFalse(all.contains(BODY), all);
        assertFalse(all.contains(HEADER), all);
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void theReportAsText() {
        template.send("direct:throws", e -> {
            e.getMessage().setBody(BODY);
            e.getMessage().setHeader("order", HEADER);
        });
        awaitViolation();

        String text = (String) console().call(DevConsole.MediaType.TEXT);

        assertTrue(text.startsWith("Exchange sanitizer report of CamelContext " + context.getName()), text);
        assertTrue(text.contains("1 violations"), text);
        assertTrue(text.contains("process threw before calling the callback: 1"), text);
        assertTrue(text.contains("route orders, step throws"), text);
        assertTrue(text.contains("at " + BrokenUserCode.ThrowsInProcess.class.getName() + ".process"), text);
        assertFalse(text.contains(BODY), text);
        assertFalse(text.contains(HEADER), text);
    }

    @Test
    void noViolations() {
        template.sendBody("direct:ok", "x");

        JsonObject json = (JsonObject) console().call(DevConsole.MediaType.JSON);

        assertEquals(true, json.get("enabled"));
        assertEquals(0L, json.getLong("total"));
        assertEquals(Map.of("R1", 0L, "R2", 0L), json.getMap("rules"));
        assertEquals(List.of(), json.get("violations"));
        // R2 (on by default in a test) and the still pending warning are on
        assertNotNull(json.getLong("commits"), json::toJson);
        assertEquals(0L, json.getLong("pendingCalls"));
        assertEquals(0L, json.getLong("stillPending"));
        assertEquals(List.of(), json.get("stillPendingWarnings"));
        assertTrue(((String) console().call(DevConsole.MediaType.TEXT)).contains("no violations"));
    }

    @Test
    @ExpectSanitizerViolation(Violation.COMMIT_NEVER_RUN)
    void aViolationOfR2() {
        Exchange out = template.send("direct:dropCommit",
                e -> e.getExchangeExtension().addOnCompletion(new SynchronizationAdapter()));
        assertNull(out.getException());
        Sanitizer sanitizer = Sanitizer.get(context);
        await().atMost(Duration.ofSeconds(10)).until(() -> sanitizer.getViolationCount(Violation.COMMIT_NEVER_RUN) > 0);

        JsonObject json = (JsonObject) console().call(DevConsole.MediaType.JSON);

        assertEquals(Map.of("R1", 0L, "R2", 1L), json.getMap("rules"));
        JsonObject kinds = json.getMap("kinds");
        assertEquals(1L, kinds.getLong("COMMIT_NEVER_RUN"));
        assertTrue(json.getLong("commits") >= 1, json::toJson);
        JsonObject v = ((JsonArray) json.get("violations")).getMap(0);
        assertEquals("R2", v.getString("rule"));
        assertEquals("COMMIT_NEVER_RUN", v.getString("kind"));
        assertEquals("dropping", v.getString("routeId"));
        // found when the exchange was done, where no step ran
        assertFalse(v.containsKey("stepId"), json::toJson);
        // the class of the commit
        assertEquals(SynchronizationAdapter.class.getName(), v.getString("processor"));
        assertTrue(v.getString("report").contains("(R2)"), v.getString("report"));

        String text = (String) console().call(DevConsole.MediaType.TEXT);
        assertTrue(text.contains("commits followed"), text);
        assertTrue(text.contains("commit never ran: 1"), text);
    }

    @Test
    void theStillPendingWarnings() {
        template.asyncSendBody("direct:lost", "x");
        Sanitizer sanitizer = Sanitizer.get(context);
        await().atMost(Duration.ofSeconds(10)).until(() -> sanitizer.getStillPendingCount() > 0);

        JsonObject json = (JsonObject) console().call(DevConsole.MediaType.JSON);

        // a warning, not a violation
        assertEquals(0L, json.getLong("total"));
        assertEquals(1L, json.getLong("stillPending"));
        assertEquals(1L, json.getLong("pendingCalls"));
        List<?> warnings = (List<?>) json.get("stillPendingWarnings");
        assertEquals(1, warnings.size());
        String warning = (String) warnings.get(0);
        assertTrue(warning.startsWith("Exchange sanitizer: callback still pending (R1, a warning)"), warning);
        assertTrue(warning.contains("route lostRoute, step lost, processor " + BrokenUserCode.NeverCallsBack.class.getName()),
                warning);

        String text = (String) console().call(DevConsole.MediaType.TEXT);
        assertTrue(text.contains("1 call reported still pending (warnings)"), text);
        assertTrue(text.contains("callback still pending (R1, a warning)"), text);

        // without the report: the counts, not the warnings
        json = (JsonObject) console().call(DevConsole.MediaType.JSON, Map.of(SanitizerDevConsole.REPORT, "false"));
        assertEquals(1L, json.getLong("stillPending"));
        assertEquals(1L, json.getLong("pendingCalls"));
        assertFalse(json.containsKey("stillPendingWarnings"), json::toJson);
    }

    @Test
    @ExpectSanitizerViolation(value = Violation.THREW_BEFORE_CALLBACK, step = "throws")
    void withoutTheReportTheViolationsHaveTheirFieldsOnly() {
        template.send("direct:throws", e -> e.getMessage().setBody("x"));
        awaitViolation();

        // as camel get sanitizer asks for its table
        JsonObject json = (JsonObject) console().call(DevConsole.MediaType.JSON, Map.of(SanitizerDevConsole.REPORT, "false"));

        assertEquals(1L, json.getLong("total"));
        JsonObject kinds = json.getMap("kinds");
        assertEquals(1L, kinds.getLong("THREW_BEFORE_CALLBACK"));
        JsonObject v = ((JsonArray) json.get("violations")).getMap(0);
        assertEquals("THREW_BEFORE_CALLBACK", v.getString("kind"));
        assertEquals("throws", v.getString("stepId"));
        assertEquals(BrokenUserCode.ThrowsInProcess.class.getName(), v.getString("processor"));
        assertFalse(v.containsKey("report"), v::toJson);

        // the option is true by default, and does not change the text
        v = ((JsonArray) ((JsonObject) console().call(DevConsole.MediaType.JSON)).get("violations")).getMap(0);
        assertNotNull(v.getString("report"));
        String text = (String) console().call(DevConsole.MediaType.TEXT, Map.of(SanitizerDevConsole.REPORT, "false"));
        assertTrue(text.contains("Forced by ThrowsInProcess"), text);
    }

    @Test
    void offWithProfileProd() throws Exception {
        // built on a thread of its own, which the extension did not mark, so the session of this class is not told that
        // a context stayed off
        ExecutorService builder = Executors.newSingleThreadExecutor();
        CamelContext prod = builder.submit(() -> {
            CamelContext c = new DefaultCamelContext();
            c.getCamelContextExtension().setProfile("prod");
            c.start();
            return c;
        }).get(10, TimeUnit.SECONDS);
        try {
            DevConsole console = resolve(prod);

            JsonObject json = (JsonObject) console.call(DevConsole.MediaType.JSON);
            assertEquals(Map.of("enabled", false, "reason", "The sanitizer is off: it stays off with profile prod"), json);
            assertEquals("The sanitizer is off: it stays off with profile prod\n", console.call(DevConsole.MediaType.TEXT));
        } finally {
            prod.stop();
            builder.shutdownNow();
        }
    }

    @Test
    void withProfileDevOutsideATestOnlyR1() throws Exception {
        // as with camel run --sanitizer: profile dev, on a thread the extension did not mark, so the checks are R1 only
        // (callback, the default outside a test) and the console has no commits
        ExecutorService builder = Executors.newSingleThreadExecutor();
        CamelContext dev = builder.submit(() -> {
            CamelContext c = new DefaultCamelContext();
            c.getCamelContextExtension().setProfile("dev");
            c.start();
            return c;
        }).get(10, TimeUnit.SECONDS);
        try {
            JsonObject json = (JsonObject) resolve(dev).call(DevConsole.MediaType.JSON);
            assertEquals(true, json.get("enabled"), json::toJson);
            assertFalse(json.containsKey("commits"), json::toJson);
            assertEquals(Map.of("R1", 0L, "R2", 0L), json.getMap("rules"));
            assertEquals(0L, json.getLong("stillPending"));
        } finally {
            dev.stop();
            builder.shutdownNow();
        }
    }

    @Test
    void foundByItsIdAndReadOnly() {
        DevConsole console = console();
        assertEquals("sanitizer", console.getId());
        assertEquals("camel", console.getGroup());
        assertTrue(console.isReadOnly());
    }

    private void awaitViolation() {
        Sanitizer sanitizer = Sanitizer.get(context);
        assertNotNull(sanitizer, "the sanitizer is installed for a test marked with @CamelSanitizer");
        await().atMost(Duration.ofSeconds(10)).until(() -> sanitizer.getViolationCount(Violation.THREW_BEFORE_CALLBACK) > 0);
    }

    private DevConsole console() {
        return resolve(context);
    }

    /** The console as the developer console registry finds it: by its id, on the classpath. */
    private static DevConsole resolve(CamelContext camelContext) {
        DefaultDevConsoleResolver resolver = new DefaultDevConsoleResolver();
        resolver.setCamelContext(camelContext);
        DevConsole console = resolver.resolveDevConsole("sanitizer");
        assertInstanceOf(SanitizerDevConsole.class, console);
        return console;
    }
}
