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
import java.lang.reflect.Parameter;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolManager.ToolArgument;
import io.quarkiverse.mcp.server.ToolManager.ToolInfo;

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
        return toolMethods().stream().map(McpToolClasses::toolName).collect(Collectors.toSet());
    }

    /** The methods that are tools. */
    static List<Method> toolMethods() throws IOException, URISyntaxException {
        return toolClasses().stream()
                .flatMap(c -> Arrays.stream(c.getDeclaredMethods()))
                .filter(m -> m.isAnnotationPresent(Tool.class))
                .toList();
    }

    static String toolName(Method method) {
        String name = method.getAnnotation(Tool.class).name();
        return Tool.ELEMENT_NAME.equals(name) ? method.getName() : name;
    }

    /** The tool of that name. */
    static Method toolMethod(String name) throws IOException, URISyntaxException {
        return toolMethods().stream().filter(m -> toolName(m).equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No tool " + name));
    }

    /**
     * The tool as the server describes it to a guardrail: its name and its arguments in declaration order, with the
     * names and required flags the server derives from the parameters (the {@link ToolArg} name, else the parameter
     * name; required unless {@link ToolArg#required()} is false or the type is {@link Optional}). The parameters the
     * server provides itself, such as the {@code McpConnection}, are not arguments.
     */
    static ToolInfo toolInfo(Method method) {
        List<ToolArgument> arguments = new ArrayList<>();
        for (Parameter p : method.getParameters()) {
            if (p.getType().getPackageName().equals(Tool.class.getPackageName())) {
                continue;
            }
            ToolArg arg = p.getAnnotation(ToolArg.class);
            String name = arg != null && !ToolArg.ELEMENT_NAME.equals(arg.name()) ? arg.name() : p.getName();
            boolean required = arg != null ? arg.required() : !Optional.class.equals(p.getType());
            arguments.add(new ToolArgument(
                    name, arg != null ? arg.description() : "", required,
                    p.getParameterizedType(), null));
        }
        String name = toolName(method);
        return (ToolInfo) Proxy.newProxyInstance(McpToolClasses.class.getClassLoader(), new Class<?>[] { ToolInfo.class },
                (proxy, m, args) -> switch (m.getName()) {
                    case "name" -> name;
                    case "arguments" -> arguments;
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(m.getName());
                });
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
