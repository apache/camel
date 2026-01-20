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

class PiiDetectorGuardrailTest {

    @Test
    void testCleanMessage() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();
        UserMessage message = UserMessage.from("What is the weather like today?");

        InputGuardrailResult result = guardrail.validate(message);

        assertTrue(result.isSuccess());
    }

    @Test
    void testEmailDetection() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();
        UserMessage message = UserMessage.from("Contact me at john.doe@example.com");

        InputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
        assertTrue(result.toString().contains("email"));
    }

    @Test
    void testPhoneDetection() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();

        // Various phone formats
        assertFalse(guardrail.validate(UserMessage.from("Call me at 555-123-4567")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("My number is (555) 123-4567")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Reach me at 5551234567")).isSuccess());
    }

    @Test
    void testSsnDetection() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();
        UserMessage message = UserMessage.from("My SSN is 123-45-6789");

        InputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
        assertTrue(result.toString().contains("Social Security"));
    }

    @Test
    void testCreditCardDetection() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();

        // Visa
        assertFalse(guardrail.validate(UserMessage.from("Card: 4111111111111111")).isSuccess());
        // Mastercard
        assertFalse(guardrail.validate(UserMessage.from("Use 5555555555554444")).isSuccess());
        // Amex
        assertFalse(guardrail.validate(UserMessage.from("Charge to 378282246310005")).isSuccess());
    }

    @Test
    void testIpAddressDetection() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();
        UserMessage message = UserMessage.from("Server IP is 192.168.1.100");

        InputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
        assertTrue(result.toString().contains("IP address"));
    }

    @Test
    void testSelectivePiiDetection() {
        PiiDetectorGuardrail guardrail = PiiDetectorGuardrail.builder()
                .detectTypes(PiiDetectorGuardrail.PiiType.EMAIL)
                .build();

        // Should detect email
        assertFalse(guardrail.validate(UserMessage.from("Email: test@test.com")).isSuccess());

        // Should not detect phone (not configured)
        assertTrue(guardrail.validate(UserMessage.from("Call 555-123-4567")).isSuccess());
    }

    @Test
    void testNonBlockingMode() {
        PiiDetectorGuardrail guardrail = PiiDetectorGuardrail.builder()
                .blockOnDetection(false)
                .build();

        UserMessage message = UserMessage.from("Contact me at john@example.com");

        InputGuardrailResult result = guardrail.validate(message);

        // Should succeed even with PII detected (non-blocking mode)
        assertTrue(result.isSuccess());
    }

    @Test
    void testNullMessage() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();

        InputGuardrailResult result = guardrail.validate((UserMessage) null);

        assertTrue(result.isSuccess());
    }

    @Test
    void testMultiplePiiTypes() {
        PiiDetectorGuardrail guardrail = new PiiDetectorGuardrail();
        UserMessage message = UserMessage.from("Email: test@test.com, Phone: 555-123-4567");

        InputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
        // Should mention both detected types
        String resultString = result.toString();
        assertTrue(resultString.contains("email") || resultString.contains("phone"));
    }
}
