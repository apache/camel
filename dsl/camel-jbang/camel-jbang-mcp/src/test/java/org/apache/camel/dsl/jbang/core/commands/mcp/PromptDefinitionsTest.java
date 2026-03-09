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

import io.quarkiverse.mcp.server.PromptMessage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PromptDefinitionsTest {

    private final PromptDefinitions prompts = new PromptDefinitions();

    // ---- camel_build_integration ----

    @Test
    void buildIntegrationReturnsNonEmptyMessages() {
        List<PromptMessage> result = prompts.buildIntegration("Read from Kafka and write to S3", "spring-boot");

        assertThat(result).isNotEmpty();
    }

    @Test
    void buildIntegrationContainsRequirements() {
        List<PromptMessage> result = prompts.buildIntegration("Read from Kafka and write to S3", null);

        String text = extractText(result);
        assertThat(text).contains("Read from Kafka and write to S3");
    }

    @Test
    void buildIntegrationReferencesTools() {
        List<PromptMessage> result = prompts.buildIntegration("poll an FTP server", null);

        String text = extractText(result);
        assertThat(text).contains("camel_catalog_components");
        assertThat(text).contains("camel_catalog_eips");
        assertThat(text).contains("camel_catalog_component_doc");
        assertThat(text).contains("camel_validate_yaml_dsl");
        assertThat(text).contains("camel_route_harden_context");
    }

    @Test
    void buildIntegrationDefaultsRuntimeToMain() {
        List<PromptMessage> result = prompts.buildIntegration("timer to log", null);

        String text = extractText(result);
        assertThat(text).contains("\"main\" runtime");
    }

    @Test
    void buildIntegrationUsesSpecifiedRuntime() {
        List<PromptMessage> result = prompts.buildIntegration("timer to log", "quarkus");

        String text = extractText(result);
        assertThat(text).contains("\"quarkus\" runtime");
    }

    @Test
    void buildIntegrationBlankRuntimeDefaultsToMain() {
        List<PromptMessage> result = prompts.buildIntegration("timer to log", "  ");

        String text = extractText(result);
        assertThat(text).contains("\"main\" runtime");
    }

    // ---- camel_migrate_project ----

    @Test
    void migrateProjectReturnsNonEmptyMessages() {
        List<PromptMessage> result = prompts.migrateProject("<project/>", "4.18.0");

        assertThat(result).isNotEmpty();
    }

    @Test
    void migrateProjectContainsPomContent() {
        String pom = "<project><version>3.20.0</version></project>";
        List<PromptMessage> result = prompts.migrateProject(pom, null);

        String text = extractText(result);
        assertThat(text).contains(pom);
    }

    @Test
    void migrateProjectReferencesTools() {
        List<PromptMessage> result = prompts.migrateProject("<project/>", "4.18.0");

        String text = extractText(result);
        assertThat(text).contains("camel_migration_analyze");
        assertThat(text).contains("camel_migration_compatibility");
        assertThat(text).contains("camel_migration_wildfly_karaf");
        assertThat(text).contains("camel_migration_recipes");
        assertThat(text).contains("camel_migration_guide_search");
    }

    @Test
    void migrateProjectIncludesTargetVersion() {
        List<PromptMessage> result = prompts.migrateProject("<project/>", "4.18.0");

        String text = extractText(result);
        assertThat(text).contains("Target version: 4.18.0");
    }

    @Test
    void migrateProjectNullVersionSuggestsLatest() {
        List<PromptMessage> result = prompts.migrateProject("<project/>", null);

        String text = extractText(result);
        assertThat(text).contains("camel_version_list");
    }

    @Test
    void migrateProjectBlankVersionSuggestsLatest() {
        List<PromptMessage> result = prompts.migrateProject("<project/>", "  ");

        String text = extractText(result);
        assertThat(text).contains("camel_version_list");
    }

    // ---- camel_security_review ----

    @Test
    void securityReviewReturnsNonEmptyMessages() {
        List<PromptMessage> result = prompts.securityReview("from: timer:tick", "yaml");

        assertThat(result).isNotEmpty();
    }

    @Test
    void securityReviewContainsRoute() {
        String route = "from:\n  uri: kafka:topic\n  steps:\n    - to: log:out";
        List<PromptMessage> result = prompts.securityReview(route, null);

        String text = extractText(result);
        assertThat(text).contains(route);
    }

    @Test
    void securityReviewReferencesTools() {
        List<PromptMessage> result = prompts.securityReview("from: timer:tick", null);

        String text = extractText(result);
        assertThat(text).contains("camel_route_harden_context");
        assertThat(text).contains("camel_route_context");
    }

    @Test
    void securityReviewDefaultsFormatToYaml() {
        List<PromptMessage> result = prompts.securityReview("from: timer:tick", null);

        String text = extractText(result);
        assertThat(text).contains("format: yaml");
    }

    @Test
    void securityReviewUsesSpecifiedFormat() {
        List<PromptMessage> result = prompts.securityReview("<route/>", "xml");

        String text = extractText(result);
        assertThat(text).contains("format: xml");
    }

    @Test
    void securityReviewBlankFormatDefaultsToYaml() {
        List<PromptMessage> result = prompts.securityReview("from: timer:tick", "  ");

        String text = extractText(result);
        assertThat(text).contains("format: yaml");
    }

    @Test
    void securityReviewContainsAuditSections() {
        List<PromptMessage> result = prompts.securityReview("from: timer:tick", null);

        String text = extractText(result);
        assertThat(text).contains("Critical Issues");
        assertThat(text).contains("Warnings");
        assertThat(text).contains("Positive Findings");
        assertThat(text).contains("Recommendations");
    }

    // ---- helper ----

    private String extractText(List<PromptMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (PromptMessage msg : messages) {
            sb.append(msg.content().asText().text());
        }
        return sb.toString();
    }
}
