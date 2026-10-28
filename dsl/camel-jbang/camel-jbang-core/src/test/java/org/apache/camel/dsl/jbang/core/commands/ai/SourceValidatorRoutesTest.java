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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Camel checks of Java and XML DSL routes in the validation shared by the TUI, camel_validate_source and camel
 * validate (CAMEL-25208).
 */
class SourceValidatorRoutesTest {

    private static CamelCatalog catalog;

    @BeforeAll
    static void loadCatalog() {
        catalog = new DefaultCamelCatalog();
    }

    private static final String JAVA = """
            import org.apache.camel.builder.RouteBuilder;

            public class MyRoute extends RouteBuilder {
                @Override
                public void configure() throws Exception {
                    from("timer:tick?peroid=1000")
                        .process(e -> e.getMessage().setBody("hello"))
                        .to("seda:out");
                }
            }
            """;

    @Test
    void theEndpointsOfAJavaRouteAreChecked() {
        List<String> errors = SourceValidator.validate("MyRoute.java", JAVA, catalog, null, null);
        assertThat(errors).containsExactly(
                "Line 6: timer: Unknown option 'peroid'. Did you mean: [period]");
    }

    @Test
    void aJavaCompileErrorKeepsItsLine() {
        String src = JAVA.replace(".to(\"seda:out\");", ".to(\"seda:out\")");
        List<String> errors = SourceValidator.validate("MyRoute.java", src, catalog, null, null);
        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0)).contains("';' expected");
        // the Camel checks still run on what the parser reads
        assertThat(errors).anyMatch(e -> e.contains("Unknown option 'peroid'"));
    }

    @Test
    void theRoutesOfAnXmlFileAreChecked() {
        String xml = """
                <routes xmlns="http://camel.apache.org/schema/xml-io">
                    <route>
                        <from uri="timer:tick"/>
                        <to uri="seda:${header.queue}"/>
                    </route>
                </routes>
                """;
        List<String> errors = SourceValidator.validate("routes.xml", xml, catalog, null, null);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).startsWith("Line 4: ").contains("toD");
    }

    @Test
    void anXmlFileThatIsNotWellFormedIsReportedAsSuch() {
        String xml = "<routes><route><from uri=\"timer:tick\"/></routes>";
        List<String> errors = SourceValidator.validate("routes.xml", xml, catalog, null, null);
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("XML is not well formed");
    }

    @Test
    void theValidateToolSaysWhatWasNotChecked() throws Exception {
        String src = JAVA.replace(".to(\"seda:out\");", ".to(\"seda:out?\" + options());")
                .replace("    @Override", "    String options() { return \"size=1\"; }\n\n    @Override");
        Map<String, String> args = new HashMap<>();
        args.put("file", "MyRoute.java");
        args.put("content", src);
        JsonObject result = (JsonObject) Jsoner.deserialize(
                String.valueOf(ToolRegistry.execute("camel_validate_source", new ToolContext(), args)));
        assertThat((JsonArray) result.get("errors")).hasSize(1);
        JsonArray notChecked = (JsonArray) result.get("notChecked");
        assertThat(notChecked).hasSize(1);
        assertThat(notChecked.getString(0)).startsWith("Line 10: not checked: ").contains("options()");
    }

    @Test
    void aRouteIdThatAnotherFileOfTheProjectHasIsReported(@TempDir Path dir) throws IOException {
        // a model asked to declare a bean in beans.yaml copied the route there too: Camel did not start
        String route = """
                - route:
                    id: order-generator
                    from:
                      uri: timer:orders
                      steps:
                        - log: "New order"
                """;
        Files.writeString(dir.resolve("order-generator.camel.yaml"), route);
        String beans = """
                - beans:
                    - name: orderNumber
                      type: "#class:java.lang.StringBuilder"
                """ + route;
        assertThat(SourceValidator.validate("beans.yaml", beans, catalog, null, dir, null, false))
                .containsExactly("Line 4: route id 'order-generator' is already the id of a route in"
                                 + " order-generator.camel.yaml, and Camel does not start with two routes of one id (camel"
                                 + " run loads every file of the directory): change the route in order-generator.camel.yaml"
                                 + " rather than adding a copy here, or give this route another id");

        // the file itself, written again, and a route of another id are fine
        assertThat(SourceValidator.validate("order-generator.camel.yaml", route, catalog, null, dir, null, false)).isEmpty();
        assertThat(SourceValidator.validate("beans.yaml", beans.replace("id: order-generator", "id: other"), catalog, null,
                dir, null, false)).isEmpty();
    }
}
