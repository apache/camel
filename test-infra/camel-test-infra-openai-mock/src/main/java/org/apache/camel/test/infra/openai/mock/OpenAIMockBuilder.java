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
package org.apache.camel.test.infra.openai.mock;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.sun.net.httpserver.HttpExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fluent builder for creating OpenAI mock expectations.
 */
public class OpenAIMockBuilder {
    private static final Logger log = LoggerFactory.getLogger(OpenAIMockBuilder.class);

    private final OpenAIMock mock;
    private final List<MockExpectation> expectations;
    private final List<EmbeddingExpectation> embeddingExpectations;
    private final List<AudioTranscriptionExpectation> audioTranscriptionExpectations;
    private final List<AudioTranscriptionExpectation> audioTranslationExpectations;
    private final List<SpeechExpectation> speechExpectations;
    private final List<ModerationExpectation> moderationExpectations;
    private final List<ImageExpectation> imageGenerationExpectations;
    private final List<ImageExpectation> imageEditExpectations;
    private MockExpectation currentExpectation;
    private EmbeddingExpectation currentEmbeddingExpectation;
    private AudioTranscriptionExpectation currentAudioTranscriptionExpectation;
    private AudioTranscriptionExpectation currentAudioTranslationExpectation;
    private SpeechExpectation currentSpeechExpectation;
    private ModerationExpectation currentModerationExpectation;
    private ImageExpectation currentImageGenerationExpectation;
    private ImageExpectation currentImageEditExpectation;

    public OpenAIMockBuilder(OpenAIMock mock, List<MockExpectation> expectations,
                             List<EmbeddingExpectation> embeddingExpectations,
                             List<AudioTranscriptionExpectation> audioTranscriptionExpectations,
                             List<AudioTranscriptionExpectation> audioTranslationExpectations,
                             List<SpeechExpectation> speechExpectations,
                             List<ModerationExpectation> moderationExpectations,
                             List<ImageExpectation> imageGenerationExpectations,
                             List<ImageExpectation> imageEditExpectations) {
        this.mock = mock;
        this.expectations = expectations;
        this.embeddingExpectations = embeddingExpectations;
        this.audioTranscriptionExpectations = audioTranscriptionExpectations;
        this.audioTranslationExpectations = audioTranslationExpectations;
        this.speechExpectations = speechExpectations;
        this.moderationExpectations = moderationExpectations;
        this.imageGenerationExpectations = imageGenerationExpectations;
        this.imageEditExpectations = imageEditExpectations;
    }

    public OpenAIMockBuilder when(String expectedInput) {
        log.debug("Setting up expectation for input: {}", expectedInput);
        currentExpectation = new MockExpectation(expectedInput);
        return this;
    }

    public OpenAIMockBuilder replyWith(String expectedResponse) {
        validateCurrentExpectation("replyWith()");
        log.debug("Setting expected response: {}", expectedResponse);
        currentExpectation.setExpectedResponse(expectedResponse);
        return this;
    }

    public OpenAIMockBuilder replyWithReasoningContent(String reasoningContent) {
        validateCurrentExpectation("replyWithReasoningContent()");
        log.debug("Setting reasoning content: {}", reasoningContent);
        currentExpectation.setReasoningContent(reasoningContent);
        return this;
    }

    public OpenAIMockBuilder replyWithToolContent(String customMessage) {
        validateCurrentExpectation("replyWithToolContent()");
        log.debug("Setting tool content response with custom message: {}", customMessage);
        currentExpectation.setToolContentResponse(customMessage);
        return this;
    }

    public OpenAIMockBuilder invokeTool(String toolName) {
        validateCurrentExpectation("invokeTool()");
        log.debug("Adding new tool execution step with tool: {}", toolName);

        ToolExecutionStep newStep = new ToolExecutionStep();
        newStep.addToolCall(new ToolCallDefinition(toolName));
        currentExpectation.addToolExecutionStep(newStep);

        return this;
    }

    public OpenAIMockBuilder andInvokeTool(String toolName) {
        validateCurrentExpectation("andInvokeTool()");
        validateHasToolSteps("andInvokeTool()");

        log.debug("Adding parallel tool to current step: {}", toolName);
        ToolExecutionStep currentStep = currentExpectation.getCurrentToolStep();
        currentStep.addToolCall(new ToolCallDefinition(toolName));

        return this;
    }

