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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.yaml.validator.DocBlocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24710: every YAML route example of the documentation passes the checks {@code camel validate yaml} and the
 * authoring tools run after the schema: endpoint URIs and their options, Simple expressions and Camel headers against
 * the catalog. The schema itself is guarded by EipDocExamplesTest in camel-yaml-dsl-validator.
 */
class CatalogDocExamplesTest {

    /**
     * Pages whose examples are right for the runtime but fail the catalog because the component metadata cannot
     * describe what the component accepts (the rest of CAMEL-24748 is fixed).
     */
    private static final Map<String, String> PAGES_SKIPPED = Map.ofEntries(
            Map.entry("olingo2-component", "deprecated; the syntax is apiName/methodName, the runtime reads"
                                           + " methodName/resourcePath with the api name implicit, and any unknown"
                                           + " option is an OData query parameter"),
            Map.entry("olingo4-component", "deprecated; the syntax is apiName/methodName, the runtime reads"
                                           + " methodName/resourcePath with the api name implicit, and any unknown"
                                           + " option is an OData query parameter"));

    /** Examples that show what only the runtime knows, by page and a text found in the example. */
    private static final Map<String, String> EXAMPLES_SKIPPED = Map.of(
            "simple-advanced", "${foo", // a custom simple function registered at runtime
            "yaml-dsl", "myStep:"); // a step contributed by a custom YAML deserializer

    private static CamelCatalog catalog;

    private record DocExamples(int examples, List<String> failures) {
    }

    @BeforeAll
    static void setup() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void everyYamlExampleOfTheDocumentationPassesTheCatalogChecks() {
        List<String> pages = new ArrayList<>();
        for (String name : catalog.findDocNames()) {
            if (!PAGES_SKIPPED.containsKey(name)) {
                pages.add(name);
            }
        }
        DocExamples result = validate(pages);
        assertThat(result.examples()).as("YAML route examples found in the documentation").isGreaterThan(1700);
        assertThat(result.failures()).as("Documentation examples that fail the catalog checks").isEmpty();
    }

    @Test
    void theSkippedPagesStillExist() {
        for (String page : PAGES_SKIPPED.keySet()) {
            assertThat(catalog.asciiDoc(page)).as("the skipped page %s (drop the entry when it is gone)", page).isNotNull();
        }
    }

    private static DocExamples validate(List<String> pages) {
        int examples = 0;
        List<String> failures = new ArrayList<>();
        for (String page : pages) {
            String doc = catalog.asciiDoc(page);
            if (doc == null) {
                continue;
            }
            List<String> blocks = DocBlocks.examples(doc);
            for (int n = 0; n < blocks.size(); n++) {
                String yaml = blocks.get(n);
                String skipped = EXAMPLES_SKIPPED.get(page);
                if (skipped != null && yaml.contains(skipped)) {
                    continue;
                }
                examples++;
                for (String msg : SourceValidator.validateCamelYaml(yaml, catalog)) {
                    failures.add(page + " example " + (n + 1) + ": " + msg);
                }
            }
        }
        return new DocExamples(examples, failures);
    }
}
