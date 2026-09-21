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

import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SourceValidatorEndpointTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    @Test
    void validExpandedFormNoErrors() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                      fixedRate: true
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void queryOptionsInTheUriAndAParametersBlock() {
        // CAMEL-24842: the runtime refuses options in both places; the schema does not see it
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: file://inbox?fileExist=Override
                            parameters:
                              fileName: invoice-2001.json
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 6: ")
                .contains("query options (fileExist=Override)")
                .contains("parameters: (fileExist: Override)")
                .contains("Uri should not contains query parameters");
    }

    @Test
    void queryOptionsInTheUriAndAParametersBlockOnFrom() {
        String yaml = """
                - from:
                    uri: "timer:t?period=1000"
                    parameters:
                      repeatCount: 1
                    steps:
                      - log: "hello"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 2: ").contains("(period: 1000)");
    }

    @Test
    void expandedFormUnknownOption() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                      badOption: xyz
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
        assertThat(errors.get(0)).containsIgnoringCase("unknown");
    }

    @Test
    void expandedFormInvalidBoolean() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      fixedRate: notABoolean
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
    }

    @Test
    void inlineUriNoErrors() {
        String yaml = """
                - from: timer:tick?period=1000
                  steps:
                    - to: log:myLogger
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void inlineUriUnknownOption() {
        String yaml = """
                - from: timer:tick?badOption=xyz
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("timer:");
        assertThat(errors.get(0)).containsIgnoringCase("unknown");
    }

    @Test
    void expandedUriWithQueryParamsNoErrors() {
        String yaml = """
                - from:
                    uri: timer:tick?period=1000
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void expandedUriWithQueryParamsAndParametersBlock() {
        // the options are valid, but the runtime refuses them in both places (CAMEL-24842)
        String yaml = """
                - from:
                    uri: timer:tick?period=1000
                    parameters:
                      fixedRate: true
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 2: ").contains("query options (period=1000)");
    }

    @Test
    void expandedUriWithQueryParamsAndBadParameter() {
        String yaml = """
                - from:
                    uri: timer:tick?period=1000
                    parameters:
                      badOption: xyz
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        // the mix is reported, and the unknown option still is
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0)).contains("query options (period=1000)");
        assertThat(errors.get(1)).contains("timer:").containsIgnoringCase("unknown");
    }

    @Test
    void multipleEndpointsValidatedIndependently() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                  steps:
                    - to:
                        uri: log:myLogger
                        parameters:
                          badOption: xyz
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors).allSatisfy(e -> assertThat(e).contains("log:"));
        assertThat(errors).noneSatisfy(e -> assertThat(e).contains("timer:"));
    }

    @Test
    void placeholderUriSkipped() {
        String yaml = """
                - from: "{{myUri}}"
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void placeholderValueSkipped() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: "{{myPeriod}}"
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void quotedParameterValues() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: "1000"
                      fixedRate: 'true'
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void inlineToWithDash() {
        String yaml = """
                - from: timer:tick?period=1000
                  steps:
                    - to: seda:myQueue?badOption=xyz
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("seda:");
    }

    @Test
    void validRouteNoEndpointErrors() {
        String yaml = """
                - from:
                    uri: timer:tick
                    parameters:
                      period: 5000
                      repeatCount: 1
                  steps:
                    - setBody:
                        simple: "Hello World"
                    - to:
                        uri: seda:result
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void schemeOnlyUriWithParameters() {
        String yaml = """
                - route:
                    id: timer-log
                    from:
                      uri: timer
                      parameters:
                        timerName: tick
                        period: 1000
                        bridgeErrorHandler2: 123
                      steps:
                        - log:
                            message: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isNotEmpty();
        assertThat(errors).anyMatch(e -> e.contains("timer:") && e.contains("bridgeErrorHandler2"));
    }

    @Test
    void schemeOnlyUriValidOptions() {
        String yaml = """
                - route:
                    id: timer-log
                    from:
                      uri: timer
                      parameters:
                        timerName: tick
                        period: 1000
                        fixedRate: true
                      steps:
                        - log:
                            message: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @Test
    void commentsIgnored() {
        String yaml = """
                # from: timer:tick?badOption=xyz
                - from:
                    uri: timer:tick
                    parameters:
                      period: 1000
                  steps:
                    - log: "${body}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors).isEmpty();
    }

    @org.junit.jupiter.api.Test
    void consumerOptionOnAToSaysHowToReadInsteadOfWrite() {
        String yaml = """
                - from:
                    uri: timer:tick
                    steps:
                      - to:
                          uri: "file:data?fileName=input.xml&noop=true"
                """;
        java.util.List<String> msgs
                = SourceValidator.validateYamlEndpoints(yaml, new org.apache.camel.catalog.DefaultCamelCatalog());
        org.assertj.core.api.Assertions.assertThat(msgs).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(msgs.get(0))
                .contains("not applicable in producer only mode")
                .contains("it writes the body")
                .contains("poll EIP");
    }

    @org.junit.jupiter.api.Test
    void componentDocSaysWhereItGoes() {
        org.apache.camel.catalog.CamelCatalog catalog = new org.apache.camel.catalog.DefaultCamelCatalog();
        org.assertj.core.api.Assertions.assertThat(CatalogDocs.usage(catalog.componentModel("file"))).contains("from: consumes")
                .contains("poll EIP");
        org.assertj.core.api.Assertions.assertThat(CatalogDocs.usage(catalog.componentModel("timer")))
                .startsWith("consumer only");
        org.assertj.core.api.Assertions.assertThat(CatalogDocs.usage(catalog.componentModel("log")))
                .startsWith("producer only");
    }

    @Test
    void anInventedOptionSaysWhatTheComponentDoesInstead() {
        List<String> msgs = SourceValidator.validateYamlEndpoints("""
                - from:
                    uri: timer:tick?interval=1000&body=hello
                    steps:
                      - to:
                          uri: file:output?fileName=report.xml&mkdir=true&body=hello
                """, catalog);
        assertThat(msgs).hasSize(4);
        assertThat(msgs.get(3)).contains("Unknown option 'body'").contains("setBody step before the to: file: step");
        assertThat(msgs.get(0)).contains("Unknown option 'interval'").contains("write period=<millis>");
        assertThat(msgs.get(1)).contains("Unknown option 'body'").contains("setBody step");
        assertThat(msgs.get(2)).contains("Unknown option 'mkdir'").contains("autoCreate=true");
    }

    @Test
    void aWildcardInTheFileIncludeOptionIsNamedAsNotARegex() {
        List<String> msgs = SourceValidator.validateYamlEndpoints("""
                - from:
                    uri: file:input?include=*.txt&noop=true
                    steps:
                      - log: "${body}"
                - from:
                    uri: file:other?include=.*\\.txt
                    steps:
                      - log: "${body}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("include=*.txt is not a regular expression")
                .contains("write include=.*\\.txt").contains("antInclude=*.txt");
    }

    @Test
    void severalEndpointsInOneToAreNamed() {
        List<String> msgs = SourceValidator.validateYamlEndpoints("""
                - from:
                    uri: timer:tick
                    steps:
                      - to:
                          uri: direct:processA,direct:processB
                      - to:
                          uri: "log:a?showAll=true"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("a to: takes one endpoint").contains("multicast: {to: [...]}")
                .contains("recipientList");
    }

    @Test
    void aHeaderNoComponentSetsIsNamedWithTheClosest() {
        List<String> msgs = SourceValidator.validateKnownHeaders("""
                - from:
                    uri: "file:in?noop=true"
                    steps:
                      - log: "${header.CamelFileNam} ${header.CamelFileName} ${header.MyOwn}"
                      - setHeader:
                          name: CamelFilePathX
                          simple: "x"
                """, catalog);
        assertThat(msgs).hasSize(2);
        assertThat(msgs.get(0)).contains("header CamelFileNam is not set by file").contains("did you mean CamelFileName")
                .contains("The file headers are");
        assertThat(msgs.get(1)).contains("header CamelFilePathX is not set by file");
    }

    @Test
    void aTimerExchangePropertyUsedAsAHeaderIsNamed() {
        // TimerConsumer sets the counter, name, period and time as exchange properties; only the fired time is a header
        List<String> msgs = SourceValidator.validateKnownHeaders("""
                - from:
                    uri: "timer:tick?period=1000"
                    steps:
                      - log: "${header.CamelTimerCounter} ${header.CamelTimerFiredTime} ${exchangeProperty.CamelTimerName}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("CamelTimerCounter is an exchange property set by timer, not a header")
                .contains("${exchangeProperty.CamelTimerCounter}");
    }

    @Test
    void anInventedHeaderForTheSourceEndpointGetsTheExpressionThatAnswersIt() {
        // a timer-triggered route has no message source to name, so a beginner or a model invents CamelFromEndpoint for
        // "which endpoint did this come from"; nothing is close, so the message must say what does answer it and what
        // the timer sets
        List<String> msgs = SourceValidator.validateKnownHeaders("""
                - from:
                    uri: "timer:tick?period=1000"
                    steps:
                      - log: "Endpoint: ${header.CamelFromEndpoint} size: ${body.length()}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("header CamelFromEndpoint is not set by timer").doesNotContain("did you mean")
                .contains("${exchange.fromEndpoint}").contains("${routeId}").contains("${header.CamelToEndpoint}")
                .contains("The timer headers are CamelTimerFiredTime")
                .contains(
                        "timer sets the exchange properties CamelTimerCounter, CamelTimerName, CamelTimerPeriod, CamelTimerTime")
                .contains("${exchangeProperty.CamelTimerCounter}")
                .contains("stays null unless a setHeader step sets it earlier in the route");
    }

    @Test
    void anInventedHeaderWithTwoComponentsListsTheTriggersHeaders() {
        // the first component in the file is the trigger; its headers are listed even when nothing is close
        List<String> msgs = SourceValidator.validateKnownHeaders("""
                - from:
                    uri: "file:in?noop=true"
                    steps:
                      - log: "${header.CamelEndpointUri} ${header.CamelFileName}"
                      - to:
                          uri: "timer:ignored"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("header CamelEndpointUri is not set by file, timer")
                .contains("${exchange.fromEndpoint}").contains("The file headers are").contains("CamelFileName");
    }

    @Test
    void anInventedSizeHeaderNamesTheBodyFunctions() {
        List<String> msgs = SourceValidator.validateKnownHeaders("""
                - from:
                    uri: "timer:tick?period=1000"
                    steps:
                      - log: "${header.CamelPayloadSize}"
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("${body.length()}").contains("${headers.size()}");
    }

    @Test
    void aPropertyOrBeanNameIsNotAHeader() {
        // CAMEL-24710: name: counts as a header only under setHeader/removeHeader; a dotted name is looked up as is,
        // by the prefix the component documents (CamelSolrField.), and as the head of an OGNL path
        List<String> msgs = SourceValidator.validateKnownHeaders("""
                - beans:
                    - name: CamelMyBean
                      type: "#class:com.example.MyBean"
                - from:
                    uri: "file:in?noop=true"
                    steps:
                      - setProperty:
                          name: CamelAwsSqsDeleteFiltered
                          constant: "true"
                      - setHeader:
                          name: CamelSolrField.id
                          simple: "${body}"
                      - log: "${header.CamelFileName.length()} ${header.CamelFileNam.length()}"
                      - to:
                          uri: solr:localhost:8983/mycollection
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("header CamelFileNam.length is not set by").contains("did you mean CamelFileLength");
    }

    @Test
    void aCommentABlockScalarAndANestedMapInTheParametersAreRead() {
        // CAMEL-24710: a comment after a value is not the value; the body of a block scalar is not options; the keys
        // under a Map option are its entries
        List<String> msgs = SourceValidator.validateYamlEndpoints("""
                - from:
                    uri: ai-tool:createOrder
                    parameters:
                      description: "Create an order"
                      argSchema: |
                        {
                          "type": "object",
                          "properties": { "id": { "type": "string" } }
                        }
                    steps:
                      - to:
                          uri: log:out
                          parameters:
                            groupSize: 30000          # the default is none
                            showAll: true
                      - to:
                          uri: once
                          parameters:
                            name: hello
                            headers:
                              foo: foolish
                              bar: 456
                            unknownOne: x
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("Unknown option 'unknownOne'");
    }

    @Test
    void aProducerOnlyComponentInFromIsNamed() {
        List<String> msgs = SourceValidator.validateYamlEndpoints("""
                - from:
                    uri: mock:result
                    steps:
                      - log: "${body}"
                - from:
                    uri: direct:ok
                    steps:
                      - to: mock:out
                """, catalog);
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0)).contains("mock is a producer-only component: it cannot be a from:").contains("direct:name");
    }

    /** CAMEL-24852: the directory of a file endpoint on a to: cannot be dynamic; toD: evaluates the uri first. */
    @Test
    void aDynamicDirectoryOnAFileEndpointSaysToUseFileNameOrToD() {
        String yaml = """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: "file://archived/${header.monthDir}?fileName=${header.CamelFileName}"
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors)
                .anyMatch(e -> e.startsWith("Line 6: file: the directory archived/${header.monthDir} cannot be dynamic")
                        && e.contains("fileName (file:archived?fileName=${...})") && e.contains("use toD:"));

        List<String> dynamic = SourceValidator.validateYamlEndpoints(yaml.replace("- to:", "- toD:"), catalog);
        assertThat(dynamic).noneMatch(e -> e.contains("cannot be dynamic"));

        // a from: with a dynamic directory fails at startup the same way
        String fromYaml = """
                - route:
                    from:
                      uri: "file://archived/${header.monthDir}"
                      steps:
                        - to:
                            uri: log:done
                """;
        assertThat(SourceValidator.validateYamlEndpoints(fromYaml, catalog))
                .anyMatch(e -> e.startsWith("Line 3: file: the directory archived/${header.monthDir} cannot be dynamic"));
    }

    /** CAMEL-24854: a doubled backslash in an include regex (kept as is inside single quotes) matches no file. */
    @Test
    void aDoubledBackslashInAnIncludeRegexIsReported() {
        String yaml = """
                - route:
                    from:
                      uri: file:orders
                      parameters:
                        include: '.*\\\\.json$'
                      steps:
                        - to:
                            uri: log:done
                """;
        List<String> errors = SourceValidator.validateYamlEndpoints(yaml, catalog);
        assertThat(errors)
                .anyMatch(e -> e.startsWith("Line 5: file: include=.*\\\\.json$ matches a literal backslash in the file name")
                        && e.endsWith("write include='.*\\.json$'"));

        List<String> ok = SourceValidator.validateYamlEndpoints(yaml.replace("\\\\.json", "\\.json"), catalog);
        assertThat(ok).noneMatch(e -> e.contains("backslash"));

        // in double quotes YAML reads \\ as one backslash: ".*\\.json$" is the regex .*\.json$, nothing to report
        List<String> doubleQuoted
                = SourceValidator.validateYamlEndpoints(yaml.replace("'.*\\\\.json$'", "\".*\\\\.json$\""), catalog);
        assertThat(doubleQuoted).noneMatch(e -> e.contains("backslash"));

        // \\myfile is a backslash on purpose: \myfile is not a regex escape, so there is nothing else it can mean
        List<String> literal = SourceValidator.validateYamlEndpoints(yaml.replace(".*\\\\.json$", ".*\\\\myfile.*"), catalog);
        assertThat(literal).noneMatch(e -> e.contains("backslash"));
    }
}
