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
package org.apache.camel.component.google.vertexai;

import org.apache.camel.spi.Metadata;

/**
 * Constants used in Camel Google Vertex AI component
 */
public final class GoogleVertexAIConstants {

    @Metadata(label = "producer", description = "The operation to perform",
              javaType = "org.apache.camel.component.google.vertexai.GoogleVertexAIOperations")
    public static final String OPERATION = "CamelGoogleVertexAIOperation";

    @Metadata(label = "producer", description = "The model ID to use for generation", javaType = "String")
    public static final String MODEL_ID = "CamelGoogleVertexAIModelId";

    @Metadata(label = "producer", description = "The project ID to use for the request", javaType = "String")
    public static final String PROJECT_ID = "CamelGoogleVertexAIProjectId";

    @Metadata(label = "producer", description = "The location/region to use for the request", javaType = "String")
    public static final String LOCATION = "CamelGoogleVertexAILocation";

    @Metadata(label = "producer", description = "The temperature parameter for generation (0.0-1.0)", javaType = "Float")
    public static final String TEMPERATURE = "CamelGoogleVertexAITemperature";

    @Metadata(label = "producer", description = "The top-p parameter for generation", javaType = "Float")
    public static final String TOP_P = "CamelGoogleVertexAITopP";

    @Metadata(label = "producer", description = "The top-k parameter for generation", javaType = "Integer")
    public static final String TOP_K = "CamelGoogleVertexAITopK";

    @Metadata(label = "producer", description = "The maximum number of output tokens", javaType = "Integer")
    public static final String MAX_OUTPUT_TOKENS = "CamelGoogleVertexAIMaxOutputTokens";

    @Metadata(label = "producer", description = "The number of candidate responses to generate", javaType = "Integer")
    public static final String CANDIDATE_COUNT = "CamelGoogleVertexAIcandidateCount";

    @Metadata(label = "producer", description = "The streaming output mode (complete or chunks)", javaType = "String")
    public static final String STREAM_OUTPUT_MODE = "CamelGoogleVertexAIStreamOutputMode";

    @Metadata(label = "producer", description = "The prompt text for text generation", javaType = "String")
    public static final String PROMPT = "CamelGoogleVertexAIPrompt";

    @Metadata(label = "producer", description = "The chat messages for chat generation",
              javaType = "java.util.List<com.google.cloud.aiplatform.v1.Content>")
    public static final String CHAT_MESSAGES = "CamelGoogleVertexAIChatMessages";

    @Metadata(label = "producer", description = "The system instruction for the model", javaType = "String")
    public static final String SYSTEM_INSTRUCTION = "CamelGoogleVertexAISystemInstruction";

    @Metadata(label = "producer", description = "The safety settings for content filtering",
              javaType = "java.util.List<com.google.cloud.aiplatform.v1.SafetySetting>")
    public static final String SAFETY_SETTINGS = "CamelGoogleVertexAISafetySettings";

    @Metadata(description = "The finish reason from the response", javaType = "String")
    public static final String FINISH_REASON = "CamelGoogleVertexAIFinishReason";

    @Metadata(description = "The number of tokens in the prompt", javaType = "Integer")
    public static final String PROMPT_TOKEN_COUNT = "CamelGoogleVertexAIPromptTokenCount";

    @Metadata(description = "The number of tokens in the response", javaType = "Integer")
    public static final String CANDIDATES_TOKEN_COUNT = "CamelGoogleVertexAICandidatesTokenCount";

    @Metadata(description = "The total token count (prompt + response)", javaType = "Integer")
    public static final String TOTAL_TOKEN_COUNT = "CamelGoogleVertexAITotalTokenCount";

    @Metadata(description = "The safety ratings from the response",
              javaType = "java.util.List<com.google.cloud.aiplatform.v1.SafetyRating>")
    public static final String SAFETY_RATINGS = "CamelGoogleVertexAISafetyRatings";

    @Metadata(description = "Whether the content was blocked by safety filters", javaType = "Boolean")
    public static final String CONTENT_BLOCKED = "CamelGoogleVertexAIContentBlocked";

    // ==================== Streaming Operation Constants ====================

    @Metadata(label = "producer generateChatStreaming streamRawPredict",
              description = "The number of streaming chunks received", javaType = "Integer")
    public static final String CHUNK_COUNT = "CamelGoogleVertexAIChunkCount";

    // ==================== Image Generation Operation Constants ====================

    @Metadata(label = "producer generateImage",
              description = "The number of images to generate", javaType = "Integer")
    public static final String IMAGE_NUMBER_OF_IMAGES = "CamelGoogleVertexAIImageNumberOfImages";

    @Metadata(label = "producer generateImage",
              description = "The aspect ratio for generated images (e.g., 1:1, 16:9, 9:16, 3:4, 4:3)", javaType = "String")
    public static final String IMAGE_ASPECT_RATIO = "CamelGoogleVertexAIImageAspectRatio";

    @Metadata(label = "producer generateImage",
              description = "The generated images from an image generation operation",
              javaType = "java.util.List<com.google.genai.types.Image>")
    public static final String GENERATED_IMAGES = "CamelGoogleVertexAIGeneratedImages";

    // ==================== Embeddings Operation Constants ====================

    @Metadata(label = "producer generateEmbeddings",
              description = "The task type for embeddings (e.g., RETRIEVAL_QUERY, RETRIEVAL_DOCUMENT, SEMANTIC_SIMILARITY, CLASSIFICATION, CLUSTERING, QUESTION_ANSWERING, FACT_VERIFICATION)",
              javaType = "String")
    public static final String EMBEDDING_TASK_TYPE = "CamelGoogleVertexAIEmbeddingTaskType";

    @Metadata(label = "producer generateEmbeddings",
              description = "The desired output dimensionality for embeddings", javaType = "Integer")
    public static final String EMBEDDING_OUTPUT_DIMENSIONALITY = "CamelGoogleVertexAIEmbeddingOutputDimensionality";

    // ==================== Multimodal Operation Constants ====================

    @Metadata(label = "producer generateMultimodal",
              description = "The media data bytes for multimodal input", javaType = "byte[]")
    public static final String MEDIA_DATA = "CamelGoogleVertexAIMediaData";

    @Metadata(label = "producer generateMultimodal",
              description = "The MIME type of the media data (e.g., image/png, image/jpeg, video/mp4, audio/mp3)",
              javaType = "String")
    public static final String MEDIA_MIME_TYPE = "CamelGoogleVertexAIMediaMimeType";

    @Metadata(label = "producer generateMultimodal",
              description = "The GCS URI of the media file for multimodal input (e.g., gs://bucket/image.png)",
              javaType = "String")
    public static final String MEDIA_GCS_URI = "CamelGoogleVertexAIMediaGcsUri";

    // ==================== rawPredict Operation Constants ====================

    @Metadata(label = "producer",
              description = "Publisher name for partner models (e.g., anthropic, meta, mistralai)",
              javaType = "String")
    public static final String PUBLISHER = "CamelGoogleVertexAIPublisher";

    @Metadata(description = "The raw JSON response from rawPredict operation", javaType = "String")
    public static final String RAW_RESPONSE = "CamelGoogleVertexAIRawResponse";

    @Metadata(label = "producer",
              description = "Anthropic API version for Claude models",
              javaType = "String")
    public static final String ANTHROPIC_VERSION = "CamelGoogleVertexAIAnthropicVersion";

    /**
     * Prevent instantiation.
     */
    private GoogleVertexAIConstants() {
    }
}