    public OpenAIMockBuilder withUsage(int promptTokens, int completionTokens) {
        validateCurrentExpectation("withUsage()");
        currentExpectation.setUsage(promptTokens, completionTokens);
        return this;
    }

    public OpenAIMockBuilder withParam(String key, Object value) {
        validateCurrentExpectation("withParam()");
        validateHasToolSteps("withParam()");

        ToolExecutionStep currentStep = currentExpectation.getCurrentToolStep();
        if (currentStep.isEmpty()) {
            throw new IllegalStateException("No tool calls in current step to add parameters to");
        }

        ToolCallDefinition lastTool = currentStep.getLastToolCall();
        log.debug("Adding parameter {} = {} to tool: {}", key, value, lastTool.getName());
        lastTool.addArgument(key, value);

        return this;
    }

    public OpenAIMockBuilder thenRespondWith(BiFunction<HttpExchange, String, String> responseFunction) {
        validateCurrentExpectation("thenRespondWith()");
        log.debug("Setting custom response function");
        currentExpectation.setCustomResponseFunction(responseFunction);
        return this;
    }

    public OpenAIMockBuilder assertRequest(Consumer<String> requestAssertion) {
        validateCurrentExpectation("assertRequest()");
        log.debug("Setting request assertion");
        currentExpectation.setRequestAssertion(requestAssertion);
        return this;
    }

    public OpenAIMockBuilder andThenInvokeTool(String toolName) {
        validateCurrentExpectation("andThenInvokeTool()");
        validateHasToolSteps("andThenInvokeTool()");

        log.debug("Creating new sequential step with tool: {}", toolName);
        ToolExecutionStep newStep = new ToolExecutionStep();
        newStep.addToolCall(new ToolCallDefinition(toolName));
        currentExpectation.addToolExecutionStep(newStep);
        currentExpectation.advanceToNextToolStep();

        return this;
    }

    // Embedding API methods

    public OpenAIMockBuilder whenEmbedding(String expectedInput) {
        log.debug("Setting up embedding expectation for input: {}", expectedInput);
        currentEmbeddingExpectation = new EmbeddingExpectation(expectedInput);
        return this;
    }

    public OpenAIMockBuilder replyWithEmbedding(float[] vector) {
        validateCurrentEmbeddingExpectation("replyWithEmbedding()");
        log.debug("Setting explicit embedding vector of size: {}", vector.length);
        List<Float> floatList = new ArrayList<>(vector.length);
        for (float f : vector) {
            floatList.add(f);
        }
        currentEmbeddingExpectation.setEmbeddingVector(floatList);
        return this;
    }

    public OpenAIMockBuilder replyWithEmbedding(List<Float> vector) {
        validateCurrentEmbeddingExpectation("replyWithEmbedding()");
        log.debug("Setting explicit embedding vector (List) of size: {}", vector.size());
        currentEmbeddingExpectation.setEmbeddingVector(vector);
        return this;
    }

    public OpenAIMockBuilder replyWithEmbedding(int size) {
        validateCurrentEmbeddingExpectation("replyWithEmbedding()");
        log.debug("Setting auto-generated embedding of size: {}", size);
        currentEmbeddingExpectation.setEmbeddingSize(size);
        return this;
    }

    // Moderation API methods

    public OpenAIMockBuilder whenModeration(String expectedInput) {
        log.debug("Setting up moderation expectation for input: {}", expectedInput);
        currentModerationExpectation = new ModerationExpectation(expectedInput);
        return this;
    }

    /**
     * Replies with a verdict that violates no category.
     */
    public OpenAIMockBuilder replyWithModerationAllowed() {
        validateCurrentModerationExpectation("replyWithModerationAllowed()");
        log.debug("Setting moderation verdict: allowed");
        currentModerationExpectation.setFlagged(false);
        return this;
    }

    /**
     * Replies with a verdict that violates the given category, which flags the whole result.
     *
     * @param category the OpenAI category name, for example {@code hate} or {@code self-harm/intent}
     * @param score    the confidence score reported for that category
     */
    public OpenAIMockBuilder replyWithModerationFlagged(String category, double score) {
        validateCurrentModerationExpectation("replyWithModerationFlagged()");
        log.debug("Setting moderation verdict: flagged for category {} with score {}", category, score);
        currentModerationExpectation.flagCategory(category, score);
        return this;
    }

