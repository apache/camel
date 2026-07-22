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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpSecretRedactorTest {

    private McpSecretRedactor createRedactor(String extraPatterns) {
        McpSecurityConfig config = new McpSecurityConfig();
        config.enabled = true;
        config.redactionEnabled = true;
        config.redactionPatterns = Optional.ofNullable(extraPatterns);
        config.maxArgumentLength = McpSecurityConfig.DEFAULT_MAX_ARGUMENT_LENGTH;
        config.accessLevel = "admin";
        config.auditEnabled = false;
        config.auditIncludeArguments = true;

        McpSecretRedactor redactor = new McpSecretRedactor();
        redactor.config = config;
        return redactor;
    }

    @Test
    void redactsPasswordKeyValue() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("password=secret123"))
                .isEqualTo(McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("Password: myP@ssw0rd"))
                .isEqualTo(McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("pwd=abc"))
                .isEqualTo(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsApiKeys() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("api_key=sk-12345"))
                .isEqualTo(McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("apiKey: abc123"))
                .isEqualTo(McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("secret-key=xyz789"))
                .isEqualTo(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsTokensAndBearer() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("token=eyJhbGciOiJIUzI1NiJ9"))
                .isEqualTo(McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("bearer: some-bearer-token"))
                .isEqualTo(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsAwsAccessKeyIds() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("key is AKIAIOSFODNN7EXAMPLE"))
                .isEqualTo("key is " + McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsConnectionStringsWithCredentials() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("uri: mongodb://user:pass@host:27017/db"))
                .isEqualTo("uri: " + McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("url=amqp://admin:secret@broker:5672/vhost"))
                .isEqualTo("url=" + McpSecretRedactor.REDACTED);
    }

    @Test
    void doesNotRedactNormalText() {
        McpSecretRedactor redactor = createRedactor(null);

        String normalText = "Route started successfully with 3 endpoints";
        assertThat(redactor.redact(normalText)).isEqualTo(normalText);
    }

    @Test
    void handlesMultipleSecretsInSameString() {
        McpSecretRedactor redactor = createRedactor(null);

        String input = "password=secret, apiKey=abc123";
        String result = redactor.redact(input);
        assertThat(result).doesNotContain("secret").doesNotContain("abc123");
    }

    @Test
    void handlesNullAndEmpty() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact(null)).isNull();
        assertThat(redactor.redact("")).isEmpty();
    }

    @Test
    void containsSecretDetectsSecrets() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.containsSecret("password=secret")).isTrue();
        assertThat(redactor.containsSecret("normal text")).isFalse();
        assertThat(redactor.containsSecret(null)).isFalse();
        assertThat(redactor.containsSecret("")).isFalse();
    }

    @Test
    void customPatternsAreApplied() {
        McpSecretRedactor redactor = createRedactor("CUSTOM_\\d+");

        assertThat(redactor.redact("value: CUSTOM_12345 here"))
                .isEqualTo("value: " + McpSecretRedactor.REDACTED + " here");
    }
}
