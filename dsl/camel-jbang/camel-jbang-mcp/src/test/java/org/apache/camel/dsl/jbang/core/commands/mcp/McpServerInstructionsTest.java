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

import io.quarkiverse.mcp.server.Tool;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The server instructions sent in the MCP initialize response: their size, their first line and the tool names they
 * point the agent to.
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
                .contains("URI scheme", "camel_validate_source");
        assertThat(McpServerInstructions.build("9.9.9")).startsWith("Apache Camel 9.9.9 ");
        assertThat(McpServerInstructions.build(null)).startsWith("Apache Camel (unknown version) ");
    }

    @Test
    void theRulesComeInTheOrderOfTheProposal() {
        String text = new McpServerInstructions().text();

        assertThat(text).containsSubsequence(
                "URI scheme: smtp", "platform-http",
                "top-level", "handled: {constant: {expression: \"true\"}}",
                "camel.server.port", "//DEPS", "camel.jbang.dependencies", "--dep",
                "useHeaderSubject=true", "useHeaderFrom=true", "useHeaderRecipients=true",
                "removeHeaders", "Camel*",
                "xslt", "headers and variables",
                "run the app",
                "Which tool:");
    }

    @Test
    void everyMappedToolIsMentionedAndIsAToolOfThisServer() throws IOException {
        String text = new McpServerInstructions().text();
        Set<String> toolNames = toolNames();

        assertThat(toolNames).as("@Tool methods found").hasSizeGreaterThan(10);
        // the text names exactly the mapped tools, and each is a tool of this server (not renamed or removed)
        Matcher m = TOOL_NAME.matcher(text);
        Set<String> named = new HashSet<>();
        while (m.find()) {
            named.add(m.group());
        }
        assertThat(toolNames).containsAll(named);
        assertThat(McpServerInstructions.MAPPED_TOOLS).containsExactlyInAnyOrderElementsOf(named);
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
