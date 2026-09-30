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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteAssist.Diagnostic;
import org.apache.camel.dsl.jbang.core.commands.ai.RouteAssist.Severity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAssistTest {

    private static CamelCatalog catalog;

    @TempDir
    Path dir;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    private static String java(String configure) {
        return """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                %s
                    }
                }
                """.formatted(configure);
    }

    private static List<Diagnostic> errors(String file, String content) {
        return RouteAssist.diagnostics(file, content, catalog, null).stream()
                .filter(d -> d.severity() == Severity.ERROR).toList();
    }

    @Test
    void aValidJavaRouteHasNoErrors() {
        String src = java("""
                        from("timer:tick?period=1000")
                            .filter(simple("${header.foo} == 'bar'"))
                                .log("Got ${body}")
                                .to("seda:out");
                """);
        assertThat(errors("MyRoute.java", src)).isEmpty();
    }

    @Test
    void anUnknownOptionOfAJavaEndpointIsReportedOnItsLine() {
        String src = java("""
                        from("timer:tick?peroid=1000")
                            .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(6);
        assertThat(errors.get(0).message()).contains("timer: Unknown option 'peroid'").contains("period");
        assertThat(errors.get(0).format()).startsWith("Line 6: ");
    }

    @Test
    void anOptionOfAUriBuiltOverSeveralLinesIsReportedOnItsOwnLine() {
        String src = java("""
                        from("timer:tick"
                                + "?period=1000"
                                + "&fixedRat=true")
                            .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(8);
        assertThat(errors.get(0).message()).contains("Unknown option 'fixedRat'");
    }

    @Test
    void theEndpointDslIsCheckedAsTheUriItBuilds() {
        String src = """
                import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

                public class MyRoute extends EndpointRouteBuilder {
                    @Override
                    public void configure() throws Exception {
                        from(timer("tick").period(1000))
                            .to(file("out").fileExist("Overide"));
                    }
                }
                """;
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(7);
        assertThat(errors.get(0).message()).contains("Invalid enum value 'Overide'").contains("Override");
    }

    @Test
    void aConstantOfTheClassIsResolved() {
        String src = """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    private static final String IN = "timer:tick?peroid=5";

                    @Override
                    public void configure() throws Exception {
                        from(IN).to("seda:out");
                    }
                }
                """;
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(8);
        assertThat(errors.get(0).message()).contains("Unknown option 'peroid'");
    }

    @Test
    void aSimpleExpressionThatDoesNotParse() {
        String src = java("""
                        from("timer:tick")
                            .filter(simple("${header.foo} =="))
                                .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(7);
        assertThat(errors.get(0).message()).startsWith("Simple syntax error");
    }

    @Test
    void aSimpleExpressionOnTheLineAfterItsStep() {
        String src = java("""
                        from("timer:tick")
                            .filter(
                                simple("${header.foo} =="))
                            .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(8);
    }

    @Test
    void aSimpleExpressionOfTheFluentBuilder() {
        String src = java("""
                        from("timer:tick")
                            .setBody().simple("${header.foo")
                            .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(7);
        assertThat(errors.get(0).message()).startsWith("Simple syntax error");
    }

    @Test
    void aTernaryWrittenAsLiteralText() {
        String src = java("""
                        from("timer:tick")
                            .setBody(simple("${header.foo} > 5 ? big : small"))
                            .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(7);
    }

    @Test
    void aToWithAnExpressionNeedsToD() {
        String src = java("""
                        from("timer:tick")
                            .to("seda:${header.queue}");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("${header.queue}").contains("toD");
        // toD is what evaluates the uri per message
        assertThat(errors("MyRoute.java", java("""
                        from("timer:tick")
                            .toD("seda:${header.queue}");
                """))).isEmpty();
    }

    @Test
    void aProducerOnlyComponentCannotBeConsumed() {
        String src = java("""
                        from("log:foo")
                            .to("seda:out");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("log is a producer-only component");
    }

    @Test
    void severalEndpointsInOneUri() {
        String src = java("""
                        from("timer:tick")
                            .to("seda:a,seda:b");
                """);
        List<Diagnostic> errors = errors("MyRoute.java", src);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).contains("names several").contains("multicast");
    }

    @Test
    void aDirectEndpointNoRouteConsumes() throws Exception {
        Files.writeString(dir.resolve("other.camel.yaml"), """
                - route:
                    from:
                      uri: direct:present
                      steps:
                        - log: "${body}"
                """);
        String src = java("""
                        from("timer:tick")
                            .to("direct:present")
                            .to("direct:missing")
                            .to("direct:local");

                        from("direct:local").log("local");
                """);
        List<Diagnostic> errors = RouteAssist.diagnostics("MyRoute.java", src, catalog, dir).stream()
                .filter(d -> d.severity() == Severity.ERROR).toList();
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(8);
        assertThat(errors.get(0).message()).contains("sends to direct:missing, and no route consumes it")
                .contains("from(\"direct:missing\")");
    }

    @Test
    void aPartTheParserCannotReadIsNotChecked() {
        String src = java("""
                        from("timer:tick")
                            .process(e -> e.getMessage().setBody("x"))
                            .to("seda:out?" + options());
                """).replace("    @Override", "    String options() { return \"size=1\"; }\n\n    @Override");
        List<Diagnostic> all = RouteAssist.diagnostics("MyRoute.java", src, catalog, null);
        assertThat(all).noneMatch(d -> d.severity() == Severity.ERROR);
        assertThat(all).anyMatch(d -> d.severity() == Severity.INFO && d.message().startsWith("not checked: "));
    }

    @Test
    void aJavaFileWithoutARouteBuilderIsNotRead() {
        assertThat(RouteAssist.supports("Foo.java", "public class Foo { }")).isFalse();
        assertThat(RouteAssist.diagnostics("Foo.java", "public class Foo { }", catalog, null)).isEmpty();
    }

    @Test
    void aValidXmlRouteHasNoErrors() {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route id="a">
                        <from uri="timer:tick?period=1000"/>
                        <choice>
                            <when>
                                <simple>${header.foo} == 'bar'</simple>
                                <to uri="seda:bar"/>
                            </when>
                        </choice>
                    </route>
                </routes>
                """;
        assertThat(RouteAssist.supports("routes.xml", xml)).isTrue();
        assertThat(errors("routes.xml", xml)).isEmpty();
    }

    @Test
    void anUnknownOptionOfAnXmlEndpointIsReportedOnItsLine() {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick?peroid=1000"/>
                        <to uri="seda:out"/>
                    </route>
                </routes>
                """;
        List<Diagnostic> errors = errors("routes.xml", xml);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(3);
        assertThat(errors.get(0).message()).contains("Unknown option 'peroid'");
    }

    @Test
    void aSimplePredicateOfAnXmlRouteThatDoesNotParse() {
        String xml = """
                <camel>
                    <route>
                        <from uri="timer:tick"/>
                        <filter>
                            <simple>${header.foo} ==</simple>
                            <to uri="seda:out"/>
                        </filter>
                    </route>
                </camel>
                """;
        List<Diagnostic> errors = errors("camel.xml", xml);
        assertThat(errors).hasSize(1);
        // the line of the <simple> element, not of the filter it belongs to
        assertThat(errors.get(0).line()).isEqualTo(5);
        assertThat(errors.get(0).message()).startsWith("Simple syntax error");
    }

    @Test
    void anElementTheXmlDslDoesNotHave() {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <fitler>
                            <simple>${header.foo}</simple>
                        </fitler>
                    </route>
                </routes>
                """;
        List<Diagnostic> errors = errors("routes.xml", xml);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).line()).isEqualTo(4);
        assertThat(errors.get(0).message()).contains("fitler");
    }

    @Test
    void aSpringXmlFileIsNotRead() {
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <camelContext xmlns="http://camel.apache.org/schema/spring">
                        <route><from uri="timer:tick?peroid=1"/><to uri="seda:x"/></route>
                    </camelContext>
                </beans>
                """;
        assertThat(RouteAssist.supports("beans.xml", xml)).isFalse();
    }

    @Test
    void theNodesOfALine() {
        String src = java("""
                        from("timer:tick")
                            .filter(simple("${header.foo} == 'bar'"))
                                .to("seda:out");
                """);
        List<RouteNodes.Node> nodes = RouteAssist.nodes("MyRoute.java", src, catalog, null);
        List<RouteNodes.Node> filter = RouteNodes.at(nodes, 7);
        assertThat(filter).extracting(RouteNodes.Node::kind)
                .containsExactly(RouteNodes.Kind.STEP, RouteNodes.Kind.EXPRESSION);
        assertThat(filter.get(1).predicate()).isTrue();
        assertThat(filter.get(1).text()).isEqualTo("${header.foo} == 'bar'");
        List<RouteNodes.Node> to = RouteNodes.at(nodes, 8);
        assertThat(to).extracting(RouteNodes.Node::uri).contains("seda:out");
        assertThat(to.get(0).parents()).containsExactly("filter");
    }

    @Test
    void whatTheRuntimeAcceptsIsNotReported() {
        // found by running the checks over the Camel examples and Camel's own tests: none of these is wrong
        String src = java("""
                        from("kamelet:myTemplate?greeting=hello")
                            .to("jms:queue:orders?exchangePattern=InOut")
                            .to("kafka:out?additional-properties[transactional.id]=1234")
                            .setBody(simple("resource:classpath:script.txt"))
                            .setBody(simple("${type:com.acme.Codes.OK}"))
                            .setBody(simple("${function(greet)}"));
                """);
        assertThat(errors("MyRoute.java", src)).isEmpty();
    }

    @Test
    void aBeansXmlOfCdiIsNotRead() {
        assertThat(RouteAssist.supports("beans.xml", "<beans bean-discovery-mode=\"all\" version=\"4.0\"/>")).isFalse();
        assertThat(RouteAssist.supports("beans.xml", """
                <beans xmlns="https://jakarta.ee/xml/ns/jakartaee" bean-discovery-mode="all"/>
                """)).isFalse();
        assertThat(RouteAssist.supports("camel.xml", """
                <beans>
                    <route><from uri="timer:tick"/><to uri="seda:out"/></route>
                </beans>
                """)).isTrue();
    }

    @Test
    void anUnknownValueOfTheParserIsNotAPlaceholder() {
        assertThat(RouteModel.hasUnknownValue("mina:tcp://localhost:?{getPort()}")).isTrue();
        assertThat(RouteModel.hasUnknownValue("kafka:orders?{{kafka.options}}")).isFalse();
        assertThat(RouteModel.hasUnknownValue("kafka:orders")).isFalse();
    }
}
