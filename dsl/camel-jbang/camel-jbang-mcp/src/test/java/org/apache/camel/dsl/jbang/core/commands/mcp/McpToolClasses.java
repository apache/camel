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
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkiverse.mcp.server.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classes of the MCP module that expose a {@link Tool}, found by scanning the main classes directory, and the names
 * of their tools: what the server lists.
 */
final class McpToolClasses {

    private McpToolClasses() {
    }

    static List<Class<?>> toolClasses() throws IOException, URISyntaxException {
        URL url = McpSecured.class.getResource(McpSecured.class.getSimpleName() + ".class");
        assertThat(url).isNotNull();
        assertThat(url.getProtocol()).as("the main classes are scanned as a directory").isEqualTo("file");
        Path dir = Path.of(url.toURI()).getParent();
        String pkg = McpSecured.class.getPackageName();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".class") && !n.contains("$"))
                    .<Class<?>> map(n -> load(pkg + "." + n.substring(0, n.length() - ".class".length())))
                    .filter(McpToolClasses::declaresTool)
                    .toList();
        }
    }

    /** The tool names: the {@link Tool#name()} when set, else the method name. */
    static Set<String> toolNames() throws IOException, URISyntaxException {
        return toolClasses().stream()
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .map(m -> {
                    String name = m.getAnnotation(Tool.class).name();
                    return Tool.ELEMENT_NAME.equals(name) ? m.getName() : name;
                })
                .collect(Collectors.toSet());
    }

    private static boolean declaresTool(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredMethods()).anyMatch(m -> m.isAnnotationPresent(Tool.class));
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, false, McpToolClasses.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
