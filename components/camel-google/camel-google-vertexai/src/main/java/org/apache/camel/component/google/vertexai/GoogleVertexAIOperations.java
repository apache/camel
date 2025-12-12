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

/**
 * Operations supported by the Google Vertex AI component.
 */
public enum GoogleVertexAIOperations {

    // ==================== Generative AI Operations (google-genai SDK) ====================
    // These operations use the generateContent API for Google models (Gemini, Imagen, etc.)

    /**
     * Generate text content using Gemini models.
     */
    generateText,

    /**
     * Generate chat response using Gemini models with conversation history.
     */
    generateChat,

    /**
     * Generate streaming chat response using Gemini models.
     */
    generateChatStreaming,

    /**
     * Generate images using Imagen models.
     */
    generateImage,

    /**
     * Generate text embeddings using embedding models.
     */
    generateEmbeddings,

    /**
     * Generate code using Gemini or code-specialized models.
     */
    generateCode,

    /**
     * Generate content from multimodal inputs (text, images, audio, video).
     */
    generateMultimodal,

    // ==================== Prediction Services Operations (google-cloud-aiplatform SDK) ====================
    // These operations use the rawPredict API for partner models (Claude, Llama, Mistral, etc.)

    /**
     * Send a raw prediction request to partner models (Claude, Llama, Mistral) or custom deployed models.
     * <p>
     * This operation uses the Vertex AI Prediction Service's rawPredict API, which accepts an arbitrary JSON payload
     * specific to the target model.
     * </p>
     * <p>
     * Endpoint format for partner models:
     * {@code projects/{project}/locations/{location}/publishers/{publisher}/models/{model}}
     * </p>
     * <p>
     * Example publishers: {@code anthropic}, {@code meta}, {@code mistralai}
     * </p>
     */
    rawPredict,

    /**
     * Send a streaming raw prediction request to partner models.
     * <p>
     * Similar to rawPredict but returns a stream of responses using server-sent events (SSE).
     * </p>
     */
    streamRawPredict
}
