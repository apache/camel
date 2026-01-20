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

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegexPatternGuardrailTest {

    @Test
    void testEmptyGuardrailAllowsAll() {
        RegexPatternGuardrail guardrail = new RegexPatternGuardrail();

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("https://example.com")).isSuccess());
    }

    @Test
    void testBlockingPattern() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.blocking(
                "https?://[^\\s]+", "URLs are not allowed");

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Visit https://example.com")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Go to http://test.org")).isSuccess());
    }

    @Test
    void testRequiringPattern() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.requiring(
                "TICKET-\\d+", "Please include a ticket number");

        assertTrue(guardrail.validate(UserMessage.from("Fix TICKET-123")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("Working on TICKET-456 now")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
    }

    @Test
    void testMultipleDenyPatterns() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .denyPattern("https?://[^\\s]+", "URLs are not allowed")
                .denyPattern("\\b(password|secret)\\b", "Sensitive keywords not allowed")
                .build();

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Visit https://example.com")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("My password is 123")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("The secret is here")).isSuccess());
    }

    @Test
    void testMultipleRequirePatterns() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .requirePattern("TICKET-\\d+", "Please include a ticket number")
                .requirePattern("@\\w+", "Please mention a user")
                .build();

        assertTrue(guardrail.validate(UserMessage.from("TICKET-123 @john")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("TICKET-123")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("@john")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
    }

    @Test
    void testCombinedDenyAndRequire() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .denyPattern("https?://[^\\s]+", "URLs are not allowed")
                .requirePattern("TICKET-\\d+", "Please include a ticket number")
                .build();

        assertTrue(guardrail.validate(UserMessage.from("Fix TICKET-123")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("TICKET-123 at https://example.com")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
    }

    @Test
    void testFailOnFirstMatch() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .denyPattern("pattern1", "Error 1")
                .denyPattern("pattern2", "Error 2")
                .failOnFirstMatch(true)
                .build();

        InputGuardrailResult result = guardrail.validate(UserMessage.from("Contains pattern1 and pattern2"));
        assertFalse(result.isSuccess());
        // Should only contain first error due to failOnFirstMatch
        assertTrue(result.toString().contains("Error 1"));
    }

    @Test
    void testCollectAllErrors() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .denyPattern("pattern1", "Error 1")
                .denyPattern("pattern2", "Error 2")
                .failOnFirstMatch(false)
                .build();

        InputGuardrailResult result = guardrail.validate(UserMessage.from("Contains pattern1 and pattern2"));
        assertFalse(result.isSuccess());
        // Should contain both errors
        assertTrue(result.toString().contains("Error 1"));
        assertTrue(result.toString().contains("Error 2"));
    }

    @Test
    void testNullMessage() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.blocking("test", "Error");

        InputGuardrailResult result = guardrail.validate((UserMessage) null);
        assertTrue(result.isSuccess());
    }

    @Test
    void testGetDenyPatterns() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .denyPattern("pattern1", "Error 1")
                .denyPattern("pattern2", "Error 2")
                .build();

        assertEquals(2, guardrail.getDenyPatterns().size());
    }

    @Test
    void testGetRequirePatterns() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.builder()
                .requirePattern("pattern1", "Error 1")
                .requirePattern("pattern2", "Error 2")
                .build();

        assertEquals(2, guardrail.getRequirePatterns().size());
    }

    @Test
    void testCaseInsensitivePattern() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.blocking(
                "(?i)blocked", "Blocked word found");

        assertFalse(guardrail.validate(UserMessage.from("This is BLOCKED")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("This is blocked")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("This is Blocked")).isSuccess());
    }

    @Test
    void testEmailPattern() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.blocking(
                "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}", "Email addresses not allowed");

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Contact me at test@example.com")).isSuccess());
    }

    @Test
    void testPhoneNumberPattern() {
        RegexPatternGuardrail guardrail = RegexPatternGuardrail.blocking(
                "\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b", "Phone numbers not allowed");

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Call me at 555-123-4567")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Call me at 5551234567")).isSuccess());
    }
}
