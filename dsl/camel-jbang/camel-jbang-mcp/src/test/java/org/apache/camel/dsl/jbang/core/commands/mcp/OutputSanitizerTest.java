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

import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutputSanitizerTest {

    @Test
    void shouldRedactSensitiveKeyInJsonObject() {
        JsonObject json = new JsonObject();
        json.put("name", "my-component");
        json.put("password", "superSecret123");
        json.put("token", "tok_abc123xyz");

        OutputSanitizer.redact(json);

        assertThat(json.getString("name")).isEqualTo("my-component");
        assertThat(json.getString("password")).isEqualTo(OutputSanitizer.REDACTED);
        assertThat(json.getString("token")).isEqualTo(OutputSanitizer.REDACTED);
    }

    @Test
    void shouldRedactNestedSensitiveKeys() {
        JsonObject nested = new JsonObject();
        nested.put("secret", "hidden-value");
        nested.put("host", "localhost");

        JsonObject json = new JsonObject();
        json.put("config", nested);
        json.put("status", "ok");

        OutputSanitizer.redact(json);

        assertThat(nested.getString("secret")).isEqualTo(OutputSanitizer.REDACTED);
        assertThat(nested.getString("host")).isEqualTo("localhost");
        assertThat(json.getString("status")).isEqualTo("ok");
    }

    @Test
    void shouldRedactBearerTokenInString() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0";

        String result = OutputSanitizer.redactString(input);

        assertThat(result).contains("Bearer " + OutputSanitizer.REDACTED);
        assertThat(result).doesNotContain("eyJhbGci");
    }

    @Test
    void shouldRedactAwsAccessKeys() {
        String input = "Using credentials AKIAIOSFODNN7EXAMPLE for access";

        String result = OutputSanitizer.redactString(input);

        assertThat(result).contains(OutputSanitizer.REDACTED);
        assertThat(result).doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void shouldRedactGenericTokenPatterns() {
        String input = "api_key=sk_test_abc123def456 and token=ghp_abcdefghijklmnop";

        String result = OutputSanitizer.redactString(input);

        assertThat(result).doesNotContain("sk_test_abc123def456");
        assertThat(result).doesNotContain("ghp_abcdefghijklmnop");
    }

    @Test
    void shouldPreserveNonSensitiveContent() {
        String input = "Route my-route is running on endpoint direct:start with 42 exchanges processed";

        String result = OutputSanitizer.redactString(input);

        assertThat(result).isEqualTo(input);
    }

    @Test
    void shouldHandleNullAndEmpty() {
        assertThat(OutputSanitizer.redactString(null)).isNull();
        assertThat(OutputSanitizer.redactString("")).isEmpty();
        assertThat(OutputSanitizer.redact((JsonObject) null)).isNull();
    }

    @Test
    void shouldNotRedactNonStringValues() {
        JsonObject json = new JsonObject();
        json.put("password", 12345);
        json.put("count", 42);

        OutputSanitizer.redact(json);

        assertThat(json.get("password")).isEqualTo(12345);
        assertThat(json.get("count")).isEqualTo(42);
    }
}
