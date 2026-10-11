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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A model that does not see the problem in its own lines writes them again, as it was told: the lines to write for an
 * option at the column of its EIP, and a write the same as the last refused one says so.
 */
class AuthoringToolsLoopTest {

    private static final String FILE = "demo.camel.yaml";

    // as the local model wrote it sixteen times in a row: steps: at the column of split:
    private static final String STEPS_NEXT_TO_SPLIT = """
            - route:
                from:
                  uri: timer:orders
                  steps:
                    - split:
                        expression:
                          simple: "${body}"
                      steps:
                        - log:
                            message: "pick ${body}"
            """;

    @Test
    void theHintShowsTheLinesToWrite() {
        List<String> errors = SourceValidator.validate(FILE, STEPS_NEXT_TO_SPLIT, new DefaultCamelCatalog(), null);
        assertThat(errors).singleElement().satisfies(m -> assertThat(m)
                .contains("steps: lines up with split:")
                .endsWith(" Write lines 8-10 as:\n"
                          + "            steps:\n"
                          + "              - log:\n"
                          + "                  message: \"pick ${body}\""));
    }

    @Test
    void theSameRefusedContentAgainIsSaidToBeTheSame(@TempDir Path dir) throws IOException {
        JsonObject first = write(dir, STEPS_NEXT_TO_SPLIT);
        assertThat(first.getString("status")).isEqualTo("invalid");
        assertThat(first.get("repeated")).isNull();

        JsonObject second = write(dir, STEPS_NEXT_TO_SPLIT);
        assertThat(second.getInteger("repeated")).isEqualTo(2);
        assertThat(second.getString("message"))
                .contains("the same as your last write of it, refused 2 times now")
                .contains("where an error shows the lines to write, copy them");
        assertThat(write(dir, STEPS_NEXT_TO_SPLIT).getInteger("repeated")).isEqualTo(3);

        // other content starts again, and a write that goes through forgets it
        assertThat(write(dir, STEPS_NEXT_TO_SPLIT.replace("pick", "take")).get("repeated")).isNull();
        assertThat(write(dir,
                STEPS_NEXT_TO_SPLIT.replace("      steps:\n            - log:\n                message: \"pick ${body}\"",
                        "        steps:\n              - log:\n                  message: \"pick ${body}\""))
                .getString("status")).isIn("created", "overwritten");
        assertThat(write(dir, STEPS_NEXT_TO_SPLIT).get("repeated")).isNull();
    }

    @Test
    void theSameRefusedEditAgainIsSaidToBeTheSame(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve(FILE), """
                - route:
                    from:
                      uri: timer:orders
                      steps:
                        - log:
                            message: "order ${body}"
                """);
        String find = "        - log:\n            message: \"order ${body}\"";
        String replace = """
                - split:
                    expression:
                      simple: "${body}"
                  steps:
                    - log:
                        message: "pick ${body}\"""";
        assertThat(edit(dir, find, replace).getString("message")).startsWith("The file was not changed: the result");
        JsonObject again = edit(dir, find, replace);
        assertThat(again.getInteger("repeated")).isEqualTo(2);
        assertThat(again.getString("message")).contains("the edit gives the same content as your last attempt");
    }

    private static JsonObject write(Path dir, String content) {
        return AuthoringTools.writeFile(new ToolContext(), dir, FILE, content, true);
    }

    private static JsonObject edit(Path dir, String find, String replace) {
        return AuthoringTools.editFile(new ToolContext(), dir, FILE, find, replace);
    }
}
