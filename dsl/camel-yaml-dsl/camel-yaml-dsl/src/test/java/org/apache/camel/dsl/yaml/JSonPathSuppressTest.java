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
package org.apache.camel.dsl.yaml;

import java.util.stream.Stream;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class JSonPathSuppressTest extends YamlTestSupport {

    static Stream<Resource> resourceProvider() {
        return Stream.of(
                asResource("expression", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - choice:
                                  when:
                                    - jsonpath: "person.middlename"
                                      steps:
                                        - to: "mock:middle"
                                  otherwise:
                                    steps:
                                      - to: "mock:other"
                        """),
                asResource("expression-block", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - choice:
                                  when:
                                    - expression:
                                        jsonpath: "person.middlename"
                                      steps:
                                        - to: "mock:middle"
                                  otherwise:
                                    steps:
                                      - to: "mock:other"
                        """));
    }

    @ParameterizedTest
    @MethodSource("resourceProvider")
    void jsonpathSuppressDefinition(Resource resource) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        var choice = (ChoiceDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        var when = (WhenDefinition) choice.getWhenClauses().get(0);
        assertThat(when.getExpression().getLanguage()).isEqualTo("jsonpath");
        assertThat(when.getExpression().getExpression()).isEqualTo("person.middlename");
        assertThat(((ToDefinition) when.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:middle");
        assertThat(((ToDefinition) choice.getOtherwise().getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:other");
    }

    @Test
    void supressTestMiddle() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - choice:
                          when:
                          - jsonpath:
                              expression: "person.middlename"
                              suppressExceptions: true
                            steps:
                            - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
                """);

        withMock("mock:middle", mock -> mock.expectedMessageCount(1));
        withMock("mock:other", mock -> mock.expectedMessageCount(0));

        context.start();

        withTemplate(t -> {
            t.to("direct:start")
                    .withBody("{\"person\" : {\"firstname\" : \"foo\", \"middlename\" : \"foo2\", \"lastname\" : \"bar\"}}")
                    .send();
        });
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void supressTestNoMiddle() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - choice:
                          when:
                          - jsonpath:
                              expression: "person.middlename"
                              suppressExceptions: true
                            steps:
                            - to: "mock:middle"
                          otherwise:
                            steps:
                              - to: "mock:other"
                """);

        withMock("mock:middle", mock -> mock.expectedMessageCount(0));
        withMock("mock:other", mock -> mock.expectedMessageCount(1));

        context.start();

        withTemplate(t -> {
            t.to("direct:start")
                    .withBody("{\"person\" : {\"firstname\" : \"foo\", \"lastname\" : \"bar\"}}")
                    .send();
        });
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void supressTestLast() throws Exception {
        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - choice:
                          when:
                          - jsonpath:
                              expression: "person.middlename"
                              suppressExceptions: true
                            steps:
                            - to: "mock:middle"
                          - jsonpath:
                              expression: "person.lastname"
                              suppressExceptions: true
                            steps:
                            - to: "mock:last"
                          otherwise:
                            steps:
                              - to: "mock:other"
                """);

        withMock("mock:middle", mock -> mock.expectedMessageCount(0));
        withMock("mock:last", mock -> mock.expectedMessageCount(1));
        withMock("mock:other", mock -> mock.expectedMessageCount(0));

        context.start();

        withTemplate(t -> {
            t.to("direct:start")
                    .withBody("{\"person\" : {\"firstname\" : \"foo\", \"lastname\" : \"bar\"}}")
                    .send();
        });
        MockEndpoint.assertIsSatisfied(context);
    }
}
