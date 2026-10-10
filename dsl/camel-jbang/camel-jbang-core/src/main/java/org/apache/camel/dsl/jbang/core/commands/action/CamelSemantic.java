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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import com.github.freva.asciitable.AsciiTable;
import com.github.freva.asciitable.Column;
import com.github.freva.asciitable.HorizontalAlign;
import com.github.freva.asciitable.OverflowBehaviour;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import picocli.CommandLine;

@CommandLine.Command(name = "semantic", description = "List semantic definitions and expert contracts",
                     sortOptions = false, showDefaultValues = true,
                     footer = {
                             "%nExamples:",
                             "  camel semantic my-app",
                             "  camel semantic get my-app --expert=guard",
                             "  camel semantic eval my-app --evaluation=safe --body='Sample message' --json",
                             "  camel semantic audit my-app --category=decision --json" })
public class CamelSemantic extends SemanticActionCommand {

    @CommandLine.Option(names = "--expert", description = "Show the operations and parameter contract of this expert")
    String expert;

    public CamelSemantic(CamelJBangMain main) {
        super(main);
    }

    @Override
    protected JsonObject request() {
        if (expert != null && expert.isBlank()) {
            throw new IllegalArgumentException("--expert must not be blank");
        }
        JsonObject request = new JsonObject();
        request.put("action", "semantic-metadata");
        if (expert == null) {
            request.put("overview", true);
        } else {
            request.put("expert", expert);
        }
        return request;
    }

    @Override
    protected int printResponse(JsonObject response) {
        if (expert != null) {
            JsonArray operations = response.getCollection("operations");
            if (operations == null || operations.isEmpty()) {
                return error(3, "No operations available for semantic expert: " + expert, null);
            }
        }
        return super.printResponse(response);
    }

    @Override
    protected void render(JsonObject response) {
        if (expert != null) {
            JsonArray operations = response.getCollection("operations");
            if (operations != null) {
                printer().println("Expert: " + expert);
                renderOperations(operations);
            }
            return;
        }
        printer().println("Definitions:");
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows(response.getCollection("evaluations")), List.of(
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "name")),
                new Column().header("EXPERT").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "expert")),
                new Column().header("OPERATION").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "operation")),
                new Column().header("STATE").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "state")),
                new Column().header("RESULT TYPE").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "resultType")),
                new Column().header("ERROR").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "error")))));
        printer().println("Experts:");
        printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, rows(response.getCollection("experts")), List.of(
                new Column().header("REFERENCE").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "reference")),
                new Column().header("NAME").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "name")),
                new Column().header("ERROR").dataAlign(HorizontalAlign.LEFT).with(r -> text(r, "error")))));
        String defaultExpert = text(response, "defaultExpert");
        printer().println("Default expert: " + (defaultExpert.isEmpty() ? "none" : defaultExpert));
        String defaultError = text(response, "defaultError");
        String defaultErrorCode = text(response, "defaultErrorCode");
        // Older runtimes expose only the message; structured codes take precedence when available.
        boolean noUniqueExpert = defaultErrorCode.isEmpty()
                ? defaultError.startsWith("Semantic language requires exactly one eligible expert")
                : "no_unique_expert".equals(defaultErrorCode);
        if (!defaultError.isEmpty() && !noUniqueExpert) {
            printer().println("Default expert error: " + defaultError);
        }
    }

    private static List<JsonObject> rows(JsonArray values) {
        return values == null ? List.of() : IntStream.range(0, values.size()).<JsonObject> mapToObj(values::getMap).toList();
    }

    private void renderOperations(JsonArray operations) {
        for (JsonObject operation : rows(operations)) {
            printer().println();
            printer().println(text(operation, "name") + " (" + text(operation, "resultType") + "): "
                              + text(operation, "description"));
            JsonObject contract = operation.getMap("contract");
            if (contract == null) {
                continue;
            }
            renderField("Input", contract.get("inputTypes"), "  ");
            renderField("Requirements", contract.get("inputRequirements"), "  ");
            renderField("Result", contract.get("resultMeaning"), "  ");
            renderField("Labels", contract.get("labels"), "  ");
            renderField("Range", range(contract, "minimum", "maximum"), "  ");
            renderField("Score levels", contract.get("scoreLevelsParameter"), "  ");
            for (String metric : List.of("probability", "probabilities", "confidence")) {
                if (Boolean.TRUE.equals(contract.get(metric))) {
                    String meaning = text(contract, "confidence".equals(metric) ? "confidenceMeaning" : "probabilityMeaning");
                    renderField(fieldLabel(metric), meaning.isEmpty() ? "available" : meaning, "  ");
                }
            }
            List<JsonObject> parameters = rows(contract.getCollection("parameters"));
            if (!parameters.isEmpty()) {
                printer().println();
                printer().println("Parameters:");
                printer().println(AsciiTable.getTable(AsciiTable.NO_BORDERS, parameters, List.of(
                        new Column().header("PARAMETER").dataAlign(HorizontalAlign.LEFT).maxWidth(24, OverflowBehaviour.NEWLINE)
                                .with((JsonObject p) -> text(p, "name")),
                        new Column().header("TYPE").dataAlign(HorizontalAlign.LEFT).maxWidth(16, OverflowBehaviour.NEWLINE)
                                .with(CamelSemantic::parameterType),
                        new Column().header("REQUIRED").dataAlign(HorizontalAlign.LEFT)
                                .with((JsonObject p) -> Boolean.TRUE.equals(p.get("required")) ? "yes" : "no"),
                        new Column().header("DETAILS").dataAlign(HorizontalAlign.LEFT).maxWidth(64, OverflowBehaviour.NEWLINE)
                                .with(CamelSemantic::parameterDetails))));
            }
        }
    }

    private static String parameterType(JsonObject parameter) {
        String type = text(parameter, "type");
        if ("List".equals(type) || "Map".equals(type)) {
            String itemType = text(parameter, "itemType");
            if (!itemType.isEmpty() && !"Object".equals(itemType)) {
                type += "<" + itemType + ">";
            }
        }
        return Boolean.TRUE.equals(parameter.get("integer")) ? type + " (integer)" : type;
    }

    private static String parameterDetails(JsonObject parameter) {
        List<String> details = new ArrayList<>();
        if (!text(parameter, "description").isEmpty()) {
            details.add(text(parameter, "description"));
        }
        if (!text(parameter, "omission").isEmpty()) {
            details.add("When omitted: " + text(parameter, "omission"));
        }
        JsonArray values = parameter.getCollection("values");
        if (values != null && !values.isEmpty()) {
            details.add("Allowed: " + String.join(", ", values.stream().map(SemanticActionCommand::displayText).toList()));
        }
        String range = range(parameter, "minimum", "maximum");
        if (!range.isEmpty()) {
            details.add("Range: " + range);
        }
        // The schema's unbounded collection limits are implementation defaults, not useful constraints.
        Object minimum = parameter.get("minSize");
        Object maximum = parameter.get("maxSize");
        if (minimum instanceof Number number && number.longValue() > 0) {
            details.add("Minimum items: " + minimum);
        }
        if (maximum instanceof Number number && number.longValue() < Integer.MAX_VALUE) {
            details.add("Maximum items: " + maximum);
        }
        return String.join("\n", details);
    }

    private static String range(JsonObject value, String minimum, String maximum) {
        String min = text(value, minimum);
        String max = text(value, maximum);
        if (min.isEmpty()) {
            return max.isEmpty() ? "" : "<= " + max;
        }
        return max.isEmpty() ? ">= " + min : min + " to " + max;
    }

    private static String text(JsonObject value, String key) {
        return displayText(value.get(key));
    }
}
