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

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyBeanBuilder;
import org.apache.camel.dsl.yaml.support.model.MySetBody;
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor;
import org.apache.camel.impl.engine.DefaultRoute;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTemplateTest extends YamlTestSupport {

    @Test
    void createTemplate() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);

        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getRoute().getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(template.getRoute().getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) template.getRoute().getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    static Resource[] createTemplateWithBeans() {
        return new Resource[] {
                asResource("beans", """
                            - routeTemplate:
                                id: "myTemplate"
                                beans:
                                  - name: "myProcessor"
                                    type: "#class:%s"
                                from:
                                  uri: "direct:{{directName}}"
                                  steps:
                                    - process:
                                        ref: "{{myProcessor}}"
                            - from:
                                uri: "direct:start"
                                steps:
                                  - to: "direct:myId"
                                  - to: "mock:result"
                        """.formatted(MyUppercaseProcessor.class.getName())),
                asResource("script", """
                            - routeTemplate:
                                id: "myTemplate"
                                beans:
                                  - name: "myProcessor"
                                    type: "%s"
                                    scriptLanguage: "groovy"
                                    script: "new %s()"
                                from:
                                  uri: "direct:{{directName}}"
                                  steps:
                                    - process:
                                        ref: "{{myProcessor}}"
                            - from:
                                uri: "direct:start"
                                steps:
                                  - to: "direct:myId"
                                  - to: "mock:result"
                        """.formatted(MyUppercaseProcessor.class.getName(), MyUppercaseProcessor.class.getName())),
                asResource("script-block", """
                            - routeTemplate:
                                id: "myTemplate"
                                beans:
                                  - name: "myProcessor"
                                    type: "org.apache.camel.Processor"
                                    scriptLanguage: "groovy"
                                    script: |
                                        new %s()
                                from:
                                  uri: "direct:{{directName}}"
                                  steps:
                                    - process:
                                        ref: "{{myProcessor}}"
                            - from:
                                uri: "direct:start"
                                steps:
                                  - to: "direct:myId"
                                  - to: "mock:result"
                        """.formatted(MyUppercaseProcessor.class.getName())),
                asResource("script-without-type", """
                            - routeTemplate:
                                id: "myTemplate"
                                beans:
                                  - name: "myProcessor"
                                    scriptLanguage: "groovy"
                                    script: "new %s()"
                                from:
                                  uri: "direct:{{directName}}"
                                  steps:
                                    - process:
                                        ref: "{{myProcessor}}"
                            - from:
                                uri: "direct:start"
                                steps:
                                  - to: "direct:myId"
                                  - to: "mock:result"
                        """.formatted(MyUppercaseProcessor.class.getName()))
        };
    }

    @ParameterizedTest
    @MethodSource("createTemplateWithBeans")
    void createTemplateWithBeans(Resource resource) throws Exception {
        PluginHelper.getRoutesLoader(context).loadRoutes(resource);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("HELLO");
        });

        context.addRouteFromTemplate("myId", "myTemplate", Map.of("directName", "myId"));
        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getTemplateBeans().size()).isEqualTo(1);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createTemplateWithBeanAndProperties() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        beans:
                          - name: "myProcessor"
                            type: "#class:%s"
                            properties:
                              payload: "test-payload"
                        from:
                          uri: "direct:{{directName}}"
                          steps:
                            - process:
                                ref: "{{myProcessor}}"
                    - from:
                        uri: "direct:start"
                        steps:
                          - to: "direct:myId"
                          - to: "mock:result"
                """.formatted(MySetBody.class.getName()));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("test-payload");
        });

        context.addRouteFromTemplate("myId", "myTemplate", Map.of("directName", "myId"));
        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getTemplateBeans().size()).isEqualTo(1);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createTemplateWithBuilderBeanWithoutType() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        beans:
                          - name: "myBean"
                            builderClass: "%s"
                            builderMethod: "createTheBean"
                            properties:
                              field1: "builder-hello"
                        from:
                          uri: "direct:{{directName}}"
                          steps:
                            - to: "bean:{{myBean}}?method=getField1"
                    - from:
                        uri: "direct:start"
                        steps:
                          - to: "direct:myId"
                          - to: "mock:result"
                """.formatted(MyBeanBuilder.class.getName()));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(1);
            mock.expectedBodiesReceived("builder-hello");
        });

        context.addRouteFromTemplate("myId", "myTemplate", Map.of("directName", "myId"));
        context.start();

        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getTemplateBeans().size()).isEqualTo(1);
        assertThat(template.getTemplateBeans().get(0).getType()).isNull();

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createTemplateWithProperties() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        parameters:
                          - name: "foo"
                            defaultValue: "myDefaultFoo"
                            description: "myFooDescription"
                          - name: "bar"
                            description: "myBarDescription"
                        from:
                          uri: "direct:info"
                          steps:
                            - log: "message"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);

        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getConfigurer()).isNull();

        assertThat(template.getTemplateParameters().stream().anyMatch(p -> "foo".equals(p.getName())
                && "myDefaultFoo".equals(p.getDefaultValue())
                && "myFooDescription".equals(p.getDescription()))).isTrue();

        assertThat(template.getTemplateParameters().stream().anyMatch(p -> "bar".equals(p.getName())
                && p.getDefaultValue() == null
                && "myBarDescription".equals(p.getDescription()))).isTrue();

        assertThat(template.getRoute().getInput().getEndpointUri()).isEqualTo("direct:info");
        assertThat(template.getRoute().getOutputs().get(0)).isInstanceOf(LogDefinition.class);
        LogDefinition log = (LogDefinition) template.getRoute().getOutputs().get(0);
        assertThat(log.getMessage()).isEqualTo("message");
    }

    @Test
    void createTemplateWithOptionalProperties() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        parameters:
                          - name: "foo"
                          - name: "bar"
                            required: false
                        from:
                          uri: "direct:{{foo}}"
                          steps:
                            - to: "mock:result?retainFirst={{?bar}}"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);

        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getConfigurer()).isNull();

        assertThat(template.getTemplateParameters().stream().anyMatch(p -> "foo".equals(p.getName())
                && p.getDefaultValue() == null
                && p.isRequired())).isTrue();

        assertThat(template.getTemplateParameters().stream().anyMatch(p -> "bar".equals(p.getName())
                && p.getDefaultValue() == null
                && !p.isRequired())).isTrue();

        assertThat(template.getRoute().getInput().getEndpointUri()).isEqualTo("direct:{{foo}}");
        assertThat(template.getRoute().getOutputs().get(0)).isInstanceOf(ToDefinition.class);
        ToDefinition to = (ToDefinition) template.getRoute().getOutputs().get(0);
        assertThat(to.getUri()).isEqualTo("mock:result?retainFirst={{?bar}}");

        context.start();

        context.addRouteFromTemplate("myRoute1", "myTemplate", Map.of("foo", "start", "bar", "1"));
        assertThat(context.hasEndpoint("mock:result")).isNull();
        assertThat(context.hasEndpoint("mock:result?retainFirst=1")).isNotNull();
        MockEndpoint mock = context.getEndpoint("mock:result?retainFirst=1", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello World");
        context.createProducerTemplate().sendBody("direct:start", "Hello World");
        mock.assertIsSatisfied();
        mock.reset();

        context.addRouteFromTemplate("myRoute2", "myTemplate", Map.of("foo", "start2"));
        MockEndpoint mock2 = context.getEndpoint("mock:result", MockEndpoint.class);
        mock2.expectedBodiesReceived("Bye World");
        context.createProducerTemplate().sendBody("direct:start2", "Bye World");
        mock2.assertIsSatisfied();
    }

    @Test
    void createTemplateWithJava() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        beans:
                          - name: "myAgg"
                            type: "org.apache.camel.AggregationStrategy"
                            scriptLanguage: "java"
                            script: "(e1, e2) -> { return e2.getMessage().getBody(); }"
                        from:
                          uri: "direct:route"
                          steps:
                            - aggregate:
                                aggregationStrategy: "{{myAgg}}"
                                completionSize: 2
                                correlationExpression:
                                  header: "StockSymbol"
                                steps:
                                  - to: "mock:result"
                """);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(2);
            mock.expectedBodiesReceived("101", "199");
        });

        context.addRouteFromTemplate("myId", "myTemplate", Map.of());
        context.start();

        withTemplate(t -> {
            t.to("direct:route").withBody("99").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("101").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("200").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("199").withHeader("StockSymbol", 2).send();
        });

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getTemplateBeans().size()).isEqualTo(1);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createTemplateWithGroovy() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        beans:
                          - name: "myAgg"
                            type: "org.apache.camel.AggregationStrategy"
                            scriptLanguage: "groovy"
                            script: "class MaxAgg { int agg(int s1, int s2) { return Math.max(s1, s2) }}; new MaxAgg()"
                        from:
                          uri: "direct:route"
                          steps:
                            - aggregate:
                                aggregationStrategy: "{{myAgg}}"
                                completionSize: 2
                                correlationExpression:
                                  header: "StockSymbol"
                                steps:
                                  - to: "mock:result"
                """);

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(2);
            mock.expectedBodiesReceived("101", "200");
        });

        context.addRouteFromTemplate("myId", "myTemplate", Map.of());
        context.start();

        withTemplate(t -> {
            t.to("direct:route").withBody("99").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("101").withHeader("StockSymbol", 1).send();
            t.to("direct:route").withBody("200").withHeader("StockSymbol", 2).send();
            t.to("direct:route").withBody("199").withHeader("StockSymbol", 2).send();
        });

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        RouteTemplateDefinition template = context.getRouteTemplateDefinitions().get(0);
        assertThat(template.getId()).isEqualTo("myTemplate");
        assertThat(template.getTemplateBeans().size()).isEqualTo(1);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createRouteTemplateWithRoute() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        parameters:
                          - name: "foo"
                          - name: "bar"
                        route:
                          streamCache: false
                          messageHistory: true
                          logMask: true
                          from:
                            uri: "direct:{{foo}}"
                            steps:
                              - to: "mock:{{bar}}"
                """);

        context.addRouteFromTemplate("myId", "myTemplate", Map.of("foo", "start", "bar", "result"));
        context.start();

        assertThat(context.getRoutes().get(0)).isInstanceOf(DefaultRoute.class);
        DefaultRoute route = (DefaultRoute) context.getRoutes().get(0);
        assertThat(route.isStreamCaching()).isFalse();
        assertThat(route.isMessageHistory()).isTrue();
        assertThat(route.isLogMask()).isTrue();
    }

    @Test
    void createRouteTemplateWithPrefix() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        parameters:
                          - name: "foo"
                          - name: "bar"
                        from:
                          uri: "direct:{{foo}}"
                          steps:
                          - choice:
                              when:
                                - header: "foo"
                                  steps:
                                    - log:
                                        id: "myLog"
                                        message: "Hello World"
                              otherwise:
                                steps:
                                  - to:
                                      uri: "mock:{{bar}}"
                                      id: "end"
                """);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("foo", "one");
        parameters.put("bar", "cheese");
        context.addRouteFromTemplate("first", "myTemplate", "aaa", parameters);

        parameters.put("foo", "two");
        parameters.put("bar", "cake");
        context.addRouteFromTemplate("second", "myTemplate", "bbb", parameters);
        context.start();

        assertThat(context.getRoute("first").filter("aaa*").size()).isEqualTo(3);
        assertThat(context.getRoute("second").filter("bbb*").size()).isEqualTo(3);
    }

    @Test
    void createTemplateWithOptionalEndpointUriNotProvided() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        parameters:
                          - name: "foo"
                          - name: "optionalUri"
                            required: false
                        from:
                          uri: "direct:{{foo}}"
                          steps:
                            - to: "{{?optionalUri}}"
                            - to: "mock:result"
                """);

        context.addRouteFromTemplate("myRoute1", "myTemplate", Map.of("foo", "start"));
        context.start();

        MockEndpoint mock = context.getEndpoint("mock:result", MockEndpoint.class);
        mock.expectedBodiesReceived("Hello World");
        context.createProducerTemplate().sendBody("direct:start", "Hello World");
        mock.assertIsSatisfied();
    }

    @Test
    void createTemplateWithOptionalEndpointUriProvided() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        parameters:
                          - name: "foo"
                          - name: "optionalUri"
                            required: false
                        from:
                          uri: "direct:{{foo}}"
                          steps:
                            - to: "{{?optionalUri}}"
                            - to: "mock:result"
                """);

        context.addRouteFromTemplate("myRoute1", "myTemplate", Map.of("foo", "start", "optionalUri", "mock:middle"));
        context.start();

        MockEndpoint middle = context.getEndpoint("mock:middle", MockEndpoint.class);
        middle.expectedBodiesReceived("Hello World");
        MockEndpoint result = context.getEndpoint("mock:result", MockEndpoint.class);
        result.expectedBodiesReceived("Hello World");
        context.createProducerTemplate().sendBody("direct:start", "Hello World");
        middle.assertIsSatisfied();
        result.assertIsSatisfied();
    }
}
