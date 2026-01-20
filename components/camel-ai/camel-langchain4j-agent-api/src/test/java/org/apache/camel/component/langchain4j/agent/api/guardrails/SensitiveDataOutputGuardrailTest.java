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

class SensitiveDataOutputGuardrailTest {

    @Test
    void testCleanResponse() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("The weather in Paris is sunny today.");

        OutputGuardrailResult result = guardrail.validate(message);

        assertTrue(result.isSuccess());
    }

    @Test
    void testApiKeyDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("Your API key is: api_key=sk_live_abc123def456ghi789jkl012mno345");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testAwsKeyDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("AWS Access Key: AKIAIOSFODNN7EXAMPLE");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testSecretDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();

        assertFalse(guardrail.validate(
                AiMessage.from("password=mysecretpass123")).isSuccess());

        assertFalse(guardrail.validate(
                AiMessage.from("token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9")).isSuccess());
    }

    @Test
    void testPrivateKeyDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("-----BEGIN RSA PRIVATE KEY-----\nMIIE...");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testCreditCardDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("The card number is 4111111111111111");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testSsnDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("SSN: 123-45-6789");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testJwtDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from(
                "Token: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testConnectionStringDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("Connect using: mongodb://user:pass@localhost:27017/db");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testGitHubTokenDetection() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();
        AiMessage message = AiMessage.from("GitHub token: ghp_1234567890abcdefghijklmnopqrstuvwxyz");

        OutputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
    }

    @Test
    void testRedactionMode() {
        SensitiveDataOutputGuardrail guardrail = SensitiveDataOutputGuardrail.redacting();
        AiMessage message = AiMessage.from("Your API key is: api_key=sk_live_abc123def456ghi789jkl012mno345");

        OutputGuardrailResult result = guardrail.validate(message);

        // In redaction mode, should succeed
        assertTrue(result.isSuccess());
    }

    @Test
    void testSelectiveDetection() {
        SensitiveDataOutputGuardrail guardrail = SensitiveDataOutputGuardrail.builder()
                .detectTypes(SensitiveDataOutputGuardrail.SensitiveDataType.CREDIT_CARD)
                .build();

        // Should detect credit card
        assertFalse(guardrail.validate(AiMessage.from("Card: 4111111111111111")).isSuccess());

        // Should not detect other types
        assertTrue(guardrail.validate(AiMessage.from("SSN: 123-45-6789")).isSuccess());
    }

    @Test
    void testWarnMode() {
        SensitiveDataOutputGuardrail guardrail = SensitiveDataOutputGuardrail.builder()
                .action(SensitiveDataOutputGuardrail.Action.WARN)
                .build();

        AiMessage message = AiMessage.from("Password: secret123");

        OutputGuardrailResult result = guardrail.validate(message);

        // Warn mode should succeed (just log warning)
        assertTrue(result.isSuccess());
    }

    @Test
    void testCustomRedactionText() {
        SensitiveDataOutputGuardrail guardrail = SensitiveDataOutputGuardrail.builder()
                .action(SensitiveDataOutputGuardrail.Action.REDACT)
                .redactionText("***HIDDEN***")
                .build();

        AiMessage message = AiMessage.from("Password: secret123");

        OutputGuardrailResult result = guardrail.validate(message);

        // In redaction mode with custom text, should succeed
        assertTrue(result.isSuccess());
    }

    @Test
    void testNullMessage() {
        SensitiveDataOutputGuardrail guardrail = new SensitiveDataOutputGuardrail();

        OutputGuardrailResult result = guardrail.validate((AiMessage) null);

        assertTrue(result.isSuccess());
    }

    @Test
    void testBuilderDefaults() {
        SensitiveDataOutputGuardrail guardrail = SensitiveDataOutputGuardrail.builder().build();

        assertEquals(SensitiveDataOutputGuardrail.Action.BLOCK, guardrail.getAction());
        assertEquals("[REDACTED]", guardrail.getRedactionText());
    }
}
