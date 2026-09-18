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
}
