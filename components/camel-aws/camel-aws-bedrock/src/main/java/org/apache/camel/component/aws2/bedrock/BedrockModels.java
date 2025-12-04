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

public enum BedrockModels {

    // Amazon Titan Models
    TITAN_TEXT_EXPRESS_V1("amazon.titan-text-express-v1"),
    TITAN_TEXT_LITE_V1("amazon.titan-text-lite-v1"),
    TITAN_IMAGE_GENERATOR_V1("amazon.titan-image-generator-v1"),
    TITAN_EMBEDDINGS_G1("amazon.titan-embed-text-v1"),
    TITAN_MULTIMODAL_EMBEDDINGS_G1("amazon.titan-embed-image-v1"),
    TITAN_TEXT_PREMIER_V1("amazon.titan-text-premier-v1:0"),
    TITAN_TEXT_EMBEDDINGS_V2("amazon.titan-embed-text-v2:0"),
    TITAN_IMAGE_GENERATOR_V2("amazon.titan-image-generator-v2:0"),

    // Amazon Nova Models
    NOVA_CANVAS_V1("amazon.nova-canvas-v1:0"),
    NOVA_LITE_V1("amazon.nova-lite-v1:0"),
    NOVA_MICRO_V1("amazon.nova-micro-v1:0"),
    NOVA_PREMIER_V1("amazon.nova-premier-v1:0"),
    NOVA_PRO_V1("amazon.nova-pro-v1:0"),
    NOVA_REEL_V1("amazon.nova-reel-v1:0"),
    NOVA_REEL_V1_1("amazon.nova-reel-v1:1"),
    NOVA_SONIC_V1("amazon.nova-sonic-v1:0"),

    // Amazon Rerank
    RERANK_V1("amazon.rerank-v1:0"),

    // AI21 Labs Models
    /** @deprecated Deprecated October 2024, use JAMBA_1_5_LARGE instead */
    @Deprecated(since = "4.10.0", forRemoval = true)
    JURASSIC2_ULTRA("ai21.j2-ultra-v1"),
    /** @deprecated Deprecated October 2024, use JAMBA_1_5_MINI instead */
    @Deprecated(since = "4.10.0", forRemoval = true)
    JURASSIC2_MID("ai21.j2-mid-v1"),
    JAMBA_1_5_LARGE("ai21.jamba-1-5-large-v1:0"),
    JAMBA_1_5_MINI("ai21.jamba-1-5-mini-v1:0"),

    // Anthropic Claude Models
    /** @deprecated Deprecated July 2025, use ANTROPHIC_CLAUDE_HAIKU_V3 instead */
    @Deprecated(since = "4.10.0", forRemoval = true)
    ANTROPHIC_CLAUDE_INSTANT_V1("anthropic.claude-instant-v1"),
    /** @deprecated Deprecated July 2025, use ANTROPHIC_CLAUDE_V3 instead */
    @Deprecated(since = "4.10.0", forRemoval = true)
    ANTROPHIC_CLAUDE_V2("anthropic.claude-v2"),
    /** @deprecated Deprecated July 2025, use ANTROPHIC_CLAUDE_V3 instead */
    @Deprecated(since = "4.10.0", forRemoval = true)
    ANTROPHIC_CLAUDE_V2_1("anthropic.claude-v2:1"),
    ANTROPHIC_CLAUDE_V3("anthropic.claude-3-sonnet-20240229-v1:0"),
    ANTROPHIC_CLAUDE_V35("anthropic.claude-3-5-sonnet-20240620-v1:0"),
    ANTROPHIC_CLAUDE_V35_2("anthropic.claude-3-5-sonnet-20241022-v2:0"),
    ANTROPHIC_CLAUDE_HAIKU_V3("anthropic.claude-3-haiku-20240307-v1:0"),
    ANTROPHIC_CLAUDE_HAIKU_V35("anthropic.claude-3-5-haiku-20241022-v1:0"),
    ANTROPHIC_CLAUDE_OPUS_V3("anthropic.claude-3-opus-20240229-v1:0"),
    ANTROPHIC_CLAUDE_V37("anthropic.claude-3-7-sonnet-20250219-v1:0"),
    ANTROPHIC_CLAUDE_OPUS_V4("anthropic.claude-opus-4-20250514-v1:0"),
    ANTROPHIC_CLAUDE_SONNET_V4("anthropic.claude-sonnet-4-20250514-v1:0"),

    // Cohere Models
    COHERE_COMMAND_R_PLUS("cohere.command-r-plus-v1:0"),
    COHERE_COMMAND_R("cohere.command-r-v1:0"),
    COHERE_EMBED_ENGLISH_V3("cohere.embed-english-v3"),
    COHERE_EMBED_MULTILINGUAL_V3("cohere.embed-multilingual-v3"),
    COHERE_RERANK_V3_5("cohere.rerank-v3-5:0"),

    // Meta Llama Models (Llama 2 deprecated Oct 2024, Llama 3+ supported)
    LLAMA3_8B_INSTRUCT("meta.llama3-8b-instruct-v1:0"),
    LLAMA3_70B_INSTRUCT("meta.llama3-70b-instruct-v1:0"),
    LLAMA3_1_8B_INSTRUCT("meta.llama3-1-8b-instruct-v1:0"),
    LLAMA3_1_70B_INSTRUCT("meta.llama3-1-70b-instruct-v1:0"),
    LLAMA3_1_405B_INSTRUCT("meta.llama3-1-405b-instruct-v1:0"),
    LLAMA3_2_1B_INSTRUCT("meta.llama3-2-1b-instruct-v1:0"),
    LLAMA3_2_3B_INSTRUCT("meta.llama3-2-3b-instruct-v1:0"),
    LLAMA3_2_11B_INSTRUCT("meta.llama3-2-11b-instruct-v1:0"),
    LLAMA3_2_90B_INSTRUCT("meta.llama3-2-90b-instruct-v1:0"),
    LLAMA3_3_70B_INSTRUCT("meta.llama3-3-70b-instruct-v1:0"),
    LLAMA4_MAVERICK_17B_INSTRUCT("meta.llama4-maverick-17b-instruct-v1:0"),
    LLAMA4_SCOUT_17B_INSTRUCT("meta.llama4-scout-17b-instruct-v1:0"),

    // Mistral AI Models
    MISTRAL_7B_INSTRUCT("mistral.mistral-7b-instruct-v0:2"),
    MISTRAL_8x7B_INSTRUCT("mistral.mixtral-8x7b-instruct-v0:1"),
    MISTRAL_LARGE("mistral.mistral-large-2402-v1:0"),
    MISTRAL_LARGE_2407("mistral.mistral-large-2407-v1:0"),
    MISTRAL_SMALL_2402("mistral.mistral-small-2402-v1:0"),
    PIXTRAL_LARGE("mistral.pixtral-large-2502-v1:0"),

    // Stability AI Models
    STABLE_DIFFUSION_3_5_LARGE("stability.sd3-5-large-v1:0"),
    STABLE_IMAGE_CONTROL_SKETCH("stability.stable-image-control-sketch-v1:0"),
    STABLE_IMAGE_CONTROL_STRUCTURE("stability.stable-image-control-structure-v1:0"),
    STABLE_IMAGE_CORE("stability.stable-image-core-v1:1");

    public final String model;

    private BedrockModels(String model) {
        this.model = model;
    }
}
