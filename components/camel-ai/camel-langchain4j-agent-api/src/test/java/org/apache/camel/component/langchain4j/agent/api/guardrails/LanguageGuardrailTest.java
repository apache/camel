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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LanguageGuardrailTest {

    @Test
    void testAllowAllLanguagesByDefault() {
        LanguageGuardrail guardrail = new LanguageGuardrail();

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("Привет мир")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("你好世界")).isSuccess());
    }

    @Test
    void testAllowOnlyEnglish() {
        LanguageGuardrail guardrail = LanguageGuardrail.allowOnly(LanguageGuardrail.Language.ENGLISH);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Привет мир")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("你好世界")).isSuccess());
    }

    @Test
    void testAllowLatinScript() {
        LanguageGuardrail guardrail = LanguageGuardrail.allowOnly(LanguageGuardrail.Language.LATIN_SCRIPT);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("Hola mundo")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("Bonjour le monde")).isSuccess());
    }

    @Test
    void testBlockCyrillic() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.CYRILLIC);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Привет мир")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Hello Привет")).isSuccess());
    }

    @Test
    void testBlockChinese() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.CHINESE);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("你好世界")).isSuccess());
    }

    @Test
    void testBlockArabic() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.ARABIC);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("مرحبا بالعالم")).isSuccess());
    }

    @Test
    void testMultipleAllowedLanguages() {
        LanguageGuardrail guardrail = LanguageGuardrail.builder()
                .allowedLanguages(LanguageGuardrail.Language.ENGLISH, LanguageGuardrail.Language.LATIN_SCRIPT)
                .build();

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("Bonjour")).isSuccess());
    }

    @Test
    void testMixedContentAllowed() {
        LanguageGuardrail guardrail = LanguageGuardrail.builder()
                .allowedLanguages(LanguageGuardrail.Language.ENGLISH, LanguageGuardrail.Language.LATIN_SCRIPT)
                .allowMixed(true)
                .build();

        assertTrue(guardrail.validate(UserMessage.from("Hello Bonjour")).isSuccess());
    }

    @Test
    void testNullMessage() {
        LanguageGuardrail guardrail = new LanguageGuardrail();

        InputGuardrailResult result = guardrail.validate((UserMessage) null);
        assertTrue(result.isSuccess());
    }

    @Test
    void testJapaneseDetection() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.JAPANESE);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("こんにちは")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("カタカナ")).isSuccess());
    }

    @Test
    void testKoreanDetection() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.KOREAN);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("안녕하세요")).isSuccess());
    }

    @Test
    void testHebrewDetection() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.HEBREW);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("שלום עולם")).isSuccess());
    }

    @Test
    void testGreekDetection() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.GREEK);

        assertTrue(guardrail.validate(UserMessage.from("Hello world")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Γειά σου κόσμε")).isSuccess());
    }

    @Test
    void testGetAllowedLanguages() {
        LanguageGuardrail guardrail = LanguageGuardrail.allowOnly(
                LanguageGuardrail.Language.ENGLISH, LanguageGuardrail.Language.LATIN_SCRIPT);

        assertTrue(guardrail.getAllowedLanguages().contains(LanguageGuardrail.Language.ENGLISH));
        assertTrue(guardrail.getAllowedLanguages().contains(LanguageGuardrail.Language.LATIN_SCRIPT));
    }

    @Test
    void testGetBlockedLanguages() {
        LanguageGuardrail guardrail = LanguageGuardrail.block(LanguageGuardrail.Language.CYRILLIC);

        assertTrue(guardrail.getBlockedLanguages().contains(LanguageGuardrail.Language.CYRILLIC));
    }
}