    /**
     * Reports a score for a category without marking it as violated.
     */
    public OpenAIMockBuilder replyWithModerationScore(String category, double score) {
        validateCurrentModerationExpectation("replyWithModerationScore()");
        log.debug("Setting moderation score for category {}: {}", category, score);
        currentModerationExpectation.scoreCategory(category, score);
        return this;
    }

    /**
     * Replies without the {@code illicit} and {@code illicit/violent} categories, as an OpenAI-compatible provider that
     * does not implement them would.
     */
    public OpenAIMockBuilder replyWithoutIllicitCategories() {
        validateCurrentModerationExpectation("replyWithoutIllicitCategories()");
        log.debug("Omitting the illicit categories from the moderation verdict");
        currentModerationExpectation.setIllicitCategoriesIncluded(false);
        return this;
    }

    /**
     * Replies without a result for this input, reproducing a provider that returns fewer verdicts than inputs.
     */
    public OpenAIMockBuilder replyWithoutModerationResult() {
        validateCurrentModerationExpectation("replyWithoutModerationResult()");
        log.debug("Omitting the moderation result for this input");
        currentModerationExpectation.setResultOmitted(true);
        return this;
    }

    // Audio Transcription API methods

    public OpenAIMockBuilder whenTranscription() {
        log.debug("Setting up audio transcription expectation");
        currentAudioTranscriptionExpectation = new AudioTranscriptionExpectation();
        return this;
    }

    public OpenAIMockBuilder replyWithTranscription(String text) {
        validateCurrentAudioTranscriptionExpectation("replyWithTranscription()");
        log.debug("Setting transcription text: {}", text);
        currentAudioTranscriptionExpectation.setTranscriptionText(text);
        return this;
    }

    public OpenAIMockBuilder withDuration(double duration) {
        AudioTranscriptionExpectation active = activeAudioExpectation("withDuration()");
        log.debug("Setting audio duration: {}", duration);
        active.setDuration(duration);
        return this;
    }

    public OpenAIMockBuilder withLanguage(String language) {
        AudioTranscriptionExpectation active = activeAudioExpectation("withLanguage()");
        log.debug("Setting audio language: {}", language);
        active.setLanguage(language);
        return this;
    }

    // Audio Translation API methods

    public OpenAIMockBuilder whenTranslation() {
        log.debug("Setting up audio translation expectation");
        currentAudioTranslationExpectation = new AudioTranscriptionExpectation();
        return this;
    }

    public OpenAIMockBuilder replyWithTranslation(String text) {
        validateCurrentAudioTranslationExpectation("replyWithTranslation()");
        log.debug("Setting translation text: {}", text);
        currentAudioTranslationExpectation.setTranscriptionText(text);
        return this;
    }

    // Audio Speech (Text-to-Speech) API methods

    public OpenAIMockBuilder whenSpeech() {
        log.debug("Setting up audio speech expectation");
        currentSpeechExpectation = new SpeechExpectation();
        return this;
    }

    public OpenAIMockBuilder replyWithSpeech(byte[] audioData) {
        validateCurrentSpeechExpectation("replyWithSpeech()");
        log.debug("Setting speech audio data of size: {}", audioData.length);
        currentSpeechExpectation.setAudioData(audioData);
        return this;
    }

    public OpenAIMockBuilder withContentType(String contentType) {
        validateCurrentSpeechExpectation("withContentType()");
        log.debug("Setting speech content type: {}", contentType);
        currentSpeechExpectation.setContentType(contentType);
        return this;
    }

    // Image Generation/Edit API methods

    /**
     * Sets up an image generation expectation that matches any prompt.
     */
    public OpenAIMockBuilder whenImageGeneration() {
        return whenImageGeneration(null);
    }

    /**
     * Sets up an image generation expectation that only matches the given prompt.
     */
    public OpenAIMockBuilder whenImageGeneration(String expectedPrompt) {
        log.debug("Setting up image generation expectation for prompt: {}", expectedPrompt);
        currentImageGenerationExpectation = new ImageExpectation();
        currentImageGenerationExpectation.setExpectedPrompt(expectedPrompt);
        return this;
    }

    /**
     * Sets up an image edit expectation. Image edit requests are multipart and are not parsed by the mock, so
     * expectations are matched in the order they were declared.
     */
    public OpenAIMockBuilder whenImageEdit() {
        log.debug("Setting up image edit expectation");
        currentImageEditExpectation = new ImageExpectation();
        return this;
    }

