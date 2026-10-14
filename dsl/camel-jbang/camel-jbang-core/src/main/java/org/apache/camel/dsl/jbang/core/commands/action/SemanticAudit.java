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

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
import org.apache.camel.dsl.jbang.core.common.SemanticAuditFilters;
import org.apache.camel.dsl.jbang.core.common.SemanticImagePreview;
import org.apache.camel.dsl.jbang.core.common.SemanticMediaInput;
import org.apache.camel.dsl.jbang.core.common.SemanticResultFormatter;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "audit", description = "Retrieve retained expert evaluations and related lifecycle records",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel semantic audit my-app --category=evaluation --route-id=support",
                             "  camel semantic audit my-app --expert=guard --limit=20 --json",
                             "  camel semantic audit my-app --event-id=<event-id> --json" })
public class SemanticAudit extends SemanticActionCommand {

    @CommandLine.Option(names = "--event-id", description = "Retrieve one recorded event instead of a page")
    String eventId;

    @CommandLine.Option(names = "--category",
                        description = "Filter by record category: evaluation or request")
    String category;

    @CommandLine.Option(names = "--expert", description = "Filter by expert bean reference")
    String expert;

    @CommandLine.Option(names = "--route-id", description = "Filter by entry route ID")
    String routeId;

    @CommandLine.Option(names = "--breadcrumb-id", description = "Filter by Camel breadcrumb ID")
    String breadcrumbId;

    @CommandLine.Option(names = "--since", description = "Earliest timestamp, inclusive, in ISO-8601 format with a UTC offset")
    String since;

    @CommandLine.Option(names = "--until", description = "Latest timestamp, exclusive, in ISO-8601 format with a UTC offset")
    String until;

    @CommandLine.Option(names = "--definition", description = "Filter by recorded definition name")
    String definition;

    @CommandLine.Option(names = "--operation", description = "Filter by recorded expert operation")
    String operation;

    @CommandLine.Option(names = "--result-type", description = "Captured result type: BOOLEAN, CHOICE, SCORE or CLASSIFICATION")
    String resultType;

    @CommandLine.Option(names = "--result-value",
                        description = "Exact Boolean, Choice or numeric Score value; requires --result-type")
    String resultValue;

    @CommandLine.Option(names = "--result-label",
                        description = "Exact Classification member; requires --result-type=CLASSIFICATION")
    String resultLabel;

    @CommandLine.Option(names = "--score-min", description = "Inclusive numeric lower bound; requires --result-type=SCORE")
    String scoreMin;

    @CommandLine.Option(names = "--score-max", description = "Inclusive numeric upper bound; requires --result-type=SCORE")
    String scoreMax;

    @CommandLine.Option(names = "--cursor", description = "Opaque cursor for an older page; retain the original filters")
    String cursor;

    @CommandLine.Option(names = "--limit", defaultValue = "50", description = "Maximum records per page (1-200)")
    int limit = 50;

    @CommandLine.Option(names = "--executing-route-id", description = "Filter by executing route ID, when supported")
    String executingRouteId;

    @CommandLine.Option(names = "--exchange-id", description = "Filter by recorded exchange ID, when supported")
    String exchangeId;

    @CommandLine.Option(names = "--case-event-id", description = "Retrieve a source-local case anchored at this event")
    String caseEventId;

    @CommandLine.Option(names = "--event-ids", split = ",", description = "Export these event IDs; at most 1000")
    List<String> eventIds;

    @CommandLine.Option(names = "--max-records", defaultValue = "200", description = "Case/export retrieval budget (1-1000)")
    int maxRecords = 200;

    @CommandLine.Option(names = "--include-input", description = "Include untrusted captured input in an explicit case/export")
    boolean includeInput;

