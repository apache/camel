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
package org.apache.camel.component.huggingface.tasks;

import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The configured Hugging Face token must be applied for every task, not only chat, so that gated or private models can
 * be loaded. The token is injected centrally as the HF_TOKEN environment variable of the generated handler.
 */
class AuthTokenInjectionTest {

    private TextGenerationPredictor predictorWithToken(String token) {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setAuthToken(token);
        return new TextGenerationPredictor(new HuggingFaceEndpoint(null, null, config));
    }

    @Test
    void authTokenIsExposedAsHfTokenEnvForEveryTask() {
        String result = predictorWithToken("hf_secret123").withAuthToken("PIPELINE");
        assertTrue(result.contains("os.environ['HF_TOKEN'] = 'hf_secret123'"),
                "generated script should export the token as HF_TOKEN");
        assertTrue(result.endsWith("PIPELINE"), "the original task script must be preserved");
    }

    @Test
    void noAuthTokenLeavesTheScriptUnchanged() {
        assertEquals("PIPELINE", predictorWithToken(null).withAuthToken("PIPELINE"));
        assertEquals("PIPELINE", predictorWithToken("").withAuthToken("PIPELINE"));
    }

    @Test
    void authTokenWithASingleQuoteIsEscaped() {
        String result = predictorWithToken("ab'cd").withAuthToken("PIPELINE");
        assertTrue(result.contains("os.environ['HF_TOKEN'] = 'ab\\'cd'"),
                "a single quote in the token must be escaped so it cannot break the Python string literal");
    }
}
