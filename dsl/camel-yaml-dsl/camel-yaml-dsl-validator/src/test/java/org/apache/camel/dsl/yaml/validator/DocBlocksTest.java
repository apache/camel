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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25736: the route examples of a page include the blocks with a delimiter longer than four dashes, and the blocks
 * that open with a comment; 53 examples of the documentation were not read before.
 */
class DocBlocksTest {

    @Test
    void aLongerDelimiterAndALeadingCommentAreRead() {
        String doc = """
                = A page

                [source,yaml]
                --------------------------------------------------------------------------------
                - from:
                    uri: direct:search
                    steps:
                      - log:
                          message: "${body}"
                --------------------------------------------------------------------------------

                [source,yaml]
                ----
                # send the dataset to a queue
                - route:
                    from:
                      uri: dataset:foo
                      steps:
                        - to:
                            uri: seda:queue
                ----

                [source,yaml]
                ----
                key: a fragment, not a route
                ----

                [source,yaml]
                ----
                # the steps to add to the route: a fragment too
                - setHeader:
                    name: CamelJmsDestinationName
                    expression:
                      constant:
                        expression: "queue:///MY_QUEUE"
                ----
                """;
        List<String> examples = DocBlocks.examples(doc);
        assertEquals(2, examples.size(), String.valueOf(examples));
        assertTrue(examples.get(0).startsWith("- from:"), examples.get(0));
        assertTrue(examples.get(1).startsWith("# send the dataset to a queue\n- route:"), examples.get(1));
    }

    @Test
    void aBlockClosesAtItsOwnDelimiter() {
        // a shorter or a longer line of dashes inside the block is part of it
        String doc = "[source,yaml]\n--------\n- from:\n    uri: direct:a\n    steps:\n      - log:\n"
                     + "          message: x\n----\n----------\n--------\nafter\n";
        List<String> examples = DocBlocks.examples(doc);
        assertEquals(1, examples.size());
        assertTrue(examples.get(0).endsWith("----\n----------\n"), examples.get(0));
    }
}
