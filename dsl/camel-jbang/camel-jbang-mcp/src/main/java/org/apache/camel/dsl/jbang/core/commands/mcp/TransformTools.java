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
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.ToolCallException;
import org.apache.camel.dsl.jbang.core.commands.RouteDslConverter;

/**
 * MCP tool transforming Camel routes between DSL formats with the route DSL converter of camel-jbang, which reads the
 * routes without running them. Validation is the shared {@code camel_validate_source} tool.
 */
@McpSecured
@ApplicationScoped
public class TransformTools {

    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("(?:public\\s+)?class\\s+(\\w+)");

    /**
     * Tool to transform routes between DSL formats.
     */
    @Tool(annotations = @Tool.Annotations(readOnlyHint = true, destructiveHint = false, openWorldHint = false),
          description = "Transform Camel routes between the YAML, XML and Java DSLs, without running them."
                        + " Routes, rests, route templates, route configurations and beans are converted."
                        + " The result is read back and compared with the source: what differs, or is not carried"
                        + " over (such as comments, or the beans when the target is Java), is listed in notes."
                        + " What cannot be converted without running it (a processor lambda) is refused with the reason.")
    public TransformResult camel_transform_route(
            @ToolArg(description = "Route definition to transform: a YAML or XML routes file, a Java RouteBuilder class,"
                                   + " or Java route statements such as from(\"timer:tick\").to(\"log:out\");") String route,
            @ToolArg(description = "Source format (yaml, xml, java)") String fromFormat,
            @ToolArg(description = "Target format (yaml, xml, java)") String toFormat) {

        if (route == null || fromFormat == null || toFormat == null) {
            throw new ToolCallException("route, fromFormat, and toFormat are required", null);
        }

        TransformResult result = new TransformResult();
        result.fromFormat = fromFormat;
        result.toFormat = toFormat;

        String from = fromFormat.toLowerCase(Locale.ROOT);
        String to = toFormat.toLowerCase(Locale.ROOT);

        if (from.equals(to)) {
            result.supported = true;
            result.result = route;
            return result;
        }
        if (!RouteDslConverter.FORMATS.contains(from) || !RouteDslConverter.FORMATS.contains(to)) {
            result.supported = false;
            result.note = "Unsupported transformation: " + fromFormat + " to " + toFormat;
            return result;
        }

        RouteDslConverter.Result r = RouteDslConverter.convert(fileName(route, from), route, to, Map.of());
        if (!r.converted()) {
            throw new ToolCallException("Cannot transform route: " + r.refused(), null);
        }
        result.supported = true;
        result.result = r.content();
        result.notes = r.notes();
        if (!r.notes().isEmpty()) {
            result.note = String.join("; ", r.notes());
        }
        return result;
    }

    /** A file name for the route as the converter tells the DSLs apart: Java by its class. */
    private static String fileName(String route, String format) {
        if ("java".equals(format)) {
            Matcher m = CLASS_NAME_PATTERN.matcher(route);
            return (m.find() ? m.group(1) : "Route") + ".java";
        }
        return "route.camel." + format;
    }

    // Result class for Jackson serialization

    public static class TransformResult {
        public String fromFormat;
        public String toFormat;
        public String note;
        public List<String> notes = List.of();
        public boolean supported;
        public String result;
    }
}
