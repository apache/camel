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
package org.apache.camel.dsl.yaml.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The YAML route examples of an AsciiDoc page, for everything that reads the documentation: the doc-samples plugin and
 * the tests that validate every example in the repository.
 * <p/>
 * A page that shows what to avoid - the to-eip page shows a {@code to:} with an expression in its uri to say why
 * {@code toD} exists - marks the block with {@value #SKIP_MARKER} on a line before it, and it is then neither validated
 * nor offered as a sample (CAMEL-24917).
 */
public final class DocBlocks {

    /** The AsciiDoc comment that keeps a block out of the examples. */
    public static final String SKIP_MARKER = "// yaml-validator: skip";

    /**
     * A YAML listing block: four or more dashes, closed by the same line. A delimiter longer than four dashes is valid
     * AsciiDoc and common in older pages, and their examples were not read (CAMEL-25736).
     */
    private static final Pattern YAML_BLOCK = Pattern.compile("\\[source,yaml\\]\\s*\\n(-{4,})\\n(.*?)\\n\\1(?=\\n|$)",
            Pattern.DOTALL);

    /** An AsciiDoc callout marker at the end of a line, which is documentation and not part of the route. */
    private static final Pattern CALLOUT = Pattern.compile("[ \\t]*#[ \\t]*<\\d+>[ \\t]*$", Pattern.MULTILINE);

    /** How far back the marker is looked for, enough for the tabs and titles between it and the block. */
    private static final int MARKER_LOOKBEHIND = 200;

    private DocBlocks() {
    }

    /**
     * The route examples of the page: the YAML blocks that start with a top-level list entry (after the comment lines
     * that may come first), without their callout markers, and without the blocks that carry {@value #SKIP_MARKER}.
     */
    public static List<String> examples(String doc) {
        List<String> answer = new ArrayList<>();
        if (doc == null) {
            return answer;
        }
        Matcher m = YAML_BLOCK.matcher(doc);
        while (m.find()) {
            String yaml = CALLOUT.matcher(m.group(2)).replaceAll("").stripTrailing() + "\n";
            if (startsWithEntry(yaml) && !markedToSkip(doc, m.start())) {
                answer.add(yaml);
            }
        }
        return answer;
    }

    /** The entries a YAML DSL file is a list of. */
    private static final Set<String> TOP_LEVEL = Set.of("beans", "dataFormats", "errorHandler", "from", "intercept",
            "interceptFrom", "interceptSendToEndpoint", "kamelet", "onCompletion", "onException", "rest",
            "restConfiguration", "route", "route-configuration", "route-template", "routeConfiguration", "routeTemplate",
            "sslContextParameters", "templated-route", "templatedRoute", "transformers", "validators");

    /** The key of a list entry, as route in "- route:". */
    private static final Pattern ENTRY_KEY = Pattern.compile("- ([\\w-]+):");

    /**
     * Whether the YAML is a route file: its first line that is not blank or a comment is a list entry. After a comment
     * it must be a top-level entry: a block that opens with "# send the dataset to a queue" and then - route: is a
     * route file, and was skipped (CAMEL-25736), while a list of steps with a comment above it is a fragment of a
     * route.
     */
    static boolean startsWithEntry(String yaml) {
        boolean comment = false;
        for (String line : yaml.split("\n")) {
            String s = line.strip();
            if (s.isEmpty()) {
                continue;
            }
            if (s.startsWith("#")) {
                comment = true;
                continue;
            }
            if (!s.startsWith("- ")) {
                return false;
            }
            if (!comment) {
                return true;
            }
            Matcher m = ENTRY_KEY.matcher(s);
            return m.lookingAt() && TOP_LEVEL.contains(m.group(1));
        }
        return false;
    }

    /** Whether the block at this offset carries {@link #SKIP_MARKER} in the lines before it. */
    public static boolean markedToSkip(String doc, int blockStart) {
        int from = Math.max(0, blockStart - MARKER_LOOKBEHIND);
        return doc.substring(from, blockStart).contains(SKIP_MARKER);
    }
}
