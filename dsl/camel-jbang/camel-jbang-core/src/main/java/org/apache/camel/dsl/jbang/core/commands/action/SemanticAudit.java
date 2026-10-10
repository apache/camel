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
package org.apache.camel.dsl.jbang.core.commands.action;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.IntStream;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.ColumnData;
import com.github.freva.asciitable.HorizontalAlign;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "audit", description = "Retrieve retained semantic audit records and linked evidence",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel semantic audit my-app --category=decision --action=block",
                             "  camel semantic audit my-app --expert=guard --limit=20 --json",
                             "  camel semantic audit my-app --event-id=<event-id> --json" })
public class SemanticAudit extends SemanticActionCommand {

    @CommandLine.Option(names = "--event-id", description = "Retrieve one event and its linked evidence instead of a page")
    String eventId;

    @CommandLine.Option(names = "--category",
                        description = "Filter by record category, such as evaluation, decision or request")
    String category;

    @CommandLine.Option(names = "--action", description = "Filter by explicit route action, such as allow or block")
    String action;

    @CommandLine.Option(names = "--expert", description = "Filter by expert bean reference")
    String expert;

    @CommandLine.Option(names = "--route-id", description = "Filter by route ID")
    String routeId;

    @CommandLine.Option(names = "--namespace", description = "Filter by application namespace")
    String namespace;

    @CommandLine.Option(names = "--correlation-id", description = "Filter by application correlation ID")
    String correlationId;

    @CommandLine.Option(names = "--breadcrumb-id", description = "Filter by Camel breadcrumb ID")
    String breadcrumbId;

    @CommandLine.Option(names = "--since", description = "Earliest timestamp, inclusive, in ISO-8601 format with a UTC offset")
    String since;

    @CommandLine.Option(names = "--cursor", description = "Opaque cursor for an older page; retain the original filters")
    String cursor;

    @CommandLine.Option(names = "--limit", defaultValue = "50", description = "Maximum records per page (1-200)")
    int limit = 50;

    public SemanticAudit(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected JsonObject request() {
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("--limit must be between 1 and 200");
        }
        JsonObject request = new JsonObject();
        request.put("action", "semantic-audit");
        option(request, "category", category, "--category", 256);
        // The connector reserves action for dispatch and translates auditAction to the console's action filter.
        option(request, "auditAction", action, "--action", 256);
        option(request, "expert", expert, "--expert", 256);
        option(request, "routeId", routeId, "--route-id", 256);
        option(request, "namespace", namespace, "--namespace", 256);
        option(request, "correlationId", correlationId, "--correlation-id", 256);
        option(request, "breadcrumbId", breadcrumbId, "--breadcrumb-id", 256);
        option(request, "cursor", cursor, "--cursor", 512);
        if (since != null) {
            try {
                request.put("since", Instant.parse(since).toString());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("--since must be an ISO-8601 timestamp with a UTC offset", e);
            }
        }
        if (eventId != null) {
            if (request.size() > 1 || commandSpec.commandLine().getParseResult().hasMatchedOption("--limit")) {
                throw new IllegalArgumentException("--event-id cannot be combined with filters, --cursor or --limit");
            }
            option(request, "eventId", eventId, "--event-id", 256);
        } else {
            request.put("limit", limit);
        }
        return request;
    }

    private static void option(JsonObject request, String key, String value, String name, int maximum) {
        if (value != null) {
            if (value.isBlank() || value.length() > maximum) {
                throw new IllegalArgumentException(name + " must not be blank or exceed " + maximum + " characters");
            }
            request.put(key, value);
        }
    }

    @Override
    protected int printResponse(JsonObject response) {
        if (response.containsKey("error")) {
            return error(1, "Audit query failed", response);
        }
        if (eventId != null && response.get("record") == null) {
            return error(3, "Audit event is unavailable: " + eventId, response);
        }
        if (Boolean.TRUE.equals(response.get("cursorExpired"))) {
            return error(1, "Audit cursor expired; start again without --cursor", response);
        }
        return super.printResponse(response);
    }

    private void renderAuditField(String key, Object value, String indent) {
        if ("input".equals(key)) {
            renderInput("Input", value, indent);
        } else {
            renderField(fieldLabel(key), value, indent);
        }
    }

