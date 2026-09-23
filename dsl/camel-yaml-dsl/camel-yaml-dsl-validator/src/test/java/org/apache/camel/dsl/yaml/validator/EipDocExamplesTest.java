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
import java.util.Map;
import java.util.function.Predicate;

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The YAML route examples of the documentation bundled in the catalog must pass the YAML validator, one test per page
 * family (CAMEL-24693, CAMEL-24710): the examples are what people copy and what the camel_catalog_sample tool returns,
 * so a wrong one in the docs fails the build here.
 */
class EipDocExamplesTest {

    /** The pattern pages without the -eip suffix that carry YAML examples. */
    private static final List<String> PATTERN_PAGES = List.of(
            "channel-adapter", "competing-consumers", "composed-message-processor", "content-enricher",
            "correlation-identifier", "dead-letter-channel", "durable-subscriber", "event-message", "guaranteed-delivery",
            "intercept", "keyValueRepository", "message-bus", "message-channel", "message-endpoint", "message-expiration",
            "message-history", "message-router", "message-translator", "messaging-bridge", "normalizer",
            "point-to-point-channel", "publish-subscribe-channel", "return-address", "scatter-gather",
            "selective-consumer", "service-activator", "transactional-client");

    /**
     * Examples that show YAML the schema cannot know, by page and a text found in the example: the yaml-dsl page shows
     * a step contributed by a custom YAML deserializer.
     */
    private static final Map<String, String> EXAMPLES_SKIPPED = Map.of("yaml-dsl", "myStep:");

    private static CamelCatalog catalog;
    private static YamlValidator validator;

    private record DocExamples(int examples, List<String> failures) {
    }

    @BeforeAll
    static void setup() throws Exception {
        catalog = new DefaultCamelCatalog();
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    void everyYamlExampleOfTheEipDocumentationValidates() throws Exception {
        List<String> pages = docNames(name -> name.endsWith("-eip"));
        pages.addAll(PATTERN_PAGES);

        DocExamples result = validate(pages);

        assertThat(result.examples()).as("YAML route examples found in the EIP documentation").isGreaterThan(250);
        assertThat(result.failures()).as("EIP documentation examples that do not validate").isEmpty();
    }

    @Test
    void everyYamlExampleOfTheOtherDocumentationPagesValidates() throws Exception {
        List<String> pages = docNames(name -> !name.endsWith("-eip") && !PATTERN_PAGES.contains(name)
                && !name.endsWith("-component") && !name.endsWith("-dataformat"));

        DocExamples result = validate(pages);

        assertThat(result.examples()).as("YAML route examples found in the other documentation pages").isGreaterThan(200);
        assertThat(result.failures()).as("Documentation examples of the other pages that do not validate").isEmpty();
    }

    @Test
    void everyYamlExampleOfTheDataFormatDocumentationValidates() throws Exception {
        List<String> pages = docNames(name -> name.endsWith("-dataformat"));

        DocExamples result = validate(pages);

        assertThat(result.examples()).as("YAML route examples found in the data format documentation").isGreaterThan(90);
        assertThat(result.failures()).as("Data format documentation examples that do not validate").isEmpty();
    }

    @Test
    void everyYamlExampleOfTheComponentDocumentationValidates() throws Exception {
        List<String> pages = docNames(name -> name.endsWith("-component"));

        DocExamples result = validate(pages);

        assertThat(result.examples()).as("YAML route examples found in the component documentation").isGreaterThan(1100);
        assertThat(result.failures()).as("Component documentation examples that do not validate").isEmpty();
    }

    private static List<String> docNames(Predicate<String> filter) {
        List<String> pages = new ArrayList<>();
        for (String name : catalog.findDocNames()) {
            if (filter.test(name)) {
                pages.add(name);
            }
        }
        return pages;
    }

    private static DocExamples validate(List<String> pages) throws Exception {
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
                List<Error> errors = validator.validate(yaml);
                if (!errors.isEmpty()) {
                    failures.add(page + " example " + (n + 1) + ": " + errors.get(0).getMessage());
                }
            }
        }
        return new DocExamples(examples, failures);
    }
}
