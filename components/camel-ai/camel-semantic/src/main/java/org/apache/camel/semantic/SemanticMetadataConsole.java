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
package org.apache.camel.semantic;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.language.semantic.SemanticLanguage;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

/** Static operation metadata and published declarations for runtime tooling. */
@DevConsole(name = "semantic-metadata", displayName = "Semantic Metadata",
            description = "Semantic experts and evaluation definitions")
public class SemanticMetadataConsole extends AbstractDevConsole {
    @Metadata(label = "query", description = "Expert registry name; omitted uses the language's default selection",
              javaType = "java.lang.String")
    public static final String EXPERT = "expert";
    @Metadata(label = "query", description = "List registered experts and published evaluation definitions",
              javaType = "java.lang.Boolean", defaultValue = "false")
    public static final String OVERVIEW = "overview";

    public record Operation(
            @Metadata(description = "The expert-owned operation name") String name,
            @Metadata(description = "The operation's purpose") String description,
            @Metadata(description = "The shape of the returned value") String resultType,
            @Metadata(description = "Input, parameter and result contract") Map<String, Object> contract) {
    }

    public record Expert(
            @Metadata(description = "Registry reference or adapter class") String reference,
            @Metadata(description = "Contract name") String name,
            @Metadata(description = "Expert description") String description,
            @Metadata(description = "Provider name") String provider,
            @Metadata(description = "Provider artifact") String artifactId,
            @Metadata(description = "Supported operations") List<Operation> operations,
            @Metadata(description = "Contract resolution error, if any") String error) {
    }

    public record Definition(
            @Metadata(description = "Evaluation name") String name,
            @Metadata(description = "Resolved expert reference") String expert,
            @Metadata(description = "Declared operation") String operation,
            @Metadata(description = "Effective state selector") String state,
            @Metadata(description = "Declared parameters") Map<String, Object> parameters,
            @Metadata(description = "Declared result shape") String resultType,
            @Metadata(description = "Expert or operation resolution error, if any") String error) {
    }

    public record Response(
            @Metadata(description = "Operations for completion") List<Operation> operations,
            @Metadata(description = "Known experts, when overview is requested") List<Expert> experts,
            @Metadata(description = "Published definitions, when overview is requested") List<Definition> evaluations,
            @Metadata(description = "Resolved default expert") String defaultExpert,
            @Metadata(description = "Default selection error, if any") String defaultError) {
    }

    public SemanticMetadataConsole() {
        super("camel", "semantic-metadata", "Semantic Metadata", "Semantic experts and evaluation definitions");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        return doCallJson(options).toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        SemanticLanguage language = (SemanticLanguage) getCamelContext().resolveLanguage("semantic");
        if (optionBoolean(options, OVERVIEW, false)) {
            return JsonRecordSupport.toJsonObject(overview(language));
        }
        List<Operation> operations;
        try {
            operations = operations(language.getExpertCapabilities(optionString(options, EXPERT)));
        } catch (IllegalArgumentException | IOException unavailable) {
            operations = List.of();
        }
        return JsonRecordSupport.toJsonObject(new Response(operations, null, null, null, null));
    }

