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
package org.apache.camel.semantic.springai;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
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
import org.springframework.ai.chat.messages.part.MediaPart;
import org.springframework.ai.chat.messages.part.MessagePart;
import org.springframework.ai.chat.messages.part.TextPart;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.content.Media;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionOptions;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ChoiceAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.DecisionInput;
import org.springframework.ai.decision.question.ChoiceQuestion;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;
import org.springframework.util.MimeType;

/**
 * A Semantic expert that asks a Spring AI {@link DecisionModel}, such as the Ollama, OpenAI or System One decision
 * model. The decision model owns the credentials, the transport, the timeouts, the retries and the observations; this
 * expert maps the evaluations to Spring AI questions and applies the Boolean policy once.
 */
@SemanticExpert(name = "spring-ai-decision", provider = "spring-ai", artifactId = "camel-semantic-spring-ai",
                description = "Typed text, structured and image assessments using a Spring AI decision model",
                operations = {
                        @SemanticOperation(name = "boolean", description = "Assess whether a condition holds",
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED, InputType.IMAGE },
                                           inputRequirements = SpringAiDecisionSemanticAdapter.INPUT_REQUIREMENTS,
                                           resultType = ResultType.BOOLEAN, booleanLabelsParameter = "criteria",
                                           resultMeaning = "Probability of yes at or above threshold, subject to the configured uncertainty policy",
                                           probability = true,
                                           probabilityMeaning = "Provider probability that the condition holds (yes), also when the result is false",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Question to assess against the input",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria",
                                                                      description = "What counts as true and as false, keyed by true and false",
                                                                      type = Map.class, itemType = String.class,
                                                                      omission = "Use the instructions"),
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
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED, InputType.IMAGE },
                                           inputRequirements = SpringAiDecisionSemanticAdapter.INPUT_REQUIREMENTS,
                                           resultType = ResultType.CHOICE, choiceLabelsParameter = "criteria",
                                           noMatchDeclared = true,
                                           resultMeaning = "The category key the decision model selected",
                                           probabilities = true,
                                           probabilityMeaning = "Provider probability for each declared category, when the provider reports them",
                                           confidence = true,
                                           confidenceMeaning = "Confidence reported by the provider, when it reports one; no derived confidence is added",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Question to assess against the input",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria",
                                                                      description = "Category keys and descriptions",
                                                                      type = Map.class, itemType = String.class,
                                                                      required = true, minSize = 2) }),
                        @SemanticOperation(name = "score", description = "Assess input against ordered rubric levels",
                                           inputTypes = { InputType.TEXT, InputType.STRUCTURED, InputType.IMAGE },
                                           inputRequirements = SpringAiDecisionSemanticAdapter.INPUT_REQUIREMENTS,
                                           resultType = ResultType.SCORE, minimum = 0, scoreLevelsParameter = "criteria",
                                           scoreDescriptionsParameter = "descriptions",
                                           resultMeaning = "Probability-weighted zero-based rubric index; fractional scores are preserved",
                                           probabilities = true,
                                           probabilityMeaning = "Provider probability for each zero-based rubric index, when the provider reports them",
                                           confidence = true,
                                           confidenceMeaning = "Confidence reported by the provider, when it reports one; no derived confidence is added",
                                           parameters = {
                                                   @SemanticParameter(name = "instructions",
                                                                      description = "Question to assess against the input",
                                                                      required = true, minSize = 1),
                                                   @SemanticParameter(name = "criteria",
                                                                      description = "Distinct rubric labels ordered from lowest to highest",
                                                                      type = List.class, itemType = String.class,
                                                                      required = true, minSize = 2),
                                                   @SemanticParameter(name = "descriptions",
                                                                      description = "Optional descriptions keyed by rubric label",
                                                                      type = Map.class, itemType = String.class,
                                                                      omission = "Use the rubric labels") }) })
public class SpringAiDecisionSemanticAdapter implements SemanticAdapter, CamelContextAware {

    static final String INPUT_REQUIREMENTS
            = "Text, a Map of named fields, an image body with Content-Type, an image data URL, or an ordered"
              + " $semanticInput envelope; the decision model must accept the input, and the Ollama and System One"
              + " decision models accept no images";

    private static final SemanticCapabilities CAPABILITIES
            = SemanticCapabilities.from(SpringAiDecisionSemanticAdapter.class);
    private static final String DATA_URL_BASE64 = ";base64,";

    private CamelContext camelContext;
    private volatile DecisionModel decisionModel;
    private String model;
    private int maxImageBytes = 10_000_000;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public DecisionModel getDecisionModel() {
        return decisionModel;
    }

    /**
     * The decision model that answers the evaluations. When it is not set, the expert uses the only
     * {@link DecisionModel} in the registry.
     */
    public void setDecisionModel(DecisionModel decisionModel) {
        this.decisionModel = decisionModel;
    }

    public String getModel() {
        return model;
    }

    /**
     * The name of the model to use instead of the default model of the decision model, such as d1-omni:2b.
     */
    public void setModel(String model) {
        this.model = model;
    }

