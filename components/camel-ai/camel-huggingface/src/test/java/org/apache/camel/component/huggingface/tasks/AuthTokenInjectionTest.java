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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.component.huggingface.HuggingFaceConfiguration;
import org.apache.camel.component.huggingface.HuggingFaceEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The configured Hugging Face token must be applied for every task, not only chat, so that gated or private models can
 * be loaded. The token is given to the Python process as the HF_TOKEN environment variable (the env option of the DJL
 * Python engine), and is not written into the generated handler.
 */
class AuthTokenInjectionTest {

    @TempDir
    Path dir;

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private TextGenerationPredictor predictorWithToken(String token) {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("gpt2");
        config.setAuthToken(token);
        HuggingFaceEndpoint endpoint = new HuggingFaceEndpoint(null, null, config);
        endpoint.setCamelContext(context);
        return new TextGenerationPredictor(endpoint);
    }

    @Test
    void authTokenIsGivenAsHfTokenEnvironmentVariable() {
        Map<String, String> options = predictorWithToken("hf_secret123").modelOptions(dir.resolve("requirements.txt"));
        assertEquals("HF_TOKEN=hf_secret123", options.get("env"));
        assertEquals("handler.py", options.get("entryPoint"));
    }

    @Test
    void noAuthTokenSetsNoEnvironmentVariable() {
        assertNull(predictorWithToken(null).modelOptions(dir.resolve("requirements.txt")).get("env"));
        assertNull(predictorWithToken("").modelOptions(dir.resolve("requirements.txt")).get("env"));
    }

    @Test
    void authTokenIsNotWrittenIntoTheHandler() throws Exception {
        predictorWithToken("hf_secret123").writeModelFiles(dir);

        String handler = Files.readString(dir.resolve("handler.py"));
        assertFalse(handler.contains("hf_secret123"), "the token must not be in the generated handler");
        assertFalse(handler.contains("HF_TOKEN"), "the handler must not set the token itself");
        assertFalse(Files.readString(dir.resolve("requirements.txt")).contains("hf_secret123"));
    }

    @Test
    void authTokenWithACommaFails() {
        TextGenerationPredictor predictor = predictorWithToken("hf_ab,cd");
        Path reqPath = dir.resolve("requirements.txt");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> predictor.modelOptions(reqPath));
        assertFalse(e.getMessage().contains("hf_ab"), "the message must not show the token");
    }
}
