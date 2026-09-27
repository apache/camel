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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.util.json.Jsoner;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * What a {@code rest: openApi:} binding hides: which of its operations carry a body.
 * <p/>
 * {@code rest-openapi} routes each operation of the specification to {@code direct:<operationId>}, and whether that
 * message has a body is decided by the verb - a GET and a DELETE carry none - which is written in the specification and
 * not in the route. The specification is a file beside the route, so it can be read (CAMEL-24844).
 */
public final class OpenApiVerbs {

    /** The verbs whose request carries no body. */
    private static final Set<String> WITHOUT_BODY = Set.of("get", "delete", "head", "options", "trace");

    private static final Pattern SPECIFICATION = Pattern.compile(
            "openApi:\\s*\\n\\s*(?:[a-zA-Z]+:[^\\n]*\\n\\s*)*?specification:\\s*[\"']?([^\"'\\s]+)[\"']?");

    private OpenApiVerbs() {
    }

    /**
     * The {@code direct:} endpoints of the operations that carry no body, for a file that binds to an OpenAPI
     * specification in the given directory. Empty when the file binds to none, or the specification cannot be read -
     * nothing is guessed.
     */
    public static Set<String> bodylessEndpoints(String content, Path directory) {
        Set<String> answer = new LinkedHashSet<>();
        if (content == null || directory == null) {
            return answer;
        }
        Matcher m = SPECIFICATION.matcher(content);
        while (m.find()) {
            Path spec = directory.resolve(m.group(1));
            if (!Files.isRegularFile(spec)) {
                continue;
            }
            try {
                Map<?, ?> paths = paths(Files.readString(spec));
                if (paths == null) {
                    continue;
                }
                for (Object path : paths.values()) {
                    if (!(path instanceof Map<?, ?> operations)) {
                        continue;
                    }
                    for (Map.Entry<?, ?> operation : operations.entrySet()) {
                        String verb = String.valueOf(operation.getKey()).toLowerCase(java.util.Locale.ROOT);
                        if (WITHOUT_BODY.contains(verb) && operation.getValue() instanceof Map<?, ?> details
                                && details.get("operationId") != null) {
                            answer.add("direct:" + details.get("operationId"));
                        }
                    }
                }
            } catch (Exception e) {
                // Catching Exception handles unreadable and unparseable JSON or YAML specifications.
            }
        }
        return answer;
    }

    private static Map<?, ?> paths(String text) {
        try {
            Object document;
            if (text.stripLeading().startsWith("{")) {
                document = Jsoner.deserialize(text);
            } else {
                document = new Yaml(new SafeConstructor(new LoaderOptions())).load(text);
            }
            return document instanceof Map<?, ?> root && root.get("paths") instanceof Map<?, ?> paths ? paths : null;
        } catch (Exception e) {
            // Ignore unreadable or unparseable JSON or YAML specifications.
            return null;
        }
    }
}
