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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The kamelet: endpoints of a route against the Kamelets they name, and the Kamelets in the catalog tools: what a model
 * got wrong writing Kamelets in the Kamelet side check of the local model benchmark.
 */
class KameletChecksTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void setUp() {
        catalog = new DefaultCamelCatalog();
        Map<String, KameletDefinitions.Definition> kamelets = new LinkedHashMap<>();
        kamelets.put("timer-source", new KameletDefinitions.Definition(
                "timer-source", "source", null,
                "Produces periodic messages with a custom payload.", "the Kamelet catalog test", List.of(
                        prop("period", false, "integer", "1000"),
                        prop("message", true, "string", null),
                        prop("contentType", false, "string", "text/plain"),
                        prop("repeatCount", false, "integer", null))));
        kamelets.put("kafka-source", new KameletDefinitions.Definition(
                "kafka-source", "source", null,
                "Receive data from Kafka topics.", "the Kamelet catalog test", List.of(
                        prop("topic", true, "string", null),
                        prop("bootstrapServers", true, "string", null),
                        prop("saslAuthType", false, "string", "NONE"))));
        kamelets.put("kafka-sink", new KameletDefinitions.Definition(
                "kafka-sink", "sink", null,
                "Send data to Kafka topics.", "the Kamelet catalog test", List.of(
                        prop("topic", true, "string", null),
                        prop("bootstrapServers", true, "string", null))));
        kamelets.put("log-sink", new KameletDefinitions.Definition(
                "log-sink", "sink", null,
                "Log data to the console.", "the Kamelet catalog test", List.of(
                        prop("showHeaders", false, "boolean", "false"),
                        prop("showStreams", false, "boolean", "false"))));
        KameletDefinitions.setTestCatalog(kamelets);
    }

    @AfterAll
    static void tearDown() {
        KameletDefinitions.setTestCatalog(null);
    }

    private static KameletDefinitions.Property prop(String name, boolean required, String type, String defaultValue) {
        return new KameletDefinitions.Property(name, required, type, defaultValue, null, List.of());
    }

    @Test
    void rightKameletsHaveNoErrors() {
        String yaml = """
                - route:
                    from:
                      uri: kamelet:timer-source
                      parameters:
                        period: 2000
                        message: hello
                      steps:
                        - to:
                            uri: kamelet:kafka-sink
                            parameters:
                              topic: orders
                              bootstrapServers: localhost:9092
                        - to: kamelet:log-sink
                """;
        assertThat(KameletChecks.validateYaml(yaml, null)).isEmpty();
    }

    @Test
    void aKameletThatIsGone() {
        // kafka-not-secured-source was folded into kafka-source (saslAuthType NONE); a model still writes it
        String yaml = """
                - route:
                    from:
                      uri: kamelet:kafka-not-secured-source
                      parameters:
                        topic: orders
                        bootstrapServers: localhost:9092
                      steps:
                        - to: kamelet:log-sink
                """;
        List<String> errors = KameletChecks.validateYaml(yaml, null);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 3: ")
                .contains("no Kamelet named kafka-not-secured-source")
                .contains("Did you mean: kafka-source");
    }

    @Test
    void anUnknownNameOnAWriteOnlyWhenCloseToAKnownOne() {
        // the project's own Kamelet may be the next file the model writes
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to: kamelet:tag-order-action
                        - to: kamelet:kafka-not-secured-sink
                """;
        List<String> errors = KameletChecks.validateYaml(yaml, null, false);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 6: ").contains("Did you mean: kafka-sink");
        assertThat(KameletChecks.validateYaml(yaml, null, true)).hasSize(2);
    }

    @Test
    void theOptionsOfTheComponentInsteadOfTheKamelet() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: kamelet:kafka-sink
                            parameters:
                              topic: orders
                              brokers: localhost:9092
                """;
        List<String> errors = KameletChecks.validateYaml(yaml, null);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).startsWith("Line 9: ")
                .contains("kamelet:kafka-sink: unknown property 'brokers'")
                .contains("The properties of kafka-sink: topic (required), bootstrapServers (required)");
        assertThat(errors.get(1)).startsWith("Line 6: ")
                .contains("the required property bootstrapServers is missing")
                .contains("mandatory parameters must be provided: bootstrapServers");
    }

    @Test
    void aMisspelledOptionalProperty() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to: kamelet:log-sink?showHeader=true
                """;
        List<String> errors = KameletChecks.validateYaml(yaml, null);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("unknown property 'showHeader'").contains("Did you mean: showHeaders");
    }

    @Test
    void aRequiredPropertyLeftOut() {
        String yaml = """
                - route:
                    from:
                      uri: kamelet:timer-source
                      parameters:
                        period: 2000
                        jsonBody: '{"a": 1}'
                      steps:
                        - to: kamelet:log-sink
                """;
        List<String> errors = KameletChecks.validateYaml(yaml, null);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("unknown property 'jsonBody'");
        assertThat(errors.get(1)).startsWith("Line 3: ").contains("the required property message is missing");
    }

    @Test
    void aRequiredPropertyFromApplicationProperties(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.properties"), "camel.kamelet.timer-source.message=hello\n");
        String yaml = """
                - route:
                    from:
                      uri: kamelet:timer-source
                      steps:
                        - to: kamelet:log-sink
                """;
        assertThat(KameletChecks.validateYaml(yaml, dir)).isEmpty();
    }

    @Test
    void theProjectsOwnKamelet(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("tag-order-action.kamelet.yaml"), """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: tag-order-action
                  labels:
                    camel.apache.org/kamelet.type: action
                spec:
                  definition:
                    title: Tag Order
                    required:
                      - tag
                    properties:
                      tag:
                        type: string
                      prefix:
                        type: string
                        default: "#"
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - setBody:
                            simple: "${body} [{{prefix}}{{tag}}]"
                """);
        String ok = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: kamelet:tag-order-action
                            parameters:
                              tag: priority
                """;
        assertThat(KameletChecks.validateYaml(ok, dir)).isEmpty();
        String wrong = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: kamelet:tag-order-action
                            parameters:
                              label: priority
                """;
        List<String> errors = KameletChecks.validateYaml(wrong, dir);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("unknown property 'label'")
                .contains("The properties of tag-order-action (the project file tag-order-action.kamelet.yaml): "
                          + "tag (required), prefix (default #)");
        assertThat(errors.get(1)).contains("the required property tag is missing");
    }

    private static final String TAG_KAMELET = """
            apiVersion: camel.apache.org/v1
            kind: Kamelet
            metadata:
              name: tag-order-action
              labels:
                camel.apache.org/kamelet.type: action
            spec:
              definition:
                title: Tag Order
                required:
                  - tag
                properties:
                  tag:
                    title: Tag
                    type: string
              template:
                from:
                  uri: kamelet:source
                  steps:
                    - setBody:
                        expression:
                          simple:
                            expression: "%s"
            """;

    @Test
    void aRightKameletFileIsValid(@TempDir Path dir) {
        String content = TAG_KAMELET.formatted("${body} [{{tag}}]");
        assertThat(SourceValidator.validate("tag-order-action.kamelet.yaml", content, catalog, null, dir)).isEmpty();
    }

    @Test
    void aKameletPropertyWrittenAsAFunction(@TempDir Path dir) {
        String content = TAG_KAMELET.formatted("${body} [${properties.tag}]");
        List<String> errors = SourceValidator.validate("tag-order-action.kamelet.yaml", content, catalog, null, dir);
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Unknown function: properties.tag")
                .contains("its property tag is the placeholder {{tag}}"));
    }

    @Test
    void aPropertyTheProjectsKameletLacks(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("tag-order-action.kamelet.yaml"), """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: tag-order-action
                spec:
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - to: kamelet:sink
                """);
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: kamelet:tag-order-action
                            parameters:
                              tag: priority
                """;
        List<String> errors = KameletChecks.validateYaml(yaml, dir);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("unknown property 'tag'").contains(": none")
                .contains("declared in its file under spec.definition.properties");
    }

    @Test
    void theEndsOfAKameletTemplateAreNotKamelets() {
        String yaml = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: my-action
                spec:
                  template:
                    from:
                      uri: kamelet:source
                      steps:
                        - to: kamelet:sink
                """;
        assertThat(KameletChecks.validateYaml(yaml, null)).isEmpty();
    }

    @Test
    void theSourceValidatorChecksKamelets(@TempDir Path dir) {
        String yaml = """
                - route:
                    from:
                      uri: kamelet:timer-source
                      parameters:
                        period: 2000
                      steps:
                        - to: kamelet:log-sink
                """;
        List<String> errors = SourceValidator.validate("orders.camel.yaml", yaml, catalog, null, dir);
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("the required property message is missing"));
    }

    @Test
    void theCatalogDocOfAKamelet() {
        JsonObject doc = CatalogDocs.catalogDoc(catalog, "kafka-source", null, null, null, null, false, false, null);
        assertThat(doc.getString("kind")).isEqualTo("kamelet");
        assertThat(doc.getString("yaml"))
                .startsWith("from: {uri: kamelet:kafka-source, parameters: {topic: <topic>, bootstrapServers:");
        assertThat(doc.toJson()).contains("\"name\":\"saslAuthType\"").contains("\"defaultValue\":\"NONE\"");
    }

    @Test
    void theCatalogDocSuggestsAKamelet() {
        JsonObject doc = CatalogDocs.catalogDoc(catalog, "kafka-not-secured-source", null, null, null, null, false,
                false, null);
        assertThat(doc.toJson()).contains("kafka-source");
    }

    @Test
    void theCatalogFindsKamelets() {
        JsonObject found = CatalogDocs.find(catalog, "kafka", "kamelet", 10);
        assertThat(found.toJson()).contains("\"kamelet:kafka-sink\"").contains("\"kamelet:kafka-source\"");
        JsonObject all = CatalogDocs.find(catalog, "timer source", null, 10);
        assertThat(all.toJson()).contains("\"kamelet:timer-source\"");
    }
}
