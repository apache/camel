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
package org.apache.camel.component.openai;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.models.decisions.Decision;
import com.openai.models.decisions.DecisionCreateParams;
import com.openai.models.decisions.DecisionInputImage;

/** Native Decisions validation shared by component callers and provider adapters. */
public final class OpenAIDecisionsSupport {
    private OpenAIDecisionsSupport() {
    }

    public static void validateRequest(DecisionCreateParams request) {
        try {
            if (request == null || request.questions().isEmpty() || request.questions().size() > 200) {
                throw new IllegalArgumentException("Decisions requires between 1 and 200 questions");
            }
            request._body().validate();
            if (!request._additionalBodyProperties().isEmpty()) {
                throw new IllegalArgumentException("Unsupported Decisions request fields");
            }
            Set<String> names = new HashSet<>();
            for (var question : request.questions()) {
                Optional<String> name = name(question);
                if (name.isPresent() && !names.add(name.get())) {
                    throw new IllegalArgumentException("Decisions question names must be unique");
                }
                if (question.isChoice()) {
                    var choices = question.asChoice().choices();
                    Set<Object> values = new HashSet<>();
                    if (choices.size() < 2 || choices.size() > 255
                            || choices.stream().anyMatch(
                                    c -> !values.add(c.value().isString() ? c.value().asString() : c.value().asBool()))) {
                        throw new IllegalArgumentException("Decisions choice requires 2 to 255 distinct values");
                    }
                } else if (question.isScore()) {
                    var levels = question.asScore().levels();
                    Set<String> labels = new HashSet<>();
                    if (levels.isEmpty() || levels.stream().anyMatch(l -> !labels.add(l.label()))) {
                        throw new IllegalArgumentException("Decisions score requires distinct ordered levels");
                    }
                }
            }
            validateInput(request.input());
        } catch (OpenAIInvalidDataException e) {
            throw new IllegalArgumentException("Invalid Decisions request structure");
        }
    }