    private void renderInput(String label, Object value, String indent) {
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            printer().println(indent + label + ":");
            map.forEach((key, item) -> renderInput(displayText(key), item, indent + "  "));
        } else if (value instanceof List<?> list && !list.isEmpty()) {
            printer().println(indent + label + ":");
            for (int i = 0; i < list.size(); i++) {
                renderInput("[" + i + "]", list.get(i), indent + "  ");
            }
        } else if (value instanceof String text && text.contains("\n")) {
            printer().println(indent + label + ":");
            for (String line : text.split("\\R", -1)) {
                printer().println(indent + "  " + displayText(line));
            }
        } else {
            printer()
                    .println(indent + label + ": " + (value == null ? "null" : "".equals(value) ? "\"\"" : displayText(value)));
        }
    }

    @Override
    protected void render(JsonObject response) {
        if (response.containsKey("error")) {
            return;
        }
        JsonObject audit = response.getMap("audit");
        if (audit != null) {
            renderStatus(audit);
        }
        if (eventId != null) {
            JsonObject record = response.getMap("record");
            if (record == null) {
                printer().println("Record unavailable.");
            } else {
                printer().println();
                printer().println("Record:");
                record.forEach((key, value) -> renderAuditField(key, value, "  "));
            }
            JsonArray evidence = response.getCollection("evidence");
            if (evidence != null && !evidence.isEmpty()) {
                printer().println();
                printer().println("Evidence:");
                for (int i = 0; i < evidence.size(); i++) {
                    JsonObject linked = evidence.getMap(i);
                    printer().println("  " + displayText(linked.get("eventId")));
                    linked.forEach((key, value) -> {
                        if (!"eventId".equals(key)) {
                            renderAuditField(key, value, "    ");
                        }
                    });
                }
            }
            return;
        }
        JsonArray values = response.getCollection("records");
        if (values != null && !values.isEmpty()) {
            List<JsonObject> rows = IntStream.range(0, values.size()).<JsonObject> mapToObj(values::getMap).toList();
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, List.of(
                    column("EVENT ID", "eventId"), column("TIMESTAMP", "timestamp"), column("CATEGORY", "category"),
                    column("ACTION", "action"), column("STATUS", "status"), column("EXPERT", "expert"),
                    column("OPERATION", "operation"), column("TARGET", "target"), column("NAMESPACE", "namespace"),
                    column("REASON CODE", "reasonCode"), column("BREADCRUMB ID", "breadcrumbId"))));
        } else if (!Boolean.TRUE.equals(response.get("cursorExpired"))) {
            printer().println("No retained audit records match this query.");
        }
        if (response.containsKey("evicted")) {
            printer().println("Evicted: " + response.get("evicted"));
        }
        if (response.get("nextCursor") != null) {
            printer().println("Next cursor: " + response.get("nextCursor"));
        }
    }

    private void renderStatus(JsonObject audit) {
        Object capturing = audit.getOrDefault("decisionsEnabled", audit.get("enabled"));
        String state = capturing instanceof Boolean enabled ? (enabled ? "enabled" : "disabled") : "unknown";
        StringJoiner summary = new StringJoiner(" | ");
        summary.add("Audit: " + state);
        if (audit.get("reader") != null) {
            summary.add("Reader: " + audit.get("reader"));
        }
        if ("memory".equals(audit.get("reader")) && audit.get("retained") != null && audit.get("capacity") != null) {
            summary.add("Retained: " + audit.get("retained") + "/" + audit.get("capacity"));
        }
        if (audit.get("dropped") != null) {
            summary.add("Dropped: " + audit.get("dropped"));
        }
        printer().println(summary.toString());
        JsonObject sinkErrors = audit.getMap("sinkErrors");
        if (sinkErrors != null) {
            sinkErrors.forEach((sink, count) -> {
                if (count instanceof Number number && number.longValue() > 0) {
                    printer().println("Sink errors (" + sink + "): " + count);
                }
            });
        }
        if (audit.get("observerErrors") instanceof Number errors && errors.longValue() > 0) {
            printer().println("Observer errors: " + errors);
        }
    }

    private static ColumnData<JsonObject> column(String heading, String field) {
        return new Column().header(heading).dataAlign(HorizontalAlign.LEFT)
                .with((JsonObject row) -> row.get(field) == null ? "" : row.get(field).toString());
    }
}
