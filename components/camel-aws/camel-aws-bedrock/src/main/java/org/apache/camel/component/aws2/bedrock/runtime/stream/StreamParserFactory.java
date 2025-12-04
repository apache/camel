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

package org.apache.camel.component.aws2.bedrock.runtime.stream;

/**
 * Factory for creating appropriate StreamResponseParser based on model ID
 */
public final class StreamParserFactory {

    private StreamParserFactory() {
        // Utility class
    }

    /**
     * Get the appropriate parser for the given model ID
     *
     * @param  modelId the Bedrock model ID
     * @return         the appropriate parser for this model
     */
    public static StreamResponseParser getParser(String modelId) {
        if (modelId == null) {
            throw new IllegalArgumentException("Model ID cannot be null");
        }

        // Amazon Titan Models
        if (modelId.startsWith("amazon.titan")) {
            return new TitanStreamParser();
        }

        // Anthropic Claude Models
        if (modelId.startsWith("anthropic.claude")) {
            return new ClaudeStreamParser();
        }

        // Meta Llama Models
        if (modelId.startsWith("meta.llama")) {
            return new LlamaStreamParser();
        }

        // Mistral AI Models
        if (modelId.startsWith("mistral.")) {
            return new MistralStreamParser();
        }

        // Cohere Models
        if (modelId.startsWith("cohere.")) {
            return new CohereStreamParser();
        }

        // Amazon Nova Models (use Claude-compatible format)
        if (modelId.startsWith("amazon.nova")) {
            return new ClaudeStreamParser();
        }

        // AI21 Models (use Titan-compatible format for simplicity)
        if (modelId.startsWith("ai21.")) {
            return new TitanStreamParser();
        }

        throw new IllegalArgumentException("Unsupported model for streaming: " + modelId);
    }
}