    private Response overview(SemanticLanguage language) {
        Map<String, Expert> experts = new TreeMap<>();
        getCamelContext().getRegistry().findByTypeWithName(SemanticAdapter.class).keySet().stream()
                .filter(name -> !SemanticLanguage.ADAPTER_NAME.equals(name)).forEach(reference -> {
                    try {
                        addExpert(experts, language.describeExpert(reference));
                    } catch (IllegalArgumentException | IOException e) {
                        experts.put(reference, new Expert(reference, null, null, null, null, List.of(), e.getMessage()));
                    }
                });
        String defaultExpert = null;
        String defaultError = null;
        try {
            defaultExpert = addExpert(experts, language.describeExpert(null));
        } catch (IllegalArgumentException | IOException e) {
            defaultError = e.getMessage();
        }
        List<Definition> definitions = new ArrayList<>();
        SemanticEvaluations registry = getCamelContext().getCamelContextExtension().getContextPlugin(SemanticEvaluations.class);
        if (registry != null) {
            new TreeMap<>(registry.snapshot()).forEach((name, evaluation) -> {
                String reference = evaluation.getExpert();
                String resultType = null;
                String error = null;
                try {
                    var selected = language.describeExpert(reference);
                    reference = addExpert(experts, selected);
                    resultType = selected.capabilities().operation(evaluation.getOperation()).getResultType().name()
                            .toLowerCase(Locale.ROOT);
                } catch (IllegalArgumentException | IOException e) {
                    error = e.getMessage();
                }
                definitions.add(new Definition(
                        name, reference, evaluation.getOperation(),
                        evaluation.getState() != null ? evaluation.getState() : language.getDefaultState(),
                        evaluation.getParameters(), resultType, error));
            });
        }
        return new Response(null, List.copyOf(experts.values()), definitions, defaultExpert, defaultError);
    }

    private static String addExpert(Map<String, Expert> experts, SemanticLanguage.ExpertMetadata metadata) {
        SemanticCapabilities capabilities = metadata.capabilities();
        experts.putIfAbsent(metadata.reference(), new Expert(
                metadata.reference(), capabilities.getName(),
                capabilities.getDescription(), capabilities.getProvider(), capabilities.getArtifactId(),
                operations(capabilities), null));
        return metadata.reference();
    }

    private static List<Operation> operations(SemanticCapabilities capabilities) {
        return capabilities.getOperations().values().stream().map(op -> {
            Map<String, Object> contract = new LinkedHashMap<>();
            contract.put("inputTypes",
                    op.getInputTypes().stream().map(type -> type.name().toLowerCase(Locale.ROOT)).sorted().toList());
            contract.put("inputRequirements", op.getInputRequirements());
            contract.put("resultMeaning", op.getResultMeaning());
            if (!op.getScoreLevelsParameter().isEmpty()) {
                contract.put("scoreLevelsParameter", op.getScoreLevelsParameter());
            }
            contract.put("probability", op.isProbability());
            contract.put("probabilities", op.isProbabilities());
            contract.put("confidence", op.isConfidence());
            contract.put("labels", op.getLabels().stream().sorted().toList());
            if (Double.isFinite(op.getMinimum())) {
                contract.put("minimum", op.getMinimum());
            }
            if (Double.isFinite(op.getMaximum())) {
                contract.put("maximum", op.getMaximum());
            }
            if (op.isProbability() || op.isProbabilities()) {
                contract.put("probabilityMeaning", op.getProbabilityMeaning());
            }
            if (op.isConfidence()) {
                contract.put("confidenceMeaning", op.getConfidenceMeaning());
            }
            List<Map<String, Object>> parameters = new ArrayList<>();
            op.getParameters().values().forEach(parameter -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("name", parameter.getName());
                value.put("type", parameter.getType().getSimpleName());
                value.put("itemType", parameter.getItemType().getSimpleName());
                value.put("integer", parameter.isInteger());
                value.put("required", parameter.isRequired());
                value.put("description", parameter.getDescription());
                value.put("omission", parameter.getOmission());
                value.put("values", parameter.getValues().stream().sorted().toList());
                if (Double.isFinite(parameter.getMinimum())) {
                    value.put("minimum", parameter.getMinimum());
                }
                if (Double.isFinite(parameter.getMaximum())) {
                    value.put("maximum", parameter.getMaximum());
                }
                value.put("minSize", parameter.getMinSize());
                value.put("maxSize", parameter.getMaxSize());
                parameters.add(value);
            });
            contract.put("parameters", parameters);
            return new Operation(
                    op.getName(), op.getDescription(), op.getResultType().name().toLowerCase(Locale.ROOT), contract);
        }).toList();
    }
}
