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
package org.apache.camel.semantic.openai;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionChoiceOption;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;
import com.openai.models.decisions.DecisionInputMessage;
import com.openai.models.decisions.DecisionInputPart;
import com.openai.models.decisions.DecisionInputText;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.openai.OpenAIDecisionServiceException;
import org.apache.camel.component.openai.OpenAIDecisionsSupport;
import org.apache.camel.component.openai.OpenAIEndpoint;
import org.apache.camel.component.openai.OpenAIOperations;
import org.apache.camel.semantic.SemanticAdapter;
import org.apache.camel.semantic.SemanticCapabilities;
import org.apache.camel.semantic.SemanticEvaluation;
import org.apache.camel.semantic.SemanticExpert;
import org.apache.camel.semantic.SemanticExpert.InputType;
import org.apache.camel.semantic.SemanticExpert.ResultType;
import org.apache.camel.semantic.SemanticInput;
import org.apache.camel.semantic.SemanticOperation;
import org.apache.camel.semantic.SemanticParameter;
import org.apache.camel.semantic.SemanticResult;

/** An explicitly registered expert; its OpenAI endpoint owns the client, credentials and transport lifecycle. */
@SemanticExpert(name = "openai-decisions", provider = "openai", artifactId = "camel-semantic-openai",
                description = "Typed text and image assessments using the OpenAI Decisions API",
                operations = {
                        @SemanticOperation(name = "boolean", description = "Assess whether a condition holds",
                                           inputTypes = { InputType.TEXT, InputType.IMAGE },
                                           inputRequirements = "Text, an image body with Content-Type, an image data URL, or an ordered $semanticInput envelope",
                                           resultType = ResultType.BOOLEAN,
                                           resultMeaning = "Predicate probability at or above threshold, subject to the configured uncertainty policy",
                                           probability = true,
                                           probabilityMeaning = "Provider probability that the condition is true",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Question to assess against the input",
                                                                      required = true, minSize = 1, maxSize = 1048576),
                                                   @SemanticParameter(name = "threshold",
                                                                      description = "Inclusive positive probability threshold",
                                                                      type = Number.class, minimum = 0, maximum = 1,
                                                                      omission = "Use 0.5"),
                                                   @SemanticParameter(name = "uncertainty",
                                                                      description = "Half-width of the uncertainty band around the threshold",
                                                                      type = Number.class, minimum = 0, maximum = 1,
                                                                      omission = "No uncertainty band"),
                                                   @SemanticParameter(name = "uncertaintyPolicy",
                                                                      description = "Behavior inside the uncertainty band",
                                                                      values = { "fail", "non-match" },
                                                                      omission = "Fail the evaluation") }),
                        @SemanticOperation(name = "choice", description = "Select one declared category",
                                           inputTypes = { InputType.TEXT, InputType.IMAGE },
                                           inputRequirements = "Text, an image body with Content-Type, an image data URL, or an ordered $semanticInput envelope",
                                           resultType = ResultType.CHOICE, choiceLabelsParameter = "criteria",
                                           noMatchDeclared = true, completeProbabilities = true,
                                           resultMeaning = "The provider-selected category key",
                                           probabilities = true,
                                           probabilityMeaning = "Provider probability for each declared category",
                                           confidence = true,
                                           confidenceMeaning = "Confidence reported by OpenAI; no derived confidence is added",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Question to assess against the input",
                                                                      required = true, minSize = 1, maxSize = 1048576),
                                                   @SemanticParameter(name = "criteria",
                                                                      description = "Category keys and descriptions",
                                                                      type = Map.class, itemType = String.class,
                                                                      required = true,
                                                                      minSize = 2, maxSize = 255) }),
                        @SemanticOperation(name = "score", description = "Assess input against ordered rubric levels",
                                           inputTypes = { InputType.TEXT, InputType.IMAGE },
                                           inputRequirements = "Text, an image body with Content-Type, an image data URL, or an ordered $semanticInput envelope",
                                           resultType = ResultType.SCORE, minimum = 0, scoreLevelsParameter = "criteria",
                                           scoreDescriptionsParameter = "descriptions", completeProbabilities = true,
                                           resultMeaning = "Provider probability-weighted zero-based rubric index; fractional scores are preserved",
                                           probabilities = true,
                                           probabilityMeaning = "Provider probability for each zero-based rubric index",
                                           confidence = true,
                                           confidenceMeaning = "Confidence reported by OpenAI; no derived confidence is added",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Question to assess against the input",
                                                                      required = true, minSize = 1, maxSize = 1048576),
                                                   @SemanticParameter(name = "criteria",
                                                                      description = "Distinct rubric labels ordered from lowest to highest",
                                                                      type = List.class, itemType = String.class,
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "descriptions",
                                                                      description = "Optional descriptions keyed by rubric label",
                                                                      type = Map.class, itemType = String.class,
                                                                      omission = "Use the rubric labels") }) })