    @Override
    public int getMaxImageBytes() {
        return maxImageBytes;
    }

    /**
     * Maximum bytes per image before base64 encoding. Defaults to 10 MB.
     */
    public void setMaxImageBytes(int maxImageBytes) {
        if (maxImageBytes <= 0) {
            throw new IllegalArgumentException("maxImageBytes must be greater than zero");
        }
        this.maxImageBytes = maxImageBytes;
    }

    private DecisionModel decisionModel() {
        DecisionModel answer = decisionModel;
        if (answer == null) {
            synchronized (this) {
                answer = decisionModel;
                if (answer == null) {
                    if (camelContext == null) {
                        throw new IllegalStateException(
                                "The Spring AI decision expert needs a decisionModel, or a CamelContext to find one");
                    }
                    answer = camelContext.getRegistry().findSingleByType(DecisionModel.class);
                    if (answer == null) {
                        throw new IllegalStateException(
                                "Set the decisionModel of the Spring AI decision expert: the registry has no"
                                                        + " DecisionModel, or more than one");
                    }
                    decisionModel = answer;
                }
            }
        }
        return answer;
    }

    @Override
    public void validate(SemanticEvaluation evaluation) {
        CAPABILITIES.validate(evaluation);
        switch (evaluation.getOperation()) {
            case "boolean" -> {
                descriptions(evaluation, "criteria").values().forEach(SpringAiDecisionSemanticAdapter::text);
                double threshold = number(evaluation, "threshold", YesNoAnswer.DEFAULT_THRESHOLD);
                double uncertainty = number(evaluation, "uncertainty", 0);
                if (threshold - uncertainty < 0 || threshold + uncertainty > 1) {
                    throw new IllegalArgumentException("Threshold and uncertainty must keep the band within [0,1]");
                }
            }
            case "choice" -> descriptions(evaluation, "criteria").forEach((label, description) -> {
                text(label);
                text(description);
            });
            default -> {
                List<String> levels = levels(evaluation);
                if (new HashSet<>(levels).size() != levels.size()) {
                    throw new IllegalArgumentException("Score rubric labels must be distinct");
                }
                levels.forEach(SpringAiDecisionSemanticAdapter::text);
                descriptions(evaluation, "descriptions").forEach((label, description) -> {
                    if (!levels.contains(label)) {
                        throw new IllegalArgumentException("Score descriptions must refer to declared rubric labels");
                    }
                    text(description);
                });
            }
        }
    }

    @Override
    public void validateInput(SemanticEvaluation evaluation, Object state) {
        Object input = SemanticInput.normalize(state);
        CAPABILITIES.operation(evaluation.getOperation()).validateInput(input);
        if (input instanceof List<?>) {
            throw new IllegalArgumentException(
                    "A Spring AI decision model takes named fields: select a Map rather than a List");
        }
    }

    @Override
    public void validateState(Object state) {
        input(SemanticInput.normalize(state));
    }

    @Override
    public SemanticResult evaluate(SemanticEvaluation evaluation, Object state) throws Exception {
        return evaluateBatch(Map.of("question", evaluation), state).get("question");
    }

