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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.DataFormatModel;
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

    @Test
    public void testParsedDataWrittenToAFile() {
        // the order is unmarshalled to read its fields, and then written as it is: a Map is not file content
        String yaml = """
                - route:
                    id: archive
                    from:
                      uri: file:inbox
                      steps:
                        - unmarshal:
                            json: {}
                        - setHeader:
                            name: orderId
                            simple: ${body[orderId]}
                        - to:
                            uri: file:archive
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 12: ");
            assertThat(m).contains("route archive: to: file needs the body as text, bytes, a stream or a file");
            assertThat(m).contains("the body here is parsed data (a Map or a List), not text");
            assertThat(m).contains("unmarshal: json turns the text into data: add marshal: json before this step");
        });
    }

    @Test
    public void testValidateKeepsTheBody() {
        // validate checks the body and leaves it as it is
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      steps:
                        - unmarshal:
                            json: {}
                        - validate:
                            simple: ${body[orderId]} != null
                        - convertHeaderTo:
                            name: CamelFileLength
                            type: java.lang.Long
                        - to: file:valid
                """;
        assertThat(lines(yaml)).singleElement()
                .satisfies(m -> assertThat(m).contains("to: file needs the body as text, bytes, a stream or a file"));
    }

    @Test
    public void testParsedDataSentWithPost() {
        // a message with a body is sent as a POST, and the http producer cannot make a request body of a Map
        String yaml = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json:
                              library: Jackson
                        - to:
                            uri: http://localhost:8080/orders
                            parameters:
                              throwExceptionOnFailure: false
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 9: ");
            assertThat(m).contains("to: http sends the body as the request (a message with a body is sent as a POST)");
            assertThat(m).contains("add marshal: json before this step, or set httpMethod: GET when the call sends no body");
        });
    }

    @Test
    public void testSplitElementsSentWithPost() {
        // what the benchmark wrote: each order line is a Map whose fields are read, and toD meant a GET lookup
        String yaml = """
                - route:
                    id: check-stock
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - split:
                            simple: "${body[lines]}"
                            steps:
                              - setHeader:
                                  name: sku
                                  simple: "${body[sku]}"
                              - toD:
                                  uri: "http://localhost:8080/stock/${header.sku}"
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).contains("route check-stock: toD: http sends the body as the request");
            assertThat(m).contains("unmarshal: json turns the text into data, and split ${body[lines]} gives its"
                                   + " elements as data");
            assertThat(m).contains("or set httpMethod: GET when the call sends no body");
        });
    }

    @Test
    public void testSplitElementsNotReadAsDataSayNothing() {
        // nothing in the split reads a field of the element, which may be text as well
        String yaml = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - split:
                            simple: "${body[skus]}"
                            steps:
                              - setHeader:
                                  name: sku
                                  simple: "${body}"
                              - to: http://localhost:8080/reserve
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testHttpCallsThatSendNoBodyAreFine() {
        // a query string or httpMethod: GET makes the call a GET; a CamelHttpMethod header can, and so can the
        // request of an HTTP consumer, whose headers the producer sees
        String yaml = """
                - route:
                    from:
                      uri: file:a
                      steps:
                        - unmarshal:
                            json: {}
                        - to: http://localhost:8080/stock?sku=A1
                        - to:
                            uri: http://localhost:8080/stock
                            parameters:
                              httpMethod: GET
                - route:
                    from:
                      uri: file:b
                      steps:
                        - unmarshal:
                            json: {}
                        - to:
                            uri: http://localhost:8080/stock
                            parameters:
                              sku: A1
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setHeader:
                            name: CamelHttpMethod
                            constant: GET
                        - to: http://localhost:8080/stock
                - route:
                    from:
                      uri: direct:c
                      steps:
                        - unmarshal:
                            json: {}
                        - to: http://localhost:8080/stock
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testParsedDataAsTheReply() {
        // what the benchmark wrote: the stock is looked up in the parsed file, and the Map found is the reply
        String yaml = """
                - route:
                    id: stock
                    from:
                      uri: platform-http:/stock
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setBody:
                            groovy: body.find { it.sku == headers.sku }
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 11: ");
            assertThat(m).contains("route stock: the body at the end of the route is the reply to the HTTP request");
            assertThat(m).contains("unmarshal: json turns the text into data, and groovy body.find { ... } keeps it as data");
            assertThat(m).contains("add marshal: json as the last step");
        });
    }

    @Test
    public void testTheCalledRouteReturnsData() {
        String yaml = """
                - route:
                    from:
                      uri: direct:lookup
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                - route:
                    id: stock
                    from:
                      uri: platform-http:/stock
                      steps:
                        - to: direct:lookup
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 14: ");
            assertThat(m).contains("to: direct:lookup returns it as data (unmarshal: json turns the text into data)");
        });
    }

    @Test
    public void testDataMadeTextAgainIsFine() {
        String yaml = """
                - route:
                    from:
                      uri: platform-http:/stock
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setBody:
                            groovy: body.find { it.sku == headers.sku }
                        - marshal:
                            json: {}
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            csv: {}
                        - setBody:
                            simple: "${body.size()} rows"
                        - to: file:report
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testWhatIsNotCertainlyDataSaysNothing() {
        // a field of the element picked, an object of a class, an iterator, a bean: the body may be text, or not data
        String yaml = """
                - route:
                    from:
                      uri: platform-http:/qty
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setBody:
                            groovy: body.find { it.sku == headers.sku }?.qty
                - route:
                    from:
                      uri: platform-http:/order
                      steps:
                        - unmarshal:
                            json:
                              unmarshalType: com.example.Order
                - route:
                    from:
                      uri: file:in
                      steps:
                        - unmarshal:
                            csv:
                              lazyLoad: true
                        - to: file:out
                - route:
                    from:
                      uri: platform-http:/total
                      steps:
                        - unmarshal:
                            json: {}
                        - bean:
                            ref: totals
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testGroovyCallsThatKeepData() {
        assertThat(TextBodyFlow.keepsData(groovy("body.findAll { it.qty > 0 }"))).isEqualTo("body.findAll { ... }");
        assertThat(TextBodyFlow.keepsData(groovy("body.find { line -> line.sku == headers.sku }")))
                .isEqualTo("body.find { ... }");
        assertThat(TextBodyFlow.keepsData(groovy("body.collect { [sku: it.sku, total: it.qty * it.price] }")))
                .isEqualTo("body.collect { ... }");
        // what follows the closure, or an element whose fields are not read, may be anything
        assertThat(TextBodyFlow.keepsData(groovy("body.find { it.sku == 'A1' }.qty"))).isNull();
        assertThat(TextBodyFlow.keepsData(groovy("body.find { it == 'A1' }"))).isNull();
        assertThat(TextBodyFlow.keepsData(groovy("body.size()"))).isNull();
    }

    private static JsonNode groovy(String script) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.putObject("expression").put("groovy", script);
        return node;
    }

    /** The facts camel-jbang reads beside a route: stock-api.json, and properties that set no REST binding. */
    private static final ProjectFacts STOCK_API = new ProjectFacts(
            Set.of(), Map.of("stock-api.json", Map.of("reserveStock", "post", "getStock", "get")), "off");

    @Test
    public void testRestOpenApiCallOfAPostOperation() {
        // what the benchmark wrote: the order is unmarshalled to read its lines, and sent as it is
        String yaml = """
                - route:
                    id: reserve
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - to:
                            uri: rest-openapi:stock-api.json#reserveStock
                            parameters:
                              host: http://localhost:8080
                """;
        assertThat(lines(yaml, STOCK_API)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 9: ");
            assertThat(m).contains("route reserve: to: rest-openapi sends the body as the request of the operation"
                                   + " reserveStock (POST in stock-api.json)");
            // the JSON text of the request first: the data is rarely the request as it is
            assertThat(m).contains("unmarshal: json turns the text into data: set the body to the JSON text of the request"
                                   + " before this step, such as with setBody and simple: '{\"name\": \"${body[name]}\"}',"
                                   + " or add marshal: json before this step when the data has the fields of the request,"
                                   + " or let the REST binding write it (bindingMode: json)");
        });
        // the same operation named by options, or with the specification as a file: resource
        String options = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - to:
                            uri: rest-openapi
                            parameters:
                              specificationUri: stock-api.json
                              operationId: reserveStock
                """;
        assertThat(lines(options, STOCK_API)).singleElement().asString()
                .contains("the operation reserveStock (POST in stock-api.json)");
        assertThat(lines(yaml.replace("rest-openapi:stock-api.json", "rest-openapi:file:stock-api.json"), STOCK_API))
                .hasSize(1);
    }

    @Test
    public void testRestOpenApiCallOfTheLinesOfAJsonPathSplit() {
        // what the benchmark wrote: the order lines are split with jsonpath, and each line, a Map, is sent as it is
        String yaml = """
                - route:
                    id: reserve-order-lines
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - split:
                            expression:
                              jsonpath:
                                expression: "$.lines"
                            steps:
                              - setHeader:
                                  name: sku
                                  expression:
                                    jsonpath:
                                      expression: "$.sku"
                              - to:
                                  uri: rest-openapi
                                  parameters:
                                    specificationUri: stock-api.json
                                    operationId: reserveStock
                """;
        assertThat(lines(yaml, STOCK_API)).singleElement().satisfies(m -> {
            assertThat(m).contains("rest-openapi sends the body as the request of the operation reserveStock");
            assertThat(m).contains("split $.lines gives its elements as data");
        });
        // a jsonpath that reads no field of the element: the element may be text
        assertThat(lines(yaml.replace("expression: \"$.sku\"", "expression: \"$\""), STOCK_API)).isEmpty();
    }

    @Test
    public void testRestOpenApiCallNamesTheFieldsOfTheRequest() {
        // r19 of the benchmark: told to marshal, the model sent each line as JSON, without the orderId of the request
        String yaml = """
                - route:
                    id: reserve-order-lines
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - split:
                            expression:
                              simple: "${body[lines]}"
                            steps:
                              - setHeader:
                                  name: sku
                                  expression:
                                    simple: "${body[sku]}"
                              - to:
                                  uri: rest-openapi
                                  parameters:
                                    specificationUri: stock-api.json
                                    operationId: reserveStock
                """;
        ProjectFacts facts = new ProjectFacts(
                Set.of(), STOCK_API.operations(), "off",
                Map.of("stock-api.json", Map.of("reserveStock", List.of("orderId", "qty"))));
        assertThat(lines(yaml, facts)).singleElement().satisfies(m -> {
            assertThat(m).contains("rest-openapi sends the body as the request of the operation reserveStock");
            assertThat(m).contains("set the body to the JSON text of the request before this step (it needs orderId, qty),"
                                   + " such as with setBody and simple: '{\"orderId\": ..., \"qty\": ...}', or add marshal:"
                                   + " json before this step when the data has the fields of the request");
        });
    }

    @Test
    public void testRestOpenApiCallsThatAreFine() {
        String post = """
                - route:
                    from:
                      uri: file:orders
                      steps:
                        - unmarshal:
                            json: {}
                        - to: rest-openapi:stock-api.json#reserveStock
                """;
        // a GET sends no body; an operation or a specification the facts do not know says nothing
        assertThat(lines(post.replace("reserveStock", "getStock"), STOCK_API)).isEmpty();
        assertThat(lines(post.replace("reserveStock", "cancelStock"), STOCK_API)).isEmpty();
        assertThat(lines(post.replace("stock-api.json", "orders-api.json"), STOCK_API)).isEmpty();
        // a REST binding writes the Map: in the properties, in the file, or not known
        assertThat(lines(post, new ProjectFacts(Set.of(), STOCK_API.operations(), "json"))).isEmpty();
        assertThat(lines("- restConfiguration:\n    bindingMode: json\n" + post, STOCK_API)).isEmpty();
        assertThat(lines(post, new ProjectFacts(Set.of(), STOCK_API.operations(), null))).isEmpty();
        assertThat(lines(post)).isEmpty();
    }

    @Test
    public void testRestRouteReply() {
        String yaml = """
                - rest:
                    get:
                      - path: /stock/{sku}
                        to: direct:stock
                - route:
                    id: stock
                    from:
                      uri: direct:stock
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                        - setBody:
                            groovy: body.find { it.sku == headers.sku }
                """;
        assertThat(lines(yaml, STOCK_API)).singleElement().satisfies(m -> {
            assertThat(m).startsWith("Line 15: ");
            assertThat(m).contains("route stock: the body at the end of the route is the reply to the REST call");
            assertThat(m).contains("add marshal: json as the last step, or let the REST binding write it (bindingMode: json)");
        });
        // a binding on the verb, the rest or the configuration writes it; and nothing is said when it is not known
        assertThat(lines(yaml.replace("        to: direct:stock", "        to: direct:stock\n        bindingMode: json"),
                STOCK_API)).isEmpty();
        assertThat(lines(yaml.replace("- rest:\n", "- rest:\n    bindingMode: auto\n"), STOCK_API)).isEmpty();
        assertThat(lines("- restConfiguration:\n    bindingMode: json\n" + yaml, STOCK_API)).isEmpty();
        assertThat(lines(yaml, new ProjectFacts(Set.of(), Map.of(), "json"))).isEmpty();
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testContractFirstOperationReply() {
        String yaml = """
                - rest:
                    openApi:
                      specification: stock-api.json
                - route:
                    id: getStock
                    from:
                      uri: direct:getStock
                      steps:
                        - setBody:
                            constant: resource:file:stock.json
                        - unmarshal:
                            json: {}
                """;
        assertThat(lines(yaml, STOCK_API)).singleElement().satisfies(m -> {
            assertThat(m).contains("route getStock: the body at the end of the route is the reply to the REST call");
            assertThat(m).contains("add marshal: json as the last step");
        });
        // the operations of the specification are what makes direct:getStock the route of a REST call
        assertThat(lines(yaml, new ProjectFacts(Set.of(), Map.of(), "off"))).isEmpty();
        assertThat(lines(yaml.replace("direct:getStock", "direct:audit"), STOCK_API)).isEmpty();
    }

    @Test
    public void testTheBodyAnHttpConsumerGaveInTheRunningApp() {
        // CAMEL-25750: the request of a platform-http route is a stream cache, which the walk alone does not know
        String yaml = """
                - route:
                    id: orders
                    from:
                      uri: platform-http:/orders
                      steps:
                        - setHeader:
                            name: qty
                            expression:
                              simple: ${body.qty}
                """;
        assertThat(lines(yaml)).isEmpty();

        ProjectFacts seen = observed(ProjectFacts.consumerKey("platform-http:/orders"),
                List.of("org.apache.camel.converter.stream.ByteArrayInputStreamCache"), List.of("payload"));
        assertThat(lines(yaml, seen)).singleElement().satisfies(m -> {
            assertThat(m).contains("simple reads a field of the body (${body.qty}), but the body here is still text");
            assertThat(m).contains("in the running app, from: platform-http:/orders gave a ByteArrayInputStreamCache");
            assertThat(m).contains("unmarshal it first with the data format of the payload");
        });
    }

    @Test
    public void testTheBodyAProducerReturnedInTheRunningApp() {
        // a sql call returns a List of rows, which a file write cannot write as it is
        String yaml = """
                - route:
                    id: report
                    from:
                      uri: timer:report
                      steps:
                        - to:
                            uri: sql:select * from orders
                        - to:
                            uri: file:reports
                """;
        assertThat(lines(yaml)).isEmpty();

        ProjectFacts seen = observed(ProjectFacts.producerKey("sql:select * from orders"),
                List.of("java.util.ArrayList"), List.of("collection"));
        assertThat(lines(yaml, seen)).singleElement().satisfies(m -> {
            assertThat(m)
                    .contains("to: file needs the body as text, bytes, a stream or a file, but the body here is parsed data");
            assertThat(m).contains("in the running app, to: sql:select * from orders returned an ArrayList");
            // the walk knows no data format for what the running app saw
            assertThat(m).contains("add marshal with the data format to write (such as marshal: json) before this step");
        });
    }

    @Test
    public void testBodiesOfMoreThanOneKindSayNothing() {
        String yaml = """
                - route:
                    id: report
                    from:
                      uri: timer:report
                      steps:
                        - to:
                            uri: sql:select * from orders
                        - to:
                            uri: file:reports
                """;
        // some calls returned rows, others text: the walk cannot tell which one this is
        ProjectFacts seen = observed(ProjectFacts.producerKey("sql:select * from orders"),
                List.of("java.util.ArrayList", "java.lang.String"), List.of("collection", "payload"));
        assertThat(lines(yaml, seen)).isEmpty();
    }

    @Test
    public void testWhatTheWalkKnowsComesFirst() {
        // the file consumer gives the file as read, whatever a running app saw: the walk knows, and says so
        String yaml = """
                - route:
                    id: orders
                    from:
                      uri: file:inbox
                      steps:
                        - setHeader:
                            name: qty
                            expression:
                              simple: ${body.qty}
                """;
        ProjectFacts seen = observed(ProjectFacts.consumerKey("file:inbox"),
                List.of("java.util.LinkedHashMap"), List.of("data"));
        assertThat(lines(yaml, seen)).singleElement().satisfies(m -> {
            assertThat(m).contains("the file as it was read");
            assertThat(m).doesNotContain("running app");
        });
    }

    @Test
    public void testTheConsumersOfTheCatalog() {
        // CAMEL-24961: what a consumer gives comes from the body types of the catalog
        String yaml = """
                - route:
                    id: sqs
                    from:
                      uri: aws2-sqs:orders
                      steps:
                        - setHeader:
                            name: orderId
                            simple: ${body[orderId]}
                - route:
                    id: mqtt
                    from:
                      uri: paho-mqtt5:sensors
                      steps:
                        - setBody:
                            groovy: body.temperature
                - route:
                    id: ftp
                    from:
                      uri: ftp:files.example.com/inbox
                      parameters:
                        include: .*\\.json
                      steps:
                        - setHeader:
                            name: orderId
                            simple: ${body[orderId]}
                - route:
                    id: ftpStream
                    from:
                      uri: ftp:files.example.com/inbox?streamDownload=true
                      steps:
                        - setHeader:
                            name: orderId
                            simple: ${body[orderId]}
                """;
        assertThat(lines(yaml)).satisfiesExactly(
                m -> assertThat(m).contains("route sqs: simple reads a field of the body (${body[orderId]})")
                        .contains("the body here is still text, not parsed data - the aws2-sqs consumer gives it as text"),
                m -> assertThat(m).contains("route mqtt: groovy reads fields of the body (body.temperature)")
                        .contains("the paho-mqtt5 consumer gives it as bytes"),
                m -> assertThat(m).contains("route ftp: simple reads a field of the body")
                        .contains("the body here is the file as it was read (a GenericFile), not parsed data")
                        .contains("the ftp consumer gives it: add unmarshal: json before this step"),
                m -> assertThat(m).contains("route ftpStream: simple reads a field of the body")
                        .contains("the ftp consumer gives it as a stream"));
    }

    @Test
    public void testTheProducersOfTheCatalog() {
        // the response of the http producer is bytes or a stream, a row of sql is data, and a file write keeps the body
        String yaml = """
                - route:
                    id: price
                    from:
                      uri: timer:tick
                      steps:
                        - to: https://api.example.com/prices
                        - setHeader:
                            name: price
                            simple: ${body[price]}
                - route:
                    id: rows
                    from:
                      uri: sql:select * from orders
                      steps:
                        - to: file:orders
                - route:
                    id: archive
                    from:
                      uri: file:inbox
                      steps:
                        - to: file:backup
                        - setBody:
                            groovy: body.items
                - route:
                    id: sensors
                    from:
                      uri: file:readings
                      steps:
                        - unmarshal:
                            json: {}
                        - to: paho-mqtt5:readings
                """;
        assertThat(lines(yaml)).satisfiesExactly(
                m -> assertThat(m).contains("route price: simple reads a field of the body (${body[price]})")
                        .contains("to: https://api.example.com/prices returns it as bytes or a stream: unmarshal it first"),
                m -> assertThat(m).contains("route rows: to: file needs the body as text, bytes, a stream or a file")
                        .contains("the sql consumer gives it as a Map: add marshal with the data format to write"),
                m -> assertThat(m).contains("route archive: groovy reads fields of the body (body.items)")
                        .contains("the file as it was read (a GenericFile)"),
                m -> assertThat(m).contains("route sensors: to: paho-mqtt5 needs the body as text, bytes, a stream or a file")
                        .contains("unmarshal: json turns the text into data: add marshal: json before this step"));
    }

    @Test
    public void testTheOptionsTheBodyDependsOn() {
        // xslt with output DOM gives a Document, an unmarshal with useList a List, gson is not known, and with download
        // false the ftp consumer gives no body
        String yaml = """
                - route:
                    from:
                      uri: file:in
                      steps:
                        - to: xslt:order.xsl?output=DOM
                        - setBody:
                            groovy: body.order
                - route:
                    from:
                      uri: file:in
                      steps:
                        - unmarshal:
                            json:
                              library: Gson
                        - to: file:out
                - route:
                    from:
                      uri: ftp:files.example.com/inbox?download=false
                      steps:
                        - setBody:
                            groovy: body.items
                - route:
                    from:
                      uri: file:in
                      steps:
                        - unmarshal:
                            json:
                              useList: true
                        - to: file:out
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("to: file needs the body as text, bytes, a stream or a file, but the body here is parsed data"));
    }

    @Test
    public void testWhatTheComponentMayConfigureSaysNothing() {
        // the valueDeserializer of kafka and the operation of aws2-sqs can be set on the component, which the route
        // does not show; a placeholder is only known when the route runs
        String yaml = """
                - route:
                    from:
                      uri: kafka:orders
                      steps:
                        - setHeader:
                            name: orderId
                            simple: ${body[orderId]}
                - route:
                    from:
                      uri: file:in
                      steps:
                        - unmarshal:
                            json: {}
                        - to: aws2-sqs:orders
                - route:
                    from:
                      uri: ftp:files.example.com/inbox?streamDownload={{stream}}
                      steps:
                        - setHeader:
                            name: orderId
                            simple: ${body[orderId]}
                """;
        assertThat(lines(yaml)).isEmpty();
    }

    @Test
    public void testTheOptionsInThePathOfTheEndpoint() {
        // the operation of openai is in the path: the answer of chat-completion is text, also when it is asked for as
        // JSON, embeddings gives a List, and an operation only known when the route runs says nothing
        String yaml = """
                - route:
                    id: city
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: Name a city in Denmark
                        - to: openai:chat-completion?jsonSchema=resource:classpath:city.json
                        - setHeader:
                            name: city
                            simple: ${body[name]}
                - route:
                    id: vector
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: Apache Camel
                        - to: llm:embeddings
                        - setHeader:
                            name: first
                            simple: ${body[0]}
                - route:
                    id: dynamic
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: Apache Camel
                        - toD: openai:${header.operation}
                        - setHeader:
                            name: first
                            simple: ${body[0]}
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m)
                .contains("route city: simple reads a field of the body (${body[name]})")
                .contains("to: openai:chat-completion returns it as text: unmarshal it first"));
    }

    @Test
    public void testTheBodyOfObjectStorage() {
        // getObject gives the stream of the object and the blob consumer the stream or the file of the blob, so a field
        // read needs an unmarshal; an upload names no operation, which the component may set, and dropbox search gives a
        // List
        String yaml = """
                - route:
                    id: get
                    from:
                      uri: timer:tick
                      steps:
                        - to: aws2-s3:reports?operation=getObject
                        - setHeader:
                            name: name
                            simple: ${body[name]}
                - route:
                    id: upload
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: '{"name": "Camel"}'
                        - to: aws2-s3:reports
                        - setHeader:
                            name: name
                            simple: ${body[name]}
                - route:
                    id: blob
                    from:
                      uri: azure-storage-blob:account/reports
                      steps:
                        - setHeader:
                            name: name
                            simple: ${body[name]}
                - route:
                    id: search
                    from:
                      uri: timer:tick
                      steps:
                        - to: dropbox:search?query=camel
                        - setHeader:
                            name: first
                            simple: ${body[0]}
                """;
        assertThat(lines(yaml)).hasSize(2).anySatisfy(m -> assertThat(m)
                .contains("route get: simple reads a field of the body (${body[name]})")
                .contains("to: aws2-s3:reports returns it as a stream: unmarshal it first"))
                .anySatisfy(m -> assertThat(m)
                        .contains("route blob: simple reads a field of the body (${body[name]})")
                        .contains("the body here is still text, not parsed data")
                        .contains("the azure-storage-blob consumer gives it as a stream or a file: unmarshal it first"));
    }

    @Test
    public void testOneReportForAStepThatFails() {
        // the first write fails at runtime, so the second does not run with this body
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      steps:
                        - unmarshal:
                            json: {}
                        - to: file:archive
                        - to: file:backup
                """;
        assertThat(lines(yaml)).singleElement().satisfies(m -> assertThat(m).startsWith("Line 7: "));
    }

    @Test
    public void testACatalogWithoutBodyTypes() throws Exception {
        // the catalog of an older Camel version has no body types: the validator's own catalog stands in for it
        DefaultCamelCatalog older = new DefaultCamelCatalog() {
            @Override
            public ComponentModel componentModel(String name) {
                ComponentModel model = super.componentModel(name);
                if (model != null) {
                    model.setBodyTypes(new LinkedHashMap<>());
                }
                return model;
            }

            @Override
            public DataFormatModel dataFormatModel(String name) {
                DataFormatModel model = super.dataFormatModel(name);
                if (model != null) {
                    model.setBodyTypes(new LinkedHashMap<>());
                }
                return model;
            }
        };
        YamlValidator other = new YamlValidator(false, null, older);
        other.init();
        String yaml = """
                - route:
                    from:
                      uri: file:inbox
                      steps:
                        - unmarshal:
                            json: {}
                        - to: file:archive
                """;
        assertThat(YamlValidator.describeAll(yaml, other.validate(yaml))).singleElement()
                .satisfies(m -> assertThat(m).contains("to: file needs the body as text, bytes, a stream or a file"));
    }

    private static ProjectFacts observed(String key, List<String> types, List<String> kinds) {
        return new ProjectFacts(Set.of(), Map.of(), null, Map.of(), Map.of(key, new ProjectFacts.Observed(types, kinds)));
    }

    private static List<String> lines(String yaml, ProjectFacts facts) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml, facts));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }

    private static List<String> lines(String yaml) {
        try {
            return YamlValidator.describeAll(yaml, validator.validate(yaml));
        } catch (Exception e) {
            throw new AssertionError("Failed to validate:\n" + yaml, e);
        }
    }
}
