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

    TITAN_TEXT_EXPRESS_V1("amazon.titan-text-express-v1"),
    TITAN_TEXT_LITE_V1("amazon.titan-text-lite-v1"),
    TITAN_IMAGE_GENERATOR_V1("amazon.titan-image-generator-v1"),
    TITAN_EMBEDDINGS_G1("amazon.titan-embed-text-v1"),
    TITAN_MULTIMODAL_EMBEDDINGS_G1("amazon.titan-embed-image-v1"),
    TITAN_TEXT_PREMIER_V1("amazon.titan-text-premier-v1:0"),
    TITAN_TEXT_EMBEDDINGS_V2("amazon.titan-embed-text-v2:0"),
    JURASSIC2_ULTRA("ai21.j2-ultra-v1"),
    JURASSIC2_MID("ai21.j2-mid-v1"),
    ANTROPHIC_CLAUDE_INSTANT_V1("anthropic.claude-instant-v1"),
    ANTROPHIC_CLAUDE_V2("anthropic.claude-v2"),
    ANTROPHIC_CLAUDE_V2_1("anthropic.claude-v2:1"),
    ANTROPHIC_CLAUDE_V3("anthropic.claude-3-sonnet-20240229-v1:0"),
    ANTROPHIC_CLAUDE_V35("anthropic.claude-3-5-sonnet-20240620-v1:0"),
    ANTROPHIC_CLAUDE_V35_2("anthropic.claude-3-5-sonnet-20241022-v2:0"),
    ANTROPHIC_CLAUDE_HAIKU_V3("anthropic.claude-3-haiku-20240307-v1:0"),
    ANTROPHIC_CLAUDE_HAIKU_V35("anthropic.claude-3-5-haiku-20241022-v1:0"),
    MISTRAL_7B_INSTRUCT("mistral.mistral-7b-instruct-v0:2"),
    MISTRAL_8x7B_INSTRUCT("mistral.mixtral-8x7b-instruct-v0:1"),
    MISTRAL_LARGE("mistral.mistral-large-2402-v1:0");

    public final String model;

    private BedrockModels(String model) {
        this.model = model;
    }
}
