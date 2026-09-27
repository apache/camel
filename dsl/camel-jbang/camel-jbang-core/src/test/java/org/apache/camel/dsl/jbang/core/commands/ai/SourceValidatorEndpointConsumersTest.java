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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24955: a direct: or seda: endpoint a route sends to, checked against the routes of every route file in the
 * directory - YAML, Java and XML - since the routes of an application are spread over files.
 */
class SourceValidatorEndpointConsumersTest {

    private static final CamelCatalog CATALOG = new DefaultCamelCatalog();

    private static final String ROUTE = """
            - route:
                id: tick
                from:
                  uri: timer:tick
                  steps:
                    - to:
                        uri: direct:lookup
            """;

    @TempDir
    Path dir;

    @Test
    void anEndpointNoRouteFileConsumesIsReported() throws Exception {
        Files.writeString(dir.resolve("other.camel.yaml"), """
                - route:
                    from:
                      uri: direct:other
                      steps:
                        - log: other
                """);
        List<String> msgs = SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null, dir);
        assertThat(msgs).singleElement().asString()
                .startsWith("route tick: sends to direct:lookup, and no route consumes it")
                .contains("the route fails to start");
    }

    @Test
    void aSiblingYamlFileConsumesIt() throws Exception {
        Files.writeString(dir.resolve("lookup.camel.yaml"), """
                - route:
                    from:
                      uri: direct:lookup
                      steps:
                        - log: found
                """);
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null, dir)).isEmpty();
    }

    @Test
    void aSiblingJavaRouteConsumesIt() throws Exception {
        Files.writeString(dir.resolve("Lookup.java"), """
                import org.apache.camel.builder.RouteBuilder;

                public class Lookup extends RouteBuilder {
                    @Override
                    public void configure() {
                        from("direct://lookup?timeout=1000").log("found");
                    }
                }
                """);
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null, dir)).isEmpty();
    }

    @Test
    void aSiblingXmlRouteConsumesIt() throws Exception {
        Files.writeString(dir.resolve("lookup.camel.xml"), """
                <routes>
                    <route>
                        <from uri="direct:lookup"/>
                        <log message="found"/>
                    </route>
                </routes>
                """);
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null, dir)).isEmpty();
    }

    @Test
    void aJavaRouteInputThatIsNotALiteralKeepsTheCheckQuiet() throws Exception {
        // from(LOOKUP) may well be direct:lookup: the check cannot be certain, so it says nothing
        Files.writeString(dir.resolve("Lookup.java"), """
                import org.apache.camel.builder.RouteBuilder;

                public class Lookup extends RouteBuilder {
                    static final String LOOKUP = "direct:lookup";

                    @Override
                    public void configure() {
                        from(LOOKUP).log("found");
                    }
                }
                """);
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null, dir)).isEmpty();
    }

    @Test
    void theFileBeingValidatedIsReadFromItsContentNotFromDisk() throws Exception {
        // the editor's buffer is newer than the file on disk, which still consumes direct:lookup
        Files.writeString(dir.resolve("r.camel.yaml"), ROUTE + """
                - route:
                    from:
                      uri: direct:lookup
                      steps:
                        - log: found
                """);
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null, dir))
                .singleElement().asString().contains("sends to direct:lookup");
    }

    @Test
    void withoutADirectoryTheCheckSaysNothing() {
        assertThat(SourceValidator.validate("r.camel.yaml", ROUTE, CATALOG, null)).isEmpty();
    }
}
