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

    @Test
    void redactsClientSecretAndUrlQueryParamAndPemBlock() {
        McpSecretRedactor redactor = createRedactor(null);

        assertThat(redactor.redact("client_secret=abcdef123456"))
                .isEqualTo(McpSecretRedactor.REDACTED);
        // JDBC/URL carrying the password as a query parameter has no userinfo '@' so it is not a connection
        // string; it must still be caught.
        assertThat(redactor.redact("jdbc:postgresql://db:5432/app?ssl=true&password=hunter2"))
                .contains(McpSecretRedactor.REDACTED)
                .doesNotContain("hunter2");
        assertThat(redactor.redact("-----BEGIN RSA PRIVATE KEY-----\nMIIEabc\n-----END RSA PRIVATE KEY-----"))
                .isEqualTo(McpSecretRedactor.REDACTED);
    }

    @Test
    void redactsStructuredJsonObjectByKeyName() {
        McpSecretRedactor redactor = createRedactor(null);

        // This is the case the String-only path missed: a JsonObject (as returned by the runtime tools) whose
        // value is JSON-quoted. The value regex cannot match a quoted value, so structured redaction by key name
        // is what protects it.
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

        // A non-secret key whose string value nonetheless embeds a credential-bearing connection string.
        JsonObject config = new JsonObject();
        config.put("broker", "amqp://admin:secret@broker:5672/vhost");
        JsonArray endpoints = new JsonArray();
        endpoints.add("timer://foo");
        endpoints.add("sql://bar?dataSource=#ds&password=leaked");
        config.put("endpoints", endpoints);

        boolean changed = redactor.redactStructured(config);

        assertThat(changed).isTrue();
        assertThat(config.getString("broker")).doesNotContain("secret").contains(McpSecretRedactor.REDACTED);
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

    @Test
    void secretKeyNameMatchingIsSeparatorAndCaseInsensitive() {
        assertThat(McpSecretRedactor.isSecretKeyName("password")).isTrue();
        assertThat(McpSecretRedactor.isSecretKeyName("client-secret")).isTrue();
        assertThat(McpSecretRedactor.isSecretKeyName("client_secret")).isTrue();
        assertThat(McpSecretRedactor.isSecretKeyName("clientSecret")).isTrue();
        assertThat(McpSecretRedactor.isSecretKeyName("api-key")).isTrue();
        assertThat(McpSecretRedactor.isSecretKeyName("keystorePassword")).isTrue();
        assertThat(McpSecretRedactor.isSecretKeyName("route.id")).isFalse();
        assertThat(McpSecretRedactor.isSecretKeyName("name")).isFalse();
        assertThat(McpSecretRedactor.isSecretKeyName(null)).isFalse();
    }
}
