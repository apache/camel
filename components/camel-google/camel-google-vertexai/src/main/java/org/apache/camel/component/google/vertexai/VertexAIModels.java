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
 * Supported models for Google Vertex AI.
 * <p>
 * This enum contains:
 * <ul>
 * <li><b>Google Models</b> - Gemini, Imagen, Veo, Gemma (use generateContent API via google-genai SDK)</li>
 * <li><b>Partner Models</b> - Claude, Llama, Mistral (use rawPredict API via google-cloud-aiplatform SDK)</li>
 * </ul>
 * </p>
 *
 * @see <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/learn/models">Vertex AI Models</a>
 * @see <a href="https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/use-partner-models">Partner
 *      Models</a>
 */
public enum VertexAIModels {

    // ==================== Gemini Models ====================
    // Gemini models fully support the generateContent API

    // Gemini 2.5 Models - Latest stable models
    GEMINI_2_5_PRO("gemini-2.5-pro"),
    GEMINI_2_5_PRO_PREVIEW("gemini-2.5-pro-preview-06-05"),
    GEMINI_2_5_FLASH("gemini-2.5-flash"),
    GEMINI_2_5_FLASH_PREVIEW("gemini-2.5-flash-preview-05-20"),
    GEMINI_2_5_FLASH_LITE("gemini-2.5-flash-lite-preview-06-17"),

    // Gemini 2.0 Models
    GEMINI_2_0_FLASH("gemini-2.0-flash"),
    GEMINI_2_0_FLASH_001("gemini-2.0-flash-001"),
    GEMINI_2_0_FLASH_LITE("gemini-2.0-flash-lite"),
    GEMINI_2_0_FLASH_LITE_001("gemini-2.0-flash-lite-001"),

    // Gemini 1.5 Models - Still available but older
    GEMINI_1_5_PRO("gemini-1.5-pro"),
    GEMINI_1_5_PRO_002("gemini-1.5-pro-002"),
    GEMINI_1_5_FLASH("gemini-1.5-flash"),
    GEMINI_1_5_FLASH_002("gemini-1.5-flash-002"),

    // ==================== Imagen Models ====================
    // Image generation models - use generateImages API

    // Imagen 4 Models - Latest image generation
    IMAGEN_4_GENERATE_001("imagen-4.0-generate-preview-05-20"),
    IMAGEN_4_ULTRA_GENERATE_001("imagen-4.0-ultra-generate-exp-05-20"),

    // Imagen 3 Models
    IMAGEN_3_GENERATE_002("imagen-3.0-generate-002"),
    IMAGEN_3_FAST_GENERATE_001("imagen-3.0-fast-generate-001"),

    // ==================== Veo Models ====================
    // Video generation models

    VEO_2_GENERATE_001("veo-2.0-generate-001"),

    // ==================== Gemma Models ====================
    // Open models hosted on Vertex AI

    GEMMA_3_27B_IT("gemma-3-27b-it"),
    GEMMA_2_27B_IT("gemma-2-27b-it"),
    GEMMA_2_9B_IT("gemma-2-9b-it"),
    GEMMA_2_2B_IT("gemma-2-2b-it"),

    // ==================== Embedding Models ====================
    // Text embedding models - use embedContent API

    TEXT_EMBEDDING_005("text-embedding-005"),
    TEXT_EMBEDDING_004("text-embedding-004"),
    TEXT_MULTILINGUAL_EMBEDDING_002("text-multilingual-embedding-002"),

    // Multimodal embedding
    MULTIMODAL_EMBEDDING_001("multimodalembedding@001"),

    // ==================== Partner Models (rawPredict API) ====================
    // These models require the rawPredict operation with the publisher parameter
    // They use the google-cloud-aiplatform SDK, not google-genai
    // Updated: 2025-12-12

    // Anthropic Claude Models (publisher: anthropic)
    // Regions: us-east5, europe-west1, asia-southeast1 (varies by model)
    // See: https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/claude

    // Claude 4.x Models (current generation)
    CLAUDE_SONNET_4_5("claude-sonnet-4-5@20250929"),
    CLAUDE_OPUS_4_5("claude-opus-4-5@20251101"),
    CLAUDE_OPUS_4_1("claude-opus-4-1@20250805"),
    CLAUDE_HAIKU_4_5("claude-haiku-4-5@20251001"),
    CLAUDE_SONNET_4("claude-sonnet-4@20250514"),
    CLAUDE_OPUS_4("claude-opus-4@20250514"),

    // Claude 3.x Models (older generation)
    CLAUDE_3_7_SONNET("claude-3-7-sonnet@20250219"),
    CLAUDE_3_5_SONNET_V2("claude-3-5-sonnet-v2@20241022"),
    CLAUDE_3_5_SONNET("claude-3-5-sonnet@20240620"),
    CLAUDE_3_5_HAIKU("claude-3-5-haiku@20241022"),
    CLAUDE_3_OPUS("claude-3-opus@20240229"),
    CLAUDE_3_HAIKU("claude-3-haiku@20240307"),

    // Meta Llama Models (publisher: meta)
    // Regions: us-central1
    // See: https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/llama

    // Llama 4 Models (latest generation)
    LLAMA_4_MAVERICK_17B_128E("llama-4-maverick-17b-128e-instruct-maas"),
    LLAMA_4_SCOUT_17B_16E("llama-4-scout-17b-16e-instruct-maas"),

    // Llama 3.x Models
    LLAMA_3_3_70B_INSTRUCT("llama-3.3-70b-instruct-maas"),
    LLAMA_3_1_405B_INSTRUCT("llama-3.1-405b-instruct-maas"),
    LLAMA_3_1_70B_INSTRUCT("llama-3.1-70b-instruct-maas"),
    LLAMA_3_1_8B_INSTRUCT("llama-3.1-8b-instruct-maas"),
    LLAMA_3_2_90B_VISION_INSTRUCT("llama-3.2-90b-vision-instruct-maas"),

    // Mistral AI Models (publisher: mistralai)
    // Regions: us-central1, europe-west4
    // See: https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/mistral

    // Current Mistral Models
    MISTRAL_MEDIUM_3("mistral-medium-3"),
    MISTRAL_SMALL_2503("mistral-small-2503"),
    CODESTRAL_2("codestral-2"),
    MISTRAL_OCR_2505("mistral-ocr-2505");

    private final String modelId;

    VertexAIModels(String modelId) {
        this.modelId = modelId;
    }

    public String getModelId() {
        return modelId;
    }

    @Override
    public String toString() {
        return modelId;
    }
}
