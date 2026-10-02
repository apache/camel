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
package org.apache.camel.dsl.jbang.core.commands;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Converting a route file between YAML, XML and Java without running it (CAMEL-25254).
 */
class RouteDslConverterTest {

    private static final String XML = """
            <routes xmlns="http://camel.apache.org/schema/xml-io">
                <!-- orders -->
                <route id="orders">
                    <from uri="kafka:orders"/>
                    <choice>
                        <when>
                            <simple>${header.priority} == 'high'</simple>
                            <log message="Urgent ${body}" loggingLevel="WARN"/>
                        </when>
                        <otherwise>
                            <to uri="direct:normal"/>
                        </otherwise>
                    </choice>
                </route>
            </routes>
            """;

    private static final String YAML = """
            - beans:
                - name: orderService
                  type: com.foo.OrderService
                  properties:
                    currency: EUR
            - route:
                id: orders
                from:
                  uri: kafka:orders
                  steps:
                    - split:
                        simple: "${body}"
                        steps:
                          - to: direct:item
            """;

    private static RouteDslConverter.Result convert(String name, String content, String format) {
        return RouteDslConverter.convert(name, content, format, Map.of());
    }

    @Test
    void xmlToYaml() {
        RouteDslConverter.Result r = convert("orders.camel.xml", XML, "yaml");
        assertThat(r.converted()).isTrue();
        assertThat(r.fileName()).isEqualTo("orders.camel.yaml");
        assertThat(r.content()).contains("- route:", "id: orders", "uri: kafka:orders", "loggingLevel: WARN");
        assertThat(r.notes()).containsExactly("The comments of orders.camel.xml are not carried over");
    }

    @Test
    void xmlToAJavaClass() {
        RouteDslConverter.Result r = convert("orders.camel.xml", XML, "java");
        assertThat(r.converted()).isTrue();
        assertThat(r.fileName()).isEqualTo("Orders.java");
        assertThat(r.content()).contains("import org.apache.camel.LoggingLevel;",
                "import org.apache.camel.builder.RouteBuilder;", "public class Orders extends RouteBuilder {",
                "public void configure() throws Exception {", "from(\"kafka:orders\")",
                ".log(LoggingLevel.WARN, \"Urgent ${body}\")");
        // the converted class reads back to the same routes: no difference noted
        assertThat(r.notes()).noneMatch(n -> n.startsWith("Check the converted"));
    }

    @Test
    void yamlBeansAreConvertedWithoutCreatingThem() {
        // com.foo.OrderService does not exist: the YAML DSL would fail creating it, the converter reads it as data
        RouteDslConverter.Result r = convert("orders.camel.yaml", YAML, "xml");
        assertThat(r.converted()).isTrue();
        assertThat(r.content()).contains("<bean name=\"orderService\" type=\"com.foo.OrderService\">",
                "<property key=\"currency\" value=\"EUR\"/>", "<split>", "<simple>${body}</simple>");

        // Java has no beans in its routes: said, not dropped silently
        r = convert("orders.camel.yaml", YAML, "java");
        assertThat(r.converted()).isTrue();
        assertThat(r.notes()).anyMatch(n -> n.contains("beans of orders.camel.yaml are not carried over"));
    }

    @Test
    void javaToYamlWithoutRunningIt() {
        String java = """
                import org.apache.camel.builder.RouteBuilder;

                public class OrderRoute extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("timer:tick").routeId("tick")
                            .setBody(simple("Hello ${date:now}"))
                            .to("log:out");
                    }
                }
                """;
        RouteDslConverter.Result r = convert("OrderRoute.java", java, "yaml");
        assertThat(r.converted()).isTrue();
        assertThat(r.fileName()).isEqualTo("OrderRoute.camel.yaml");
        assertThat(r.content()).contains("id: tick", "uri: timer:tick", "Hello ${date:now}");
    }

    @Test
    void whatCannotBeConvertedIsRefusedWithAReason() {
        String processor = """
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick").process(e -> e.getIn().setBody("x")).to("log:out");
                    }
                }
                """;
        assertThat(convert("MyRoute.java", processor, "yaml").refused()).contains("cannot be converted without running it");

        String predicate = """
                public class MyRoute extends RouteBuilder {
                    public void configure() {
                        from("timer:tick").filter(header("x").isEqualTo("y")).to("log:out");
                    }
                }
                """;
        assertThat(convert("MyRoute.java", predicate, "xml").refused()).contains("write it with simple first");

        assertThat(convert("orders.camel.xml", XML, "xml").refused()).contains("is xml already");
        assertThat(convert("notes.txt", "x", "yaml").refused()).contains("no YAML, XML or Java route file");
    }

    @Test
    void semanticDeclarationsAreNotLostWithoutAWord() {
        String yaml = """
                - semantic:
                    question:
                      urgent:
                        type: boolean
                        instructions: Urgent?
                - route:
                    from:
                      uri: direct:input
                      steps:
                        - log: Hello
                """;
        assertThat(convert("semantic.camel.yaml", yaml, "xml").refused()).contains("semantic declarations");

        String java = """
                import org.apache.camel.builder.RouteBuilder;
                import static org.apache.camel.semantic.SemanticQuestionsBuilder.semanticQuestions;
                public class SemanticRoute extends RouteBuilder {
                    public void configure() {
                        semanticQuestions(this).question("urgent").type("boolean").register();
                        from("direct:input").log("Hello");
                    }
                }
                """;
        // the parser cannot read the declaration: refused at its line, not dropped
        assertThat(convert("SemanticRoute.java", java, "yaml").refused()).contains("SemanticRoute.java:5",
                "cannot be converted without running it");
    }

    @Test
    void theJavaClassImportsWhatItsRoutesUse() {
        String routes = """
                from("direct:a")
                    .setBody(expression().xpath("/t:ticket").namespaces(Map.of("t", "urn:tickets")).end())
                    .log(LoggingLevel.WARN, "${body}");
                """;
        assertThat(RouteDslConverter.javaClass("Tickets", routes)).contains("import java.util.Map;",
                "import org.apache.camel.LoggingLevel;", "import org.apache.camel.builder.RouteBuilder;")
                .doesNotContain("ExchangePattern");
    }

    @Test
    void namesOfTheConvertedFiles() {
        assertThat(RouteDslConverter.targetName("orders.camel.yaml", "xml")).isEqualTo("orders.camel.xml");
        assertThat(RouteDslConverter.targetName("my-routes.xml", "yaml")).isEqualTo("my-routes.camel.yaml");
        assertThat(RouteDslConverter.targetName("my-routes.camel.xml", "java")).isEqualTo("MyRoutes.java");
        assertThat(RouteDslConverter.targetName("OrderRoute.java", "yaml")).isEqualTo("OrderRoute.camel.yaml");
        assertThat(RouteDslConverter.javaClassName("1st")).isEqualTo("Route1st");
    }

    @Test
    void theFirstDifferenceOfTwoDumps() {
        assertThat(RouteDslConverter.firstDifference("<a/>\n<b/>", "<a/>\n  <b/>")).isNull();
        assertThat(RouteDslConverter.firstDifference("<a/>\n<b/>", "<a/>\n<c/>")).isEqualTo("expected <b/> but found <c/>");
        // bean definitions go, the bean EIP of a route stays
        String xml = """
                <bean name="x" type="Foo">
                    <properties>
                        <property key="a" value="b"/>
                    </properties>
                </bean>
                <route>
                    <bean ref="x"/>
                </route>
                """;
        assertThat(RouteDslConverter.withoutBeans(xml)).doesNotContain("Foo", "property").contains("<bean ref=\"x\"/>");
    }
}