    public static void validateInput(DecisionCreateParams.Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Decisions input is required");
        }
        try {
            input.validate();
            validateContent(input);
        } catch (OpenAIInvalidDataException e) {
            throw new IllegalArgumentException("Invalid Decisions input structure");
        }
    }

    private static void validateContent(DecisionCreateParams.Input input) {
        if (input.isString()) {
            validateText(input.asString());
            return;
        }
        var messages = input.asDecisionInputMessages();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Decisions input messages must not be empty");
        }
        int images = 0;
        for (var message : messages) {
            var content = message.content();
            if (!message._additionalProperties().isEmpty()) {
                throw new IllegalArgumentException("Unsupported Decisions input message fields");
            }
            if (content.isString()) {
                validateText(content.asString());
            } else {
                if (content.asParts().isEmpty()) {
                    throw new IllegalArgumentException("Decisions input parts must not be empty");
                }
                for (var part : content.asParts()) {
                    if (part.isInputText()) {
                        if (!part.asInputText()._additionalProperties().isEmpty()) {
                            throw new IllegalArgumentException("Unsupported Decisions text fields");
                        }
                        validateText(part.asInputText().text());
                    } else if (part.isInputImage()) {
                        if (++images > 128) {
                            throw new IllegalArgumentException("Decisions accepts at most 128 images per request");
                        }
                        validateImage(part.asInputImage());
                    } else {
                        throw new IllegalArgumentException("Decisions accepts only text and image input parts");
                    }
                }
            }
        }
    }

    private static void validateText(String text) {
        if (text.length() > 10_485_760) {
            throw new IllegalArgumentException("Decisions text exceeds the provider input limit");
        }
    }

    private static void validateImage(DecisionInputImage image) {
        String data = image.imageUrl();
        int start = data.indexOf(";base64,");
        if (!image._additionalProperties().isEmpty() || start < 0
                || !Set.of("data:image/png", "data:image/jpeg", "data:image/webp", "data:image/gif")
                        .contains(data.substring(0, start))
                || data.length() > 1_073_741_824) {
            throw new IllegalArgumentException("Decisions requires inline PNG, JPEG, WebP or GIF base64 image data URLs");
        }
        start += 8;
        int length = data.length() - start;
        if (length == 0 || length % 4 != 0) {
            throw new IllegalArgumentException("Invalid image base64 encoding");
        }
        // Scan without decoding to avoid allocating another copy of image data up to 1 GiB.
        int padding = 0;
        for (int i = start; i < data.length(); i++) {
            char c = data.charAt(i);
            if (c == '=' && i >= data.length() - 2) {
                padding++;
            } else if (padding != 0 || !(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                    || c >= '0' && c <= '9' || c == '+' || c == '/')) {
                throw new IllegalArgumentException("Invalid image base64 encoding");
            }
        }
    }

    static Decision execute(OpenAIClient client, DecisionCreateParams request) throws InterruptedException {
        try {
            Decision response = client.decisions().create(request);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("OpenAI decision interrupted");
            }
            validateResponse(request, response);
            return response;
        } catch (OpenAIInvalidDataException e) {
            throw new OpenAIDecisionResponseException("Invalid OpenAI decision response", e);
        } catch (OpenAIException e) {
            throw new OpenAIDecisionServiceException("OpenAI decision request failed", e);
        }
    }

    private static Optional<String> name(DecisionCreateParams.Question question) {
        if (question.isPredicate()) {
            return question.asPredicate().name();
        } else if (question.isChoice()) {
            return question.asChoice().name();
        }
        return question.asScore().name();
    }

    private static void validateResponse(DecisionCreateParams request, Decision response) {
        if (response == null || response.answers().size() != request.questions().size()) {
            throw invalid("Decisions returned an unexpected answer count");
        }
        if (response.model().isBlank()) {
            throw invalid("Decisions response is missing its model");
        }
        if (!response._usage().isMissing() && !response._usage().isNull()) {
            var usage = response.usage();
            if (usage.inputTokens() < 0 || usage.outputTokens() < 0 || usage.totalTokens() < 0) {
                throw invalid("Decisions response contains invalid token usage");
            }
        }
        for (int i = 0; i < request.questions().size(); i++) {
            var question = request.questions().get(i);
            var answer = response.answers().get(i);
            Optional<String> answerName;
            if (answer.isRefusal()) {
                throw new OpenAIDecisionRefusalException("OpenAI declined to answer a decision question");
            } else if (question.isPredicate() && answer.isPredicate()) {
                probability(answer.asPredicate().probability());
                answerName = answer.asPredicate().name();
            } else if (question.isChoice() && answer.isChoice()) {
                choice(question.asChoice(), answer.asChoice());
                answerName = answer.asChoice().name();
            } else if (question.isScore() && answer.isScore()) {
                score(question.asScore(), answer.asScore());
                answerName = answer.asScore().name();
            } else {
                throw invalid("Decisions answer type does not match its question");
            }
            if (!Objects.equals(name(question), answerName)) {
                throw invalid("Decisions answer name does not match its question");
            }
        }
    }

    private static void choice(DecisionCreateParams.Question.Choice question, Decision.Answer.Choice answer) {
        Set<Object> expected = new HashSet<>();
        question.choices().forEach(c -> expected.add(c.value().isString() ? c.value().asString() : c.value().asBool()));
        if (!expected.contains(answer.choice().isString() ? answer.choice().asString() : answer.choice().asBool())) {
            throw invalid("Decisions choice is outside the declared domain");
        }
        if (!answer._confidence().isMissing() && !answer._confidence().isNull()) {
            probability(answer.confidence());
        }
        if (!answer._probabilities().isMissing() && !answer._probabilities().isNull()) {
            Set<Object> actual = new HashSet<>();
            for (var p : answer.probabilities()) {
                probability(p.probability());
                if (!actual.add(p.value().isString() ? p.value().asString() : p.value().asBool())) {
                    throw invalid("Duplicate Decisions choice probability");
                }
            }
            if (!actual.equals(expected)) {
                throw invalid("Decisions probabilities do not cover the declared choices");
            }
        }
    }

    private static void score(DecisionCreateParams.Question.Score question, Decision.Answer.Score answer) {
        List<DecisionCreateParams.Question.Score.Level> levels = question.levels();
        double score = answer.score();
        if (!Double.isFinite(score) || score < 0 || score > levels.size() - 1) {
            throw invalid("Decisions score is outside the ordered rubric");
        }
        if (!answer._confidence().isMissing() && !answer._confidence().isNull()) {
            probability(answer.confidence());
        }
        if (!answer._probabilities().isMissing() && !answer._probabilities().isNull()) {
            Set<Long> actual = new HashSet<>();
            for (var p : answer.probabilities()) {
                probability(p.probability());
                if (p.value() < 0 || p.value() >= levels.size() || !actual.add(p.value())
                        || !levels.get((int) p.value()).label().equals(p.label())) {
                    throw invalid("Decisions probability does not match the ordered rubric");
                }
            }
            if (actual.size() != levels.size()) {
                throw invalid("Decisions probabilities do not cover the ordered rubric");
            }
        }
    }

    private static void probability(double value) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw invalid("Decisions probability or confidence is outside [0,1]");
        }
    }

    private static OpenAIDecisionResponseException invalid(String message) {
        return new OpenAIDecisionResponseException(message);
    }
}