    @CommandLine.Option(names = "--output",
                        description = "Write the full JSON response to a new file, including requested captured image data")
    Path output;

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
        option(request, "expert", expert, "--expert", 256);
        option(request, "routeId", routeId, "--route-id", 256);
        option(request, "executingRouteId", executingRouteId, "--executing-route-id", 256);
        option(request, "exchangeId", exchangeId, "--exchange-id", 256);
        if (category != null && !List.of("evaluation", "request").contains(category)) {
            throw new IllegalArgumentException("--category must be evaluation or request");
        }
        option(request, "breadcrumbId", breadcrumbId, "--breadcrumb-id", 256);
        option(request, "cursor", cursor, "--cursor", 512);
        option(request, "definition", definition, "--definition", 256);
        option(request, "operation", operation, "--operation", 256);
        option(request, "resultType", resultType, "--result-type", 256);
        option(request, "resultValue", resultValue, "--result-value", 256);
        option(request, "resultLabel", resultLabel, "--result-label", 256);
        option(request, "scoreMin", scoreMin, "--score-min", 256);
        option(request, "scoreMax", scoreMax, "--score-max", 256);
        timestamp(request, "since", since);
        timestamp(request, "until", until);
        SemanticAuditFilters.validateQuery(request);
        if (caseEventId != null || eventIds != null) {
            if (eventId != null || caseEventId != null && eventIds != null || cursor != null
                    || commandSpec.commandLine().getParseResult().hasMatchedOption("--limit")
                    || request.size() > (since == null ? 1 : 2) || maxRecords < 1 || maxRecords > 1000) {
                throw new IllegalArgumentException(
                        "Case/export selectors cannot be combined with event lookup or page filters");
            }
            option(request, "caseEventId", caseEventId, "--case-event-id", 256);
            if (eventIds != null) {
                if (eventIds.size() > maxRecords || eventIds.stream().anyMatch(id -> id.isBlank() || id.length() > 256)) {
                    throw new IllegalArgumentException("Invalid --event-ids request");
                }
                request.put("eventIds", eventIds);
            }
            request.put("maxRecords", maxRecords);
            request.put("includeInput", includeInput);
            return request;
        }
        if (includeInput || commandSpec.commandLine().getParseResult().hasMatchedOption("--max-records")) {
            throw new IllegalArgumentException("--include-input and --max-records require an explicit case/export");
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

    private static void timestamp(JsonObject request, String name, String value) {
        if (value != null) {
            try {
                request.put(name, Instant.parse(value).toString());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("--" + name + " must be an ISO-8601 timestamp with a UTC offset", e);
            }
        }
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
        if (Boolean.TRUE.equals(response.get("unavailable")) || eventId != null && response.get("record") == null) {
            return error(3, "Audit event is unavailable: " + (caseEventId != null ? caseEventId : eventId), response);
        }
        if (Boolean.TRUE.equals(response.get("cursorExpired"))) {
            return error(1, "Audit cursor expired; start again without --cursor", response);
        }
        if (output != null) {
            try {
                Files.writeString(output, response.toJson(), StandardOpenOption.CREATE_NEW);
                printer().println("Audit exported to " + displayText(output));
                return 0;
            } catch (IOException e) {
                String reason = e instanceof FileAlreadyExistsException ? "file already exists"
                        : e instanceof AccessDeniedException ? "permission denied"
                        : e instanceof FileSystemException failure && failure.getReason() != null
                                ? displayText(failure.getReason())
                        : "I/O failure (" + e.getClass().getSimpleName() + ")";
                return error(1, "Cannot create audit export file: " + reason, null);
            }
        }
        return super.printResponse(json ? (JsonObject) SemanticMediaInput.withoutImageData(response) : response);
    }

    private void renderAuditField(String key, Object value, String indent) {
        if ("input".equals(key)) {
            renderInput("Input", SemanticMediaInput.withoutImageData(value), indent);
            for (var preview : SemanticImagePreview.prepare(value)) {
                preview.textLines().forEach(line -> printer().println(indent + line));
            }
        } else if ("inputType".equals(key)) {
            renderField("Input type", SemanticMediaInput.typeLabel(value), indent);
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
        if (response.get("inputWarning") != null) {
            printer().println(displayText(response.get("inputWarning")));
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
            return;
        }
        JsonArray values = response.getCollection("records");
        if (values != null && !values.isEmpty()) {
            List<JsonObject> rows = IntStream.range(0, values.size()).<JsonObject> mapToObj(values::getMap).toList();
            printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows, List.of(
                    column("EVENT ID", "eventId"), column("TIMESTAMP", "timestamp"),
                    column("EXECUTING ROUTE", "executingRouteId"),
                    column("EVALUATION", "definition"), column("EXPERT", "expert"),
                    new Column().header("INPUT").dataAlign(HorizontalAlign.LEFT)
                            .with((JsonObject row) -> SemanticMediaInput.typeLabel(row.get("inputType"))),
                    new Column().header("RESULT").dataAlign(HorizontalAlign.LEFT)
                            .with((JsonObject row) -> SemanticResultFormatter.audit(row).compact()),
                    column("EXECUTION", "status"), column("BREADCRUMB ID", "breadcrumbId"))));
        } else if (!Boolean.TRUE.equals(response.get("cursorExpired"))) {
            printer().println("No retained audit records match this query.");
        }
        for (String field : List.of("retrievalScope", "relationshipCompleteness", "retrievalLimited", "missingIds",
                "unsupportedRelationships")) {
            if (response.containsKey(field)) {
                renderField(fieldLabel(field), response.get(field), "");
            }
        }
        if (response.containsKey("evicted")) {
            printer().println("Evicted: " + response.get("evicted"));
        }
        if (response.get("nextCursor") != null) {
            printer().println("Next cursor: " + response.get("nextCursor"));
        }
    }

    private void renderStatus(JsonObject audit) {
        Object capturing = audit.getOrDefault("capturing", audit.get("enabled"));
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
