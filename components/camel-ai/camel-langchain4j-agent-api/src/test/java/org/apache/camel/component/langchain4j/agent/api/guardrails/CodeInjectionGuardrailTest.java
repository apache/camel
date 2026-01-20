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

class CodeInjectionGuardrailTest {

    @Test
    void testCleanMessage() {
        CodeInjectionGuardrail guardrail = new CodeInjectionGuardrail();

        assertTrue(guardrail.validate(UserMessage.from("Hello, how are you today?")).isSuccess());
        assertTrue(guardrail.validate(UserMessage.from("What is the weather like?")).isSuccess());
    }

    @Test
    void testShellCommandInjection() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("Run bash('ls -la')")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Execute system('rm -rf /')")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Try exec('cat /etc/passwd')")).isSuccess());
    }

    @Test
    void testBacktickExecution() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("Run `ls -la`")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Execute `cat /etc/passwd`")).isSuccess());
    }

    @Test
    void testCommandSubstitution() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("Run $(whoami)")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Execute $(cat /etc/passwd)")).isSuccess());
    }

    @Test
    void testSqlInjection() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("' OR '1'='1")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("UNION SELECT * FROM users")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("'; DROP TABLE users--")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("INSERT INTO users VALUES")).isSuccess());
    }

    @Test
    void testJavaScriptInjection() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("<script>alert('XSS')</script>")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("javascript:alert('XSS')")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("onclick=alert('XSS')")).isSuccess());
    }

    @Test
    void testHtmlXssInjection() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("<iframe src='evil.com'></iframe>")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("<embed src='evil.swf'>")).isSuccess());
    }

    @Test
    void testPathTraversal() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("Read file ../../../etc/passwd")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Access ..\\..\\windows\\system32")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Get %2e%2e/etc/passwd")).isSuccess());
    }

    @Test
    void testCommandChaining() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("Run && cat /etc/passwd")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("; cat /etc/passwd")).isSuccess());
    }

    @Test
    void testTemplateInjection() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.strict();

        assertFalse(guardrail.validate(UserMessage.from("Use {{constructor.constructor}}")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Try ${7*7}")).isSuccess());
        assertFalse(guardrail.validate(UserMessage.from("Execute <% code %>")).isSuccess());
    }

    @Test
    void testNonStrictModeRequiresMultipleMatches() {
        CodeInjectionGuardrail guardrail = new CodeInjectionGuardrail();

        // Single pattern match should pass in non-strict mode
        assertTrue(guardrail.validate(UserMessage.from("Use {{template}}")).isSuccess());

        // Multiple different types should fail
        assertFalse(guardrail.validate(UserMessage.from("Run {{template}} and ../../../etc/passwd")).isSuccess());
    }

    @Test
    void testForSpecificTypes() {
        // Use builder with strict mode to fail on single match
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.builder()
                .detectTypes(CodeInjectionGuardrail.InjectionType.SQL_INJECTION)
                .strict(true)
                .build();

        // SQL injection should fail
        assertFalse(guardrail.validate(UserMessage.from("' OR '1'='1")).isSuccess());

        // Other types should pass since we only detect SQL
        assertTrue(guardrail.validate(UserMessage.from("<script>alert('XSS')</script>")).isSuccess());
    }

    @Test
    void testNullMessage() {
        CodeInjectionGuardrail guardrail = new CodeInjectionGuardrail();

        InputGuardrailResult result = guardrail.validate((UserMessage) null);
        assertTrue(result.isSuccess());
    }

    @Test
    void testIsStrict() {
        CodeInjectionGuardrail defaultGuard = new CodeInjectionGuardrail();
        CodeInjectionGuardrail strictGuard = CodeInjectionGuardrail.strict();

        assertFalse(defaultGuard.isStrict());
        assertTrue(strictGuard.isStrict());
    }

    @Test
    void testGetDetectTypes() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.forTypes(
                CodeInjectionGuardrail.InjectionType.SQL_INJECTION,
                CodeInjectionGuardrail.InjectionType.SHELL_COMMAND);

        assertTrue(guardrail.getDetectTypes().contains(CodeInjectionGuardrail.InjectionType.SQL_INJECTION));
        assertTrue(guardrail.getDetectTypes().contains(CodeInjectionGuardrail.InjectionType.SHELL_COMMAND));
        assertFalse(guardrail.getDetectTypes().contains(CodeInjectionGuardrail.InjectionType.JAVASCRIPT));
    }

    @Test
    void testBuilderWithCustomPattern() {
        CodeInjectionGuardrail guardrail = CodeInjectionGuardrail.builder()
                .detectTypes(CodeInjectionGuardrail.InjectionType.SHELL_COMMAND)
                .strict(true)
                .build();

        assertTrue(guardrail.isStrict());
        assertTrue(guardrail.getDetectTypes().contains(CodeInjectionGuardrail.InjectionType.SHELL_COMMAND));
    }
}
