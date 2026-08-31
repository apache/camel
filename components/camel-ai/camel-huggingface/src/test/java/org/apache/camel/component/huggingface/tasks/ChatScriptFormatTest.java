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
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the chat.py template's format-argument alignment after the per-task token clause was removed (the token is now
 * applied centrally as HF_TOKEN). A misaligned placeholder would make getPythonScript throw.
 */
class ChatScriptFormatTest {

    private DefaultCamelContext context;

    @BeforeEach
    void setUp() {
        context = new DefaultCamelContext();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    @Test
    void chatScriptFormatsAndCarriesNoTokenClause() {
        HuggingFaceConfiguration config = new HuggingFaceConfiguration();
        config.setModelId("gpt2");
        HuggingFaceEndpoint endpoint = new HuggingFaceEndpoint(null, null, config);
        endpoint.setCamelContext(context);

        String script = new ChatPredictor(endpoint).getPythonScript();

        assertTrue(script.contains("pipeline(task='text-generation'"), "the chat pipeline call must be rendered");
        assertTrue(script.contains("model='gpt2'"), "the configured model must be interpolated");
        assertFalse(script.contains("token="), "the per-task token clause must be gone (token is applied via HF_TOKEN)");
    }
}
