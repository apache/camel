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

import com.networknt.schema.Error;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24698: "property 'x' is not defined in the schema" should say what was probably meant: the closest option name,
 * or that the entry belongs at the top level of the file.
 */
public class YamlValidatorPropertyHintTest {

    private static YamlValidator validator;

    @BeforeAll
    public static void setup() throws Exception {
        validator = new YamlValidator();
        validator.init();
    }

    /** CAMEL-24850: the three shapes get the form to write, in both schema modes. */
    private static List<YamlValidator> bothModes() throws Exception {
        YamlValidator canonical = new YamlValidator(true);
        canonical.init();
        return List.of(validator, canonical);
    }

    /** CAMEL-24881: the route-level error handler is errorHandler: with the kind as its key. */
    @Test
    public void testRouteErrorHandlerShapesGetTheForm() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        id: a
                        noErrorHandler: true
                        from:
                          uri: direct:a
                          steps:
                            - log: "a"
                    - route:
                        id: b
                        errorHandlerType: none
                        from:
                          uri: direct:b
                          steps:
                            - log: "b"
                    - route:
                        id: c
                        errorHandler:
                          noErrorHandler: true
                        from:
                          uri: direct:c
                          steps:
                            - log: "c"
                    - route:
                        id: d
                        errorHandler:
                          type: noErrorHandler
                        from:
                          uri: direct:d
                          steps:
                            - log: "d"
                    """);
            assertThat(errors).extracting(Error::getMessage)
                    .anyMatch(m -> m.contains("noErrorHandler") && m.contains("errorHandler: {noErrorHandler: {}}")
                            && m.contains("kind as its key"))
                    .anyMatch(m -> m.contains("errorHandlerType") && m.contains("errorHandler: {noErrorHandler: {}}"))
                    .anyMatch(m -> m.contains("noErrorHandler takes no options: write noErrorHandler: {}"))
                    .anyMatch(m -> m.contains("errorHandler: has the kind of handler as its key, not a type property"));
        }
    }

    /** CAMEL-25328: an error handler written as a step of the route says where it goes. */
    @Test
    public void testErrorHandlerAsAStepGetsTheRouteForm() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        id: payment-provider
                        from:
                          uri: direct:charge
                          steps:
                            - noErrorHandler: {}
                            - log: "charged"
                    """);
            assertThat(errors).extracting(Error::getMessage)
                    .anyMatch(m -> m.contains("noErrorHandler") && m.contains("an error handler is not a step")
                            && m.contains("errorHandler: {noErrorHandler: {}}"));
        }
    }

    /**
     * An errorHandler under from: gets both of its places: under the route for that route, a top-level item for every
     * route. Told only "a top-level entry", r21 and r25 of the local-model benchmark moved a noErrorHandler meant for
     * one route to the top level.
     */
    @Test
    public void testErrorHandlerUnderFromGetsBothPlaces() throws Exception {
        String yaml = """
                - route:
                    id: payment-provider
                    from:
                      uri: direct:charge
                      errorHandler:
                        noErrorHandler: {}
                      steps:
                        - log:
                            message: "charged"
                """;
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate(yaml);
            assertThat(errors).singleElement().extracting(Error::getMessage).asString()
                    .contains("property 'errorHandler' is not defined")
                    .endsWith("(errorHandler: is a key of the route, not of from:. For this route only, write it under"
                              + " the route at the same level as from:, as in route: {errorHandler: {noErrorHandler: {}},"
                              + " from: {uri: ..., steps: [...]}}. For every route, write it as a top-level item instead:"
                              + " - errorHandler: {noErrorHandler: {}})")
                    .doesNotContain("is a top-level entry");
            assertThat(YamlValidator.describeAll(yaml, errors)).singleElement().asString().startsWith("Line 5: ");
        }
        // the handler as written, its options in flow style
        List<Error> errors = validator.validate(yaml.replace("""
                        noErrorHandler: {}
                """, """
                        deadLetterChannel:
                          deadLetterUri: direct:parked
                          redeliveryPolicy:
                            maximumRedeliveries: 2
                """));
        assertThat(errors).singleElement().extracting(Error::getMessage).asString()
                .contains("as in route: {errorHandler: {deadLetterChannel: {deadLetterUri: \"direct:parked\","
                          + " redeliveryPolicy: {maximumRedeliveries: 2}}}, from: {uri: ..., steps: [...]}}");
        // noErrorHandler: true (r21) is shown in its form, with a map
        errors = validator.validate(yaml.replace("noErrorHandler: {}", "noErrorHandler: true"));
        assertThat(errors).singleElement().extracting(Error::getMessage).asString()
                .contains("as in route: {errorHandler: {noErrorHandler: {}}, from:")
                .doesNotContain("true");
        // a - from: item has no place for it
        errors = validator.validate("""
                - from:
                    uri: direct:charge
                    errorHandler:
                      noErrorHandler: {}
                    steps:
                      - log: "charged"
                """);
        assertThat(errors).singleElement().extracting(Error::getMessage).asString()
                .contains("write the route as - route: with errorHandler: at the same level as from:, as in - route:"
                          + " {errorHandler: {noErrorHandler: {}}, from: {uri: ..., steps: [...]}}");
        // where it belongs, nothing is reported
        assertThat(validator.validate("""
                - route:
                    id: payment-provider
                    errorHandler:
                      noErrorHandler: {}
                    from:
                      uri: direct:charge
                      steps:
                        - log: "charged"
                """)).isEmpty();
    }

    /**
     * A second top-level errorHandler replaces the first for every route, without a word (r22 to r25 of the local-model
     * benchmark: a noErrorHandler meant for the route below it, and the dead letter channel was lost).
     */
    @Test
    public void testASecondTopLevelErrorHandlerIsReported() throws Exception {
        String yaml = """
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: direct:parked
                - route:
                    id: checkout
                    from:
                      uri: direct:checkout
                      steps:
                        - to: direct:charge
                - errorHandler:
                    noErrorHandler: {}
                - route:
                    id: payment-provider
                    from:
                      uri: direct:charge
                      steps:
                        - log: "charged"
                - route:
                    id: parked
                    from:
                      uri: direct:parked
                      steps:
                        - log: "parked"
                """;
        List<Error> errors = validator.validate(yaml);
        assertThat(YamlValidator.describeAll(yaml, errors)).singleElement().asString()
                .isEqualTo("Line 10: /2: a second top-level errorHandler: Camel uses one top-level errorHandler for"
                           + " all routes, and line 1 already sets one, so this one replaces it for every route, not only"
                           + " for the route payment-provider below it. For the route payment-provider only, write"
                           + " errorHandler: under that route at the same level as from:, as in route: {id:"
                           + " payment-provider, errorHandler: {noErrorHandler: {}}, from: {uri: ..., steps: [...]}}");
        // at the end of the file, with no route below it
        String last = """
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: direct:parked
                - route:
                    from:
                      uri: direct:charge
                      steps:
                        - log: "charged"
                - errorHandler:
                    defaultErrorHandler: {}
                """;
        assertThat(validator.validate(last)).singleElement().extracting(Error::getMessage).asString()
                .contains("so this one replaces it for every route. For one route only, write errorHandler: under"
                          + " that route at the same level as from:, as in route: {errorHandler: {defaultErrorHandler:"
                          + " {}}, from: {uri: ..., steps: [...]}}");
        // one top-level errorHandler, and one under the route: nothing to report
        String routeLevel = yaml.replace("""
                - errorHandler:
                    noErrorHandler: {}
                - route:
                    id: payment-provider
                """, """
                - route:
                    id: payment-provider
                    errorHandler:
                      noErrorHandler: {}
                """);
        assertThat(validator.validate(routeLevel)).isEmpty();
        // the error handler of a routeConfiguration is for the routes that use it
        assertThat(validator.validate(routeLevel + """
                - routeConfiguration:
                    id: no-retries
                    errorHandler:
                      noErrorHandler: {}
                """)).isEmpty();
    }

    /** CAMEL-24888: the shapes the local model wrote on the HTTP rungs, each with the form to write. */
    @Test
    public void testHttpRungShapesGetTheForm() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        from:
                          uri: file:orders
                          steps:
                            - setExchangeProperty:
                                name: orderId
                                expression:
                                  simple:
                                    expression: "${body[orderId]}"
                            - setBody:
                                expression:
                                  constant: null
                            - marshal:
                                json:
                                  library: jackson
                    """);
            assertThat(errors).extracting(Error::getMessage)
                    .anyMatch(m -> m.contains("setExchangeProperty") && m.contains("the EIP is setProperty"))
                    .anyMatch(m -> m.contains("constant is a text") && m.contains("simple: {expression: \"${null}\"}"))
                    .anyMatch(m -> m.contains("the library name is case sensitive: write library: Jackson"));
            // the name comes from the data format's own enumeration, not from json's
            errors = v.validate("""
                    - route:
                        from:
                          uri: file:orders
                          steps:
                            - marshal:
                                avro:
                                  library: apacheavro
                    """);
            assertThat(errors).extracting(Error::getMessage)
                    .anyMatch(m -> m.contains("the library name is case sensitive: write library: ApacheAvro")
                            && !m.contains("Gson"));
            errors = v.validate("""
                    - route:
                        from:
                          uri: file:orders
                          steps:
                            - toD:
                                uri: "http://localhost:8080/stock/${exchangeProperty.sku}"
                                options:
                                  throwExceptionOnFailure: false
                            - setBody:
                                expression:
                                  jsonpath:
                                    jsonPath: "$[?(@.sku == 'X')]"
                    """);
            assertThat(errors).extracting(Error::getMessage)
                    .anyMatch(m -> m.contains("toD takes its options like to: under parameters:"))
                    .anyMatch(m -> m.contains("the JSONPath text goes under expression:"));
        }
    }

    /**
     * An option of a language written next to its short form (a local model in the benchmark of Oct 21-22 2026 wrote
     * resultType there, was told only that the property is not defined, and wrote the same file 23 times).
     */
    @Test
    public void testAnOptionNextToTheShortFormOfALanguageSaysWhereItGoes() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        from:
                          uri: direct:get-order
                          steps:
                            - setBody:
                                expression:
                                  jsonpath: "$[?(@.orderId == '${header.id}')]"
                                  resultType: java.util.Map
                    """);
            assertThat(errors).extracting(Error::getMessage)
                    .anyMatch(m -> m.contains("resultType is an option of the jsonpath language: write the language as a"
                                              + " map, with the text under expression: and resultType next to it"
                                              + " (jsonpath: {expression: \"$[?(@.orderId == '${header.id}')]\","
                                              + " resultType: java.util.Map})"));
        }
        // the full form is valid
        assertThat(validator.validate("""
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.id}')]"
                                resultType: java.util.Map
                """)).isEmpty();
    }

    /** CAMEL-24888: a double-quoted value that is never closed is named, instead of the parser's block-end message. */
    @Test
    public void testAnUnclosedQuoteIsNamed() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:a
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.sku == '${header.sku}')]
                                resultType: java.util.List
                        - log: "done"
                """);
        assertThat(errors).extracting(Error::getMessage)
                .anyMatch(m -> m.startsWith("line 8: the value opens a double quote and never closes it")
                        && m.contains("write the line as expression: \"$[?(@.sku == '${header.sku}')]\""));
    }

    /** CAMEL-24906: the value corrected twice ends with two quotes, and the line to write says so. */
    @Test
    public void testADoubledClosingQuoteIsNamed() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:a
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.sku == '${header.sku}')]""
                                resultType: java.util.List
                """);
        assertThat(errors).extracting(Error::getMessage)
                .anyMatch(m -> m.contains("ends with two double quotes; remove the extra one")
                        && m.endsWith("write the line as expression: \"$[?(@.sku == '${header.sku}')]\""));
    }

    @Test
    public void testPollEnrichWithAUriSaysItIsAnExpression() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        from:
                          uri: timer:tick
                          steps:
                            - pollEnrich:
                                uri: file:./order.json
                            - enrich:
                                resourceUri: direct:prices
                    """);
            assertThat(errors).hasSize(2);
            assertThat(errors.get(0).getMessage())
                    .contains("the endpoint of pollEnrich is an expression: write pollEnrich: {expression: {constant:"
                              + " {expression: \"file:./order.json\"}}}");
            assertThat(errors.get(1).getMessage())
                    .contains("the endpoint of enrich is an expression: write enrich: {expression: {constant:"
                              + " {expression: \"direct:prices\"}}}");
        }
    }

    @Test
    public void testStepsAsAGroupItemSaysThereIsNoGroup() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        from:
                          uri: timer:tick
                          steps:
                            - steps:
                                - setHeader:
                                    name: a
                                    expression:
                                      constant:
                                        expression: "1"
                            - to:
                                uri: mock:a
                                steps:
                                  - log:
                                      message: hi
                    """);
            assertThat(errors).hasSize(2);
            for (Error e : errors) {
                assertThat(e.getMessage())
                        .contains("steps: is the list of a route or of an EIP that owns a pipeline")
                        .contains("step: {id: ..., steps: [...]} for a named group")
                        .doesNotContain("did you mean");
            }
        }
    }

    @Test
    public void testEipsDirectlyUnderStepGetOneMessageWithTheShape() throws Exception {
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        from:
                          uri: timer:tick
                          steps:
                            - step:
                                setHeader:
                                  name: a
                                  expression:
                                    constant:
                                      expression: "1"
                                split:
                                  expression:
                                    simple:
                                      expression: "${body}"
                                  steps:
                                    - log:
                                        message: hi
                    """);
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).getMessage())
                    .isEqualTo("step is the Step EIP, a named group: its EIPs go in its steps: list"
                               + " (step: {id: ..., steps: [- setHeader: ...]})");
        }
    }

    @Test
    public void testMisspelledOptionGetsTheClosestName() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                          loggerName: sensor
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("loggerName").contains("did you mean 'logName'?");
    }

    @Test
    public void testTopLevelEntryInsideARouteSaysWhereItGoes() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    id: r
                    onException:
                      exception:
                        - java.lang.Exception
                      steps:
                        - log:
                            message: "error"
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: "hi"
                """);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.getMessage().contains("'onException' is a top-level entry"));
    }

    @Test
    public void testFarOffNameGetsNoGuess() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - split:
                          tokenize: ","
                          cheese: true
                          steps:
                            - log:
                                message: "hi"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("cheese").doesNotContain("did you mean");
    }

    @Test
    public void testSecondDocumentIsReported() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                ---
                Run it with camel run and you will see hi every second.
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("line 6").contains("more than one YAML document");
        // a leading document marker is fine
        assertThat(validator.validate("""
                ---
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """)).isEmpty();
    }

    @Test
    public void testIndentedDocumentMarkerIsText() throws Exception {
        // a ... line in a script elides the rest of the code, it is text of the block scalar
        assertThat(validator.validate("""
                - beans:
                  - name: myBean
                    type: com.acme.MyBean
                    scriptLanguage: groovy
                    script: >
                      bean = new com.acme.MyBean()
                      ...
                      return bean
                """)).isEmpty();
    }

    @Test
    public void testMapWhereAListIsExpectedSaysHowToWriteIt() throws Exception {
        List<Error> errors = validator.validate("""
                route:
                  from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("array expected") && e.getMessage().contains("- route:"));

        errors = validator.validate("""
                - beans:
                    myBean:
                      type: "#class:com.example.MyBean"
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("beans is a list: - name: myBean"));

        errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      log:
                        message: "hi"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("steps is a list"));
    }

    @Test
    public void testBeanItemKeyedByNameSaysHowToWriteIt() throws Exception {
        List<Error> errors = validator.validate("""
                - beans:
                    - myBean:
                        type: "#class:com.example.MyBean"
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("a bean item is written as - name: myBean"));
    }

    @Test
    public void testTemplateBeansKeyedByNameSayHowToWriteThem() throws Exception {
        // CAMEL-24704: the beans of a routeTemplate and a templatedRoute are the same list as the top-level beans
        List<Error> errors = validator.validate("""
                - routeTemplate:
                    id: myTemplate
                    beans:
                      myBean:
                        type: "#class:com.example.MyBean"
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - to: "mock:result"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("beans is a list: - name: myBean"));

        errors = validator.validate("""
                - templatedRoute:
                    routeTemplateRef: myTemplate
                    beans:
                      - myBean:
                          type: "#class:com.example.MyBean"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("a bean item is written as - name: myBean"));

        errors = validator.validate("""
                - routeTemplate:
                    id: myTemplate
                    beans:
                      - id: myBean
                        type: "#class:com.example.MyBean"
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - to: "mock:result"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("(name instead of id)"));
    }

    @Test
    public void testJbangDepsLineIsNamed() throws Exception {
        List<Error> errors = validator.validate("""
                //DEPS org.apache.camel:camel-groovy

                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("line 1: //DEPS is read as text by YAML")
                .contains("# //DEPS org.apache.camel:camel-groovy").contains("camel.jbang.dependencies");
        // the comment form is what camel-jbang reads, and it is plain YAML
        assertThat(validator.validate("""
                # //DEPS org.apache.camel:camel-groovy
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """)).isEmpty();
    }

    @Test
    public void testProseAfterTheRoutesIsNamed() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"

                Run it with camel run and you will see hi every second.
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).startsWith("line 7 is not YAML (\"Run it with camel run").contains("# comment");
    }

    @Test
    public void testProseAfterAListItemNamesTheProseLine() throws Exception {
        // the parser reports the list item on line 1 first and the prose line last; the prose line is the problem
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: "timer:tick?period=5000"
                      steps:
                        - log:
                            message: "tick"

                To run it, execute in the same directory:
                camel run timer-log.camel.yaml
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).startsWith("line 8 is not YAML (\"To run it, execute").contains("# comment");
    }

    @Test
    public void testPropertyNameSuggestsName() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setProperty:
                          propertyName: attempt
                          constant: "1"
                """);
        assertThat(errors).anySatisfy(
                e -> assertThat(e.getMessage()).contains("property 'propertyName' is not defined")
                        .contains("did you mean 'name'?"));
    }

    @Test
    public void testSplitWithoutAnExpressionIsReported() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          constant: "a,b,c"
                      - split:
                          delimiter: ","
                          streaming: true
                          steps:
                            - log: "${body}"
                      - filter:
                          steps:
                            - log: "kept"
                """);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getInstanceLocation().toString()).isEqualTo("/0/from/steps/1/split");
        assertThat(errors.get(0).getMessage()).startsWith("split has no expression: write the language as a key")
                .contains("tokenize").contains("delimiter only applies");
        assertThat(errors.get(1).getMessage()).startsWith("filter has no expression");
    }

    @Test
    public void testExpressionNodesWithAnExpressionAreFine() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - split:
                          tokenize: ","
                          steps:
                            - log: "${body}"
                      - setHeader:
                          name: id
                          expression:
                            simple: "${exchangeId}"
                      - choice:
                          when:
                            - simple: "${body} != null"
                              steps:
                                - log: "x"
                """);
        assertThat(errors).isEmpty();
    }

    @Test
    public void testTwoKeysInOneStepSayAStepIsOneEip() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - to:
                          uri: file:input?fileName=customers.xml
                        steps: []
                      - log: "done"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("must have at most 1 properties")
                .contains("a step is one EIP"));
    }

    @Test
    public void testBeanAsALanguageSaysMethod() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          bean: myProcessor
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'bean' is not defined")
                .contains("the bean language is written as method:"));
        // the strict schema's oneOf and per-language required errors at the same node are dropped
        assertThat(errors).noneSatisfy(e -> assertThat(e.getMessage()).contains("0 are valid"));
        assertThat(errors).hasSize(1);
    }

    @Test
    public void testHeaderNameAsTheKeySaysName() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setHeader:
                          CamelNumberA:
                            constant: "5"
                      - bean:
                          ref: calc
                          parameters: [1, 2]
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'CamelNumberA' is not defined")
                .contains(
                        "the name is a property: setHeader: {name: CamelNumberA, expression: {simple: {expression: \"...\"}}}"));
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'parameters' is not defined")
                .contains("arguments are written in the method call"));
    }

    @Test
    public void testOtherwiseAsAListSaysSteps() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - simple: "${body} != null"
                              steps:
                                - log: "a"
                          otherwise:
                            - log: "b"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("array found, object expected")
                .contains("otherwise holds its EIPs under steps: otherwise: {steps: [- log: \"...\"]}"));
    }

    @Test
    public void testStepDirectlyUnderOtherwiseOrWhenSaysSteps() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - simple: "${body} != null"
                              log: "a"
                          otherwise:
                            log: "b"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'log' is not defined")
                .contains("the steps of otherwise go under steps: (otherwise: {steps: [- log: ...]})"));
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'log' is not defined")
                .contains("the steps of when go under steps:"));
    }

    @Test
    public void testStepsAtTheRouteLevelSaysUnderFrom() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    id: r
                    from:
                      uri: timer:tick
                    steps:
                      - log: "a"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'steps' is not defined")
                .contains("steps: goes under from:"));
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("required property 'steps' not found")
                .contains("indented under from:"));
    }

    @Test
    public void testSecondKeyAtTheTopLevelSaysIndent() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log: "a"
                - beans:
                  myBean:
                    type: "#class:com.example.MyBean"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("must have at most 1 properties")
                .contains("must be indented under it"));
    }

    @Test
    public void testLogComponentOptionOnTheLogEipSaysToStep() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "a"
                          showHeaders: true
                          level: INFO
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'showHeaders' is not defined")
                .contains("is an option of the log component, not of the log EIP")
                .contains("log:com.example?showHeaders=..."));
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'level' is not defined")
                .contains("did you mean 'loggingLevel'?"));
    }

    @Test
    public void testExceptionClassAsTheKeySaysList() throws Exception {
        List<Error> errors = validator.validate("""
                - onException:
                    java.lang.Exception:
                      handled:
                        constant: "true"
                - from:
                    uri: timer:tick
                    steps:
                      - log: "a"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'java.lang.Exception' is not defined")
                .contains(
                        "the exception class is a list item under exception: (onException: {exception: [java.lang.Exception]"));
    }

    @Test
    public void testSimpleSyntaxInsideGroovyIsNamed() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - groovy: "${body.value} < 1"
                              steps:
                                - log: "small"
                          otherwise:
                            steps:
                              - log: "big"
                      - setBody:
                          groovy: "body.value * 2"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("groovy: ${...} is simple syntax, not groovy: write the expression in groovy (body.value < 1")
                .contains("or use simple: {expression: \"${body.value} < 1\"}");
    }

    @Test
    public void testJsonPathWithInlinedSimpleIsValid() throws Exception {
        // the jsonpath language evaluates ${...} inside a JsonPath first (allowSimple, on by default)
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.store.book[?(@.price < ${header.max})]"
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.sku == '${header.sku}')]"
                                resultType: java.util.List
                """);
        assertThat(errors).isEmpty();

        // a whole ${...} is simple syntax, and so is any ${...} when allowSimple is off
        errors = validator.validate("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "${body.price}"
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$.store.book[?(@.price < ${header.max})]"
                                allowSimple: false
                """);
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).getMessage()).startsWith("jsonpath: ${...} is simple syntax, not jsonpath");
    }

    @Test
    public void testOnExceptionAsAListSaysMap() throws Exception {
        List<Error> errors = validator.validate("""
                - onException:
                    - exception:
                        - java.lang.Exception
                      steps:
                        - log: "oops"
                - from:
                    uri: timer:tick
                    steps:
                      - log: "a"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("array found, object expected")
                .contains("onException is a map, not a list").contains("several - onException: items"));
    }

    @Test
    public void testWhenItemWithoutAnExpressionIsReportedOnce() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - choice:
                          when:
                            - steps:
                                - log: "a"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getInstanceLocation().toString()).isEqualTo("/0/from/steps/0/choice/when/0");
        assertThat(errors.get(0).getMessage()).startsWith("when has no expression");
    }

    @Test
    public void testSortWithoutAnExpressionIsFine() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - sort:
                          comparator: "#cmp"
                      - log: "sorted"
                """);
        assertThat(errors).isEmpty();
    }

    @Test
    public void testResilienceOptionsOnTheCircuitBreakerSayConfiguration() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - circuitBreaker:
                          name: myBreaker
                          failureThreshold: 5
                          steps:
                            - to: "http://example.com"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'name' is not defined")
                .contains("the circuit breaker's name is its id"));
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'failureThreshold' is not defined")
                .contains("resilience4jConfiguration: {failureRateThreshold: ...}"));
    }

    @Test
    public void testRestConfigurationPropertyListItemSaysKeyAndValue() throws Exception {
        // CAMEL-24840: the map form already says "write it as a list"; the list item written as a map said nothing
        List<Error> errors = validator.validate("""
                - restConfiguration:
                    component: platform-http
                    bindingMode: json
                    dataFormatProperty:
                      - prettyPrint: "true"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'prettyPrint' is not defined")
                .contains("an item of dataFormatProperty is a key and a value: - key: prettyPrint followed by value:"));
    }

    @Test
    public void testLogMessageAsAnExpressionMapSaysPlainString() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message:
                            simple: "Body: ${body}"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("object found, string expected")
                .contains("message is a plain string that is already a simple expression"));
    }

    @Test
    public void testGroovyGStringInsideQuotesIsFine() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          groovy: |
                            def t = exchange.getIn().getHeader('type')
                            "type is ${t}"
                """);
        assertThat(errors).isEmpty();
    }

    @Test
    public void testScriptAsALanguageKeySaysLanguage() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          script: "body.toUpperCase()"
                """);
        assertThat(errors).anySatisfy(e -> assertThat(e.getMessage()).contains("property 'script' is not defined")
                .contains("script is an EIP step, not a language"));
    }

    @Test
    public void testValueContinuingAfterItsQuoteIsNamed() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: ">>> [INFO] " + exchange.getIn().getBody()
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).startsWith("line 5: the value of message continues after its closing quote")
                .contains("no concatenation");
    }

    @Test
    public void testALineWithNoKeyIsNamed() throws Exception {
        // r24 ai-ticket-classifier step 2: a setHeader with the bare line directory (meant as name: directory), told 15
        // times that the key below it was in the wrong column
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - choice:
                            when:
                                - simple: "${header.kind} == 'shipping'"
                                  steps:
                                    - setHeader:
                                        directory
                                        simple: "tickets-out/shipping"
                """;
        List<Error> errors = validator.validate(yaml);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("line 10: directory has no colon, so it is not a key")
                .endsWith("every line of setHeader is a key with its value - here write name: directory")
                .doesNotContain("every key of a mapping must start in the same column"));
        // the same at the top of the steps
        errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setHeader:
                          CamelFileName
                          simple: "${header.id}.json"
                """);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("line 5: CamelFileName has no colon").endsWith("here write name: CamelFileName"));
    }

    @Test
    public void testAnExceptionClassWithNoKeyIsAListItemOfException() throws Exception {
        // r25 openapi-server step 3: the class as a bare line under onException, told to write it as a key with a value
        String yaml = """
                - onException:
                    org.apache.camel.support.processor.PredicateValidationException
                    handled: true
                    steps:
                      - log: "invalid"
                """;
        assertThat(validator.validate(yaml)).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("line 2: org.apache.camel.support.processor.PredicateValidationException has no colon, so it"
                            + " is not a key")
                .endsWith("the exception classes of onException are a list under exception: - write line 2 as these"
                          + " two lines:\n    exception:\n      - org.apache.camel.support.processor"
                          + ".PredicateValidationException"));
        // a class with no package, and a word that is no class, which is a key with its value
        assertThat(validator.validate(yaml.replace("org.apache.camel.support.processor.", ""))).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).endsWith("\n      - PredicateValidationException"));
        assertThat(validator.validate(yaml.replace("org.apache.camel.support.processor.PredicateValidationException",
                "continued"))).singleElement().satisfies(e -> assertThat(e.getMessage())
                        .endsWith("every line of onException is a key with its value - here write continued: <value>"));
        // in a doCatch: next to the other keys of its item, and under doCatch itself
        String doCatch = """
                - from:
                    uri: direct:in
                    steps:
                      - doTry:
                          steps:
                            - to: direct:out
                          doCatch:
                            - onWhen:
                                simple: "${exception.message} contains 'stock'"
                              java.io.IOException
                              steps:
                                - log: "failed"
                """;
        assertThat(validator.validate(doCatch)).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("line 10: java.io.IOException has no colon")
                .endsWith("the exception classes of doCatch are a list under exception: - write line 10 as these two"
                          + " lines:\n              exception:\n                - java.io.IOException"));
        assertThat(validator.validate(doCatch.replace("""
                            - onWhen:
                                simple: "${exception.message} contains 'stock'"
                              java.io.IOException
                              steps:
                                - log: "failed"
                """, """
                              java.io.IOException
                              steps:
                                - log: "failed"
                """))).singleElement().satisfies(e -> assertThat(e.getMessage())
                .contains("the exception classes of doCatch are a list under exception:"));
        // the class as the list item itself, with the steps of the doCatch below it
        assertThat(validator.validate(doCatch.replace("""
                            - onWhen:
                                simple: "${exception.message} contains 'stock'"
                              java.io.IOException
                """, """
                            - java.io.IOException
                """))).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("line 8: java.io.IOException has no key")
                .endsWith("write line 8 as these two lines:\n            - exception:\n                - java.io.IOException"));
        // the class after the other keys of onException
        assertThat(validator.validate(yaml.replace("""
                    org.apache.camel.support.processor.PredicateValidationException
                    handled: true
                """, """
                    handled: true
                    org.apache.camel.support.processor.PredicateValidationException
                """))).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("line 3: org.apache.camel.support.processor.PredicateValidationException has no colon")
                .contains("the exception classes of onException are a list under exception:"));
    }

    @Test
    public void testAColonAfterTheClosingQuoteIsNamed() throws Exception {
        // r24 rest-code-first step 1: the jsonpath line copied with the colon of a key after it, told 5 times that a
        // YAML value has no concatenation
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - setBody:
                          jsonpath:
                            expression: "$[?(@.orderId == '${header.orderId}')]":
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 6: the value of expression ends with a : after its closing quote")
                .endsWith("write the line as expression: \"$[?(@.orderId == '${header.orderId}')]\"")
                .doesNotContain("concatenation");
    }

    @Test
    public void testTwoGetsWithTheSamePathInOneRest() throws Exception {
        // r24 rest-code-first step 1: a second get with no path:, which Camel refuses when it loads the routes
        String yaml = """
                - rest:
                    path: /orders
                    get:
                      - to: direct:list-orders
                      - to: direct:get-order
                - route:
                    from:
                      uri: direct:list-orders
                      steps:
                        - log: "list"
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - log: "one"
                """;
        List<Error> errors = validator.validate(yaml);
        assertThat(errors).singleElement().satisfies(e -> assertThat(e.getMessage())
                .contains("rest: a second get with no path:").contains("Duplicate verb detected in rest-dsl")
                .contains("path: /{id}"));
        String oneOrder = "      - to: direct:get-order";
        String withPath = "      - path: /{id}\n        to: direct:get-order";
        assertThat(validator.validate(yaml.replace(oneOrder, withPath))).isEmpty();
        // the same path twice
        assertThat(validator.validate(yaml.replace(oneOrder, withPath).replace("      - to: direct:list-orders",
                "      - path: /{id}\n        to: direct:list-orders"))).singleElement()
                .satisfies(e -> assertThat(e.getMessage()).contains("a second get with the path /{id}"));
        // a get and a post with no path are two verbs, which is fine
        assertThat(validator.validate(yaml.replace(oneOrder, "    post:\n" + oneOrder))).isEmpty();
    }

    @Test
    public void testTextAfterAJsonPathIsNamed() throws Exception {
        // r25 rest-code-first step 1: the words of a message copied into the text of the path, so that every request
        // failed with "InvalidPathException: Could not parse token starting at position 29"
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.orderId}')] {resultType: java.util.Map}"
                """;
        assertThat(validator.validate(yaml)).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("the path ends at position 38, and the text after it is not part of a path:"
                            + " {resultType: java.util.Map}. The runtime reads the whole text as the path and fails"
                            + " with InvalidPathException. resultType is an option of jsonpath")
                .endsWith(" as:\njsonpath:\n  expression: \"$[?(@.orderId == '${header.orderId}')]\"\n"
                          + "  resultType: java.util.Map"));
        // the short form, and text that is no option: the line without it
        assertThat(validator.validate(yaml.replace("""
                              jsonpath:
                                expression: "$[?(@.orderId == '${header.orderId}')] {resultType: java.util.Map}"
                """, """
                              jsonpath: "$.orders[0] as the first order"
                """))).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("the path ends at position 11, and the text after it is not part of a path: as the first"
                            + " order.")
                .endsWith("Write the line as:\njsonpath: \"$.orders[0]\""));
        // a bracket or a quote that is not closed
        assertThat(validator.validate(yaml.replace("')] {resultType: java.util.Map}", "')")))
                .singleElement().satisfies(e -> assertThat(e.getMessage())
                        .startsWith("the [ at position 1 has no closing ], so the text is not a path"));
        assertThat(validator.validate(yaml.replace("${header.orderId}')] {resultType: java.util.Map}", "ORD-1)]")))
                .singleElement().satisfies(e -> assertThat(e.getMessage())
                        .startsWith("the ' at position 17 has no closing '"));
        // the JSON answer of a 404 written as jsonpath, which failed on a null body
        String text = "$[?(@.orderId == '${header.orderId}')] {resultType: java.util.Map}";
        String json = yaml.replace("\"" + text + "\"", "'{\"error\": \"unknown order ${header.id}\"}'");
        assertThat(validator.validate(json)).singleElement().satisfies(e -> assertThat(e.getMessage())
                .startsWith("the text is a JSON text, not a path")
                .endsWith("as:\nsimple:\n  expression: '{\"error\": \"unknown order ${header.id}\"}'"));
        // what a path has: spaces and inlined simple in a filter, a regular expression with brackets, a function, a
        // placeholder, and in a when the easy predicate syntax with an operator after the path
        for (String path : List.of(
                "$[?(@.orderId == '${header.orderId}' && @.status != 'paid')]",
                "$.store.book[?(@.price < ${header.max})]", "$.store.book[?(@.author =~ /[(]REES/i)].title",
                "$.lines.length()", "$['first name']", "$.{{field}}", "$.a)", "  $.lines[*].sku  ", "$.status == 'paid'",
                "store.qty > 10", "$[?(@.qty > 1)] && $[?(@.qty < 5)]")) {
            assertThat(validator.validate(yaml.replace(text, path.replace("\"", "\\\"")))).as(path).isEmpty();
        }
    }

    @Test
    public void testAPathParameterWrittenAsTextIsNamed() throws Exception {
        // r25 rest-code-first step 1: '{id}' for the path parameter, so every id was answered with 404 "unknown order
        // {id}"
        String yaml = """
                - rest:
                    path: /orders
                    get:
                      - path: "{id}"
                        to:
                          uri: direct:get-order
                - route:
                    id: get-order
                    from:
                      uri: direct
                      parameters:
                        name: get-order
                      steps:
                        - setBody:
                            constant: "resource:file:orders.json"
                        - unmarshal:
                            json: {}
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$[?(@.orderId == '{id}')]"
                                resultType: java.util.Map
                        - filter:
                            simple: "${body} == null"
                            steps:
                              - setBody:
                                  simple: '{"error": "unknown order {id}"}'
                """;
        String said = "{id} is the literal text; the path parameter is the header ${header.id}. Write the line as:\n";
        assertThat(validator.validate(yaml)).extracting(Error::getMessage).containsExactly(
                said + "expression: \"$[?(@.orderId == '${header.id}')]\"",
                said + "simple: '{\"error\": \"unknown order ${header.id}\"}'");
        // a constant evaluates nothing: the lines with simple
        String constant = yaml.replace("""
                              - setBody:
                                  simple: '{"error": "unknown order {id}"}'
                """, """
                              - setBody:
                                  constant:
                                    expression: '{"error": "unknown order {id}"}'
                """).replace("{id}')]", "${header.id}')]");
        assertThat(validator.validate(constant)).singleElement().satisfies(e -> assertThat(e.getMessage())
                .isEqualTo("{id} is the literal text; the path parameter is the header ${header.id}, and constant does"
                           + " not evaluate it, simple does. Write the lines as:\nsimple:\n"
                           + "  expression: '{\"error\": \"unknown order ${header.id}\"}'"));
        // what is not the path parameter: a property placeholder, a simple function, a path in a log message, a name
        // that is no path parameter, and a route that no operation reaches
        String right = yaml.replace("'{id}')]", "'${header.id}')]")
                .replace("unknown order {id}", "unknown order {{shop.name}} ${id} for GET /orders/{id} {sku}");
        assertThat(validator.validate(right)).isEmpty();
        String unreached = yaml.replace("uri: direct:get-order", "uri: direct:other")
                           + "- route:\n    from:\n      uri: direct:other\n      steps:\n        - log: \"other\"\n";
        assertThat(validator.validate(unreached)).isEmpty();
    }

    @Test
    public void testJsonAsALanguageSaysItIsADataFormat() throws Exception {
        // r25 rest-code-first step 1: the JSON answer of a 404 under json:, told "did you mean 'js'?" 4 times in a row
        String yaml = """
                - route:
                    from:
                      uri: direct:get-order
                      steps:
                        - setBody:
                            expression:
                              json:
                                library: Jackson
                                expression: '{"error": "unknown order ${header.id}"}'
                """;
        for (YamlValidator v : bothModes()) {
            assertThat(v.validate(yaml)).singleElement().satisfies(e -> assertThat(e.getMessage())
                    .startsWith("json is a data format, not a language (marshal: json writes a Map or a List as"
                                + " JSON). Set a JSON text with simple, as it has ${...}, and set the header"
                                + " Content-Type to application/json if the caller needs it.")
                    .endsWith(" as:\nsimple:\n  expression: '{\"error\": \"unknown order ${header.id}\"}'")
                    .doesNotContain("did you mean"));
        }
        // a text with no ${...} is a constant
        assertThat(validator.validate(yaml.replace("unknown order ${header.id}", "unknown order")))
                .singleElement().satisfies(e -> assertThat(e.getMessage())
                        .endsWith(" as:\nconstant:\n  expression: '{\"error\": \"unknown order\"}'"));
    }

    @Test
    public void testFileAsALanguageSaysToReadTheFileWithConstant() throws Exception {
        // r20 to r26 connect-service-jms-request-reply step 3: stock.json read with file: under expression (5 of 7 runs
        // in r26), told only that file is not defined in the schema
        String yaml = """
                - route:
                    from:
                      uri: jms:queue:stock
                      steps:
                        - setVariable:
                            name: stock
                            expression:
                              file:
                                filePath: stock.json
                """;
        for (YamlValidator v : bothModes()) {
            assertThat(v.validate(yaml)).singleElement().satisfies(e -> assertThat(e.getMessage())
                    .isEqualTo("file is not a language. To read a data file the route needs, such as a lookup table, use"
                               + " the constant language with resource:file: (the value is the text of the file). Write"
                               + " file: and the lines under it as:\nconstant:\n  expression: \"resource:file:stock.json\""));
        }
        // the file as the value of file:, and file: as the language key of the EIP itself
        assertThat(validator.validate(yaml.replace("file:\n                filePath: stock.json",
                "file: stock.json"))).singleElement().satisfies(e -> assertThat(e.getMessage())
                        .endsWith("as:\nconstant:\n  expression: \"resource:file:stock.json\""));
        assertThat(validator.validate("""
                - route:
                    from:
                      uri: jms:queue:stock
                      steps:
                        - setBody:
                            file: "stock.json"
                """)).anySatisfy(e -> assertThat(e.getMessage())
                .endsWith("as:\nexpression:\n  constant:\n    expression: \"resource:file:stock.json\""));
        // a file name with ${...}, which constant does not evaluate
        assertThat(validator.validate(yaml.replace("filePath: stock.json", "filePath: orders/${header.orderId}.json")))
                .singleElement().satisfies(e -> assertThat(e.getMessage())
                        .endsWith("A file name with ${...} is read with pollEnrich instead, as constant does not evaluate"
                                  + " it: pollEnrich: {expression: {simple: {expression:"
                                  + " \"file:orders?fileName=${header.orderId}.json&noop=true\"}}}"));
        // the file of the script EIP is a script to run, not data
        assertThat(validator.validate(yaml.replace("- setVariable:\n            name: stock\n", "- script:\n")))
                .isNotEmpty().allSatisfy(e -> assertThat(e.getMessage()).doesNotContain("file is not a language"));
    }

    @Test
    public void testStepsUnderChoiceSaysWhenAndOtherwise() throws Exception {
        // r26 contracts-openapi-server step 4: told that choice is an EIP that owns steps:, a model wrote it 6 times
        for (YamlValidator v : bothModes()) {
            List<Error> errors = v.validate("""
                    - route:
                        from:
                          uri: direct:reserve
                          steps:
                            - choice:
                                steps:
                                  - log:
                                      message: hi
                    """);
            assertThat(errors).singleElement().satisfies(e -> assertThat(e.getMessage())
                    .contains("choice has no steps: of its own: write the steps under a when: (next to its expression:) or"
                              + " under otherwise:")
                    .doesNotContain("owns a pipeline"));
        }
    }

    @Test
    public void testLanguageAsAPropertySaysToUseItAsTheKey() throws Exception {
        List<Error> errors = validator.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - script:
                          language: groovy
                          text: "println 'hi'"
                """);
        assertThat(errors).anyMatch(
                e -> e.getMessage().contains("the language as the key")
                        && e.getMessage().contains("groovy: {expression: \"...\"}"));
    }

    @Test
    public void testBeanWithIdInsteadOfNameSaysSo() throws Exception {
        List<Error> errors = validator.validate("""
                - beans:
                    - id: myBean
                      type: "#class:com.example.MyBean"
                - from:
                    uri: timer:tick
                    steps:
                      - log:
                          message: "hi"
                """);
        assertThat(errors).anyMatch(e -> e.getMessage().contains("name instead of id"));
    }

    @Test
    public void testEmptyFileSaysWhatAFileIs() throws Exception {
        List<Error> errors = validator.validate("# nothing here yet\n");
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("no YAML").contains("- route:");
    }

    // CAMEL-24847: a data format named as its artifact or catalog entry says which YAML key and option to write

    private static String unmarshalError(YamlValidator v, String key) throws Exception {
        List<Error> errors = v.validate("""
                - from:
                    uri: timer:tick
                    steps:
                      - unmarshal:
                          %s: {}
                """.formatted(key));
        assertThat(errors).as(key).hasSize(1);
        return errors.get(0).getMessage();
    }

    @Test
    public void testDataFormatLibraryNameSaysTheKeyAndTheLibrary() throws Exception {
        assertThat(unmarshalError(validator, "jackson"))
                .contains("property 'jackson' is not defined")
                .contains("the data format is json, Jackson is its library: write json: {library: Jackson}");
        assertThat(unmarshalError(validator, "gson")).contains("write json: {library: Gson}");
        assertThat(unmarshalError(validator, "json-b")).contains("write json: {library: Jsonb}");
        assertThat(unmarshalError(validator, "protobuf-jackson")).contains("write protobuf: {library: Jackson}");
        assertThat(unmarshalError(validator, "jackson-avro")).contains("write avro: {library: Jackson}");
        assertThat(unmarshalError(validator, "bindy-csv"))
                .contains("the data format is bindy, Csv is its type: write bindy: {type: Csv}");
        assertThat(unmarshalError(validator, "snake-yaml")).contains("the data format is yaml: write yaml: {...}");
    }

    @Test
    public void testDataFormatArtifactNameSaysTheKeyAndTheLibrary() throws Exception {
        // json-jackson is the artifact, not a data format name: the catalog's suggestion is jackson
        assertThat(unmarshalError(validator, "json-jackson"))
                .contains("the data format is json, Jackson is its library: write json: {library: Jackson}");
    }

    @Test
    public void testDataFormatKeySpelledDifferentlyGetsTheKey() throws Exception {
        assertThat(unmarshalError(validator, "jackson-xml")).contains("did you mean 'jacksonXml'?");
        assertThat(unmarshalError(validator, "JSON")).contains("did you mean 'json'?");
        assertThat(unmarshalError(validator, "base-64")).contains("did you mean 'base64'?");
    }

    @Test
    public void testDataFormatWordListsTheCatalogMatches() throws Exception {
        assertThat(unmarshalError(validator, "xml")).contains("did you mean fhirXml, groovyXml or jacksonXml?");
        assertThat(unmarshalError(validator, "zip")).contains("did you mean zipDeflater, zipFile or gzipDeflater?");
        assertThat(unmarshalError(validator, "gzip")).contains("did you mean 'gzipDeflater'?");
    }

    @Test
    public void testDataFormatTypoGetsTheClosestKey() throws Exception {
        assertThat(unmarshalError(validator, "jsn")).contains("did you mean 'json'?");
        assertThat(unmarshalError(validator, "yml")).contains("did you mean 'yaml'?");
    }

    @Test
    public void testUnknownDataFormatSaysWhatTheKeyIs() throws Exception {
        assertThat(unmarshalError(validator, "xstream"))
                .contains("the key of unmarshal is the data format: json, jacksonXml, csv, yaml")
                .contains("camel catalog dataformat");
    }

    @Test
    public void testCanonicalDataFormatHintIsNotFollowedByTheListOfEveryDataFormat() throws Exception {
        YamlValidator canonical = new YamlValidator(true);
        List<Error> errors = canonical.validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - marshal:
                            json-jackson: {}
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .contains("write json: {library: Jackson}")
                .doesNotContain("must have exactly one of");
    }

    @Test
    public void testDistance() {
        assertThat(YamlValidator.distance("loggername", "logname")).isEqualTo(3);
        assertThat(YamlValidator.closest("loggerName", java.util.Set.of("logName", "message", "marker"))).isEqualTo("logName");
        assertThat(YamlValidator.closest("cheese", java.util.Set.of("steps", "id"))).isNull();
    }

    @Test
    void otherJbangDirectivesGetTheCommentHintWithoutTheDependencyAdvice() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                //SOURCES MyBean.java
                - from:
                    uri: timer:tick
                    steps:
                      - log: hi
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("//SOURCES is read as text by YAML")
                .contains("# //SOURCES MyBean.java").doesNotContain("--dep");
    }

    @Test
    void aMissingFileIsOneError() throws Exception {
        List<Error> errors = new YamlValidator().validate(new java.io.File("no-such-file.camel.yaml"));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage()).contains("no-such-file.camel.yaml");
    }

    /**
     * CAMEL-24837: a list item indented differently from the first item of its list gets the raw snakeyaml "expected
     * <block end>, but found '-'"; say which list it belongs to and that the items share one column.
     */
    @Test
    void aListItemInAnotherColumnNamesTheListItBelongsTo() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - choice:
                            when:
                              - simple: "${body} > 1"
                                steps:
                                  - log:
                                      message: big
                            - simple: "${body} > 2"
                              steps:
                                - log:
                                    message: bigger
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 11 (\"            - simple: \"${body} > 2\"\"): this list item starts in column 13")
                .contains(
                        "the list that starts at line 7 (\"              - simple: \"${body} > 1\"\") has its items in column 15")
                .contains("every item of a list must start in the same column")
                .doesNotContain("block end");
    }

    /** CAMEL-26384: a long line is quoted only up to 120 characters, so the hint does not grow with the value. */
    @Test
    void aLongListItemLineIsCutInTheHint() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: a
                          - log: "%s"
                """.formatted("x".repeat(200)));
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 7 (\"          - log: \"xxx").contains("...\"): this list item starts in column 11")
                .doesNotContain("x".repeat(120));
    }

    /** CAMEL-24837: the item is over-indented, so the list it belongs to is the shallower one above it. */
    @Test
    void anOverIndentedListItemNamesTheListAboveIt() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: a
                          - log:
                              message: b
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 7 (\"").contains("\"): this list item starts in column 11")
                .contains("the list that starts at line 5 (\"").contains("\") has its items in column 9");
    }

    /**
     * CAMEL-24837: a key indented differently from its siblings gets "expected <block end>, but found '&lt;block
     * mapping start&gt;'"; name the key and the column its siblings are in.
     */
    @Test
    void aKeyInAnotherColumnNamesTheMappingItBelongsTo() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: hi
                     id: foo
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 7: id starts in column 6")
                .contains("the keys of the mapping that starts at line 2 are in column 5")
                .contains("every key of a mapping must start in the same column")
                .doesNotContain("block mapping start");
    }

    /**
     * CAMEL-24837: a key indented deeper than its siblings gets "mapping values are not allowed here", the same message
     * as a colon inside a value; the marker is on the key's own colon, so it is the indentation.
     */
    @Test
    void anOverIndentedKeySaysItIsTheIndentation() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                       steps:
                        - log:
                            message: hi
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 4: steps starts in column 8")
                .contains("the keys of the mapping that starts at line 3 are in column 7")
                .doesNotContain("mapping values are not allowed here");
    }

    /**
     * CAMEL-24837: the other cause of "mapping values are not allowed here" is a colon inside an unquoted value; the
     * marker is on a later colon of the line, not on the key's own.
     */
    @Test
    void aColonInsideAnUnquotedValueSaysToQuoteIt() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - log:
                            message: hello: world
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 6: the value of message holds a colon")
                .contains("\"hello: world\"")
                .doesNotContain("mapping values are not allowed here");
    }

    /**
     * CAMEL-24837: a backslash inside double quotes is an escape character; the value is meant literally, so it goes in
     * single quotes.
     */
    @Test
    void aBackslashInDoubleQuotesSaysToUseSingleQuotes() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: "file:orders?include=.*\\.json"
                      steps:
                        - log:
                            message: hi
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 3: \\. inside double quotes is an escape character")
                .contains("'file:orders?include=.*\\.json'")
                .doesNotContain("unknown escape character");
    }

    /** CAMEL-24837: a tab used for indentation, said in YAML words instead of "cannot start any token". */
    @Test
    void aTabUsedForIndentationIsNamed() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                \t      uri: timer:tick
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 3: the indentation uses a tab")
                .contains("YAML indents with spaces")
                .doesNotContain("cannot start any token");
    }

    /**
     * CAMEL-24837: a list whose items start with a bare "-" on its own line is still the list the stray item belongs
     * to; naming the nested list instead would point at the wrong place.
     */
    @Test
    void aListWrittenWithBareDashesIsStillTheListThatIsNamed() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - choice:
                            when:
                              -
                                simple: "${body} > 1"
                                steps:
                                  - log:
                                      message: big
                            - simple: "${body} > 2"
                              steps:
                                - log:
                                    message: bigger
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 12 (\"").contains("\"): this list item starts in column 13")
                .contains("the list that starts at line 7 (\"").contains("\") has its items in column 15");
    }

    /**
     * CAMEL-24837: the parser names the stray item "&lt;block sequence start&gt;" instead of "-" when the list it broke
     * uses bare dashes; it is the same mistake and gets the same message.
     */
    @Test
    void aStrayItemReportedAsABlockSequenceStartIsNamedToo() throws Exception {
        List<Error> errors = new YamlValidator().validate("""
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        -
                          log:
                            message: hi
                         - log:
                             message: there
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 8 (\"").contains("\"): this list item starts in column 10")
                .contains("the list that starts at line 5 (\"").contains("\") has its items in column 9")
                .doesNotContain("block sequence start");
    }

    /** A language written as a step: no "did you mean 'sample'?", but where an expression goes. */
    @Test
    void aLanguageWrittenAsAStepSaysWhereItGoes() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:stock
                      steps:
                        - setBody:
                            expression:
                              jsonpath:
                                expression: "$"
                        - simple: "${body}"
                        - log: "Stock service processing order"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .contains("'simple' is a language, not a step")
                .contains("- setBody: {expression: {simple: {expression: \"${body}\"}}}")
                .doesNotContain("did you mean");
    }

    /** - stop as a step gets the raw "string found, object expected": a step is a key followed by its options. */
    @Test
    void aStepWrittenAsAPlainNameSaysToWriteItAsAKey() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - filter:
                            expression:
                              simple:
                                expression: "${body} == null"
                            steps:
                              - stop
                        - to
                        - halt
                """);
        assertThat(errors).extracting(Error::getMessage).containsExactly(
                "a step is a key followed by its options: write - stop: {} (the options of the step, if any, go inside"
                                                                         + " the braces)",
                "a step is a key followed by its options: write - to: {uri: \"...\"}",
                "a step is a key followed by its options, such as - log: {message: \"...\"} or - stop: {}; halt is not a"
                                                                                       + " step");
    }

    /**
     * A key in the column of the dashes of a list gets "expected <block end>, but found '?'": steps: under the -
     * simple: of a when:, meant for the item.
     */
    @Test
    void aKeyInTheColumnOfTheDashesOfAListNamesBothColumns() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - choice:
                            when:
                              - simple: "${body} == 'a'"
                              steps:
                                - log: "a"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .isEqualTo("line 8: steps starts in column 15, the column of the - of the list item at line 7, so it is"
                           + " part of neither that item nor the key that holds the list. Write it in column 17 to make"
                           + " it a key of the item, as simple is, or in column 13 to make it a key next to when"
                           + " (line 6)");
    }

    /** onFallback: under the - to: of the steps: of a circuitBreaker, meant for the circuit breaker. */
    @Test
    void aKeyInTheColumnOfTheDashesOfAListCanBelongToTheParent() throws Exception {
        List<Error> errors = validator.validate("""
                - route:
                    from:
                      uri: direct:start
                      steps:
                        - circuitBreaker:
                            steps:
                              - to:
                                  uri: http://localhost:8080/x
                              onFallback:
                                steps:
                                  - log: "fallback"
                """);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getMessage())
                .startsWith("line 9: onFallback starts in column 15, the column of the - of the list item at line 7")
                .endsWith("as to is, or in column 13 to make it a key next to steps (line 6)");
    }
}
