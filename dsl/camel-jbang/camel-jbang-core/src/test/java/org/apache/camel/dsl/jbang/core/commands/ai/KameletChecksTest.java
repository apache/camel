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
import static org.assertj.core.api.Assertions.assertThatCode;

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

    // CAMEL-25403: a camel: dependency the template does not use, as the model copied camel:timer from the source sample

    private static String withDependencies(String kamelet, String... deps) {
        StringBuilder sb = new StringBuilder("  dependencies:\n");
        for (String d : deps) {
            sb.append("    - \"").append(d).append("\"\n");
        }
        return kamelet.replace("  template:\n", sb + "  template:\n");
    }

    @Test
    void aDependencyTheTemplateDoesNotUseIsANote() {
        String content = withDependencies(TAG_KAMELET.formatted("${body} [{{tag}}]"), "camel:timer");
        assertThat(KameletChecks.unusedDependencies(content)).singleElement().asString()
                .contains("camel:timer is not used by the template")
                .contains("no timer: endpoint");
        // a note, not an error: the Kamelet works, so the write is not refused
        assertThat(KameletChecks.validateKameletFile(content)).isEmpty();
    }

    @Test
    void camelCoreIsImpliedAndCamelKameletIsNot() {
        String content = withDependencies(TAG_KAMELET.formatted("${body} [{{tag}}]"), "camel:core", "camel:kamelet");
        assertThat(KameletChecks.unusedDependencies(content)).singleElement().asString()
                .contains("camel:core is implied");
        // also when the template uses a bean, whose class may need the other dependencies
        String bean = withDependencies(TAG_KAMELET.formatted("${body}"), "camel:core", "camel:kafka")
                .replace("        - setBody:\n",
                        "        - bean:\n            beanType: org.example.HoistField\n        - setBody:\n");
        assertThat(KameletChecks.unusedDependencies(bean)).singleElement().asString()
                .contains("camel:core is implied");
    }

    @Test
    void anUnusedDependencyIsFixedByRemovingItsLine() {
        // told only what to list, a local model replaced camel:timer with kamelet:source: the note says to remove it,
        // and the fix removes the line
        String content = withDependencies(TAG_KAMELET.formatted("${body} [{{tag}}]"), "camel:kamelet", "camel:timer");
        String note = KameletChecks.unusedDependencies(content).get(0);
        assertThat(note).contains("camel:timer is not used by the template").contains("remove this line");
        QuickFixes.Fix fix = QuickFixes.fixFor(note, "    - \"camel:timer\"");
        assertThat(fix).isNotNull();
        assertThat(fix.removesLine()).isTrue();
        assertThat(fix.label()).isEqualTo("remove camel:timer");
        assertThat(QuickFixes.fixFor("spec.dependencies: camel:core is implied, every Camel runtime has it: remove this line",
                "    - camel:core").removesLine()).isTrue();
        // the fix as camel_validate_source gives it: the line and its line break go
        JsonObject result = AuthoringTools.validate(new ToolContext(), "tag-order-action.kamelet.yaml", content);
        assertThat(result.toJson()).contains("\"find\":\"    - \\\"camel:timer\\\"\\n\"").contains("\"replace\":\"\"");
    }

    @Test
    void theLastDependencyGoesWithTheDependenciesKey() {
        List<String> lines = List.of("spec:", "  dependencies:", "    - \"camel:timer\"", "  template:");
        assertThat(QuickFixes.linesToRemove(lines, 2)).containsExactly(1, 2);
        List<String> two = List.of("spec:", "  dependencies:", "    - \"camel:timer\"", "    - \"camel:kamelet\"");
        assertThat(QuickFixes.linesToRemove(two, 2)).containsExactly(2, 2);
        assertThat(QuickFixes.linesToRemove(two, 3)).containsExactly(3, 3);
        // the model's Kamelet, whose only dependency was camel:timer
        String content = withDependencies(TAG_KAMELET.formatted("${body} [{{tag}}]"), "camel:timer");
        JsonObject result = AuthoringTools.validate(new ToolContext(), "tag-order-action.kamelet.yaml", content);
        assertThat(result.toJson()).contains("\"find\":\"  dependencies:\\n    - \\\"camel:timer\\\"\\n\"");
    }

    @Test
    void theDependenciesTheTemplateUsesAreNotNoted() {
        String source = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: order-source
                  labels:
                    camel.apache.org/kamelet.type: source
                spec:
                  definition:
                    title: Order Source
                    properties:
                      period:
                        title: Period
                        type: integer
                        default: 5000
                  dependencies:
                    - "camel:timer"
                    - "camel:jq"
                    - "camel:http"
                    - "camel:kamelet"
                    - "mvn:org.example:orders:1.0"
                  template:
                    from:
                      uri: timer:orders
                      parameters:
                        period: "{{period}}"
                      steps:
                        - setBody:
                            expression:
                              jq:
                                expression: '.'
                        - to:
                            uri: https://example.com/orders
                        - to:
                            uri: kamelet:sink
                """;
        assertThat(KameletChecks.unusedDependencies(source)).isEmpty();
    }

    @Test
    void aTemplateThatMayUseAComponentUnseenIsNotNoted() {
        String placeholderScheme = withDependencies(TAG_KAMELET.formatted("${body}"), "camel:kafka")
                .replace("uri: kamelet:source", "uri: \"{{scheme}}:orders\"");
        assertThat(KameletChecks.unusedDependencies(placeholderScheme)).isEmpty();
        String bean = withDependencies(TAG_KAMELET.formatted("${body}"), "camel:kafka")
                .replace("    from:\n",
                        "    beans:\n      - name: client\n        type: \"#class:org.example.Client\"\n    from:\n");
        assertThat(KameletChecks.unusedDependencies(bean)).isEmpty();
        // the Kafka transform actions of the catalog call a class that needs camel:kafka
        String beanStep = withDependencies(TAG_KAMELET.formatted("${body}"), "camel:kafka")
                .replace("        - setBody:\n",
                        "        - bean:\n            beanType: org.example.HoistField\n        - setBody:\n");
        assertThat(KameletChecks.unusedDependencies(beanStep)).isEmpty();
    }

    @Test
    void aComponentThatRunsOnAnotherIsNotNoted() {
        // cron runs on quartz, rest-openapi sends with an http component: neither is named by the template
        String cron = withDependencies(TAG_KAMELET.formatted("${body}"), "camel:cron", "camel:quartz")
                .replace("uri: kamelet:source", "uri: \"cron:tick?schedule=0/3+*+*+*+*+?\"");
        assertThat(KameletChecks.unusedDependencies(cron)).isEmpty();
        String rest = withDependencies(TAG_KAMELET.formatted("${body}"), "camel:rest-openapi", "camel:http")
                .replace("        - setBody:\n", "        - to:\n            uri: rest-openapi\n        - setBody:\n");
        assertThat(KameletChecks.unusedDependencies(rest)).isEmpty();
    }

    @Test
    void writingAKameletReportsTheNotes(@TempDir Path dir) {
        String content = withDependencies(TAG_KAMELET.formatted("${body} [{{tag}}]"), "camel:timer");
        JsonObject result = AuthoringTools.writeFile(new ToolContext(), dir, "tag-order-action.kamelet.yaml", content, true);
        assertThat(result.getString("status")).isEqualTo("created");
        assertThat(result.toJson()).contains("camel:timer is not used by the template");
        JsonObject clean = AuthoringTools.writeFile(new ToolContext(), dir, "tag-order-action.kamelet.yaml",
                TAG_KAMELET.formatted("${body} [{{tag}}]"), true);
        assertThat(clean.containsKey("notes")).isFalse();
    }

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
    void theShapeOfAKameletFile(@TempDir Path dir) {
        // what a local model wrote in the benchmark: properties under spec, required on the property, do: as the route
        String content = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: tag-order-action
                  labels:
                    camel.apache.org/kamelet.type: action
                spec:
                  definition:
                    title: Tag Order Action
                  properties:
                    tag:
                      title: Tag
                      type: string
                      required: true
                  do:
                    - setBody:
                        simple: "${body} [${properties.tag}]"
                """;
        List<String> errors = SourceValidator.validate("tag-order-action.kamelet.yaml", content, catalog, null, dir);
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("Line 10: ").contains("spec.properties is not a key")
                .contains("spec.definition"));
        assertThat(errors).anySatisfy(e -> assertThat(e).startsWith("Line 15: ").contains("spec.do is not a key")
                .contains("template: {from: {uri: kamelet:source"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("has no spec.template"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("Unknown function: properties.tag")
                .contains("the placeholder {{tag}}"));
    }

    @Test
    void aRequiredMarkOnAPropertyAndAnActionFromAComponent() {
        String content = """
                kind: Kamelet
                metadata:
                  name: my-action
                  labels:
                    camel.apache.org/kamelet.type: action
                spec:
                  definition:
                    required:
                      - tag
                      - other
                    properties:
                      tag:
                        type: string
                        required: true
                  template:
                    from:
                      uri: timer:tick
                      steps:
                        - to: kamelet:sink
                """;
        List<String> errors = KameletChecks.validateKameletFile(content);
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("properties.tag.required"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("lists other, which is not under"));
        assertThat(errors).anySatisfy(e -> assertThat(e).contains("an action Kamelet starts from: {uri: kamelet:source}"));
    }

    @Test
    void aTemplateThatStartsFromItself() {
        // the benchmark: from: kamelet:tag-order-action/route in tag-order-action recursed to a StackOverflowError
        String content = """
                kind: Kamelet
                metadata:
                  name: tag-order-action
                spec:
                  template:
                    from:
                      uri: kamelet:tag-order-action/route
                      steps:
                        - setBody:
                            simple: "${body} [{{tag}}]"
                """;
        assertThat(KameletChecks.validateKameletFile(content)).anySatisfy(
                e -> assertThat(e).startsWith("Line 7: ").contains("is entered from kamelet:source"));
    }

    @Test
    void stepsUnderTheTemplate() {
        String content = """
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
        assertThat(KameletChecks.validateKameletFile(content)).anySatisfy(
                e -> assertThat(e).contains("spec.template.steps: the steps go under from:"));
    }

    @Test
    void theDependenciesOfAKamelet() {
        // camel:simple failed to download on every reload: simple is in camel core
        assertThat(KameletChecks.camelDependency("simple")).contains("part of camel core").contains("leave it out");
        assertThat(KameletChecks.camelDependency("timer")).isNull();
        assertThat(KameletChecks.camelDependency("jackson")).isNull();
        assertThat(KameletChecks.camelDependency("jq")).isNull();
        assertThat(KameletChecks.camelDependency("core")).isNull();
        assertThat(KameletChecks.camelDependency("no-such-thing")).contains("is not a Camel artifact");
    }

    @Test
    void aGoodKameletFileHasNoShapeErrors() {
        assertThat(KameletChecks.validateKameletFile(TAG_KAMELET.formatted("${body} [{{tag}}]"))).isEmpty();
    }

    @Test
    void anUnknownDocNameWithKameletPointsToTheGuide() {
        JsonObject doc = CatalogDocs.catalogDoc(catalog, "kamelet-custom", null, null, null, null, false, false, null);
        assertThat(doc.toJson()).contains("camel_catalog_doc name=kamelet docPage=custom");
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
    void anUnknownNameThatRepeatsAWordDoesNotCrash() {
        // a reproducer of the review: Set.of refused the duplicate aws
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to: kamelet:aws-s3-to-aws-sqs
                """;
        assertThatCode(() -> KameletChecks.validateYaml(yaml, null)).doesNotThrowAnyException();
        assertThatCode(() -> CatalogDocs.catalogDoc(catalog, "kafka-to-kafka", null, null, null, null, false, false,
                null)).doesNotThrowAnyException();
    }

    @Test
    void theOptionsOfTheKameletComponentAreNotUnknownProperties() {
        // routeId and noErrorHandler in the uri, location and timeout under parameters: options of the kamelet: endpoint
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to: kamelet:log-sink?routeId=myLog&noErrorHandler=false
                        - to:
                            uri: kamelet:log-sink
                            parameters:
                              location: file:/k/log-sink.kamelet.yaml
                              timeout: 5000
                """;
        assertThat(KameletChecks.validateYaml(yaml, null)).isEmpty();
    }

    @Test
    void aRequiredPropertyWithAColonSeparator(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.properties"), "camel.kamelet.timer-source.message: hello\n");
        assertThat(KameletChecks.validateYaml(TIMER_TO_LOG, dir)).isEmpty();
    }

    @Test
    void aRequiredPropertyFromTheTemplatePropertiesOfTheComponent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("application.properties"),
                "camel.component.kamelet.template-properties[timer-source].message=hello\n");
        assertThat(KameletChecks.validateYaml(TIMER_TO_LOG, dir)).isEmpty();
    }

    private static final String TIMER_TO_LOG = """
            - route:
                from:
                  uri: kamelet:timer-source
                  steps:
                    - to: kamelet:log-sink
            """;

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
    void howToWriteAKameletWithKindKamelet() {
        // a model asked name=kamelet kind=kamelet docPage=custom 14 times and got "Kamelet not found"
        JsonObject doc = CatalogDocs.catalogDoc(catalog, "kamelet", null, "kamelet", null, null, false, false, "custom");
        assertThat(doc.getString("kind")).isEqualTo("component");
        assertThat(doc.getString("name")).isEqualTo("kamelet");
        JsonObject other = CatalogDocs.catalogDoc(catalog, "kamelet-custom", null, "kamelet", null, null, false, false,
                "custom");
        assertThat(other.getString("name")).isEqualTo("kamelet");
    }

    @Test
    void aKameletNotInTheCatalogPointsToHowToWriteOne() {
        JsonObject doc = CatalogDocs.catalogDoc(catalog, "tag-order-action", null, "kamelet", null, null, false, false,
                null);
        assertThat(doc.toJson()).contains("Kamelet not found: tag-order-action")
                .contains("camel_catalog_doc name=kamelet docPage=custom");
    }

    @Test
    void theSampleOfKameletShowsAKameletFile() {
        // every model asked camel_catalog_sample kamelet first, and got only routes that call a Kamelet
        JsonObject sample = CatalogSamples.sample(catalog, "kamelet", 3);
        JsonObject file = (JsonObject) sample.get("kameletFile");
        assertThat(file).isNotNull();
        assertThat(file.getString("yaml")).contains("kind: Kamelet").contains("uri: kamelet:source");
        assertThat(file.getString("placement")).contains("<name>.kamelet.yaml");
        assertThat(sample.getString("guide")).contains("docPage=custom");
    }

    @Test
    void aSampleWithKindKamelet() {
        assertThat(CatalogSamples.sample(catalog, "kamelet", "tag-order-action", 3).getString("kind"))
                .isEqualTo("kamelet file");
    }

    @Test
    void aSampleOfAKameletByAnotherName() {
        // kamelet-custom, as a model named it after its project
        JsonObject sample = CatalogSamples.sample(catalog, "kamelet-custom", 3);
        assertThat(sample.getString("kind")).isEqualTo("kamelet file");
        assertThat(sample.toJson()).contains("kind: Kamelet").contains("kamelet.type: action");
    }

    @Test
    void theDocPagesAreNamedWithTheirCall() {
        JsonObject doc = CatalogDocs.catalogDoc(catalog, "kamelet", null, null, null, null, false, false, null);
        assertThat(doc.getString("docPagesHint")).contains("docPage=custom: Writing a custom Kamelet");
    }

    @Test
    void theCatalogFindsKamelets() {
        JsonObject found = CatalogDocs.find(catalog, "kafka", "kamelet", 10);
        assertThat(found.toJson()).contains("\"kamelet:kafka-sink\"").contains("\"kamelet:kafka-source\"");
        JsonObject all = CatalogDocs.find(catalog, "timer source", null, 10);
        assertThat(all.toJson()).contains("\"kamelet:timer-source\"");
    }
}
