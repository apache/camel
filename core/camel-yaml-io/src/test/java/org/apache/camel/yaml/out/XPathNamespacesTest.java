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
package org.apache.camel.yaml.out;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Optional;

import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.xml.in.ModelParser;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("CAMEL-2140")
class XPathNamespacesTest {

    @Test
    void test() throws Exception {
        try (ByteArrayInputStream is = new ByteArrayInputStream(XML.getBytes(Charset.defaultCharset()))) {
            Optional<RoutesDefinition> routesDefinition = new ModelParser(is, XmlToYamlTest.NAMESPACE).parseRoutesDefinition();
            assertThat(routesDefinition).isPresent()
                    .get(InstanceOfAssertFactories.type(RoutesDefinition.class))
                    .extracting(RoutesDefinition::getRoutes, InstanceOfAssertFactories.list(RouteDefinition.class))
                    .singleElement()
                    .extracting(RouteDefinition::getOutputs, InstanceOfAssertFactories.list(ProcessorDefinition.class))
                    .hasSize(3);

            StringWriter sw = new StringWriter();
            new org.apache.camel.yaml.out.ModelWriter(sw).writeRoutesDefinition(routesDefinition.get());

            assertThat(sw).hasToString(EXPECTED_YAML);
        }
    }

    //language=XML
    private static final String XML
            = """
                    <routes xmlns="http://camel.apache.org/schema/spring"
                            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                            xmlns:routes-ns-def="http://www.example.com/schema"
                            xsi:schemaLocation="http://camel.apache.org/schema/spring https://camel.apache.org/schema/spring/camel-spring.xsd">

                        <route id="direct:route-with-xpath-expression-custom-namespace"
                               xmlns:route-ns-def="http://www.example.com/schema">

                            <from uri="direct:route-with-xpath-expression-custom-namespace"/>

                            <setProperty name="child-expression-namespace-from-routes">
                                <xpath saxon="true" resultType="java.lang.String">/routes-ns-def:parent/routes-ns-def:child</xpath>
                            </setProperty>

                            <setProperty name="child-expression-namespace-from-route">
                                <xpath saxon="true" resultType="java.lang.String">/route-ns-def:parent/route-ns-def:child</xpath>
                            </setProperty>

                            <setProperty name="child-expression-namespace-from-xpath">
                                <xpath saxon="true" resultType="java.lang.String" xmlns:expression-ns-def="http://www.example.com/schema">/expression-ns-def:parent/expression-ns-def:child</xpath>
                            </setProperty>
                        </route>

                    </routes>
                    """;

    //language=yaml
    private static final String EXPECTED_YAML = """
            - route:
                id: direct:route-with-xpath-expression-custom-namespace
                from:
                  uri: direct:route-with-xpath-expression-custom-namespace
                  steps:
                    - setProperty:
                        name: child-expression-namespace-from-routes
                        xpath:
                          resultType: java.lang.String
                          saxon: "true"
                          namespace:
                            xsi: http://www.w3.org/2001/XMLSchema-instance
                            routes-ns-def: http://www.example.com/schema
                            route-ns-def: http://www.example.com/schema
                          expression: /routes-ns-def:parent/routes-ns-def:child
                    - setProperty:
                        name: child-expression-namespace-from-route
                        xpath:
                          resultType: java.lang.String
                          saxon: "true"
                          namespace:
                            xsi: http://www.w3.org/2001/XMLSchema-instance
                            routes-ns-def: http://www.example.com/schema
                            route-ns-def: http://www.example.com/schema
                          expression: /route-ns-def:parent/route-ns-def:child
                    - setProperty:
                        name: child-expression-namespace-from-xpath
                        xpath:
                          resultType: java.lang.String
                          saxon: "true"
                          namespace:
                            xsi: http://www.w3.org/2001/XMLSchema-instance
                            routes-ns-def: http://www.example.com/schema
                            route-ns-def: http://www.example.com/schema
                            expression-ns-def: http://www.example.com/schema
                          expression: /expression-ns-def:parent/expression-ns-def:child
            """;
}
