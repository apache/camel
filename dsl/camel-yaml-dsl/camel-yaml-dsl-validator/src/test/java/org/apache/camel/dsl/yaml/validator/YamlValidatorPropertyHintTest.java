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
                .startsWith("line 11: this list item starts in column 13")
                .contains("the list that starts at line 7 has its items in column 15")
                .contains("every item of a list must start in the same column")
                .doesNotContain("block end");
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
                .startsWith("line 7: this list item starts in column 11")
                .contains("the list that starts at line 5 has its items in column 9");
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
                .startsWith("line 12: this list item starts in column 13")
                .contains("the list that starts at line 7 has its items in column 15");
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
                .startsWith("line 8: this list item starts in column 10")
                .contains("the list that starts at line 5 has its items in column 9")
                .doesNotContain("block sequence start");
    }
}
