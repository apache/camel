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

import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A YAML code block of an AI answer, made into something the YAML DSL schema can check (CAMEL-26960). The code of an
 * answer is mostly a fragment: a {@code from:} without its route, a list of steps, one EIP. Checked as a route file,
 * the schema refuses its shape before it looks at its keys, so the fragment is wrapped in what it is a part of: an
 * entry of a route file goes in a file, steps and an EIP go in a route, and an option of one EIP (redeliveryPolicy:)
 * goes under that EIP. An option map without its key (slidingWindowSize: 10) is not checked: what it belongs to is not
 * known.
 *
 * @param content the YAML the schema checks: the block as it is for a route file, else the block in its wrapping
 * @param shift   the lines the wrapping puts before the first line of the block, to subtract from a line of an error
 * @param items   the locations of the items that hold the block: an unknown key there says only that the block is not
 *                what it was taken for (options of an EIP shown without the EIP: slidingWindowSize: 10), so it is not a
 *                mistake of the block
 */
record YamlFragment(String content, int shift, Pattern items) {

    /** The key of the first line: an item of a list (- from:) or a key of a mapping (from:). */
    private static final Pattern FIRST_KEY = Pattern.compile("^(- )?([A-Za-z_][\\w.-]*)\\s*:");

    /** The top-level keys of YAML that is not Camel: docker-compose, Kubernetes, Spring Boot and Quarkus settings. */
    private static final Set<String> OTHER_YAML = Set.of("apiVersion", "kind", "metadata", "services", "version",
            "spring", "quarkus", "camel", "server", "management", "logging", "jobs", "on");

    /** The route that steps and an EIP are put in: they are checked as the steps of its from:. */
    private static final String ROUTE = "- route:\n    from:\n      uri: direct:answer\n      steps:\n";

    private static final Pattern FILE_ITEMS = Pattern.compile("/\\d+");
    private static final Pattern ENTRY_ITEMS = Pattern.compile("/0");
    private static final Pattern STEP_ITEMS = Pattern.compile("/0(/route(/from(/steps/\\d+)?)?)?");

    /**
     * The keys that are neither an entry nor a step but an option of one EIP, which a model shows on their own
     * (redeliveryPolicy: with its options), and the wrapping that puts them under that EIP: what goes before the block,
     * how far the block is indented, and the locations of the wrapping.
     */
    private record Parent(String before, String indent, Pattern items) {
    }

    private static final Parent ON_EXCEPTION = new Parent(
            "- onException:\n    exception:\n      - java.lang.Exception\n", "    ",
            Pattern.compile("/0(/onException(/exception(/\\d+)?)?)?"));

    private static final Parent CIRCUIT_BREAKER = new Parent(
            ROUTE + "        - circuitBreaker:\n", "            ",
            Pattern.compile("/0(/route(/from(/steps(/0(/circuitBreaker)?)?)?)?)?"));

    private static final Map<String, Parent> PARENTS = Map.of(
            "redeliveryPolicy", ON_EXCEPTION,
            "resilience4jConfiguration", CIRCUIT_BREAKER,
            "faultToleranceConfiguration", CIRCUIT_BREAKER,
            "onFallback", CIRCUIT_BREAKER);

    /**
     * The block made checkable, or null when it is not Camel YAML: it has no key, or its first key is one of another
     * tool (services:, apiVersion:, spring:).
     */
    static YamlFragment of(String code) {
        String[] lines = code.split("\n", -1);
        int indent = Integer.MAX_VALUE;
        String first = null;
        for (String line : lines) {
            if (!line.isBlank() && !line.strip().startsWith("#")) {
                indent = Math.min(indent, YamlLines.countLeadingSpaces(line));
                if (first == null) {
                    first = line;
                }
            }
        }
        if (first == null) {
            return null;
        }
        // a block cut out of a deeper level of a route: its lines as if it were at the top
        String[] dedented = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            dedented[i] = YamlLines.countLeadingSpaces(lines[i]) >= indent ? lines[i].substring(indent) : lines[i].strip();
        }
        Matcher m = FIRST_KEY.matcher(first.substring(indent));
        if (!m.find() || OTHER_YAML.contains(m.group(2))) {
            return null;
        }
        boolean item = m.group(1) != null;
        if (CatalogSamples.TOP_LEVEL.contains(m.group(2))) {
            // a route file (- route:, - beans:, - onException:), or one of its entries written as a mapping (from:)
            return item
                    ? new YamlFragment(String.join("\n", dedented), 0, FILE_ITEMS)
                    : new YamlFragment(indented(dedented, "- ", "  "), 0, ENTRY_ITEMS);
        }
        Parent parent = item ? null : PARENTS.get(m.group(2));
        if (parent != null) {
            // redeliveryPolicy: under an onException, resilience4jConfiguration: under a circuitBreaker
            return new YamlFragment(
                    parent.before() + indented(dedented, parent.indent(), parent.indent()),
                    (int) parent.before().chars().filter(c -> c == '\n').count(), parent.items());
        }
        // steps (- to:, - log:), or one EIP (idempotentConsumer:): the steps of a route
        return item
                ? new YamlFragment(ROUTE + indented(dedented, "        ", "        "), 4, STEP_ITEMS)
                : new YamlFragment(ROUTE + indented(dedented, "        - ", "          "), 4, STEP_ITEMS);
    }

    /**
     * The lines with first in front of the first line that has a key and other in front of the others, so a mapping
     * becomes an item of a list, or a list moves in under the key that holds it. The lines stay where they are, so a
     * line of the content is a line of the block plus {@link #shift()}.
     */
    private static String indented(String[] lines, String first, String other) {
        StringBuilder sb = new StringBuilder();
        boolean keyed = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.isEmpty()) {
                boolean key = !keyed && !line.isBlank() && !line.strip().startsWith("#");
                sb.append(key ? first : other).append(line);
                keyed |= key;
            }
            if (i < lines.length - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
