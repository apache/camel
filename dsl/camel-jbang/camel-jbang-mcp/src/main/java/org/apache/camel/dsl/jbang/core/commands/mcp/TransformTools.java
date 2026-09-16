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

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.xml.in.ModelParser;
import org.apache.camel.yaml.out.YamlModelWriter;

/**
 * MCP tool transforming Camel routes between DSL formats using Quarkus MCP Server. Validation is the shared
 * {@code camel_validate_source} tool.
 */
@McpSecured
@ApplicationScoped
public class TransformTools {

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");

    /**
     * Tool to transform routes between DSL formats.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Transform a Camel route between different DSL formats (YAML, XML). " +
                        "Note: Java to YAML/XML transformation has limitations."
                        + " Java DSL can only be used as source format, not as target format.")
    public TransformResult camel_transform_route(
            @ToolArg(description = "Route definition to transform") String route,
            @ToolArg(description = "Source format (yaml, xml, java)") String fromFormat,
            @ToolArg(description = "Target format (yaml, xml)") String toFormat) {

        if (route == null || fromFormat == null || toFormat == null) {
            throw new ToolCallException("route, fromFormat, and toFormat are required", null);
        }

        TransformResult result = new TransformResult();
        result.fromFormat = fromFormat;
        result.toFormat = toFormat;

        String from = fromFormat.toLowerCase();
        String to = toFormat.toLowerCase();

        if (from.equals(to)) {
            result.supported = true;
            result.result = route;
            return result;
        }

        try {
            if ("xml".equals(from) && "yaml".equals(to)) {
                result.result = transformXmlToYaml(route);
                result.supported = true;
            } else if ("yaml".equals(from) && "xml".equals(to)) {
                result.result = transformYamlToXml(route);
                result.supported = true;
            } else if ("java".equals(from) && "yaml".equals(to)) {
                result.result = transformJavaToFormat(route, "yaml");
                result.supported = true;
            } else if ("java".equals(from) && "xml".equals(to)) {
                result.result = transformJavaToFormat(route, "xml");
                result.supported = true;
            } else {
                result.supported = false;
                result.note = "Unsupported transformation: " + fromFormat + " to " + toFormat;
            }
        } catch (Throwable e) {
            throw new ToolCallException(
                    "Failed to transform route (" + e.getClass().getName() + "): " + e.getMessage(), null);
        }

        return result;
    }

    /**
     * Transform an XML route definition to YAML format.
     */
    private String transformXmlToYaml(String xml) throws Exception {
        // Try Spring namespace first (most common), then fall back to no namespace
        RoutesDefinition routes = null;
        try (ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
            routes = new ModelParser(is, "http://camel.apache.org/schema/spring")
                    .parseRoutesDefinition().orElse(null);
        }
        if (routes == null) {
            try (ByteArrayInputStream is = new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))) {
                routes = new ModelParser(is)
                        .parseRoutesDefinition().orElse(null);
            }
        }
        if (routes == null) {
            throw new IllegalArgumentException(
                    "Could not parse XML route. Ensure it contains a valid <routes> or <route> element.");
        }

        YamlModelWriter writer = new YamlModelWriter();
        List<JsonObject> roots = new ArrayList<>();
        for (RouteDefinition route : routes.getRoutes()) {
            roots.add(writer.writeRouteDefinition(route));
        }
        return writer.printAsYaml(roots);
    }

    /**
     * Transform a YAML route definition to XML format.
     */
    private String transformYamlToXml(String yaml) throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        try {
            ctx.build();

            Resource resource = ResourceHelper.fromString("route.yaml", yaml);
            PluginHelper.getRoutesLoader(ctx).loadRoutes(resource);

            List<RouteDefinition> routeDefs = ctx.getRouteDefinitions();
            if (routeDefs == null || routeDefs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Could not parse YAML route. Ensure it contains a valid route definition.");
            }

            RoutesDefinition rd = new RoutesDefinition();
            rd.setRoutes(routeDefs);

            StringWriter sw = new StringWriter();
            new org.apache.camel.xml.out.ModelWriter(sw).writeRoutesDefinition(rd);
            return sw.toString();
        } finally {
            ctx.stop();
        }
    }

    private String transformJavaToFormat(String java, String targetFormat) throws Exception {
        DefaultCamelContext ctx = new DefaultCamelContext();
        try {
            ctx.build();

            String source = wrapSnippetIfNeeded(java);
            String className = extractClassName(source);
            Resource resource = ResourceHelper.fromString(className + ".java", source);
            PluginHelper.getRoutesLoader(ctx).loadRoutes(resource);

            List<RouteDefinition> routeDefs = ctx.getRouteDefinitions();
            if (routeDefs == null || routeDefs.isEmpty()) {
                throw new IllegalArgumentException(
                        "Could not parse Java route. Ensure it contains a valid route definition.");
            }

            if ("yaml".equals(targetFormat)) {
                YamlModelWriter writer = new YamlModelWriter();
                List<JsonObject> roots = new ArrayList<>();
                for (RouteDefinition route : routeDefs) {
                    roots.add(writer.writeRouteDefinition(route));
                }
                return writer.printAsYaml(roots);
            } else {
                RoutesDefinition rd = new RoutesDefinition();
                rd.setRoutes(routeDefs);

                StringWriter sw = new StringWriter();
                new org.apache.camel.xml.out.ModelWriter(sw).writeRoutesDefinition(rd);
                return sw.toString();
            }
        } finally {
            ctx.stop();
        }
    }

    private static String wrapSnippetIfNeeded(String source) {
        if (CLASS_NAME_PATTERN.matcher(source).find()) {
            return source;
        }
        return "import org.apache.camel.builder.RouteBuilder;\n\n"
               + "public class SnippetRoute extends RouteBuilder {\n"
               + "    @Override\n"
               + "    public void configure() {\n"
               + "        " + source + "\n"
               + "    }\n"
               + "}\n";
    }

    private static String extractClassName(String source) {
        Matcher m = CLASS_NAME_PATTERN.matcher(source);
        return m.find() ? m.group(1) : "Route";
    }

    // Result class for Jackson serialization

    public static class TransformResult {
        public String fromFormat;
        public String toFormat;
        public String note;
        public boolean supported;
        public String result;
    }
}
