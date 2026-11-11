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

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Endpoint;
import org.apache.camel.dsl.jbang.core.commands.ai.ProjectRoutes.Route;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * The operations of a REST DSL that serves an OpenAPI contract (contract first: {@code rest: openApi: specification:
 * stock-api.json}). The routes do not name them: each operation is served by the route {@code direct:<operationId>},
 * and the paths and methods are in the contract. The readers of the sources record the contract as one route of kind
 * {@link #KIND}; the overview, which knows the project directory, reads the contract and puts one REST operation per
 * operation of it in its place, as the REST DSL written out would have.
 */
public final class RestContracts {

    /** The kind of the route a reader records for a REST DSL that serves a contract. */
    static final String KIND = "openApiContract";

    private static final Set<String> METHODS = Set.of("get", "post", "put", "patch", "delete", "head", "options");

    private RestContracts() {
    }

    /**
     * The route a reader records for a contract: its specification as the description, the path of the REST DSL as its
     * path.
     */
    static Route contract(String specification, String basePath, String file, int line, String format) {
        return new Route(
                null, KIND, specification, null, file, line, format, false, null, List.of(), List.of(), 1, -1, 0,
                null, basePath, false, null);
    }

    /** The routes with each contract in the place of its operations; a contract that cannot be read has none. */
    static List<Route> expand(Path dir, List<Route> routes, CamelCatalog catalog) {
        if (routes.stream().noneMatch(r -> KIND.equals(r.kind()))) {
            return routes;
        }
        List<Route> answer = new ArrayList<>();
        for (Route r : routes) {
            if (KIND.equals(r.kind())) {
                answer.addAll(operations(dir, r, catalog));
            } else {
                answer.add(r);
            }
        }
        return answer;
    }

    private static List<Route> operations(Path dir, Route contract, CamelCatalog catalog) {
        Map<?, ?> spec = read(dir, contract.file(), contract.description());
        if (spec == null || !(spec.get("paths") instanceof Map<?, ?> paths)) {
            return List.of();
        }
        // the base path of the contract (servers: url: http://localhost:8080/api), under the path of the REST DSL
        String base = ProjectRoutes.joinPath(contract.restPath(), serverPath(spec));
        List<Route> answer = new ArrayList<>();
        for (Map.Entry<?, ?> p : paths.entrySet()) {
            if (!(p.getValue() instanceof Map<?, ?> ops)) {
                continue;
            }
            for (Map.Entry<?, ?> o : ops.entrySet()) {
                String method = String.valueOf(o.getKey()).toLowerCase(Locale.ROOT);
                if (!METHODS.contains(method) || !(o.getValue() instanceof Map<?, ?> op)
                        || !(op.get("operationId") instanceof String operationId)) {
                    continue;
                }
                List<Endpoint> produces = new ArrayList<>();
                produces.add(ProjectRoutes.endpoint("direct:" + operationId, null, false, catalog));
                Object summary = op.get("summary");
                answer.add(new Route(
                        null, "rest", summary instanceof String s ? s : null, null, contract.file(), contract.line(),
                        contract.format(), false, null, List.of(), produces, 1, -1, 0, method.toUpperCase(Locale.ROOT),
                        ProjectRoutes.joinPath(base, String.valueOf(p.getKey())), false, null));
            }
        }
        return answer;
    }

    /** The path of the first server of the contract (/api of http://localhost:8080/api), or none. */
    static String serverPath(Map<?, ?> spec) {
        if (spec.get("servers") instanceof List<?> servers && !servers.isEmpty()
                && servers.get(0) instanceof Map<?, ?> server && server.get("url") instanceof String url) {
            try {
                String path = URI.create(url).getPath();
                return path == null || "/".equals(path) ? null : path;
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        // Swagger 2
        return spec.get("basePath") instanceof String b ? b : null;
    }

    /** The contract, JSON or YAML, by its path in the project or next to the route file; null when not found. */
    private static Map<?, ?> read(Path dir, String routeFile, String specification) {
        if (dir == null || specification == null) {
            return null;
        }
        String name = specification.replaceFirst("^(classpath|file):", "");
        List<Path> candidates = new ArrayList<>();
        candidates.add(dir.resolve(name));
        Path routeDir = routeFile != null ? dir.resolve(routeFile).getParent() : null;
        if (routeDir != null) {
            candidates.add(routeDir.resolve(name));
        }
        for (Path f : candidates) {
            if (f.normalize().startsWith(dir.normalize()) && Files.isRegularFile(f)) {
                try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                    // JSON is YAML too
                    Object doc = new Yaml(new SafeConstructor(new LoaderOptions())).load(r);
                    return doc instanceof Map<?, ?> m ? m : null;
                } catch (IOException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
