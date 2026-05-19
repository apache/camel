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
package org.apache.camel.component.a2a;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class A2AGeneratedMetadataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void generatedEndpointMetadataDocumentsSecurityCategoriesAndDescriptions() throws Exception {
        JsonNode properties = MAPPER.readTree(Files.readString(metadataPath())).path("properties");

        JsonNode validateAuth = properties.path("validateAuth");
        assertThat(validateAuth.path("security").asText()).isEqualTo("insecure:dev");
        assertThat(validateAuth.path("insecureValue").asText()).isEqualTo("false");
        assertThat(validateAuth.path("defaultValue").asBoolean()).isTrue();
        assertThat(validateAuth.path("description").asText())
                .contains("validate authentication")
                .contains("Disable explicitly");

        JsonNode allowLocalWebhookUrls = properties.path("allowLocalWebhookUrls");
        assertThat(allowLocalWebhookUrls.path("security").asText()).isEqualTo("insecure:dev");
        assertThat(allowLocalWebhookUrls.path("insecureValue").asText()).isEqualTo("true");
        assertThat(allowLocalWebhookUrls.path("defaultValue").asBoolean()).isFalse();
        assertThat(allowLocalWebhookUrls.path("description").asText())
                .contains("localhost")
                .contains("SSRF protection");

        JsonNode pushRetryBackoffMs = properties.path("pushRetryBackoffMs");
        assertThat(pushRetryBackoffMs.path("defaultValue").asLong()).isEqualTo(1000L);
        assertThat(pushRetryBackoffMs.path("description").asText()).contains("exponential backoff");

        JsonNode oauthProfile = properties.path("oauthProfile");
        assertThat(oauthProfile.path("description").asText())
                .contains("OAuth 2.0 Client Credentials")
                .contains("camel-oauth");
    }

    private static Path metadataPath() {
        Path path = Path.of(
                "src/generated/resources/META-INF/org/apache/camel/component/a2a/a2a.json");
        if (Files.exists(path)) {
            return path;
        }
        return Path.of("components/camel-ai/camel-a2a").resolve(path);
    }
}
