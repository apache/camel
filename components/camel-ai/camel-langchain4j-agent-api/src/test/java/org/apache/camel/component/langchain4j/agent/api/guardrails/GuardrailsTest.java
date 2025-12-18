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
package org.apache.camel.component.langchain4j.agent.api.guardrails;

import java.util.List;

import org.apache.camel.component.langchain4j.agent.api.AgentConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardrailsTest {

    @Test
    void testDefaultInputGuardrails() {
        List<Class<?>> guardrails = Guardrails.defaultInputGuardrails();

        assertNotNull(guardrails);
        assertEquals(3, guardrails.size());
        assertTrue(guardrails.contains(InputLengthGuardrail.class));
        assertTrue(guardrails.contains(PiiDetectorGuardrail.class));
        assertTrue(guardrails.contains(PromptInjectionGuardrail.class));
    }

    @Test
    void testDefaultOutputGuardrails() {
        List<Class<?>> guardrails = Guardrails.defaultOutputGuardrails();

        assertNotNull(guardrails);
        assertEquals(2, guardrails.size());
        assertTrue(guardrails.contains(OutputLengthGuardrail.class));
        assertTrue(guardrails.contains(SensitiveDataOutputGuardrail.class));
    }

    @Test
    void testMinimalInputGuardrails() {
        List<Class<?>> guardrails = Guardrails.minimalInputGuardrails();

        assertNotNull(guardrails);
        assertEquals(1, guardrails.size());
        assertTrue(guardrails.contains(InputLengthGuardrail.class));
    }

    @Test
    void testStrictInputGuardrails() {
        List<Class<?>> guardrails = Guardrails.strictInputGuardrails();

        assertNotNull(guardrails);
        assertEquals(4, guardrails.size());
        assertTrue(guardrails.contains(InputLengthGuardrail.class));
        assertTrue(guardrails.contains(PiiDetectorGuardrail.class));
        assertTrue(guardrails.contains(PromptInjectionGuardrail.class));
        assertTrue(guardrails.contains(KeywordFilterGuardrail.class));
    }

    @Test
    void testInputGuardrailFactories() {
        assertNotNull(Guardrails.inputLength());
        assertEquals(5000, Guardrails.inputLength(5000).getMaxChars());
        assertNotNull(Guardrails.piiDetector());
        assertNotNull(Guardrails.piiDetectorBuilder());
        assertNotNull(Guardrails.promptInjection());
        assertTrue(Guardrails.promptInjectionStrict().isStrict());
    }

    @Test
    void testOutputGuardrailFactories() {
        assertNotNull(Guardrails.outputLength());
        assertEquals(5000, Guardrails.outputLength(5000).getMaxChars());
        assertTrue(Guardrails.outputLengthTruncating(1000).isTruncateOnOverflow());
        assertNotNull(Guardrails.sensitiveData());
        assertEquals(SensitiveDataOutputGuardrail.Action.REDACT,
                Guardrails.sensitiveDataRedacting().getAction());
        assertNotNull(Guardrails.jsonFormat());
    }

    @Test
    void testKeywordFilterFactories() {
        KeywordFilterGuardrail inputFilter = Guardrails.keywordFilter("spam", "test");
        assertNotNull(inputFilter);
        assertTrue(inputFilter.getBlockedWords().contains("spam"));
        assertTrue(inputFilter.getBlockedWords().contains("test"));

        KeywordOutputFilterGuardrail outputFilter = Guardrails.outputKeywordFilter("confidential");
        assertNotNull(outputFilter);
        assertEquals(KeywordOutputFilterGuardrail.Action.BLOCK, outputFilter.getAction());

        KeywordOutputFilterGuardrail redactingFilter = Guardrails.outputKeywordFilterRedacting("secret");
        assertEquals(KeywordOutputFilterGuardrail.Action.REDACT, redactingFilter.getAction());
    }

    @Test
    void testJsonFormatWithFields() {
        JsonFormatGuardrail guardrail = Guardrails.jsonFormatWithFields("id", "name", "email");
        assertTrue(guardrail.getRequiredFields().contains("id"));
        assertTrue(guardrail.getRequiredFields().contains("name"));
        assertTrue(guardrail.getRequiredFields().contains("email"));
    }

    @Test
    void testConfigurationBuilder() {
        AgentConfiguration config = Guardrails.configure()
                .withDefaultGuardrails()
                .build();

        assertNotNull(config);
        assertNotNull(config.getInputGuardrailClasses());
        assertNotNull(config.getOutputGuardrailClasses());
        assertFalse(config.getInputGuardrailClasses().isEmpty());
        assertFalse(config.getOutputGuardrailClasses().isEmpty());
    }

    @Test
    void testConfigurationBuilderWithSpecificGuardrails() {
        AgentConfiguration config = Guardrails.configure()
                .withPiiDetection()
                .withPromptInjectionDetection()
                .withSensitiveDataDetection()
                .build();

        assertNotNull(config);
        assertEquals(2, config.getInputGuardrailClasses().size());
        assertEquals(1, config.getOutputGuardrailClasses().size());
    }

    @Test
    void testConfigurationBuilderWithIndividualGuardrails() {
        AgentConfiguration config = Guardrails.configure()
                .withInputGuardrail(InputLengthGuardrail.class)
                .withOutputGuardrail(JsonFormatGuardrail.class)
                .build();

        assertNotNull(config);
        assertEquals(1, config.getInputGuardrailClasses().size());
        assertEquals(1, config.getOutputGuardrailClasses().size());
        assertTrue(config.getInputGuardrailClasses().contains(InputLengthGuardrail.class));
        assertTrue(config.getOutputGuardrailClasses().contains(JsonFormatGuardrail.class));
    }

    @Test
    void testConfigurationBuilderCombiningDefaults() {
        AgentConfiguration config = Guardrails.configure()
                .withDefaultInputGuardrails()
                .withJsonFormatValidation()
                .build();

        assertNotNull(config);
        assertEquals(3, config.getInputGuardrailClasses().size());
        assertEquals(1, config.getOutputGuardrailClasses().size());
    }
}
