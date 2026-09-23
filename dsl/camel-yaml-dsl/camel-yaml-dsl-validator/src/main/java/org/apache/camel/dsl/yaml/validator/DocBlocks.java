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

    private static final Pattern YAML_BLOCK = Pattern.compile("\\[source,yaml\\]\\s*\\n----\\n(.*?)\\n----",
            Pattern.DOTALL);

    /** An AsciiDoc callout marker at the end of a line, which is documentation and not part of the route. */
    private static final Pattern CALLOUT = Pattern.compile("[ \\t]*#[ \\t]*<\\d+>[ \\t]*$", Pattern.MULTILINE);

    /** How far back the marker is looked for, enough for the tabs and titles between it and the block. */
    private static final int MARKER_LOOKBEHIND = 200;

    private DocBlocks() {
    }

    /**
     * The route examples of the page: the YAML blocks that start with a top-level list entry, without their callout
     * markers, and without the blocks that carry {@value #SKIP_MARKER}.
     */
    public static List<String> examples(String doc) {
        List<String> answer = new ArrayList<>();
        if (doc == null) {
            return answer;
        }
        Matcher m = YAML_BLOCK.matcher(doc);
        while (m.find()) {
            String yaml = CALLOUT.matcher(m.group(1)).replaceAll("").stripTrailing() + "\n";
            if (yaml.stripLeading().startsWith("- ") && !markedToSkip(doc, m.start())) {
                answer.add(yaml);
            }
        }
        return answer;
    }

    /** Whether the block at this offset carries {@link #SKIP_MARKER} in the lines before it. */
    public static boolean markedToSkip(String doc, int blockStart) {
        int from = Math.max(0, blockStart - MARKER_LOOKBEHIND);
        return doc.substring(from, blockStart).contains(SKIP_MARKER);
    }
}
