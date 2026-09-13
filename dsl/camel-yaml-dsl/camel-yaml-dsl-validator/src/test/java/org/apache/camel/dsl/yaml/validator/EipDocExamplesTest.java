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

import com.networknt.schema.Error;
import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every YAML route example in the EIP documentation must pass the YAML validator (CAMEL-24693): the examples are what
 * people copy and what the camel_catalog_sample tool returns, so a wrong one in the docs fails the build here.
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

    private static final Pattern YAML_BLOCK = Pattern.compile("\\[source,yaml\\]\\n-{4}\\n(.*?)\\n-{4}", Pattern.DOTALL);

    @Test
    void everyYamlExampleOfTheEipDocumentationValidates() throws Exception {
        CamelCatalog catalog = new DefaultCamelCatalog();
        YamlValidator validator = new YamlValidator();
        validator.init();

        List<String> pages = new ArrayList<>();
        for (String name : catalog.findDocNames()) {
            if (name.endsWith("-eip")) {
                pages.add(name);
            }
        }
        pages.addAll(PATTERN_PAGES);

        int examples = 0;
        List<String> failures = new ArrayList<>();
        for (String page : pages) {
            String doc = catalog.asciiDoc(page);
            if (doc == null) {
                continue;
            }
            int n = 0;
            Matcher m = YAML_BLOCK.matcher(doc);
            while (m.find()) {
                String yaml = m.group(1).stripTrailing() + "\n";
                if (!yaml.stripLeading().startsWith("- ")) {
                    // a fragment (an option list, a snippet), not a route file
                    continue;
                }
                examples++;
                n++;
                List<Error> errors = validator.validate(yaml);
                if (!errors.isEmpty()) {
                    failures.add(page + " example " + n + ": " + errors.get(0).getMessage());
                }
            }
        }

        assertThat(examples).as("YAML route examples found in the EIP documentation").isGreaterThan(250);
        assertThat(failures).as("EIP documentation examples that do not validate").isEmpty();
    }
}
