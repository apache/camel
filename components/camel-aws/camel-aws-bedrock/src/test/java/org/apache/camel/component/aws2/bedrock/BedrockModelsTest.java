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

package org.apache.camel.component.aws2.bedrock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class BedrockModelsTest {

    @ParameterizedTest
    @EnumSource(BedrockModels.class)
    void testAllModelsHaveValidModelIds(BedrockModels model) {
        assertNotNull(model.model, "Model ID should not be null for " + model.name());
        assertFalse(model.model.isEmpty(), "Model ID should not be empty for " + model.name());
        assertTrue(model.model.contains("."), "Model ID should contain provider prefix for " + model.name());
    }

    @Test
    void testAmazonTitanModels() {
        assertEquals("amazon.titan-text-express-v1", BedrockModels.TITAN_TEXT_EXPRESS_V1.model);
        assertEquals("amazon.titan-text-lite-v1", BedrockModels.TITAN_TEXT_LITE_V1.model);
        assertEquals("amazon.titan-image-generator-v1", BedrockModels.TITAN_IMAGE_GENERATOR_V1.model);
        assertEquals("amazon.titan-embed-text-v1", BedrockModels.TITAN_EMBEDDINGS_G1.model);
        assertEquals("amazon.titan-embed-image-v1", BedrockModels.TITAN_MULTIMODAL_EMBEDDINGS_G1.model);
        assertEquals("amazon.titan-text-premier-v1:0", BedrockModels.TITAN_TEXT_PREMIER_V1.model);
        assertEquals("amazon.titan-embed-text-v2:0", BedrockModels.TITAN_TEXT_EMBEDDINGS_V2.model);
        assertEquals("amazon.titan-image-generator-v2:0", BedrockModels.TITAN_IMAGE_GENERATOR_V2.model);
    }

    @Test
    void testAmazonNovaModels() {
        assertEquals("amazon.nova-canvas-v1:0", BedrockModels.NOVA_CANVAS_V1.model);
        assertEquals("amazon.nova-lite-v1:0", BedrockModels.NOVA_LITE_V1.model);
        assertEquals("amazon.nova-micro-v1:0", BedrockModels.NOVA_MICRO_V1.model);
        assertEquals("amazon.nova-premier-v1:0", BedrockModels.NOVA_PREMIER_V1.model);
        assertEquals("amazon.nova-pro-v1:0", BedrockModels.NOVA_PRO_V1.model);
        assertEquals("amazon.nova-reel-v1:0", BedrockModels.NOVA_REEL_V1.model);
        assertEquals("amazon.nova-reel-v1:1", BedrockModels.NOVA_REEL_V1_1.model);
        assertEquals("amazon.nova-sonic-v1:0", BedrockModels.NOVA_SONIC_V1.model);
    }

    @Test
    void testAmazonRerankModel() {
        assertEquals("amazon.rerank-v1:0", BedrockModels.RERANK_V1.model);
    }

    @Test
    void testAI21LabsModels() {
        assertEquals("ai21.j2-ultra-v1", BedrockModels.JURASSIC2_ULTRA.model);
        assertEquals("ai21.j2-mid-v1", BedrockModels.JURASSIC2_MID.model);
        assertEquals("ai21.jamba-1-5-large-v1:0", BedrockModels.JAMBA_1_5_LARGE.model);
        assertEquals("ai21.jamba-1-5-mini-v1:0", BedrockModels.JAMBA_1_5_MINI.model);
    }

    @Test
    void testAnthropicClaudeModels() {
        assertEquals("anthropic.claude-instant-v1", BedrockModels.ANTROPHIC_CLAUDE_INSTANT_V1.model);
        assertEquals("anthropic.claude-v2", BedrockModels.ANTROPHIC_CLAUDE_V2.model);
        assertEquals("anthropic.claude-v2:1", BedrockModels.ANTROPHIC_CLAUDE_V2_1.model);
        assertEquals("anthropic.claude-3-sonnet-20240229-v1:0", BedrockModels.ANTROPHIC_CLAUDE_V3.model);
        assertEquals("anthropic.claude-3-5-sonnet-20240620-v1:0", BedrockModels.ANTROPHIC_CLAUDE_V35.model);
        assertEquals("anthropic.claude-3-5-sonnet-20241022-v2:0", BedrockModels.ANTROPHIC_CLAUDE_V35_2.model);
        assertEquals("anthropic.claude-3-haiku-20240307-v1:0", BedrockModels.ANTROPHIC_CLAUDE_HAIKU_V3.model);
        assertEquals("anthropic.claude-3-5-haiku-20241022-v1:0", BedrockModels.ANTROPHIC_CLAUDE_HAIKU_V35.model);
        assertEquals("anthropic.claude-3-opus-20240229-v1:0", BedrockModels.ANTROPHIC_CLAUDE_OPUS_V3.model);
        assertEquals("anthropic.claude-3-7-sonnet-20250219-v1:0", BedrockModels.ANTROPHIC_CLAUDE_V37.model);
        assertEquals("anthropic.claude-opus-4-20250514-v1:0", BedrockModels.ANTROPHIC_CLAUDE_OPUS_V4.model);
        assertEquals("anthropic.claude-sonnet-4-20250514-v1:0", BedrockModels.ANTROPHIC_CLAUDE_SONNET_V4.model);
    }

    @Test
    void testCohereModels() {
        assertEquals("cohere.command-r-plus-v1:0", BedrockModels.COHERE_COMMAND_R_PLUS.model);
        assertEquals("cohere.command-r-v1:0", BedrockModels.COHERE_COMMAND_R.model);
        assertEquals("cohere.embed-english-v3", BedrockModels.COHERE_EMBED_ENGLISH_V3.model);
        assertEquals("cohere.embed-multilingual-v3", BedrockModels.COHERE_EMBED_MULTILINGUAL_V3.model);
        assertEquals("cohere.rerank-v3-5:0", BedrockModels.COHERE_RERANK_V3_5.model);
    }

    @Test
    void testMetaLlamaModels() {
        assertEquals("meta.llama3-8b-instruct-v1:0", BedrockModels.LLAMA3_8B_INSTRUCT.model);
        assertEquals("meta.llama3-70b-instruct-v1:0", BedrockModels.LLAMA3_70B_INSTRUCT.model);
        assertEquals("meta.llama3-1-8b-instruct-v1:0", BedrockModels.LLAMA3_1_8B_INSTRUCT.model);
        assertEquals("meta.llama3-1-70b-instruct-v1:0", BedrockModels.LLAMA3_1_70B_INSTRUCT.model);
        assertEquals("meta.llama3-1-405b-instruct-v1:0", BedrockModels.LLAMA3_1_405B_INSTRUCT.model);
        assertEquals("meta.llama3-2-1b-instruct-v1:0", BedrockModels.LLAMA3_2_1B_INSTRUCT.model);
        assertEquals("meta.llama3-2-3b-instruct-v1:0", BedrockModels.LLAMA3_2_3B_INSTRUCT.model);
        assertEquals("meta.llama3-2-11b-instruct-v1:0", BedrockModels.LLAMA3_2_11B_INSTRUCT.model);
        assertEquals("meta.llama3-2-90b-instruct-v1:0", BedrockModels.LLAMA3_2_90B_INSTRUCT.model);
        assertEquals("meta.llama3-3-70b-instruct-v1:0", BedrockModels.LLAMA3_3_70B_INSTRUCT.model);
        assertEquals("meta.llama4-maverick-17b-instruct-v1:0", BedrockModels.LLAMA4_MAVERICK_17B_INSTRUCT.model);
        assertEquals("meta.llama4-scout-17b-instruct-v1:0", BedrockModels.LLAMA4_SCOUT_17B_INSTRUCT.model);
    }

    @Test
    void testMistralAIModels() {
        assertEquals("mistral.mistral-7b-instruct-v0:2", BedrockModels.MISTRAL_7B_INSTRUCT.model);
        assertEquals("mistral.mixtral-8x7b-instruct-v0:1", BedrockModels.MISTRAL_8x7B_INSTRUCT.model);
        assertEquals("mistral.mistral-large-2402-v1:0", BedrockModels.MISTRAL_LARGE.model);
        assertEquals("mistral.mistral-large-2407-v1:0", BedrockModels.MISTRAL_LARGE_2407.model);
        assertEquals("mistral.mistral-small-2402-v1:0", BedrockModels.MISTRAL_SMALL_2402.model);
        assertEquals("mistral.pixtral-large-2502-v1:0", BedrockModels.PIXTRAL_LARGE.model);
    }

    @Test
    void testStabilityAIModels() {
        assertEquals("stability.sd3-5-large-v1:0", BedrockModels.STABLE_DIFFUSION_3_5_LARGE.model);
        assertEquals("stability.stable-image-control-sketch-v1:0", BedrockModels.STABLE_IMAGE_CONTROL_SKETCH.model);
        assertEquals(
                "stability.stable-image-control-structure-v1:0", BedrockModels.STABLE_IMAGE_CONTROL_STRUCTURE.model);
        assertEquals("stability.stable-image-core-v1:1", BedrockModels.STABLE_IMAGE_CORE.model);
    }

    @Test
    void testModelCategorization() {
        assertTrue(BedrockModels.TITAN_TEXT_EXPRESS_V1.model.startsWith("amazon."));
        assertTrue(BedrockModels.JAMBA_1_5_LARGE.model.startsWith("ai21."));
        assertTrue(BedrockModels.ANTROPHIC_CLAUDE_V3.model.startsWith("anthropic."));
        assertTrue(BedrockModels.COHERE_COMMAND_R.model.startsWith("cohere."));
        assertTrue(BedrockModels.LLAMA3_8B_INSTRUCT.model.startsWith("meta."));
        assertTrue(BedrockModels.MISTRAL_7B_INSTRUCT.model.startsWith("mistral."));
        assertTrue(BedrockModels.STABLE_DIFFUSION_3_5_LARGE.model.startsWith("stability."));
    }

    @Test
    void testEnumCountAndNewModels() {
        BedrockModels[] allModels = BedrockModels.values();
        assertEquals(
                60,
                allModels.length,
                "Should have exactly 60 models (including deprecated models with @Deprecated annotations)");

        boolean hasNovaModels = false;
        boolean hasJambaModels = false;
        boolean hasLlamaModels = false;
        boolean hasCohereModels = false;
        boolean hasStabilityModels = false;

        for (BedrockModels model : allModels) {
            if (model.model.contains("nova")) {
                hasNovaModels = true;
            }
            if (model.model.contains("jamba")) {
                hasJambaModels = true;
            }
            if (model.model.contains("llama")) {
                hasLlamaModels = true;
            }
            if (model.model.startsWith("cohere.")) {
                hasCohereModels = true;
            }
            if (model.model.startsWith("stability.")) {
                hasStabilityModels = true;
            }
        }

        assertTrue(hasNovaModels, "Should include Amazon Nova models");
        assertTrue(hasJambaModels, "Should include AI21 Jamba models");
        assertTrue(hasLlamaModels, "Should include Meta Llama models");
        assertTrue(hasCohereModels, "Should include Cohere models");
        assertTrue(hasStabilityModels, "Should include Stability AI models");
    }
}
