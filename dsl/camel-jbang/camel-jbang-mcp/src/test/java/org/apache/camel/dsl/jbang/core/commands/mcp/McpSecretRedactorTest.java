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

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
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
    void redactsKeyValueUsingCamelSensitiveKeywords() {
        McpSecretRedactor redactor = createRedactor(null);

        // The value is masked and the key is preserved (Camel's DefaultMaskingFormatter behaviour).
        assertThat(redactor.redact("password=secret123"))
                .isEqualTo("password=" + McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("apikey=sk-12345"))
                .isEqualTo("apikey=" + McpSecretRedactor.REDACTED);
        assertThat(redactor.redact("accessToken=abc123"))
                .isEqualTo("accessToken=" + McpSecretRedactor.REDACTED);
        // A compound property name is matched via the embedded keyword.
        assertThat(redactor.redact("datasource.password=hunter2"))
                .doesNotContain("hunter2")
                .contains(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsSecretCarriedAsUrlQueryParameter() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("uri: jdbc:postgresql://db:5432/app?ssl=true&password=hunter2"))
                .doesNotContain("hunter2")
                .contains(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsSensitiveJsonField() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("{\"password\":\"hunter2\",\"name\":\"orders\"}"))
                .doesNotContain("hunter2")
                .contains("orders");
    }

    @Test
    void doesNotRedactNonSensitiveKeyValue() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("route=timer-1")).isEqualTo("route=timer-1");
        assertThat(redactor.redact("Route started successfully with 3 endpoints"))
                .isEqualTo("Route started successfully with 3 endpoints");
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

    @Test
    void redactsStructuredJsonObjectByKeyName() {
        McpSecretRedactor redactor = createRedactor(null);

        // The case the String-only path missed: a JsonObject (as returned by the runtime tools) whose value is
        // JSON-quoted. Structured redaction blanks it by key name using SensitiveUtils.containsSensitive, which
        // strips a compound property to its last segment (datasource.password -> password).
        JsonObject properties = new JsonObject();
        properties.put("datasource.url", "jdbc:postgresql://db:5432/app");
        properties.put("datasource.password", "hunter2");
        properties.put("app.name", "orders");
        JsonObject nested = new JsonObject();
        nested.put("client-secret", "s3cr3t");
        properties.put("oauth", nested);

        boolean changed = redactor.redactStructured(properties);

        assertThat(changed).isTrue();
        assertThat(properties.getString("datasource.password")).isEqualTo(McpSecretRedactor.REDACTED);
        assertThat(properties.getString("app.name")).isEqualTo("orders");
        assertThat(properties.getString("datasource.url")).isEqualTo("jdbc:postgresql://db:5432/app");
        assertThat(((JsonObject) properties.get("oauth")).getString("client-secret"))
                .isEqualTo(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsSecretsEmbeddedInStructuredStringValues() {
        McpSecretRedactor redactor = createRedactor(null);

        // A non-secret key whose string value nonetheless embeds a key=value credential.
        JsonObject config = new JsonObject();
        config.put("name", "orders");
        JsonArray endpoints = new JsonArray();
        endpoints.add("timer://foo");
        endpoints.add("sql://bar?dataSource=#ds&password=leaked");
        config.put("endpoints", endpoints);

        boolean changed = redactor.redactStructured(config);

        assertThat(changed).isTrue();
        assertThat(config.getString("name")).isEqualTo("orders");
        assertThat(endpoints.getString(0)).isEqualTo("timer://foo");
        assertThat(endpoints.getString(1)).doesNotContain("leaked").contains(McpSecretRedactor.REDACTED);
    }

    @Test
    void structuredRedactionReportsNoChangeForCleanData() {
        McpSecretRedactor redactor = createRedactor(null);

        JsonObject clean = new JsonObject();
        clean.put("name", "orders");
        clean.put("routes", 3);

        assertThat(redactor.redactStructured(clean)).isFalse();
        assertThat(clean.getString("name")).isEqualTo("orders");
    }
}
