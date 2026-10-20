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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.camel.dsl.jbang.core.common.ActionClient;
import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;
import org.apache.camel.dsl.jbang.core.common.RuntimeHelper;
import org.apache.camel.dsl.yaml.validator.ProjectFacts;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

/**
 * What a running integration of a route file saw at the endpoints of its routes (CAMEL-25750): the body each consumer
 * gave, and the body each producer returned, as the step after it was reached with while the message history was on
 * (the dev profile turns it on). The validator fills the gaps of its walk with them: what the walk alone does not know,
 * such as the stream cache of an HTTP request, or the List a sql call returns.
 * <p/>
 * The facts are per endpoint, not per line: the validator checks the next version of the file while the integration
 * runs the one before, and what an endpoint gives does not move with the lines.
 */
final class ObservedBodies {

    /** How long to wait for the integration to answer: validation does not wait for long. */
    private static final long TIMEOUT = 3000;

    /** How long the facts of a file are kept: a write validates the file before and after the change. */
    private static final long KEEP = 2000;

    private static final Map<Path, Facts> CACHE = new ConcurrentHashMap<>();

    private record Facts(long time, Map<String, ProjectFacts.Observed> observed) {
    }

    private ObservedBodies() {
    }

    /**
     * What a running integration of the file saw; nothing when no integration runs routes of the file, or it does not
     * answer in time.
     */
    static Map<String, ProjectFacts.Observed> read(String fileName, Path directory) {
        if (fileName == null || fileName.isBlank()) {
            return Map.of();
        }
        Path file = (directory != null ? directory.resolve(fileName) : Path.of(fileName)).toAbsolutePath().normalize();
        Facts cached = CACHE.get(file);
        if (cached != null && System.currentTimeMillis() - cached.time() < KEEP) {
            return cached.observed();
        }
        Map<String, ProjectFacts.Observed> answer = Map.of();
        // the processes are only looked for when an integration runs at all: most validations have none
        String[] statusFiles = CommandLineHelper.getCamelDir().toFile().list((d, n) -> n.endsWith("-status.json"));
        if (statusFiles == null || statusFiles.length == 0) {
            return answer;
        }
        for (RuntimeHelper.ProcessInfo process : RuntimeHelper.discoverProcesses()) {
            if (runsRoutesOf(RuntimeHelper.readStatus(process.pid()), file)) {
                answer = ask(process.pid());
                break;
            }
        }
        CACHE.put(file, new Facts(System.currentTimeMillis(), answer));
        return answer;
    }

    private static Map<String, ProjectFacts.Observed> ask(long pid) {
        try {
            JsonObject request = new JsonObject();
            request.put("action", "route-structure");
            request.put("filter", "*");
            String answer = ActionClient.executeText(pid, request, TIMEOUT);
            JsonObject structure = answer != null ? Jsoner.deserialize(answer, (JsonObject) null) : null;
            return structure != null ? fromRouteStructure(structure) : Map.of();
        } catch (Exception e) {
            // the validator then says only what it knows from the file
            return Map.of();
        }
    }

    /** Whether the status of an integration has a route loaded from the file. */
    static boolean runsRoutesOf(JsonObject status, Path file) {
        JsonArray routes = status != null ? status.getCollection("routes") : null;
        if (routes == null) {
            return false;
        }
        for (Object o : routes) {
            if (o instanceof JsonObject route && sameFile(route.getString("source"), file)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a route source (file:/work/orders.camel.yaml:12, classpath:orders.camel.yaml:3) is the file: by its path
     * when it has one, by its name otherwise.
     */
    static boolean sameFile(String source, Path file) {
        if (source == null || file.getFileName() == null) {
            return false;
        }
        String s = source.replaceFirst(":\\d+$", "");
        int colon = s.indexOf(':');
        if (colon > 1) {
            // the scheme, not a drive letter of Windows
            s = s.substring(colon + 1);
        }
        if (s.startsWith("/") || s.contains(":\\")) {
            return Path.of(s).toAbsolutePath().normalize().equals(file);
        }
        String name = s.substring(Math.max(s.lastIndexOf('/'), s.lastIndexOf('\\')) + 1);
        return name.equals(file.getFileName().toString());
    }

    /**
     * The bodies of the endpoints, from the route-structure console: what the step after a from was reached with is
     * what the consumer gave, and what the step after a to (the next step of its block) was reached with is what the
     * producer returned. A to that ends its block has no step that only it reaches, and a toD no endpoint of its own.
     */
    static Map<String, ProjectFacts.Observed> fromRouteStructure(JsonObject structure) {
        Map<String, Set<String>> types = new LinkedHashMap<>();
        Map<String, Set<String>> kinds = new LinkedHashMap<>();
        JsonArray routes = structure.getCollection("routes");
        if (routes == null) {
            return Map.of();
        }
        for (Object r : routes) {
            List<JsonObject> code = r instanceof JsonObject route ? route.getCollection("code") : null;
            if (code == null) {
                continue;
            }
            for (int i = 0; i < code.size(); i++) {
                JsonObject line = code.get(i);
                String type = line.getString("type");
                String uri = line.getString("uri");
                if (uri == null) {
                    continue;
                }
                uri = Jsoner.unescape(uri);
                JsonObject after = null;
                String key = null;
                if ("from".equals(type)) {
                    after = firstStep(code, i);
                    key = ProjectFacts.consumerKey(uri);
                } else if ("to".equals(type)) {
                    after = nextStep(code, i);
                    key = ProjectFacts.producerKey(uri);
                }
                List<String> seen = after != null ? after.getCollection("bodyTypes") : null;
                List<String> seenKinds = after != null ? after.getCollection("bodyKinds") : null;
                if (key == null || seen == null || seen.isEmpty() || seenKinds == null || seenKinds.isEmpty()) {
                    continue;
                }
                types.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(seen);
                kinds.computeIfAbsent(key, k -> new LinkedHashSet<>()).addAll(seenKinds);
            }
        }
        Map<String, ProjectFacts.Observed> answer = new LinkedHashMap<>();
        types.forEach((key, t) -> answer.put(key, new ProjectFacts.Observed(List.copyOf(t), List.copyOf(kinds.get(key)))));
        return answer;
    }

    /** The first step of a route: the line after its from, one level deeper. */
    private static JsonObject firstStep(List<JsonObject> code, int from) {
        int level = level(code.get(from));
        return from + 1 < code.size() && level(code.get(from + 1)) == level + 1 ? code.get(from + 1) : null;
    }

    /** The step after a to in its block: the next line on its level, before the block ends. */
    private static JsonObject nextStep(List<JsonObject> code, int to) {
        int level = level(code.get(to));
        for (int j = to + 1; j < code.size(); j++) {
            int l = level(code.get(j));
            if (l == level) {
                return code.get(j);
            } else if (l < level) {
                return null;
            }
        }
        return null;
    }

    private static int level(JsonObject line) {
        Integer level = line.getInteger("level");
        return level != null ? level : 0;
    }
}