public class OpenAIDecisionsSemanticAdapter implements SemanticAdapter, CamelContextAware {
    private static final SemanticCapabilities CAPABILITIES = SemanticCapabilities.from(OpenAIDecisionsSemanticAdapter.class);
    private CamelContext camelContext;
    private String endpointUri = "openai:decisions";
    private int maxImageBytes = 10_000_000;
    private volatile OpenAIEndpoint endpoint;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String getEndpointUri() {
        return endpointUri;
    }

    @Override
    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    /** Maximum bytes per image before base64 encoding. Defaults to 10 MB. Configure before evaluation. */
    public void setMaxImageBytes(int maxImageBytes) {
        if (maxImageBytes <= 0) {
            throw new IllegalArgumentException("maxImageBytes must be greater than zero");
        }
        this.maxImageBytes = maxImageBytes;
    }

    /** Configured Decisions endpoint, including model and transport options. Set before the first evaluation. */
    public synchronized void setEndpointUri(String endpointUri) {
        if (endpoint != null || endpointUri == null || endpointUri.isBlank()) {
            throw new IllegalArgumentException("Set a nonblank Decisions endpoint URI before evaluation");
        }
        this.endpointUri = endpointUri;
    }

    private OpenAIEndpoint endpoint() {
        if (camelContext != null && (camelContext.isStopping() || camelContext.isStopped())) {
            throw new OpenAIDecisionServiceException("OpenAI Decisions requires a running CamelContext");
        }
        if (endpoint == null) {
            synchronized (this) {
                if (endpoint == null) {
                    if (camelContext == null) {
                        throw new IllegalStateException("OpenAI Decisions expert requires a CamelContext");
                    }
                    OpenAIEndpoint resolved;
                    try {
                        resolved = camelContext.getEndpoint(endpointUri, OpenAIEndpoint.class);
                    } catch (ResolveEndpointFailedException e) {
                        throw new OpenAIDecisionServiceException("Unable to initialize the OpenAI Decisions endpoint", e);
                    }
                    if (resolved.getOperation() != OpenAIOperations.decisions) {
                        throw new IllegalArgumentException("OpenAI Decisions expert requires a Decisions endpoint");
                    }
                    endpoint = resolved;
                }
            }
        }
        return endpoint;
    }

    @Override
    public void validate(SemanticEvaluation evaluation) {
        CAPABILITIES.validate(evaluation);
        if ("boolean".equals(evaluation.getOperation())) {
            double threshold = number(evaluation, "threshold", 0.5);
            double uncertainty = number(evaluation, "uncertainty", 0);
            if (threshold - uncertainty < 0 || threshold + uncertainty > 1) {
                throw new IllegalArgumentException("Threshold and uncertainty must keep the band within [0,1]");
            }
        } else if ("choice".equals(evaluation.getOperation())) {
            descriptions(evaluation, "criteria").forEach((label, description) -> {
                text(label);
                text(description);
            });
        } else {
            List<String> levels = levels(evaluation);
            if (new HashSet<>(levels).size() != levels.size()) {
                throw new IllegalArgumentException("Score rubric labels must be distinct");
            }
            levels.forEach(OpenAIDecisionsSemanticAdapter::text);
            descriptions(evaluation, "descriptions").forEach((label, description) -> {
                if (!levels.contains(label)) {
                    throw new IllegalArgumentException("Score descriptions must refer to declared rubric labels");
                }
                text(description);
            });
        }
    }

    private static void text(String text) {
        if (text.isBlank() || text.length() > 1_048_576) {
            throw new IllegalArgumentException("Criteria require nonblank text within the provider text limit");
        }
    }

    @Override
    public void validateInput(SemanticEvaluation evaluation, Object state) {
        state = SemanticInput.normalize(state);
        CAPABILITIES.operation(evaluation.getOperation()).validateInput(state);
    }

    @Override
    public void validateState(Object state) {
        SemanticInput.validateImageSize(SemanticInput.normalize(state), maxImageBytes);
        OpenAIDecisionsSupport.validateInput(input(SemanticInput.normalize(state)));
    }

