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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An edit or write is refused for the problems it brings, not for the ones the file already had: an edit that fixes one
 * problem of a file with two is written.
 */
class AuthoringToolsExistingProblemsTest {

    private static final String ROUTE = """
            import org.apache.camel.builder.RouteBuilder;

            public class MyRoute extends RouteBuilder {
                @Override
                public void configure() throws Exception {
                    from("timer:tick?peroid=1000")
                        .to("seda:out?siz=10");
                }
            }
            """;

    @TempDir
    Path dir;

    @Test
    void anEditThatFixesOneOfTwoProblemsIsWritten() throws Exception {
        Files.writeString(dir.resolve("MyRoute.java"), ROUTE);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, "MyRoute.java", "siz=10", "size=10");
        assertThat(result.getString("status")).isEqualTo("edited");
        assertThat(Files.readString(dir.resolve("MyRoute.java"))).contains("size=10");
        // the problem the file still has is said, so it is not taken for fixed
        JsonArray existing = (JsonArray) result.get("existingProblems");
        assertThat(existing).hasSize(1);
        assertThat(existing.getString(0)).contains("peroid");
    }

    @Test
    void anEditThatBringsAProblemIsRefused() throws Exception {
        Files.writeString(dir.resolve("MyRoute.java"), ROUTE);
        JsonObject result = AuthoringTools.editFile(new ToolContext(), dir, "MyRoute.java", "seda:out", "seda:out2?fooBar=1&x");
        assertThat(result.getString("status")).isEqualTo("invalid");
        assertThat(result.toJson()).contains("fooBar");
        assertThat(Files.readString(dir.resolve("MyRoute.java"))).isEqualTo(ROUTE);
    }

    @Test
    void aProblemIsTheSameWhereverItsLineMoved() {
        List<String> stillThere = new ArrayList<>();
        List<String> added = AuthoringTools.newProblems(
                List.of("Line 6: timer: Unknown option 'peroid'", "Line 7: x"),
                List.of("Line 8: timer: Unknown option 'peroid'", "Line 9: y"), stillThere);
        assertThat(added).containsExactly("Line 9: y");
        assertThat(stillThere).containsExactly("Line 8: timer: Unknown option 'peroid'");
    }
}
