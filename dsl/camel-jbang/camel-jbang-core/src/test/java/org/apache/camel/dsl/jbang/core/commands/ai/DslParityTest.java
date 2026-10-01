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
import java.util.stream.Stream;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The same routes written in the YAML, Java and XML DSL get the same problems on the matching lines. YAML is checked on
 * its text and Java and XML on the model they are read into; the checks of a uri and of an expression are shared, and
 * this keeps what finds them from drifting apart.
 */
class DslParityTest {

    private static CamelCatalog catalog;

    @TempDir
    Path dir;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    /**
     * A route in the three DSLs.
     *
     * @param name   what the case is about
     * @param marker a text on the line the problem is on, the same in the three DSLs; null when the route is valid
     * @param key    a text the message of the problem has in the three DSLs
     */
    record Case(String name, String yaml, String java, String xml, String marker, String key) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static String yaml(String steps, String from) {
        return """
                - route:
                    from:
                      uri: "%s"
                      steps:
                %s""".formatted(from, steps.indent(8));
    }

    private static String java(String steps, String from) {
        return """
                import org.apache.camel.builder.RouteBuilder;

                public class MyRoute extends RouteBuilder {
                    @Override
                    public void configure() throws Exception {
                        from("%s")
                %s;
                    }
                }
                """.formatted(from, steps.stripTrailing().indent(12).stripTrailing());
    }

    private static String xml(String steps, String from) {
        return """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="%s"/>
                %s    </route>
                </routes>
                """.formatted(from.replace("&", "&amp;"), steps.indent(8));
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case(
                        "valid",
                        yaml("""
                                - filter:
                                    expression:
                                      simple:
                                        expression: "${header.foo} == 'bar'"
                                    steps:
                                      - to:
                                          uri: "seda:out"
                                """, "timer:tick?period=1000"),
                        java("""
                                .filter(simple("${header.foo} == 'bar'"))
                                    .to("seda:out")
                                .end()
                                """, "timer:tick?period=1000"),
                        xml("""
                                <filter>
                                    <simple>${header.foo} == 'bar'</simple>
                                    <to uri="seda:out"/>
                                </filter>
                                """, "timer:tick?period=1000"),
                        null, null),
                new Case(
                        "unknown option",
                        yaml("""
                                - to:
                                    uri: "seda:out"
                                """, "timer:tick?peroid=1000"),
                        java("""
                                .to("seda:out")
                                """, "timer:tick?peroid=1000"),
                        xml("""
                                <to uri="seda:out"/>
                                """, "timer:tick?peroid=1000"),
                        "peroid", "Unknown option 'peroid'"),
                new Case(
                        "invalid enum value",
                        yaml("""
                                - to:
                                    uri: "file:out?fileExist=Overide"
                                """, "timer:tick"),
                        java("""
                                .to("file:out?fileExist=Overide")
                                """, "timer:tick"),
                        xml("""
                                <to uri="file:out?fileExist=Overide"/>
                                """, "timer:tick"),
                        "Overide", "Invalid enum value 'Overide'"),
                new Case(
                        "producer option on a consumer",
                        yaml("""
                                - to:
                                    uri: "seda:out"
                                """, "seda:in?blockWhenFull=true"),
                        java("""
                                .to("seda:out")
                                """, "seda:in?blockWhenFull=true"),
                        xml("""
                                <to uri="seda:out"/>
                                """, "seda:in?blockWhenFull=true"),
                        "blockWhenFull", "not applicable in consumer only mode"),
                new Case(
                        "simple predicate that does not parse",
                        yaml("""
                                - filter:
                                    expression:
                                      simple:
                                        expression: "${header.foo} =="
                                    steps:
                                      - to:
                                          uri: "seda:out"
                                """, "timer:tick"),
                        java("""
                                .filter(simple("${header.foo} =="))
                                    .to("seda:out")
                                .end()
                                """, "timer:tick"),
                        xml("""
                                <filter>
                                    <simple>${header.foo} ==</simple>
                                    <to uri="seda:out"/>
                                </filter>
                                """, "timer:tick"),
                        "${header.foo} ==", "Simple syntax error"),
                new Case(
                        "simple expression with an unknown function",
                        yaml("""
                                - setBody:
                                    expression:
                                      simple:
                                        expression: "Hello ${headr.name}"
                                """, "timer:tick"),
                        java("""
                                .setBody(simple("Hello ${headr.name}"))
                                """, "timer:tick"),
                        xml("""
                                <setBody>
                                    <simple>Hello ${headr.name}</simple>
                                </setBody>
                                """, "timer:tick"),
                        "headr", "Simple syntax error"),
                new Case(
                        "to with an expression",
                        yaml("""
                                - to:
                                    uri: "seda:${header.queue}"
                                """, "timer:tick"),
                        java("""
                                .to("seda:${header.queue}")
                                """, "timer:tick"),
                        xml("""
                                <to uri="seda:${header.queue}"/>
                                """, "timer:tick"),
                        "${header.queue}", "toD"),
                new Case(
                        "producer-only component as from",
                        yaml("""
                                - to:
                                    uri: "seda:out"
                                """, "log:in"),
                        java("""
                                .to("seda:out")
                                """, "log:in"),
                        xml("""
                                <to uri="seda:out"/>
                                """, "log:in"),
                        "log:in", "producer-only"),
                new Case(
                        "several endpoints in one uri",
                        yaml("""
                                - to:
                                    uri: "seda:a,seda:b"
                                """, "timer:tick"),
                        java("""
                                .to("seda:a,seda:b")
                                """, "timer:tick"),
                        xml("""
                                <to uri="seda:a,seda:b"/>
                                """, "timer:tick"),
                        "seda:a,seda:b", "names several"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void theSameProblemsInTheThreeDsls(Case c) {
        check("route.camel.yaml", c.yaml(), c);
        check("MyRoute.java", c.java(), c);
        check("routes.xml", c.xml(), c);
    }

    private static void check(String file, String content, Case c) {
        List<String> errors = SourceValidator.validate(file, content, catalog, null, null);
        if (c.marker() == null) {
            assertThat(errors).as(file + "\n" + content).isEmpty();
            return;
        }
        assertThat(errors).as(file + "\n" + content).hasSize(1);
        assertThat(errors.get(0)).as(file).startsWith("Line " + lineOf(content, c.marker()) + ": ").contains(c.key());
    }

    private static int lineOf(String content, String marker) {
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains(marker)) {
                return i + 1;
            }
        }
        throw new IllegalArgumentException(marker + " is not in\n" + content);
    }

