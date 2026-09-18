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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The YAML route examples of the user manual must pass the YAML validator (CAMEL-24774), as the examples of the pages
 * bundled in the catalog do in {@link EipDocExamplesTest}: the manual is not in the catalog, so its pages are read from
 * the source tree when the tests run inside the Camel repository.
 * <p/>
 * A block is a route example when its first entry is one of the roots of the YAML DSL schema ({@code - route:},
 * {@code - beans:}, {@code - rest:}...); a step written on its own ({@code - setBody:}) or the YAML of another tool is
 * not judged. The upgrade and migration guides show the syntax of older releases on purpose and are left out.
 */
class UserManualDocExamplesTest {

    private static final Path PAGES = Path.of("docs", "user-manual", "modules", "ROOT", "pages");
    private static final Pattern YAML_BLOCK = Pattern.compile("\\[source,yaml\\]\\n-{4,}\\n(.*?)\\n-{4,}", Pattern.DOTALL);
    private static final Pattern FIRST_KEY = Pattern.compile("^\\s*-\\s+([A-Za-z]+)\\s*:");

    private static YamlValidator validator;
    private static Set<String> roots;

    private record DocExamples(int examples, List<String> failures) {
    }

    @BeforeAll
    static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
        roots = new HashSet<>();
        try (InputStream is = YamlValidator.class.getResourceAsStream("/schema/camelYamlDsl.json")) {
            JsonNode schema = new ObjectMapper().readTree(is);
            schema.path("items").path("properties").fieldNames().forEachRemaining(roots::add);
        }
    }

    @Test
    void everyYamlRouteExampleOfTheUserManualValidates() throws Exception {
        Map<String, String> pages = currentPages();
        assumeTrue(!pages.isEmpty(), "the user manual is only checked inside the Camel source tree");

        DocExamples result = validate(pages);

        assertThat(roots).as("the roots of the YAML DSL schema").contains("route", "beans", "rest", "routeTemplate");
        assertThat(result.examples()).as("YAML route examples found in the user manual").isGreaterThan(150);
        assertThat(result.failures()).as("User manual examples that do not validate").isEmpty();
    }

    @Test
    void theCheckSeesWhatItIsFor() throws Exception {
        String page
                = "[source,yaml]\n----\n- route:\n    from:\n      uri: direct:a\n      steps:\n        - to:\n            uri: mock:b\n----\n"
                  + "[source,yaml]\n----\n- route:\n    templateParameters:\n      - name: x\n----\n"
                  + "[source,yaml]\n----\n- setBody:\n    simple: hello\n----\n"
                  + "[source,yaml]\n----\n- require: '@antora/lunr'\n----\n";

        DocExamples result = validate(Map.of("fake", page));

        assertThat(result.examples()).as("the two route examples, not the step or the Antora entry").isEqualTo(2);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.failures().get(0)).startsWith("fake.adoc:12 example 2:");
    }

    private static DocExamples validate(Map<String, String> pages) throws Exception {
        int examples = 0;
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> entry : pages.entrySet()) {
            String page = entry.getKey();
            String doc = entry.getValue();
            int n = 0;
            Matcher m = YAML_BLOCK.matcher(doc);
            while (m.find()) {
                String yaml = m.group(1).stripTrailing() + "\n";
                Matcher key = FIRST_KEY.matcher(yaml.stripLeading());
                if (!key.find() || !roots.contains(key.group(1))) {
                    // a step or an option fragment, or the YAML of another tool: not a route file
                    continue;
                }
                n++;
                examples++;
                List<Error> errors = validator.validate(yaml);
                if (!errors.isEmpty()) {
                    int line = 1 + countLines(doc, m.start(1));
                    failures.add(page + ".adoc:" + line + " example " + n + ": " + errors.get(0).getMessage());
                }
            }
        }
        return new DocExamples(examples, failures);
    }

    /**
     * The pages of the user manual that document the current release, by name, or an empty map outside the source tree.
     * The source tree is found as {@code UserManualPages.repositoryRoot()} in the camel-catalog tests finds it: the
     * directory up the tree that holds both the user manual pages and the components; keep the two in step.
     */
    private static Map<String, String> currentPages() throws Exception {
        Map<String, String> answer = new TreeMap<>();
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !(Files.isDirectory(dir.resolve(PAGES)) && Files.isDirectory(dir.resolve("components")))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            return answer;
        }
        try (Stream<Path> files = Files.walk(dir.resolve(PAGES))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".adoc")).toList()) {
                String name = file.getFileName().toString();
                name = name.substring(0, name.length() - 5);
                if (name.contains("upgrade-guide") || name.contains("migration-guide")) {
                    continue;
                }
                answer.put(name, Files.readString(file));
            }
        }
        return answer;
    }

    private static int countLines(String text, int end) {
        int n = 0;
        for (int i = 0; i < end; i++) {
            if (text.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }
}
