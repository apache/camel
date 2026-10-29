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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * CAMEL-26399: the argument names models carry over from another tool are taken for the names the tool declares, and a
 * call that still misses one is answered with the arguments the tool takes.
 */
class ToolArgumentAliasesTest {

    private static ToolArgumentAliases.Resolved<Object> resolve(String tool, Object... pairs) {
        Map<String, Object> args = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            args.put((String) pairs[i], pairs[i + 1]);
        }
        return ToolArgumentAliases.resolve(tool, ToolArgumentAliases.arguments(ToolRegistry.findTool(tool)), args);
    }

    @Test
    void queryAndNameAreTheTermOfCatalogFind() {
        ToolArgumentAliases.Resolved<Object> query = resolve("camel_catalog_find", "query", "activemq classic 6 jms");
        assertThat(query.arguments()).containsExactly(entry("term", "activemq classic 6 jms"));
        assertThat(query.missing()).isEmpty();
        assertThat(query.note()).isEqualTo("term is the argument name (got query)");

        ToolArgumentAliases.Resolved<Object> name = resolve("camel_catalog_find", "name", "groovy", "kind", "language");
        assertThat(name.arguments()).containsExactly(entry("term", "groovy"), entry("kind", "language"));
        assertThat(name.note()).isEqualTo("term is the argument name (got name)");
    }

    @Test
    void nameIsTheExampleOfExampleFile() {
        // get_example_file is the registry tool behind camel_catalog_example_file, as camel ask calls it
        ToolArgumentAliases.Resolved<Object> resolved = resolve("get_example_file", "name", "contracts/rest-code-first",
                "file", "rest-code-first.camel.yaml");

        assertThat(resolved.arguments()).containsExactly(entry("example", "contracts/rest-code-first"),
                entry("file", "rest-code-first.camel.yaml"));
        assertThat(resolved.missing()).isEmpty();
        assertThat(resolved.note()).isEqualTo("example is the argument name (got name)");
    }

    @Test
    void nameFilenameAndPathAreTheFileAndSourceTheContentOfValidateSource() {
        assertThat(resolve("camel_validate_source", "name", "hr-api-call.yaml", "content", "- route:").arguments())
                .containsExactly(entry("file", "hr-api-call.yaml"), entry("content", "- route:"));

        ToolArgumentAliases.Resolved<Object> both = resolve("camel_validate_source", "filename", "routes.yaml",
                "source", "- route:");
        assertThat(both.arguments()).containsExactly(entry("file", "routes.yaml"), entry("content", "- route:"));
        assertThat(both.note()).isEqualTo("file is the argument name (got filename); content is the argument name"
                                          + " (got source)");

        // an absolute path is only taken for the file: resolving it is the tool's business
        assertThat(resolve("camel_validate_source", "path", "/workspace/camel/salesleads.yaml").arguments())
                .containsExactly(entry("file", "/workspace/camel/salesleads.yaml"));
    }

    @Test
    void theFirstAliasOfAnArgumentWinsAndTheOthersAreIgnored() {
        // a path says more than a file name
        ToolArgumentAliases.Resolved<Object> resolved = resolve("camel_validate_source",
                "filename", "salesleads.yaml", "path", "/workspace/camel/salesleads.yaml");

        assertThat(resolved.arguments()).containsEntry("file", "/workspace/camel/salesleads.yaml");
        assertThat(resolved.renamed()).containsExactly(entry("file", "path"));
        assertThat(resolved.ignored()).containsExactly("filename");
        assertThat(resolved.note()).isEqualTo("file is the argument name (got path); ignored: filename;"
                                              + " camel_validate_source takes directory, file (required), content,"
                                              + " camelVersion");
    }

    @Test
    void theDeclaredArgumentWinsOverItsAlias() {
        ToolArgumentAliases.Resolved<Object> resolved = resolve("camel_catalog_find", "term", "mqtt", "query", "s3");

        assertThat(resolved.arguments()).containsEntry("term", "mqtt");
        assertThat(resolved.renamed()).isEmpty();
        assertThat(resolved.ignored()).containsExactly("query");
    }

    @Test
    void aNullArgumentIsNotGiven() {
        Map<String, Object> args = new HashMap<>();
        args.put("term", null);
        args.put("query", "mqtt");

        ToolArgumentAliases.Resolved<Object> resolved = ToolArgumentAliases.resolve("camel_catalog_find",
                ToolArgumentAliases.arguments(ToolRegistry.findTool("camel_catalog_find")), args);

        assertThat(resolved.arguments()).containsExactly(entry("term", "mqtt"));
        assertThat(resolved.missing()).isEmpty();
    }

    @Test
    void anAliasThatIsAnArgumentOfTheToolIsLeftAlone() {
        // camel_catalog_doc takes name: it has no alias, and its name is never taken for anything else
        ToolArgumentAliases.Resolved<Object> doc = resolve("camel_catalog_doc", "name", "kafka");
        assertThat(doc.arguments()).containsExactly(entry("name", "kafka"));
        assertThat(doc.note()).isNull();

        // a tool of the table that declares one of its aliases keeps it as its own argument
        List<ToolArgumentAliases.Argument> declared = List.of(new ToolArgumentAliases.Argument("term", true),
                new ToolArgumentAliases.Argument("name", false));
        ToolArgumentAliases.Resolved<String> find = ToolArgumentAliases.resolve("camel_catalog_find", declared,
                Map.of("name", "groovy"));
        assertThat(find.arguments()).containsExactly(entry("name", "groovy"));
        assertThat(find.missing()).containsExactly("term");
    }

    @Test
    void theDeclaredNamesNeedNoNote() {
        ToolArgumentAliases.Resolved<Object> resolved = resolve("camel_catalog_find", "term", "mqtt", "limit", 1);

        assertThat(resolved.renamed()).isEmpty();
        assertThat(resolved.ignored()).isEmpty();
        assertThat(resolved.note()).isNull();
    }

    @Test
    void anUnknownArgumentIsNamedWithTheArgumentsOfTheTool() {
        ToolArgumentAliases.Resolved<Object> resolved = resolve("camel_catalog_find", "term", "mqtt", "limit", 1,
                "bogus", "x");

        assertThat(resolved.missing()).isEmpty();
        assertThat(resolved.note())
                .isEqualTo("ignored: bogus; camel_catalog_find takes term (required), kind, limit, camelVersion");
    }

    @Test
    void aMissingArgumentIsAnsweredWithTheArgumentsOfTheTool() {
        ToolArgumentAliases.Resolved<Object> unknown = resolve("camel_catalog_find", "q", "mqtt");
        assertThat(unknown.missing()).containsExactly("term");
        assertThat(unknown.missingMessage())
                .isEqualTo("camel_catalog_find takes term (required), kind, limit, camelVersion; got q");

        ToolArgumentAliases.Resolved<Object> none = resolve("get_example_file");
        assertThat(none.missing()).containsExactly("example", "file");
        assertThat(none.missingMessage()).isEqualTo("get_example_file takes example (required), file (required);"
                                                    + " got no arguments");

        // the names the call gave, aliases included
        assertThat(resolve("get_example_file", "name", "timer-log").missingMessage())
                .isEqualTo("get_example_file takes example (required), file (required); got name");
    }

    @Test
    void theAliasesNameArgumentsOfTheRegistryTools() {
        for (String tool : List.of("camel_catalog_find", "get_example_file", "camel_validate_source")) {
            List<String> names = ToolArgumentAliases.arguments(ToolRegistry.findTool(tool)).stream()
                    .map(ToolArgumentAliases.Argument::name).toList();
            assertThat(ToolArgumentAliases.aliases(tool)).as(tool).isNotEmpty().allSatisfy((alias, name) -> {
                assertThat(names).as(tool + " declares " + name).contains(name);
                assertThat(names).as(tool + " does not declare " + alias).doesNotContain(alias);
            });
        }
    }

    @Test
    void theNoteIsTheFirstFieldOfAJsonObjectAnswer() throws Exception {
        String answer = ToolArgumentAliases.withNote("{\"term\":\"mqtt\",\"matches\":[]}", "term is the argument name");

        JsonObject json = (JsonObject) Jsoner.deserialize(answer);
        assertThat(json.keySet()).containsExactly(ToolArgumentAliases.NOTE_KEY, "term", "matches");
        assertThat(json.getString(ToolArgumentAliases.NOTE_KEY)).isEqualTo("term is the argument name");

        // other answers are left as they are
        assertThat(ToolArgumentAliases.withNote("[1, 2]", "note")).isEqualTo("[1, 2]");
        assertThat(ToolArgumentAliases.withNote("No running Camel processes found", "note"))
                .isEqualTo("No running Camel processes found");
        assertThat(ToolArgumentAliases.withNote("{not json", "note")).isEqualTo("{not json");
        assertThat(ToolArgumentAliases.withNote("{\"a\":1}", null)).isEqualTo("{\"a\":1}");
    }
}
