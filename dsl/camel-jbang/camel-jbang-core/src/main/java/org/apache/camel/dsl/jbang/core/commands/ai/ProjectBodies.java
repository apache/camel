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

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.apache.camel.dsl.yaml.validator.BodyOrigin;
import org.apache.camel.dsl.yaml.validator.ProjectFacts;
import org.apache.camel.dsl.yaml.validator.YamlValidator;

/**
 * Where the message body of each YAML route of a project comes from, for {@code camel overview} (CAMEL-25200). It is
 * the analysis of the body check of {@code camel validate}, made the way {@code camel validate} makes it: per file,
 * with the same schema validator, only for a file that passes the schema, and with the GET operations of an OpenAPI
 * contract beside the route known to carry no body. So the {@code body-never-set} findings of the two are the same.
 */
final class ProjectBodies {

    private ProjectBodies() {
    }

    /**
     * The body origin of each YAML route, by the key of the route in the overview.
     *
     * @param dir     the project directory, for the OpenAPI contracts the routes bind to; may be null
     * @param sources the content of each route file by its path relative to the directory
     * @param routes  the routes of the overview
     */
    static Map<String, BodyOrigin> analyze(
            Path dir, Map<String, String> sources, List<Route> routes, CamelCatalog catalog) {
        Map<String, BodyOrigin> answer = new LinkedHashMap<>();
        YamlValidator validator = null;
        for (Map.Entry<String, String> e : sources.entrySet()) {
            String file = e.getKey();
            String name = file.toLowerCase(Locale.ROOT);
            if (!name.endsWith(".yaml") && !name.endsWith(".yml")) {
                continue;
            }
            List<BodyOrigin> origins;
            try {
                if (validator == null) {
                    validator = catalog != null
                            ? SourceValidator.yamlValidator(catalog) : SourceValidator.yamlValidator();
                }
                Set<String> bodyless = dir != null ? OpenApiVerbs.bodylessEndpoints(e.getValue(), dir) : Set.of();
                origins = validator.bodyOrigins(e.getValue(), new ProjectFacts(bodyless, Map.of(), null));
            } catch (Exception ex) {
                // the overview is still an overview without the bodies
                continue;
            }
            if (!origins.isEmpty()) {
                match(file, origins, routes, answer);
            }
        }
        return answer;
    }

    /**
     * Gives each origin the route of the overview it is about: by its id, else by its place among the routes of the
     * file, when the overview has the same routes for the file.
     */
    private static void match(String file, List<BodyOrigin> origins, List<Route> routes, Map<String, BodyOrigin> answer) {
        List<Route> ofFile = routes.stream()
                .filter(r -> file.equals(r.file()) && "route".equals(r.kind()))
                .sorted(Comparator.comparingInt(Route::line))
                .toList();
        boolean sameRoutes = ofFile.size() == origins.size();
        for (BodyOrigin o : origins) {
            Route route = null;
            if (o.route() != null && !o.route().isBlank()) {
                route = ofFile.stream().filter(r -> o.route().equals(r.id())).findFirst().orElse(null);
            } else if (sameRoutes) {
                route = ofFile.get(o.index());
            }
            if (route != null) {
                answer.putIfAbsent(route.key(), o);
            }
        }
    }
}
