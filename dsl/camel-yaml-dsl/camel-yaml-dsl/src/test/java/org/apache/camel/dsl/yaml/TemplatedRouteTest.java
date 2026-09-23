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

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyUppercaseProcessor;
import org.apache.camel.model.RouteDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplatedRouteTest extends YamlTestSupport {

    @Test
    void createTemplatedRouteWithGroup() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        from:
                          uri: "direct:{{directName}}"
                          steps:
                            - process:
                                ref: "{{myProcessor}}"
                            - to: "mock:result"
                    - templatedRoute:
                        routeId: "myRoute"
                        routeTemplateRef: "myTemplate"
                        group: "myGroup"
                        parameters:
                          - name: "directName"
                            value: "foo"
                        beans:
                          - name: "myProcessor"
                            type: "%s"
                            scriptLanguage: "groovy"
                            script: |
                                new %s()
                    - templatedRoute:
                        routeId: "myRoute2"
                        routeTemplateRef: "myTemplate"
                        group: "myGroup"
                        parameters:
                          - name: "directName"
                            value: "foo2"
                        beans:
                          - name: "myProcessor"
                            type: "org.apache.camel.Processor"
                            scriptLanguage: "groovy"
                            script: "new %s()"
                """.formatted(MyUppercaseProcessor.class.getName(), MyUppercaseProcessor.class.getName(),
                MyUppercaseProcessor.class.getName()));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(2);
            mock.expectedBodiesReceived("HELLO", "WORLD");
        });

        context.start();

        withTemplate(t -> {
            t.to("direct:foo").withBody("hello").send();
            t.to("direct:foo2").withBody("world").send();
        });

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        RouteDefinition route1 = context.getRouteDefinitions().get(0);
        assertThat(route1.getRouteId()).isEqualTo("myRoute");
        assertThat(route1.getGroup()).isEqualTo("myGroup");

        RouteDefinition route2 = context.getRouteDefinitions().get(1);
        assertThat(route2.getRouteId()).isEqualTo("myRoute2");
        assertThat(route2.getGroup()).isEqualTo("myGroup");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void createTemplatedRoute() throws Exception {
        loadRoutes("""
                    - routeTemplate:
                        id: "myTemplate"
                        from:
                          uri: "direct:{{directName}}"
                          steps:
                            - process:
                                ref: "{{myProcessor}}"
                            - to: "mock:result"
                    - templatedRoute:
                        routeId: "myRoute"
                        routeTemplateRef: "myTemplate"
                        parameters:
                          - name: "directName"
                            value: "foo"
                        beans:
                          - name: "myProcessor"
                            type: "org.apache.camel.Processor"
                            scriptLanguage: "groovy"
                            script: |
                                new %s()
                    - templatedRoute:
                        routeId: "myRoute2"
                        routeTemplateRef: "myTemplate"
                        parameters:
                          - name: "directName"
                            value: "foo2"
                        beans:
                          - name: "myProcessor"
                            type: "org.apache.camel.Processor"
                            scriptLanguage: "groovy"
                            script: "new %s()"
                """.formatted(MyUppercaseProcessor.class.getName(), MyUppercaseProcessor.class.getName()));

        withMock("mock:result", mock -> {
            mock.expectedMessageCount(2);
            mock.expectedBodiesReceived("HELLO", "WORLD");
        });

        context.start();

        withTemplate(t -> {
            t.to("direct:foo").withBody("hello").send();
            t.to("direct:foo2").withBody("world").send();
        });

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        RouteDefinition route1 = context.getRouteDefinitions().get(0);
        assertThat(route1.getRouteId()).isEqualTo("myRoute");

        RouteDefinition route2 = context.getRouteDefinitions().get(1);
        assertThat(route2.getRouteId()).isEqualTo("myRoute2");

        MockEndpoint.assertIsSatisfied(context);
    }
}
