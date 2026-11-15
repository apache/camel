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
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

/**
 * The report of the exchange sanitizer: the violations with their stacks, the counts per rule, the commits followed
 * (rule R2), and the calls reported still pending (warnings of rule R1).
 * <p/>
 * The console only reads. Its option {@link #REPORT} leaves out the full report of each violation and the still pending
 * warnings, for a caller that shows only the fields, such as the table of camel get sanitizer. The report holds ids,
 * names, threads, stacks and the class and message of an exception, never bodies or headers; an exception message can
 * still carry message data, so the console is for development (over HTTP it is served only when the developer consoles
 * are switched on, such as with {@code camel.management.devConsoleEnabled=true} of Camel Main).
 *
 * @since 4.24
 */
@DevConsole(name = "sanitizer", displayName = "Sanitizer",
            description = "The violations of the exchange sanitizer, with their stacks, the counts per rule, and the calls still pending")
public class SanitizerDevConsole extends AbstractDevConsole {

    public record ViolationEntry(
            @Metadata(description = "The rule the violation breaks: R1 (the callback of a step) or R2 (the commit of a consumer)") String rule,
            @Metadata(description = "The kind of violation, such as COMPLETED_TWICE or COMMIT_NEVER_RUN") String kind,
            @Metadata(description = "The kind of violation in plain words, such as: callback called twice") String title,
            @Metadata(description = "The id of the exchange") String exchangeId,
            @Metadata(description = "The id of the route of the step (for R2 without a step, the route of the consumer)") String routeId,
            @Metadata(description = "The id of the step, such as process1 or to2 (for R2, the step that ran on the thread where it was found, only when there was one)") String stepId,
            @Metadata(description = "The class name of the processor of the step, such as the processor or the bean of a user (only when it was found); for R2 the class of the commit") String processor,
            @Metadata(description = "The full report of the violation, as logged: where it happened, the threads and their stacks, the class and message of what was thrown, and the usual fix (only with the report option)") String report) {
    }

    public record Response(
            @Metadata(description = "Whether the sanitizer is switched on: with profile dev, camel.sanitizer.enabled=true or @CamelSanitizer, never with profile prod") boolean enabled,
            @Metadata(description = "Why the sanitizer is off (only when it is off)") String reason,
            @Metadata(description = "The calls of the processors of steps checked (only when it is on)") Long calls,
            @Metadata(description = "The commits of consumers followed for rule R2 (only when R2 is checked)") Long commits,
            @Metadata(description = "The violations reported, also those past the first 1000 that are counted but not listed (only when it is on)") Long total,
            @Metadata(description = "The violations reported per rule, R1 and R2 (only when it is on)") Map<String, Long> rules,
            @Metadata(description = "The violations reported per kind, every kind (only when it is on)") Map<String, Long> kinds,
            @Metadata(description = "The violations, the first 1000 in the order they were reported (only when it is on)") List<ViolationEntry> violations,
            @Metadata(description = "The calls of steps pending now, whose callback has not come yet (only when the still pending warning of R1 is on)") Long pendingCalls,
            @Metadata(description = "The calls reported still pending: warnings, not violations (only when the still pending warning of R1 is on)") Long stillPending,
            @Metadata(description = "The still pending warnings, the first 1000 as logged (only when the still pending warning of R1 is on, and with the report option)") List<String> stillPendingWarnings) {
    }

    @Metadata(label = "query",
              description = "Whether to include the full report of each violation and the still pending warnings; without it only the fields and the counts, as the table of camel get sanitizer shows them",
              javaType = "java.lang.Boolean", defaultValue = "true")
    public static final String REPORT = "report";

    public SanitizerDevConsole() {
        super("camel", "sanitizer", "Sanitizer",
              "The violations of the exchange sanitizer, with their stacks, the counts per rule, and the calls still pending");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        Sanitizer sanitizer = Sanitizer.get(getCamelContext());
        if (sanitizer == null) {
            return offReason() + "\n";
        }
        StringBuilder sb = new StringBuilder(sanitizer.report());
        for (String warning : sanitizer.getStillPendingReports()) {
            sb.append("\n\n").append(warning);
        }
        return sb.append('\n').toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        Sanitizer sanitizer = Sanitizer.get(getCamelContext());
        root.put("enabled", sanitizer != null);
        if (sanitizer == null) {
            root.put("reason", offReason());
            return root;
        }
        boolean report = optionBoolean(options, REPORT, true);
        // the list before the counts, so each violation listed is counted (one reported meanwhile is only counted)
        List<SanitizerViolation> violations = sanitizer.getViolations();
        root.put("calls", sanitizer.getCallCount());
        Commits commits = sanitizer.getCommits();
        if (commits != null) {
            root.put("commits", commits.getCommitCount());
        }
        Map<String, Long> rules = new TreeMap<>();
        JsonObject kinds = new JsonObject();
        long total = 0;
        for (Violation kind : Violation.values()) {
            long n = sanitizer.getViolationCount(kind);
            kinds.put(kind.name(), n);
            rules.merge(kind.getRule(), n, Long::sum);
            total += n;
        }
        root.put("total", total);
        root.put("rules", new JsonObject(rules));
        root.put("kinds", kinds);
        JsonArray list = new JsonArray();
        for (SanitizerViolation v : violations) {
            JsonObject jo = new JsonObject();
            jo.put("rule", v.getKind().getRule());
            jo.put("kind", v.getKind().name());
            jo.put("title", v.getKind().getTitle());
            jo.put("exchangeId", v.getExchangeId());
            jo.put("routeId", v.getRouteId());
            if (v.getStepId() != null) {
                // a violation of R2 found where no step ran has none
                jo.put("stepId", v.getStepId());
            }
            if (v.getProcessor() != null) {
                jo.put("processor", v.getProcessor());
            }
            if (report) {
                jo.put("report", v.getText());
            }
            list.add(jo);
        }
        root.put("violations", list);
        PendingCalls pending = sanitizer.getPendingCalls();
        if (pending != null) {
            // the warnings before the count, so each warning listed is counted
            List<String> warnings = report ? sanitizer.getStillPendingReports() : null;
            root.put("pendingCalls", (long) pending.size());
            root.put("stillPending", sanitizer.getStillPendingCount());
            if (warnings != null) {
                root.put("stillPendingWarnings", new JsonArray(warnings));
            }
        }
        return root;
    }

    private String offReason() {
        String profile = getCamelContext().getCamelContextExtension().getProfile();
        if ("prod".equalsIgnoreCase(profile)) {
            return "The sanitizer is off: it stays off with profile prod";
        }
        return "The sanitizer is off: it is switched on with profile dev (unless camel.sanitizer.enabled=false) or with"
               + " camel.sanitizer.enabled=true, never with profile prod, and stays off with pooled exchanges or no"
               + " checks";
    }
}
