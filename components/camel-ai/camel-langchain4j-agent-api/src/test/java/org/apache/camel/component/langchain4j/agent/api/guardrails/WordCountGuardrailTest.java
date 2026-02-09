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

class WordCountGuardrailTest {

    @Test
    void testDefaultAllowsAll() {
        WordCountGuardrail guardrail = new WordCountGuardrail();

        assertTrue(guardrail.validate(AiMessage.from("Hello")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("Hello world how are you today")).isSuccess());
    }

    @Test
    void testAtLeast() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(5);

        // Fewer than 5 words
        assertFalse(guardrail.validate(AiMessage.from("Hello")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("One two three four")).isSuccess());

        // Exactly 5 words
        assertTrue(guardrail.validate(AiMessage.from("One two three four five")).isSuccess());

        // More than 5 words
        assertTrue(guardrail.validate(AiMessage.from("One two three four five six seven")).isSuccess());
    }

    @Test
    void testAtMost() {
        WordCountGuardrail guardrail = WordCountGuardrail.atMost(5);

        // Fewer than 5 words
        assertTrue(guardrail.validate(AiMessage.from("Hello")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("Hello world")).isSuccess());

        // Exactly 5 words
        assertTrue(guardrail.validate(AiMessage.from("One two three four five")).isSuccess());

        // More than 5 words
        assertFalse(guardrail.validate(AiMessage.from("One two three four five six")).isSuccess());
    }

    @Test
    void testBetween() {
        WordCountGuardrail guardrail = WordCountGuardrail.between(3, 7);

        // Fewer than 3 words
        assertFalse(guardrail.validate(AiMessage.from("Hello")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("Hello world")).isSuccess());

        // Between 3 and 7 words
        assertTrue(guardrail.validate(AiMessage.from("One two three")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("One two three four five")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("One two three four five six seven")).isSuccess());

        // More than 7 words
        assertFalse(guardrail.validate(AiMessage.from("One two three four five six seven eight")).isSuccess());
    }

    @Test
    void testBuilder() {
        WordCountGuardrail guardrail = WordCountGuardrail.builder()
                .minWords(10)
                .maxWords(50)
                .build();

        assertEquals(10, guardrail.getMinWords());
        assertEquals(50, guardrail.getMaxWords());
    }

    @Test
    void testNullMessage() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(1);

        OutputGuardrailResult result = guardrail.validate((AiMessage) null);
        assertFalse(result.isSuccess());
    }

    @Test
    void testEmptyMessage() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(1);

        assertFalse(guardrail.validate(AiMessage.from("")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("   ")).isSuccess());
    }

    @Test
    void testGetters() {
        WordCountGuardrail guardrail = WordCountGuardrail.between(5, 100);

        assertEquals(5, guardrail.getMinWords());
        assertEquals(100, guardrail.getMaxWords());
    }

    @Test
    void testWordCountWithPunctuation() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(5);

        // Words with punctuation should still be counted as words
        assertTrue(guardrail.validate(AiMessage.from("Hello, world! How are you?")).isSuccess());
    }

    @Test
    void testWordCountWithMultipleSpaces() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(3);

        // Multiple spaces should not create extra words
        assertTrue(guardrail.validate(AiMessage.from("One   two   three")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("One   two")).isSuccess());
    }

    @Test
    void testWordCountWithNewlines() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(4);

        // Newlines should separate words
        assertTrue(guardrail.validate(AiMessage.from("One\ntwo\nthree\nfour")).isSuccess());
    }

    @Test
    void testWordCountWithTabs() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(3);

        // Tabs should separate words
        assertTrue(guardrail.validate(AiMessage.from("One\ttwo\tthree")).isSuccess());
    }

    @Test
    void testLongResponse() {
        WordCountGuardrail guardrail = WordCountGuardrail.atMost(10);

        String longResponse
                = "This is a very long response that contains many more words than the maximum allowed limit of ten words";
        assertFalse(guardrail.validate(AiMessage.from(longResponse)).isSuccess());
    }

    @Test
    void testExactWordCount() {
        WordCountGuardrail guardrail = WordCountGuardrail.between(5, 5);

        assertFalse(guardrail.validate(AiMessage.from("One two three four")).isSuccess());
        assertTrue(guardrail.validate(AiMessage.from("One two three four five")).isSuccess());
        assertFalse(guardrail.validate(AiMessage.from("One two three four five six")).isSuccess());
    }

    @Test
    void testDefaultMaxIsDefault() {
        WordCountGuardrail guardrail = WordCountGuardrail.atLeast(1);

        assertEquals(WordCountGuardrail.DEFAULT_MAX_WORDS, guardrail.getMaxWords());
    }

    @Test
    void testDefaultMinIsDefault() {
        WordCountGuardrail guardrail = WordCountGuardrail.atMost(100);

        assertEquals(WordCountGuardrail.DEFAULT_MIN_WORDS, guardrail.getMinWords());
    }
}
