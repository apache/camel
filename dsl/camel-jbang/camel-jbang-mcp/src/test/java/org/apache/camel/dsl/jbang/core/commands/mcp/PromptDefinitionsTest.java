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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.quarkiverse.mcp.server.Prompt;
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
        assertThat(text).contains("camel_catalog_find");
        assertThat(text).contains("kind=\"eip\"");
        assertThat(text).contains("camel_catalog_doc");
        assertThat(text).contains("camel_validate_source");
        assertThat(text).doesNotContain("camel_catalog_components")
                .doesNotContain("camel_catalog_eips")
                .doesNotContain("camel_catalog_component_doc")
                .doesNotContain("camel_validate_yaml_dsl");
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
    void migrateProjectDoesNotInlinePomContent() {
        // The prompt must not embed the full pom content; size should not scale with pomContent length.
        String smallPom = "<project/>";
        String largePom = "<project>" + "<dependency/>".repeat(1000) + "</project>";

        int smallSize = extractText(prompts.migrateProject(smallPom, "4.18.0")).length();
        int largeSize = extractText(prompts.migrateProject(largePom, "4.18.0")).length();

        assertThat(largeSize).isEqualTo(smallSize);
        assertThat(extractText(prompts.migrateProject(largePom, "4.18.0"))).doesNotContain(largePom);
    }

    @Test
    void migrateProjectAcknowledgesSuppliedPom() {
        List<PromptMessage> result = prompts.migrateProject("<project/>", "4.18.0");

        String text = extractText(result);
        assertThat(text).contains("supplied as the `pomContent` argument");
    }

    @Test
    void migrateProjectFallsBackToContextWhenPomOmitted() {
        List<PromptMessage> result = prompts.migrateProject(null, "4.18.0");

        String text = extractText(result);
        assertThat(text).contains("conversation context");
    }

    @Test
    void migrateProjectBlankPomFallsBackToContext() {
        List<PromptMessage> result = prompts.migrateProject("  ", "4.18.0");

        String text = extractText(result);
        assertThat(text).contains("conversation context");
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
        // the arguments the tools take: camel_migration_compatibility has no components argument
        assertThat(text).contains(
                "`camel_migration_compatibility` with `currentVersion`, `targetVersion`, `runtime`, and `javaVersion`.");
        assertThat(text).contains("`quarkusPlatformVersion`").contains("`runtime=quarkus`");
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

    // ---- every prompt ----

    @Test
    void everyToolAPromptNamesExists() throws Exception {
        // a prompt names its tools as `snake_case`: each must be a tool the server lists
        Set<String> tools = McpToolClasses.toolNames();
        Pattern named = Pattern.compile("`([a-z][a-z0-9]*(?:_[a-z0-9]+)+)`");
        Set<String> mentioned = new TreeSet<>();
        for (Method m : PromptDefinitions.class.getDeclaredMethods()) {
            if (!m.isAnnotationPresent(Prompt.class)) {
                continue;
            }
            Object[] args = new Object[m.getParameterCount()];
            Arrays.fill(args, "myRoute");
            @SuppressWarnings("unchecked")
            List<PromptMessage> messages = (List<PromptMessage>) m.invoke(prompts, args);
            Matcher matcher = named.matcher(extractText(messages));
            while (matcher.find()) {
                mentioned.add(matcher.group(1));
            }
        }

        assertThat(mentioned).contains("camel_runtime_context", "camel_runtime_top", "camel_catalog_find");
        assertThat(mentioned).as("tools named by the prompts").isSubsetOf(tools);
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