    /** A misspelled option of a YAML route: an error of the YAML DSL schema, reported on the line of the option. */
    @Test
    void aMisspelledYamlOptionIsReportedOnItsLine() {
        String yaml = yaml("""
                - log:
                    message: "${body}"
                    logLevel: WARN
                """, "timer:tick");
        List<String> errors = SourceValidator.validate("route.camel.yaml", yaml, catalog, null, null);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line " + lineOf(yaml, "logLevel") + ": ").contains("logLevel");
    }

    /** A direct: endpoint no route of the directory consumes, in the three DSLs. */
    @Test
    void aDirectEndpointNoRouteConsumes() throws Exception {
        Files.writeString(dir.resolve("other.camel.yaml"), """
                - route:
                    from:
                      uri: "direct:present"
                      steps:
                        - log:
                            message: "${body}"
                """);
        Case c = new Case(
                "direct",
                yaml("""
                        - to:
                            uri: "direct:present"
                        - to:
                            uri: "direct:missing"
                        """, "timer:tick"),
                java("""
                        .to("direct:present")
                        .to("direct:missing")
                        """, "timer:tick"),
                xml("""
                        <to uri="direct:present"/>
                        <to uri="direct:missing"/>
                        """, "timer:tick"),
                "direct:missing", "sends to direct:missing, and no route consumes it");
        for (String[] f : new String[][] {
                { "route.camel.yaml", c.yaml() }, { "MyRoute.java", c.java() }, { "routes.xml", c.xml() } }) {
            List<String> errors = SourceValidator.validate(f[0], f[1], catalog, null, dir);
            assertThat(errors).as(f[0]).hasSize(1);
            assertThat(errors.get(0)).as(f[0]).contains(c.key());
        }
    }
}