    @Override
    public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) throws Exception {
        return evaluateBatch(Map.of("question", evaluation), state).get("question");
    }

    @Override
    public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticEvaluation> evaluations, Object state)
            throws Exception {
        if (evaluations.isEmpty() || evaluations.size() > 200) {
            throw new IllegalArgumentException("OpenAI Decisions requires 1 to 200 evaluations per batch");
        }
        Object input = SemanticInput.normalize(state);
        SemanticInput.validateImageSize(input, maxImageBytes);
        evaluations.forEach((name, evaluation) -> {
            text(name);
            validate(evaluation);
            validateInput(evaluation, input);
        });
        OpenAIEndpoint current = endpoint();
        String model = current.getConfiguration().getModel();
        if (model == null || model.isBlank()) {
            throw new IllegalArgumentException("Configure a Decisions model on the OpenAI endpoint");
        }
        var request = DecisionCreateParams.builder().model(model).input(input(input));
        evaluations.forEach((name, evaluation) -> request.addQuestion(question(name, evaluation)));
        Decision response = current.decide(request.build());
        Map<String, Object> metadata = metadata(response);
        Map<String, SemanticResult> results = new LinkedHashMap<>();
        int index = 0;
        for (var entry : evaluations.entrySet()) {
            SemanticResult result = result(entry.getValue(), response.answers().get(index++), metadata);
            CAPABILITIES.operation(entry.getValue().getOperation()).validateResult(result);
            results.put(entry.getKey(), result);
        }
        return results;
    }

    private static DecisionCreateParams.Input input(Object state) {
        if (state instanceof String text) {
            return DecisionCreateParams.Input.ofString(text);
        }
        List<DecisionInputPart> parts = new ArrayList<>();
        for (Object value : SemanticInput.parts(state)) {
            Map<?, ?> part = (Map<?, ?>) value;
            if ("text".equals(part.get("type"))) {
                parts.add(DecisionInputPart.ofInputText(DecisionInputText.builder().text((String) part.get("text")).build()));
            } else {
                var image = DecisionInputImage.builder().imageUrl((String) part.get("data"));
                if (part.get("detail") instanceof String detail) {
                    image.detail(DecisionInputImage.Detail.of(detail));
                }
                parts.add(DecisionInputPart.ofInputImage(image.build()));
            }
        }
        return DecisionCreateParams.Input.ofDecisionInputMessages(List.of(
                DecisionInputMessage.builder().contentOfParts(parts).build()));
    }

    private static DecisionCreateParams.Question question(String name, SemanticEvaluation evaluation) {
        String instructions = (String) evaluation.getParameters().get("instructions");
        if ("boolean".equals(evaluation.getOperation())) {
            return DecisionCreateParams.Question.ofPredicate(DecisionCreateParams.Question.Predicate.builder()
                    .name(name).instructions(instructions).build());
        } else if ("choice".equals(evaluation.getOperation())) {
            var question = DecisionCreateParams.Question.Choice.builder().name(name).instructions(instructions);
            descriptions(evaluation, "criteria").forEach((label, description) -> question.addChoice(
                    DecisionChoiceOption.builder().value(label).description(description).build()));
            return DecisionCreateParams.Question.ofChoice(question.build());
        }
        var question = DecisionCreateParams.Question.Score.builder().name(name).instructions(instructions);
        Map<String, String> descriptions = descriptions(evaluation, "descriptions");
        for (String label : levels(evaluation)) {
            var level = DecisionCreateParams.Question.Score.Level.builder().label(label);
            if (descriptions.containsKey(label)) {
                level.description(descriptions.get(label));
            }
            question.addLevel(level.build());
        }
        return DecisionCreateParams.Question.ofScore(question.build());
    }

    private static SemanticResult result(SemanticEvaluation evaluation, Decision.Answer answer, Map<String, Object> metadata) {
        if (answer.isPredicate()) {
            double probability = answer.asPredicate().probability();
            double threshold = number(evaluation, "threshold", 0.5);
            double uncertainty = number(evaluation, "uncertainty", 0);
            boolean value = probability >= threshold;
            if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
                if (!"non-match".equals(evaluation.getParameters().get("uncertaintyPolicy"))) {
                    throw new IllegalStateException("OpenAI decision probability is inside the configured uncertainty band");
                }
                value = false;
            }
            return new SemanticResult(value, probability, Map.of(), null, metadata);
        }
        Map<String, Double> probabilities = new LinkedHashMap<>();
        if (answer.isChoice()) {
            var choice = answer.asChoice();
            choice._probabilities().asKnown()
                    .ifPresent(values -> values.forEach(p -> probabilities.put(p.value().asString(), p.probability())));
            return new SemanticResult(
                    choice.choice().asString(), null, probabilities, choice._confidence().asKnown().orElse(null), metadata);
        }
        var score = answer.asScore();
        score._probabilities().asKnown()
                .ifPresent(values -> values.forEach(p -> probabilities.put(Long.toString(p.value()), p.probability())));
        return new SemanticResult(score.score(), null, probabilities, score._confidence().asKnown().orElse(null), metadata);
    }

    private static Map<String, Object> metadata(Decision response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "openai");
        metadata.put("model", response.model());
        response._usage().asKnown().ifPresent(usage -> metadata.put("usage", Map.of(
                "inputTokens", usage.inputTokens(), "outputTokens", usage.outputTokens(), "totalTokens", usage.totalTokens())));
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static List<String> levels(SemanticEvaluation evaluation) {
        return (List<String>) evaluation.getParameters().get("criteria");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> descriptions(SemanticEvaluation evaluation, String parameter) {
        Map<?, ?> values = (Map<?, ?>) evaluation.getParameters().getOrDefault(parameter, Map.of());
        if (values.keySet().stream().anyMatch(key -> !(key instanceof String))) {
            throw new IllegalArgumentException("Criteria and description keys must be strings");
        }
        return (Map<String, String>) values;
    }

    private static double number(SemanticEvaluation evaluation, String parameter, double defaultValue) {
        return ((Number) evaluation.getParameters().getOrDefault(parameter, defaultValue)).doubleValue();
    }
}