    @Override
    public Map<String, SemanticResult> evaluateBatch(Map<String, SemanticEvaluation> evaluations, Object state)
            throws Exception {
        if (evaluations.isEmpty()) {
            throw new IllegalArgumentException("A Spring AI decision needs at least one evaluation");
        }
        Object normalized = SemanticInput.normalize(state);
        evaluations.forEach((name, evaluation) -> {
            text(name);
            validate(evaluation);
            validateInput(evaluation, normalized);
        });
        DecisionPrompt.Builder prompt = DecisionPrompt.builder().input(input(normalized));
        evaluations.forEach((name, evaluation) -> prompt.question(name, question(evaluation)));
        if (model != null && !model.isBlank()) {
            prompt.options(DecisionOptions.builder().model(model).build());
        }
        DecisionModel current = decisionModel();
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Spring AI decision evaluation interrupted");
        }
        DecisionResponse response = current.call(prompt.build());
        if (response == null) {
            throw new IllegalStateException("The decision model returned no response");
        }
        // a DecisionResponse answers every question of its prompt with an answer of the kind of the question, within
        // its options or levels; a refused evaluation fails the whole batch before any result is built
        for (String name : evaluations.keySet()) {
            if (response.getAnswer(name) instanceof RefusalAnswer refusal) {
                throw new SpringAiDecisionRefusalException(name, refusal.getReason());
            }
        }
        Map<String, Object> metadata = metadata(current, response);
        Map<String, SemanticResult> results = new LinkedHashMap<>();
        evaluations.forEach((name, evaluation) -> {
            SemanticResult result = result(evaluation, response, name, metadata);
            CAPABILITIES.operation(evaluation.getOperation()).validateResult(result);
            results.put(name, result);
        });
        return results;
    }

    private DecisionInput input(Object state) {
        if (SemanticInput.isMedia(state)) {
            SemanticInput.validateImageSize(state, maxImageBytes);
            List<MessagePart> parts = new ArrayList<>();
            for (Object item : SemanticInput.parts(state)) {
                Map<?, ?> part = (Map<?, ?>) item;
                if ("text".equals(part.get("type"))) {
                    parts.add(TextPart.of(text((String) part.get("text"))));
                } else {
                    if (part.containsKey("detail")) {
                        throw new IllegalArgumentException(
                                "The Spring AI decision expert does not support the image detail setting");
                    }
                    parts.add(MediaPart.of(image((String) part.get("data"))));
                }
            }
            return DecisionInput.content(parts);
        }
        if (state instanceof Map<?, ?> map) {
            Map<String, Object> fields = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (!(key instanceof String field) || field.isBlank()) {
                    throw new IllegalArgumentException("The fields of a structured input need nonblank string names");
                }
                fields.put(field, value);
            });
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("A structured input needs at least one field");
            }
            return DecisionInput.fields(fields);
        }
        if (state instanceof String text) {
            return DecisionInput.text(text(text));
        }
        throw new IllegalArgumentException("Unsupported selected state; accepts text, a Map of fields or images");
    }

    private static Media image(String data) {
        int separator = data.indexOf(DATA_URL_BASE64);
        if (!data.startsWith("data:image/") || separator < 0) {
            throw new IllegalArgumentException("Image parts need a base64 image data URL, such as data:image/png;base64,...");
        }
        MimeType mimeType;
        byte[] bytes;
        try {
            mimeType = MimeType.valueOf(data.substring("data:".length(), separator));
            bytes = Base64.getDecoder().decode(data.substring(separator + DATA_URL_BASE64.length()));
        } catch (IllegalArgumentException e) {
            // the exception message could quote the image data
            throw new IllegalArgumentException("Invalid image data URL");
        }
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Image data must not be empty");
        }
        return Media.builder().mimeType(mimeType).data(bytes).build();
    }

    private static DecisionQuestion question(SemanticEvaluation evaluation) {
        String instructions = (String) evaluation.getParameters().get("instructions");
        return switch (evaluation.getOperation()) {
            case "boolean" -> {
                Map<String, String> criteria = descriptions(evaluation, "criteria");
                yield YesNoQuestion.builder()
                        .text(instructions)
                        .whenYes(criteria.get("true"))
                        .whenNo(criteria.get("false"))
                        .build();
            }
            case "choice" -> ChoiceQuestion.builder()
                    .text(instructions)
                    .options(new LinkedHashMap<>(descriptions(evaluation, "criteria")))
                    .build();
            default -> {
                Map<String, String> descriptions = descriptions(evaluation, "descriptions");
                ScoreQuestion.Builder question = ScoreQuestion.builder().text(instructions);
                levels(evaluation).forEach(label -> question.level(label, descriptions.get(label)));
                yield question.build();
            }
        };
    }

    private static SemanticResult result(
            SemanticEvaluation evaluation, DecisionResponse response, String name, Map<String, Object> metadata) {
        return switch (evaluation.getOperation()) {
            case "boolean" -> {
                double probability = response.getYesNo(name).getProbability();
                double threshold = number(evaluation, "threshold", YesNoAnswer.DEFAULT_THRESHOLD);
                double uncertainty = number(evaluation, "uncertainty", 0);
                boolean value = probability >= threshold;
                if (uncertainty > 0 && probability >= threshold - uncertainty && probability <= threshold + uncertainty) {
                    if (!"non-match".equals(evaluation.getParameters().get("uncertaintyPolicy"))) {
                        throw new IllegalStateException(
                                "Spring AI decision probability is inside the configured uncertainty band");
                    }
                    value = false;
                }
                yield new SemanticResult(value, probability, Map.of(), null, metadata);
            }
            case "choice" -> {
                ChoiceAnswer choice = response.getChoice(name);
                yield new SemanticResult(
                        choice.getValue(), null, new LinkedHashMap<>(choice.getProbabilities()), choice.getConfidence(),
                        metadata);
            }
            default -> {
                ScoreAnswer score = response.getScore(name);
                Map<String, Double> probabilities = new LinkedHashMap<>();
                List<Double> values = score.getProbabilities();
                for (int i = 0; i < values.size(); i++) {
                    probabilities.put(Integer.toString(i), values.get(i));
                }
                yield new SemanticResult(score.getValue(), null, probabilities, score.getConfidence(), metadata);
            }
        };
    }

    private static Map<String, Object> metadata(DecisionModel decisionModel, DecisionResponse response) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", "spring-ai");
        metadata.put("decisionModel", decisionModel.getClass().getSimpleName());
        String model = response.getMetadata().getModel();
        if (model != null) {
            metadata.put("model", model);
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage != null) {
            Map<String, Object> tokens = new LinkedHashMap<>();
            putIfPresent(tokens, "inputTokens", usage.getPromptTokens());
            putIfPresent(tokens, "outputTokens", usage.getCompletionTokens());
            putIfPresent(tokens, "totalTokens", usage.getTotalTokens());
            if (!tokens.isEmpty()) {
                metadata.put("usage", tokens);
            }
        }
        return metadata;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Integer value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String text(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Instructions, criteria, names and text input must not be blank");
        }
        return text;
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
