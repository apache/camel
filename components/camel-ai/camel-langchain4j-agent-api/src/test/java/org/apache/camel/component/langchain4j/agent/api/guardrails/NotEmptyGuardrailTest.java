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

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotEmptyGuardrailTest {

    @Test
    void testValidResponse() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail();

        assertTrue(guardrail.validate(AiMessage.from("Hello, how can I help you?")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("Here is your answer.")).isSuccess());
    }

    @Test
    void testEmptyResponse() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail();

        assertFalse(guardrail.validate(AiMessage.from("")).isSuccess());
    }

    @Test
    void testWhitespaceOnlyResponse() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail();

        assertFalse(guardrail.validate(AiMessage.from("   ")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("\t\n")).isSuccess());
    }

    @Test
    void testNullMessage() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail();

        OutputGuardrailResult result = guardrail.validate((AiMessage) null);
        assertFalse(result.isSuccess());
    }

    @Test
    void testRefusalDetectionEnabled() {
        NotEmptyGuardrail guardrail = NotEmptyGuardrail.withRefusalDetection();

        // Normal responses should pass
        assertTrue(guardrail.validate(AiMessage.from("Here is your answer.")).isSuccess());

        // Refusal patterns should fail
        assertFalse(guardrail.validate(AiMessage.from("I cannot help with that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I can't provide that information.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I'm unable to assist with that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I am unable to do that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I don't have access to that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I'm not able to help.")).isSuccess());
    }

    @Test
    void testRefusalDetectionDisabledByDefault() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail();

        // Refusal patterns should pass when detection is disabled
        assertTrue(guardrail.validate(AiMessage.from("I cannot help with that.")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("I can't provide that information.")).isSuccess());
    }

    @Test
    void testMinMeaningfulLength() {
        NotEmptyGuardrail guardrail = NotEmptyGuardrail.withMinLength(10);

        // Too short
        assertFalse(guardrail.validate(AiMessage.from("Hi")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("Yes")).isSuccess());

        // Just at minimum
        assertTrue(guardrail.validate(AiMessage.from("0123456789")).isSuccess());

        // Above minimum
        assertTrue(guardrail.validate(AiMessage.from("This is a proper response.")).isSuccess());
    }

    @Test
    void testCustomConfiguration() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail(true, 20);

        // Too short
        assertFalse(guardrail.validate(AiMessage.from("Short")).isSuccess());

        // Refusal
        assertFalse(guardrail.validate(AiMessage.from("I cannot help with that request at all.")).isSuccess());

        // Valid response
        assertTrue(guardrail.validate(AiMessage.from("This is a proper response that is long enough.")).isSuccess());
    }

    @Test
    void testMinLengthEnforcesAtLeastOne() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail(false, 0);

        // Min length should be enforced to at least 1
        assertEquals(1, guardrail.getMinMeaningfulLength());
    }

    @Test
    void testNegativeMinLengthTreatedAsOne() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail(false, -5);

        assertEquals(1, guardrail.getMinMeaningfulLength());
    }

    @Test
    void testGetters() {
        NotEmptyGuardrail guardrail = new NotEmptyGuardrail(true, 50);

        assertTrue(guardrail.isDetectRefusals());
        assertEquals(50, guardrail.getMinMeaningfulLength());
    }

    @Test
    void testRefusalPatternsCaseInsensitive() {
        NotEmptyGuardrail guardrail = NotEmptyGuardrail.withRefusalDetection();

        // Test case insensitivity
        assertFalse(guardrail.validate(AiMessage.from("I CANNOT help with that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("i cannot help with that.")).isSuccess());
    }

    @Test
    void testRefusalMustBeAtStart() {
        NotEmptyGuardrail guardrail = NotEmptyGuardrail.withRefusalDetection();

        // Refusal at start should fail
        assertFalse(guardrail.validate(AiMessage.from("I cannot do that for you.")).isSuccess());

        // Refusal not at start should pass
        assertTrue(
                guardrail.validate(AiMessage.from("While I cannot do everything, here is what I can help with.")).isSuccess());
    }

    @Test
    void testApologyRefusals() {
        NotEmptyGuardrail guardrail = NotEmptyGuardrail.withRefusalDetection();

        assertFalse(guardrail.validate(AiMessage.from("I apologize, but I cannot help with that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("Sorry, but I cannot assist.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I'm sorry, I cannot do that.")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("I'm afraid I cannot help.")).isSuccess());
    }
}
