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

import java.util.List;

import io.quarkiverse.mcp.server.TextResourceContents;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestInfraResourcesTest {

    private final TestInfraResources resources;

    TestInfraResourcesTest() {
        resources = new TestInfraResources();
        resources.testInfraData = new TestInfraData();
    }

    // ---- Infra services catalog ----

    @Test
    void infraServicesReturnsValidJson() throws Exception {
        TextResourceContents contents = resources.infraServices();

        assertThat(contents.uri()).isEqualTo("camel://testing/infra-services");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getInteger("totalServices")).isGreaterThan(0);
        assertThat(result.getInteger("totalSchemes")).isGreaterThan(0);

        JsonArray services = result.getCollection("services");
        assertThat(services).isNotEmpty();
    }

    @Test
    void infraServicesGroupsSchemesByService() throws Exception {
        TextResourceContents contents = resources.infraServices();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        JsonArray services = result.getCollection("services");

        // Find the Artemis service (handles jms, activemq, sjms, sjms2, amqp)
        JsonObject artemis = services.stream()
                .map(s -> (JsonObject) s)
                .filter(s -> "ArtemisService".equals(s.getString("serviceClass")))
                .findFirst()
                .orElse(null);

        assertThat(artemis).isNotNull();
        JsonArray schemes = artemis.getCollection("schemes");
        assertThat(schemes).contains("jms", "activemq");
        assertThat(schemes).hasSizeGreaterThanOrEqualTo(3);
    }

    @Test
    void infraServicesEntriesHaveRequiredFields() throws Exception {
        TextResourceContents contents = resources.infraServices();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        JsonArray services = result.getCollection("services");

        for (Object obj : services) {
            JsonObject entry = (JsonObject) obj;
            assertThat(entry.getString("serviceClass")).isNotBlank();
            assertThat(entry.getString("factoryClass")).isNotBlank();
            assertThat(entry.getString("artifactId")).isNotBlank();
            assertThat(entry.getString("package")).isNotBlank();
            JsonArray entrySchemes = entry.getCollection("schemes");
            assertThat(entrySchemes).isNotEmpty();
        }
    }

    @Test
    void infraServicesTotalSchemeMatchesMap() throws Exception {
        TextResourceContents contents = resources.infraServices();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        TestInfraData data = new TestInfraData();
        assertThat(result.getInteger("totalSchemes")).isEqualTo(data.getTestInfraMap().size());
    }

    // ---- Infra service detail for known scheme ----

    @Test
    void detailReturnsKafkaService() throws Exception {
        TextResourceContents contents = resources.infraServiceDetail("kafka");

        assertThat(contents.uri()).isEqualTo("camel://testing/infra-service/kafka");
        assertThat(contents.mimeType()).isEqualTo("application/json");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getString("scheme")).isEqualTo("kafka");
        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("serviceClass")).isEqualTo("KafkaService");
        assertThat(result.getString("factoryClass")).isEqualTo("KafkaServiceFactory");
        assertThat(result.getString("artifactId")).isEqualTo("camel-test-infra-kafka");
        assertThat(result.getString("package")).isNotBlank();
        assertThat(result.getString("usageSnippet")).contains("@RegisterExtension");
        assertThat(result.getString("usageSnippet")).contains("KafkaServiceFactory.createService()");
        assertThat(result.getString("mavenDependency")).contains("camel-test-infra-kafka");
    }

    @Test
    void detailReturnsArtemisForJmsScheme() throws Exception {
        TextResourceContents contents = resources.infraServiceDetail("jms");
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());

        assertThat(result.getBoolean("found")).isTrue();
        assertThat(result.getString("serviceClass")).isEqualTo("ArtemisService");
        assertThat(result.getString("artifactId")).isEqualTo("camel-test-infra-artemis");
    }

    // ---- Infra service detail not found ----

    @Test
    void detailReturnsNotFoundForUnknownScheme() throws Exception {
        TextResourceContents contents = resources.infraServiceDetail("nonexistent");

        assertThat(contents.uri()).isEqualTo("camel://testing/infra-service/nonexistent");

        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        assertThat(result.getString("scheme")).isEqualTo("nonexistent");
        assertThat(result.getBoolean("found")).isFalse();
        assertThat(result.getString("message")).contains("No test infrastructure service");
    }

    // ---- Deduplication ----

    @Test
    void infraServicesDeduplicatesServices() throws Exception {
        TextResourceContents contents = resources.infraServices();
        JsonObject result = (JsonObject) Jsoner.deserialize(contents.text());
        JsonArray services = result.getCollection("services");

        // Ensure no duplicate service classes
        List<String> serviceClasses = services.stream()
                .map(s -> ((JsonObject) s).getString("serviceClass"))
                .toList();
        assertThat(serviceClasses).doesNotHaveDuplicates();
    }
}