    /**
     * Adds an image to the reply, served as a base64 payload. Call more than once to reply with several images.
     */
    public OpenAIMockBuilder replyWithImage(byte[] imageData) {
        ImageExpectation expectation = validateCurrentImageExpectation("replyWithImage()");
        log.debug("Setting image data of size: {}", imageData.length);
        expectation.addBase64Image(Base64.getEncoder().encodeToString(imageData));
        return this;
    }

    /**
     * Adds an image to the reply, served as a URL. Call more than once to reply with several images.
     */
    public OpenAIMockBuilder replyWithImageUrl(String url) {
        ImageExpectation expectation = validateCurrentImageExpectation("replyWithImageUrl()");
        log.debug("Setting image url: {}", url);
        expectation.addImageUrl(url);
        return this;
    }

    /**
     * Adds a revised prompt for the image at the same position in the reply.
     */
    public OpenAIMockBuilder withRevisedPrompt(String revisedPrompt) {
        ImageExpectation expectation = validateCurrentImageExpectation("withRevisedPrompt()");
        expectation.addRevisedPrompt(revisedPrompt);
        return this;
    }

    /**
     * Sets the output format reported by the reply, which drives the content type set on the exchange.
     */
    public OpenAIMockBuilder withImageOutputFormat(String outputFormat) {
        ImageExpectation expectation = validateCurrentImageExpectation("withImageOutputFormat()");
        expectation.setOutputFormat(outputFormat);
        return this;
    }

    /**
     * Sets the image size reported by the reply.
     */
    public OpenAIMockBuilder withImageSize(String size) {
        ImageExpectation expectation = validateCurrentImageExpectation("withImageSize()");
        expectation.setSize(size);
        return this;
    }

    /**
     * Asserts on the raw image request body. Image edit requests are multipart, so the body is exposed as raw bytes
     * rather than as parsed fields.
     */
    public OpenAIMockBuilder assertImageRequest(Consumer<byte[]> requestAssertion) {
        ImageExpectation expectation = validateCurrentImageExpectation("assertImageRequest()");
        expectation.setRequestAssertion(requestAssertion);
        return this;
    }

    /**
     * Sets the token usage reported by the reply, as the GPT image models do.
     */
    public OpenAIMockBuilder withImageUsage(int inputTokens, int outputTokens) {
        ImageExpectation expectation = validateCurrentImageExpectation("withImageUsage()");
        expectation.setUsage(inputTokens, outputTokens);
        return this;
    }

    public OpenAIMockBuilder end() {
        if (currentExpectation != null) {
            log.debug("Finalizing expectation for input: {}", currentExpectation.getExpectedInput());
            expectations.add(currentExpectation);
            currentExpectation = null;
        } else if (currentEmbeddingExpectation != null) {
            log.debug("Finalizing embedding expectation for input: {}", currentEmbeddingExpectation.getExpectedInput());
            embeddingExpectations.add(currentEmbeddingExpectation);
            currentEmbeddingExpectation = null;
        } else if (currentAudioTranscriptionExpectation != null) {
            log.debug("Finalizing audio transcription expectation");
            audioTranscriptionExpectations.add(currentAudioTranscriptionExpectation);
            currentAudioTranscriptionExpectation = null;
        } else if (currentAudioTranslationExpectation != null) {
            log.debug("Finalizing audio translation expectation");
            audioTranslationExpectations.add(currentAudioTranslationExpectation);
            currentAudioTranslationExpectation = null;
        } else if (currentSpeechExpectation != null) {
            log.debug("Finalizing audio speech expectation");
            speechExpectations.add(currentSpeechExpectation);
            currentSpeechExpectation = null;
        } else if (currentModerationExpectation != null) {
            log.debug("Finalizing moderation expectation for input: {}", currentModerationExpectation.getExpectedInput());
            moderationExpectations.add(currentModerationExpectation);
            currentModerationExpectation = null;
        } else if (currentImageGenerationExpectation != null) {
            log.debug("Finalizing image generation expectation");
            imageGenerationExpectations.add(currentImageGenerationExpectation);
            currentImageGenerationExpectation = null;
        } else if (currentImageEditExpectation != null) {
            log.debug("Finalizing image edit expectation");
            imageEditExpectations.add(currentImageEditExpectation);
            currentImageEditExpectation = null;
        } else {
            throw new IllegalStateException(
                    "Call when(), whenEmbedding(), whenTranscription(), whenTranslation(), whenSpeech(), "
                                            + "whenModeration(), whenImageGeneration(), or whenImageEdit() "
                                            + "before end()");
        }
        return this;
    }

