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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputLengthGuardrailTest {

    @Test
    void testValidInputLength() {
        InputLengthGuardrail guardrail = new InputLengthGuardrail();
        UserMessage message = UserMessage.from("Hello, how are you?");

        InputGuardrailResult result = guardrail.validate(message);

        assertTrue(result.isSuccess());
    }

    @Test
    void testInputTooShort() {
        InputLengthGuardrail guardrail = new InputLengthGuardrail(100, 10);
        UserMessage message = UserMessage.from("Hi");

        InputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
        assertTrue(result.toString().contains("too short"));
    }

    @Test
    void testInputTooLong() {
        InputLengthGuardrail guardrail = new InputLengthGuardrail(20, 1);
        UserMessage message = UserMessage.from("This is a very long message that exceeds the maximum allowed length");

        InputGuardrailResult result = guardrail.validate(message);

        assertFalse(result.isSuccess());
        assertTrue(result.toString().contains("too long"));
    }

    @Test
    void testNullMessage() {
        InputLengthGuardrail guardrail = new InputLengthGuardrail();

        InputGuardrailResult result = guardrail.validate((UserMessage) null);

        assertFalse(result.isSuccess());
    }

    @Test
    void testFactoryMethodMaxLength() {
        InputLengthGuardrail guardrail = InputLengthGuardrail.maxLength(50);

        assertEquals(50, guardrail.getMaxChars());
        assertEquals(InputLengthGuardrail.DEFAULT_MIN_CHARS, guardrail.getMinChars());
    }

    @Test
    void testFactoryMethodCreate() {
        InputLengthGuardrail guardrail = InputLengthGuardrail.create(100, 5);

        assertEquals(100, guardrail.getMaxChars());
        assertEquals(5, guardrail.getMinChars());
    }

    @Test
    void testInvalidMaxChars() {
        assertThrows(IllegalArgumentException.class, () -> new InputLengthGuardrail(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new InputLengthGuardrail(-1, 1));
    }

    @Test
    void testInvalidMinChars() {
        assertThrows(IllegalArgumentException.class, () -> new InputLengthGuardrail(100, -1));
    }

    @Test
    void testMinExceedsMax() {
        assertThrows(IllegalArgumentException.class, () -> new InputLengthGuardrail(10, 20));
    }

    @Test
    void testBoundaryConditions() {
        InputLengthGuardrail guardrail = new InputLengthGuardrail(10, 5);

        // Exactly at minimum
        UserMessage minMessage = UserMessage.from("12345");
        assertTrue(guardrail.validate(minMessage).isSuccess());

        // Exactly at maximum
        UserMessage maxMessage = UserMessage.from("1234567890");
        assertTrue(guardrail.validate(maxMessage).isSuccess());

        // One below minimum
        UserMessage belowMin = UserMessage.from("1234");
        assertFalse(guardrail.validate(belowMin).isSuccess());

        // One above maximum
        UserMessage aboveMax = UserMessage.from("12345678901");
        assertFalse(guardrail.validate(aboveMax).isSuccess());
    }
}
