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
package org.apache.camel.component.aws2.bedrock.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Bedrock accepts cross-region inference profile ids and inference-profile ARNs in place of a plain foundation-model
 * id. They must resolve to the same foundation model so the response is parsed with the right model family.
 */
class BedrockProducerModelIdTest {

    private static final String CLAUDE = "anthropic.claude-3-5-sonnet-20241022-v2:0";

    @Test
    void plainModelIdIsUnchanged() {
        assertEquals(CLAUDE, BedrockProducer.resolveFoundationModelId(CLAUDE));
    }

    @Test
    void crossRegionInferenceProfileIdsResolveToTheFoundationModel() {
        assertEquals(CLAUDE, BedrockProducer.resolveFoundationModelId("us." + CLAUDE));
        assertEquals(CLAUDE, BedrockProducer.resolveFoundationModelId("eu." + CLAUDE));
        assertEquals(CLAUDE, BedrockProducer.resolveFoundationModelId("apac." + CLAUDE));
        assertEquals(CLAUDE, BedrockProducer.resolveFoundationModelId("us-gov." + CLAUDE));
    }

    @Test
    void inferenceProfileArnResolvesToTheFoundationModel() {
        String arn = "arn:aws:bedrock:eu-west-1:123456789012:inference-profile/eu." + CLAUDE;
        assertEquals(CLAUDE, BedrockProducer.resolveFoundationModelId(arn));
    }

    @Test
    void nullModelIdIsTolerated() {
        assertNull(BedrockProducer.resolveFoundationModelId(null));
    }
}