    public OpenAIMock build() {
        if (currentExpectation != null) {
            log.debug("Auto-finalizing current expectation during build");
            expectations.add(currentExpectation);
            currentExpectation = null;
        }
        if (currentEmbeddingExpectation != null) {
            log.debug("Auto-finalizing current embedding expectation during build");
            embeddingExpectations.add(currentEmbeddingExpectation);
            currentEmbeddingExpectation = null;
        }
        if (currentAudioTranscriptionExpectation != null) {
            log.debug("Auto-finalizing current audio transcription expectation during build");
            audioTranscriptionExpectations.add(currentAudioTranscriptionExpectation);
            currentAudioTranscriptionExpectation = null;
        }
        if (currentAudioTranslationExpectation != null) {
            log.debug("Auto-finalizing current audio translation expectation during build");
            audioTranslationExpectations.add(currentAudioTranslationExpectation);
            currentAudioTranslationExpectation = null;
        }
        if (currentSpeechExpectation != null) {
            log.debug("Auto-finalizing current audio speech expectation during build");
            speechExpectations.add(currentSpeechExpectation);
            currentSpeechExpectation = null;
        }
        if (currentModerationExpectation != null) {
            log.debug("Auto-finalizing current moderation expectation during build");
            moderationExpectations.add(currentModerationExpectation);
            currentModerationExpectation = null;
        }
        if (currentImageGenerationExpectation != null) {
            log.debug("Auto-finalizing current image generation expectation during build");
            imageGenerationExpectations.add(currentImageGenerationExpectation);
            currentImageGenerationExpectation = null;
        }
        if (currentImageEditExpectation != null) {
            log.debug("Auto-finalizing current image edit expectation during build");
            imageEditExpectations.add(currentImageEditExpectation);
            currentImageEditExpectation = null;
        }
        log.info("Built OpenAIMock with {} chat, {} embedding, {} transcription, {} translation, "
                 + "{} speech, {} moderation, {} image generation, and {} image edit expectations",
                expectations.size(), embeddingExpectations.size(), audioTranscriptionExpectations.size(),
                audioTranslationExpectations.size(), speechExpectations.size(), moderationExpectations.size(),
                imageGenerationExpectations.size(), imageEditExpectations.size());
        return mock;
    }

    private ImageExpectation validateCurrentImageExpectation(String methodName) {
        if (currentImageGenerationExpectation != null) {
            return currentImageGenerationExpectation;
        }
        if (currentImageEditExpectation != null) {
            return currentImageEditExpectation;
        }
        throw new IllegalStateException("Call whenImageGeneration() or whenImageEdit() before " + methodName);
    }

    private void validateCurrentExpectation(String methodName) {
        if (currentExpectation == null) {
            throw new IllegalStateException("Call when() before " + methodName);
        }
    }

    private void validateCurrentEmbeddingExpectation(String methodName) {
        if (currentEmbeddingExpectation == null) {
            throw new IllegalStateException("Call whenEmbedding() before " + methodName);
        }
    }

    private void validateCurrentAudioTranscriptionExpectation(String methodName) {
        if (currentAudioTranscriptionExpectation == null) {
            throw new IllegalStateException("Call whenTranscription() before " + methodName);
        }
    }

    private void validateCurrentAudioTranslationExpectation(String methodName) {
        if (currentAudioTranslationExpectation == null) {
            throw new IllegalStateException("Call whenTranslation() before " + methodName);
        }
    }

    private void validateCurrentSpeechExpectation(String methodName) {
        if (currentSpeechExpectation == null) {
            throw new IllegalStateException("Call whenSpeech() before " + methodName);
        }
    }

    private void validateCurrentModerationExpectation(String methodName) {
        if (currentModerationExpectation == null) {
            throw new IllegalStateException("Call whenModeration() before " + methodName);
        }
    }

    private AudioTranscriptionExpectation activeAudioExpectation(String methodName) {
        if (currentAudioTranscriptionExpectation != null) {
            return currentAudioTranscriptionExpectation;
        }
        if (currentAudioTranslationExpectation != null) {
            return currentAudioTranslationExpectation;
        }
        throw new IllegalStateException("Call whenTranscription() or whenTranslation() before " + methodName);
    }

    private void validateHasToolSteps(String methodName) {
        if (currentExpectation.getToolSequence().isEmpty()) {
            throw new IllegalStateException("Call invokeTool() before " + methodName);
        }
    }
}
