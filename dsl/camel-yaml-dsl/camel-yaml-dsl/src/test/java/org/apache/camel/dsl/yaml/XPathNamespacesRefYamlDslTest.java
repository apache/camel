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

import org.apache.camel.RoutesBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.builder.Namespaces;
import org.junit.jupiter.api.Test;

/**
 * Tests that a route loaded from YAML DSL can reference a {@link Namespaces} bean from the registry via the xpath
 * step's namespacesRef property, with the bean itself also declared in YAML.
 */
class XPathNamespacesRefYamlDslTest {

    @Test
    void loadsXPathWithNamespacesRef() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            YamlRoutesBuilderLoader loader = new YamlRoutesBuilderLoader();
            loader.setCamelContext(context);
            RoutesBuilder routesBuilder = loader.loadRoutesBuilder(ResourceHelper.fromString("xpath-namespaces-ref.yaml", """
                    - beans:
                        - name: myNamespaces
                          type: org.apache.camel.support.builder.Namespaces
                          properties:
                            namespaces[c]: "http://acme.com/cheese"
                            namespaces[w]: "http://acme.com/wine"
                    - route:
                        id: xpath-namespaces-ref
                        from:
                          uri: "direct:xpathNamespacesRef"
                          steps:
                            - choice:
                                when:
                                  - xpath:
                                      expression: "/c:number = 55"
                                      resultQName: "BOOLEAN"
                                      namespacesRef: "myNamespaces"
                                    steps:
                                      - to: "mock:cheeseMatch"
                                  - xpath:
                                      expression: "/w:number = 77"
                                      resultQName: "BOOLEAN"
                                      namespacesRef: "myNamespaces"
                                    steps:
                                      - to: "mock:wineMatch"
                                otherwise:
                                  steps:
                                    - to: "mock:noMatch"
                    """));
            context.addRoutes(routesBuilder);
            context.start();

            MockEndpoint cheeseMatch = context.getEndpoint("mock:cheeseMatch", MockEndpoint.class);
            MockEndpoint wineMatch = context.getEndpoint("mock:wineMatch", MockEndpoint.class);
            MockEndpoint noMatch = context.getEndpoint("mock:noMatch", MockEndpoint.class);

            // positive example: cheese namespace
            cheeseMatch.expectedMessageCount(1);
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/cheese\">55</number>");
            cheeseMatch.assertIsSatisfied();

            // positive example: wine namespace
            wineMatch.expectedMessageCount(1);
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/wine\">77</number>");
            wineMatch.assertIsSatisfied();

            noMatch.expectedMessageCount(2);

            // negative, data-wise: correct namespace, wrong value
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/cheese\">99</number>");

            // negative, namespace-wise: correct value, wrong namespace URI
            context.createProducerTemplate().sendBody("direct:xpathNamespacesRef",
                    "<number xmlns=\"http://acme.com/water\">77</number>");

            noMatch.assertIsSatisfied();
        }
    }
}
