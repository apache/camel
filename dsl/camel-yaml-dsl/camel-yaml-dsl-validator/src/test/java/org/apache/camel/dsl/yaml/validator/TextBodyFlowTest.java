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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24844: a Groovy or simple field read on a body that is still text - the file as loaded, the output of a
 * marshal, or what a direct: route returns - is reported with the unmarshal step that parses it.
 */
public class TextBodyFlowTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    @Test
    public void testMarshalInsteadOfUnmarshal() {
        // what the benchmark wrote: the file is loaded, then marshalled, then read with Groovy
        String yaml = """
                - route:
                    id: lookup
                    from:
                      uri: direct
                      parameters:
                        name: lookup
                      steps:
                        - setBody:
                            expression:
                              constant:
                                expression: resource:file:stock.json
                        - marshal:
                            json:
                              library: Jackson
                        - setHeader:
                            name: sku
                            expression:
                              simple: ${header.sku}
                        - setBody:
                            expression:
                              groovy: body.find { it.sku == headers.sku }
                """;
        List<String> lines = lines(yaml);
        assertThat(lines).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 20: ");
            assertThat(m).contains("groovy reads fields of the body (body.find { ... })");
            assertThat(m).contains("marshal: json writes data as text");
            assertThat(m).contains("unmarshal: json is the step, not marshal");
        });
    }

    @Test
    public void testTheCalledRouteReturnsText() {
        String yaml = """
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            expression:
                              constant:
                                expression: resource:file:stock.json
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - to:
                            uri: direct:lookup
                        - setHeader:
                            name: item
                            expression:
                              groovy: body.find { it.sku == headers.sku }
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 18: ");
            assertThat(m).contains("route getStock: groovy reads fields of the body");
            assertThat(m).contains("to: direct:lookup returns it as text");
            assertThat(m).contains("add unmarshal: json before this step");
        });
    }

    @Test
    public void testSimpleKeyOnACsvFile() {
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      parameters:
                        include: ".*\\\\.csv"
                      steps:
                        - choice:
                            when:
                              - simple: "${body[status]} == 'NEW'"
                                steps:
                                  - to: mock:new
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).contains("simple reads a field of the body (${body[status]})");
            assertThat(m).contains("the body here is the file as it was read (a GenericFile), not parsed data");
            assertThat(m).contains("add unmarshal: csv");
        });
    }

    @Test
    public void testTheFormatFromTheFileExtension() {
        // the extension maps to a content type as MimeTypeHelper knows it: .tsv is tab-separated values, read with csv
        assertThat(TextBodyFlow.formatOf("resource:file:orders.tsv")).isEqualTo("csv");
        assertThat(TextBodyFlow.formatOf("orders.yml")).isEqualTo("yaml");
        assertThat(TextBodyFlow.formatOf("catalog.xml")).isEqualTo("jacksonXml");
        assertThat(TextBodyFlow.formatOf("events.ics")).isEqualTo("ical");
        assertThat(TextBodyFlow.formatOf("notes.txt")).isNull();
        assertThat(TextBodyFlow.formatOf("{\"sku\": \"A1\"}")).isEqualTo("json");
    }

    @Test
    public void testAnUnclosedKeyIsStillReported() {
        assertThat(TextBodyFlow.groovyRead("body['sku", java.util.Set.of())).isEqualTo("body['");
        assertThat(TextBodyFlow.groovyRead("body['sku']", java.util.Set.of())).isEqualTo("body['sku']");
        assertThat(TextBodyFlow.simpleRead("${body[sku", java.util.Set.of())).isEqualTo("${body[s");
    }

    @Test
    public void testUnknownFormatNamesTheChoices() {
        String yaml = """
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setBody:
                            constant: "sku=A1"
                        - setBody:
                            groovy: "body.sku"
                """;
        assertThat(lines(yaml)).singleElement()
                .satisfies(m -> assertThat(m).contains("the data format of the payload (json, jacksonXml, csv, ...)"));
    }

    @Test
    public void testUnmarshalFirstIsFine() {
        String yaml = """
                - route:
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setBody:
                            groovy: body.find { it.sku == headers.sku }
                        - setHeader:
                            name: found
                            simple: ${body[sku]}
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testTextOperationsAreFine() {
        // jsonpath reads JSON text; length, toUpperCase, a regex find and an index all work on text
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      steps:
                        - setHeader:
                            name: sku
                            jsonpath: $.sku
                        - setHeader:
                            name: size
                            groovy: body.length() + body.toUpperCase().size() + body.find(/A\\d/).size()
                        - setHeader:
                            name: first
                            simple: ${body[0]} ${body.length()}
                        - log: ${body}
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testTheFileItselfIsReadable() {
        // the file consumer's body is a GenericFile: its own properties are not fields of the content
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      steps:
                        - setBody:
                            simple: ${body.file}
                - route:
                    from:
                      uri: file:outbox
                      steps:
                        - log: ${body.fileName} has ${body.fileLength} bytes
                        - setHeader:
                            name: name
                            groovy: body.fileNameOnly
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testUnknownBodySaysNothing() {
        // a consumer of its own, a bean, an unknown route, or a choice that may set the body: nothing is certain
        String yaml = """
                - route:
                    from:
                      uri: platform-http:/orders
                      steps:
                        - setBody:
                            groovy: body.find { it.sku == 'A1' }
                - route:
                    from:
                      uri: direct:a
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - bean:
                            ref: parser
                        - setBody:
                            groovy: body.items
                - route:
                    from:
                      uri: direct:b
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - to: direct:elsewhere
                        - setBody:
                            groovy: body.items
                - route:
                    from:
                      uri: direct:c
                      steps:
                        - setBody:
                            simple: ${header.payload}
                        - setBody:
                            groovy: body.items
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
