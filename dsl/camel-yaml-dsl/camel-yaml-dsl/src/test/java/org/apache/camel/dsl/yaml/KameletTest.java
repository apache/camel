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
import org.apache.camel.dsl.yaml.support.model.MySetBody;
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.spi.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class KameletTest extends YamlTestSupport {

    @Override
    public void doSetup() throws Exception {
        context.start();
    }

    static Stream<Resource> kameletResourceProvider() {
        return Stream.of(
                asResource("inline", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - kamelet: "setPayload?payload=1"
                              - to: "mock:kamelet"
                        """),
                asResource("name", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - kamelet:
                                  name: "setPayload?payload=1"
                              - to: "mock:kamelet"
                        """),
                asResource("properties", """
                        - from:
                            uri: "direct:start"
                            steps:
                              - kamelet:
                                  name: "setPayload"
                                  parameters:
                                    payload: 1
                              - to: "mock:kamelet"
                        """));
    }

    @ParameterizedTest
    @MethodSource("kameletResourceProvider")
    void kamelet(Resource resource) throws Exception {
        addTemplate("setPayload", t -> t
                .from("kamelet:source")
                .setBody().simple("${body}: {{payload}}"));

        loadRoutes(resource);

        withMock("mock:kamelet", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("a: 1");
        });

        withTemplate(t -> t.to("direct:start").withBody("a").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletAggregation() throws Exception {
        addTemplate("aggregate", t -> t
                .from("kamelet:source")
                .aggregate()
                .simple("${header.StockSymbol}")
                .aggregationStrategy(new UseLatestAggregationStrategy())
                .completionSize("{{size}}")
                .to("kamelet:sink"));

        loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - kamelet:
                          name: aggregate?size=2
                          steps:
                            - to: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("2", "4"));

        withTemplate(t -> {
            t.to("direct:route").withBody("1").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("2").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("3").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("4").withHeader("StockSymbol", 2).send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletFilterWithFlow() throws Exception {
        addTemplate("simple-filter", t -> t
                .from("kamelet:source")
                .filter().simple("${header.foo} == \"a\"")
                .to("kamelet:sink"));

        loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - kamelet:
                          name: "simple-filter"
                      - to:
                          uri: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("1", "3"));

        withTemplate(t -> {
            t.to("direct:route").withBody("1").withHeader("foo", "a").send();
            t.to("direct:route").withBody("2").withHeader("foo", "b").send();
            t.to("direct:route").withBody("3").withHeader("foo", "a").send();
            t.to("direct:route").withBody("4").withHeader("foo", "c").send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletAggregationWithFlow() throws Exception {
        addTemplate("aggregate", t -> t
                .from("kamelet:source")
                .aggregate()
                .simple("${header.StockSymbol}")
                .aggregationStrategy(new UseLatestAggregationStrategy())
                .completionSize("{{size}}")
                .to("kamelet:sink"));

        loadRoutes("""
                - from:
                    uri: "direct:route"
                    steps:
                      - kamelet: aggregate?size=2
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("2", "4"));

        withTemplate(t -> {
            t.to("direct:route").withBody("1").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("2").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("3").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("4").withHeader("StockSymbol", 2).send();
        });

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletDefinitionWithLocalBean() throws Exception {
        loadRoutes("""
                - routeTemplate:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: "#class:%s"
                    from:
                      uri: "kamelet:source"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
                """.formatted(MyUppercaseProcessor.class.getName()));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("HELLO");
        });

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletDefinitionWithLocalBeanAndProperties() throws Exception {
        loadRoutes("""
                - routeTemplate:
                    id: "myTemplate"
                    beans:
                      - name: "myProcessor"
                        type: "#class:%s"
                        properties:
                          payload: "test-payload"
                    from:
                      uri: "kamelet:source"
                      steps:
                        - process:
                            ref: "{{myProcessor}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
                """.formatted(MySetBody.class.getName()));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("test-payload");
        });

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletDefinitionWithLocalBeanViaRef() throws Exception {
        loadRoutes("""
                - routeTemplate:
                    id: "myTemplate"
                    beans:
                      - name: "myCounter"
                        type: java.util.concurrent.atomic.AtomicInteger
                    from:
                      uri: "kamelet:source"
                      steps:
                        - bean:
                            ref: "{{myCounter}}"
                            method: "getAndIncrement"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("0");
        });

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletDefinitionWithDefaultParameters() throws Exception {
        loadRoutes("""
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "myParameter"
                        defaultValue: "myDefaultValue"
                        description: "myParameterDescription"
                    from:
                      uri: "kamelet:source"
                      steps:
                        - setBody:
                            constant: "{{myParameter}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("myDefaultValue");
        });

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletDefinitionWithParameters() throws Exception {
        loadRoutes("""
                - routeTemplate:
                    id: "myTemplate"
                    parameters:
                      - name: "myParameter"
                        defaultValue: "myDefaultValue"
                        description: "myParameterDescription"
                    from:
                      uri: "kamelet:source"
                      steps:
                        - setBody:
                            constant: "{{myParameter}}"
                - from:
                    uri: "direct:start"
                    steps:
                      - to: "kamelet:myTemplate?myParameter=test"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("test");
        });

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletPropertiesNotUrlEncoded() throws Exception {
        addTemplate("setPayloadWithType", t -> t
                .from("kamelet:source")
                .setBody().simple("{{contentType}}")
                .to("kamelet:sink"));

        loadRoutes("""
                - from:
                    uri: "direct:start"
                    steps:
                      - kamelet:
                          name: "setPayloadWithType"
                          parameters:
                            contentType: "application/json"
                      - to: "mock:result"
                """);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("application/json");
        });

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void kameletNoteProperty() throws Exception {
        addTemplate("setPayload", t -> t
                .from("kamelet:source")
                .setBody().simple("${body}: {{payload}}"));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - kamelet:
                          name: "setPayload"
                          description: "calls setPayload template"
                          note: "a developer note"
                          parameters:
                            payload: 1
                      - to: "mock:kamelet"
                """);

        withMock("mock:kamelet", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("a: 1");
        });

        withTemplate(t -> t.to("direct:start").withBody("a").send());

        MockEndpoint.assertIsSatisfied(context);

        KameletDefinition kamelet = (KameletDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(kamelet.getDescription()).isEqualTo("calls setPayload template");
        assertThat(kamelet.getNote()).isEqualTo("a developer note");
    }
}
