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

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.Tool;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server instructions sent in the MCP initialize response: their size, their first line and the tool names they
 * point the agent to. They serve everyday work; the rules of a migration to Camel are a separate text, sent when a
 * migration starts.
 */
class McpServerInstructionsTest {

    private static final Pattern TOOL_NAME = Pattern.compile("\\bcamel_[a-z0-9_]+\\b");

    @Test
    void theTextFitsTheHostLimitAndStartsWithTheVersionedSummary() {
        String version = new DefaultCamelCatalog().getCatalogVersion();
        String text = new McpServerInstructions().text();
        String firstLine = text.lines().findFirst().orElseThrow();

        assertThat(text).hasSizeLessThan(McpServerInstructions.MAX_LENGTH);
        // Pi shows only the first line, Codex weighs the first 512 characters most
        assertThat(firstLine).hasSizeLessThan(512).startsWith("Apache Camel " + version + " ")
                .contains("build, run and fix", "camel_edit_file", "camel_run", "camel_get_log");
        assertThat(McpServerInstructions.build("9.9.9")).startsWith("Apache Camel 9.9.9 ");
        assertThat(McpServerInstructions.build(null)).startsWith("Apache Camel (unknown version) ");
    }

    @Test
    void theRulesComeInTheOrderOfTheProposal() {
        String text = new McpServerInstructions().text();

        // the mistakes that cost a model most when it writes routes, the daily tool loop, then the way to a migration
        assertThat(text).containsSubsequence(
                "steps of an EIP", "go under its steps",
                "A choice has one when list", "otherwise: {steps: [...]}",
                "top-level", "handled: {constant: {expression: \"true\"}}",
                "variable (setVariable, ${variable.name}), not an exchange property",
                "Simple: functions inside ${...}, operators between them",
                "URI scheme", "platform-http",
                "After a write, read its answer",
                "Daily work:", "camel_get_files", "camel_edit_file", "camel_write_file", "camel_run",
                "camel_control", "camel_get_log", "camel_get_errors", "camel_eval_expression",
                "Which tool:",
                "Migrating to Camel, or between Camel versions?");
        assertThat(text.strip()).endsWith("or call camel_migration_instructions.");
    }

    @Test
    void theRulesOfAMigrationAreLeftToTheMigrationText() {
        String text = new McpServerInstructions().text();

        // rules that only matter when migrating to Camel are not in the text of everyday work
        assertThat(text).doesNotContain("useHeaderSubject", "removeHeaders", "xslt", "--dep", "camel.server.port",
                "smtp");
        assertThat(McpServerInstructions.migration("4.0.0")).contains(
                "useHeaderSubject=true", "removeHeaders", "xslt", "--dep", "camel.server.port", "smtp");
    }

    @Test
    void theMigrationTextHasTheRulesOfAMigrationThenTheWorkflow() {
        String version = new DefaultCamelCatalog().getCatalogVersion();
        String text = McpServerInstructions.migration();

        assertThat(text).startsWith("Migrating an integration to Apache Camel " + version + ",");
        assertThat(McpServerInstructions.migration(null)).startsWith("Migrating an integration to Apache Camel "
                                                                     + "(unknown version),");
        assertThat(text).containsSubsequence(
                "URI scheme: smtp", "platform-http",
                "useHeaderSubject=true", "useHeaderFrom=true", "useHeaderRecipients=true",
                "removeHeaders", "Camel*",
                "xslt", "headers and variables",
                "camel.server.port", "//DEPS", "camel.jbang.dependencies", "--dep",
                "run the app",
                "Workflow:",
                "camel_migrate_project");
    }

    @Test
    void everyToolOfTheMigrationTextIsAToolOfThisServer() throws IOException {
        Set<String> named = namesIn(McpServerInstructions.migration());
        named.removeAll(promptNames());

        assertThat(toolNames()).containsAll(named);
        assertThat(McpServerInstructions.MIGRATION_TOOLS).containsExactlyInAnyOrderElementsOf(named);
    }

    @Test
    void aCitrusTestRunsWithCamelTestRunNotCamelRun() {
        String text = new McpServerInstructions().text();

        // a model ran its Citrus test with camel_run, which started an integration with no routes
        assertThat(text).contains("- run a Citrus test (test/<name>.citrus.it.yaml): camel test run <file> in the project"
                                  + " directory, not camel_run");
    }

    @Test
    void everyMappedToolIsMentionedAndIsAToolOfThisServer() throws IOException {
        Set<String> named = namesIn(new McpServerInstructions().text());
        Set<String> toolNames = toolNames();
        Set<String> promptNames = promptNames();

        assertThat(toolNames).as("@Tool methods found").hasSizeGreaterThan(10);
        assertThat(promptNames).as("@Prompt methods found").contains("camel_migrate_project");
        // the text names exactly the mapped tools and prompts, and each is one of this server (not renamed or removed)
        Set<String> prompts = new HashSet<>(named);
        prompts.retainAll(promptNames);
        named.removeAll(promptNames);
        assertThat(toolNames).containsAll(named);
        assertThat(McpServerInstructions.MAPPED_TOOLS).containsExactlyInAnyOrderElementsOf(named);
        assertThat(McpServerInstructions.MAPPED_PROMPTS).containsExactlyInAnyOrderElementsOf(prompts);
    }

    @Test
    void aSampleCitrusTestIsInTheExamples() {
        String text = new McpServerInstructions().text();

        assertThat(text).contains("Citrus test", "camel test run", "camel_catalog_examples with filter citrus");
    }

    @Test
    void anExplicitPropertyReplacesTheText() {
        McpServerInstructions bean = new McpServerInstructions("4.0.0");

        assertThat(bean.instructions("camel-mcp-server")).contains(bean.text());

        bean.configured = Optional.of("my own instructions");
        assertThat(bean.instructions("camel-mcp-server")).isEmpty();

        bean.configured = Optional.of(" ");
        assertThat(bean.instructions("camel-mcp-server")).contains(bean.text());
    }

    /** The camel_* names in a text. */
    private static Set<String> namesIn(String text) {
        Matcher m = TOOL_NAME.matcher(text);
        Set<String> named = new HashSet<>();
        while (m.find()) {
            named.add(m.group());
        }
        return named;
    }

    /** The names of the {@code @Prompt} methods of the MCP server. */
    private static Set<String> promptNames() {
        Set<String> names = new HashSet<>();
        for (Method method : PromptDefinitions.class.getDeclaredMethods()) {
            Prompt prompt = method.getAnnotation(Prompt.class);
            if (prompt != null) {
                names.add(Prompt.ELEMENT_NAME.equals(prompt.name()) ? method.getName() : prompt.name());
            }
        }
        return names;
    }

    /** The names of the {@code @Tool} methods of every class in the package of the MCP server. */
    private static Set<String> toolNames() throws IOException {
        Path classes;
        try {
            classes = Path.of(McpServerInstructions.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
        String pkg = McpServerInstructions.class.getPackageName();
        Path dir = classes.resolve(pkg.replace('.', '/'));
        Set<String> names = new HashSet<>();
        List<String> classNames;
        try (Stream<Path> files = Files.list(dir)) {
            classNames = files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".class"))
                    .map(n -> pkg + "." + n.substring(0, n.length() - ".class".length()))
                    .toList();
        }
        for (String className : classNames) {
            Class<?> type;
            try {
                type = Class.forName(className, false, McpServerInstructions.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                continue;
            }
            for (Method method : type.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool != null) {
                    names.add(Tool.ELEMENT_NAME.equals(tool.name())
                            ? method.getName() : tool.name());
                }
            }
        }
        return names;
    }
}
