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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Endpoint;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;

/**
 * The components a route source uses: the components of its endpoints, read as routes of its DSL by the reader of
 * {@code camel overview} (YAML and XML structurally, Java with the Java DSL parser, the endpoint DSL and constants
 * resolved). A {@code log} step is not the log component, and a name in a comment or a description is no endpoint.
 * Content that cannot be read as routes, such as a fragment of a route, falls back to looking for the component names
 * in the text (CAMEL-25586).
 */
public final class RouteComponents {

    private RouteComponents() {
    }

    /**
     * The components the route uses, in the order of the source.
     *
     * @param  content the route source: YAML, XML or Java DSL
     * @param  format  yaml, xml or java; null or blank to tell from the content
     * @param  catalog the catalog the components are looked up in
     * @return         the names of the components the catalog knows
     */
    public static List<String> of(String content, String format, CamelCatalog catalog) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<Route> routes;
        try {
            routes = ProjectRoutes.parse("route." + extension(format, content), content, catalog);
        } catch (RuntimeException e) {
            routes = List.of();
        }
        if (routes.isEmpty()) {
            return mentioned(content, catalog.findComponentNames());
        }
        Set<String> answer = new LinkedHashSet<>();
        for (Route r : routes) {
            if ("rest".equals(r.kind())) {
                // the rest DSL runs on the rest component
                add(answer, "rest", catalog);
            }
            add(answer, r.from(), catalog);
            r.consumes().forEach(e -> add(answer, e, catalog));
            r.produces().forEach(e -> add(answer, e, catalog));
        }
        return new ArrayList<>(answer);
    }

    /**
     * The given components the route uses (as {@link #of(String, String, CamelCatalog)}), in the order they are given.
     *
     * @param  content    the route source: YAML, XML or Java DSL
     * @param  format     yaml, xml or java; null or blank to tell from the content
     * @param  catalog    the catalog the components are looked up in
     * @param  components the names of the components to look for, not null
     * @return            the names of the given components the route uses
     */
    public static List<String> of(String content, String format, CamelCatalog catalog, List<String> components) {
        List<String> used = of(content, format, catalog);
        return components.stream().filter(used::contains).toList();
    }

    /**
     * The components whose name the text has as an endpoint ({@code name:}) or a quoted name: the fallback for content
     * that cannot be read as routes.
     */
    static List<String> mentioned(String content, List<String> components) {
        String lower = content.toLowerCase(Locale.ROOT);
        List<String> answer = new ArrayList<>();
        for (String comp : components) {
            if ((lower.contains(comp + ":") || lower.contains("\"" + comp + "\"") || lower.contains("'" + comp + "'"))
                    && !answer.contains(comp)) {
                answer.add(comp);
            }
        }
        return answer;
    }

    private static void add(Set<String> answer, Endpoint e, CamelCatalog catalog) {
        if (e != null && e.scheme() != null) {
            add(answer, e.scheme().toLowerCase(Locale.ROOT), catalog);
        }
    }

    private static void add(Set<String> answer, String scheme, CamelCatalog catalog) {
        if (!answer.contains(scheme) && isComponent(scheme, catalog)) {
            answer.add(scheme);
        }
    }

    private static boolean isComponent(String scheme, CamelCatalog catalog) {
        try {
            return catalog.componentModel(scheme) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** The file extension of the route source: the given format, else what the content looks like. */
    static String extension(String format, String content) {
        if (format != null && !format.isBlank()) {
            String f = format.strip().toLowerCase(Locale.ROOT);
            return "yml".equals(f) ? "yaml" : f;
        }
        String s = content.stripLeading();
        if (s.startsWith("<")) {
            return "xml";
        }
        if (content.contains("RouteBuilder") || content.contains("void configure()")) {
            return "java";
        }
        return "yaml";
    }
}
