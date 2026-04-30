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
package org.apache.camel.dsl.jbang.core.commands.mcp;

import io.quarkiverse.mcp.server.ToolCallException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PropertiesTranslateToolsTest {

    private final PropertiesTranslateTools tools = new PropertiesTranslateTools();

    @Test
    void translatesMainToSpringBoot() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.server.port=8080\ncamel.management.port=8081", "main", "spring-boot");

        assertThat(result.fromRuntime()).isEqualTo("main");
        assertThat(result.toRuntime()).isEqualTo("spring-boot");
        assertThat(result.summary().total()).isEqualTo(2);
        assertThat(result.summary().translated()).isEqualTo(2);
        assertThat(result.properties().get(0).translated()).isEqualTo("server.port=8080");
        assertThat(result.properties().get(0).status()).isEqualTo("translated");
        assertThat(result.properties().get(1).translated()).isEqualTo("management.server.port=8081");
        assertThat(result.properties().get(1).status()).isEqualTo("translated");
    }

    @Test
    void translatesMainToQuarkus() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.server.port=8080\ncamel.server.host=0.0.0.0\ncamel.server.path=/api\n"
                                                                                                     + "camel.management.port=9000\ncamel.management.path=/q\ncamel.management.enabled=true",
                "main", "quarkus");

        assertThat(result.summary().translated()).isEqualTo(6);
        assertThat(result.properties().get(0).translated()).isEqualTo("quarkus.http.port=8080");
        assertThat(result.properties().get(1).translated()).isEqualTo("quarkus.http.host=0.0.0.0");
        assertThat(result.properties().get(2).translated()).isEqualTo("quarkus.http.root-path=/api");
        assertThat(result.properties().get(3).translated()).isEqualTo("quarkus.management.port=9000");
        assertThat(result.properties().get(4).translated()).isEqualTo("quarkus.management.root-path=/q");
        assertThat(result.properties().get(5).translated()).isEqualTo("quarkus.management.enabled=true");
    }

    @Test
    void translatesSpringBootToQuarkus() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "server.port=8080\nserver.address=0.0.0.0\nmanagement.server.port=9000",
                "spring-boot", "quarkus");

        assertThat(result.summary().translated()).isEqualTo(3);
        assertThat(result.properties().get(0).translated()).isEqualTo("quarkus.http.port=8080");
        assertThat(result.properties().get(1).translated()).isEqualTo("quarkus.http.host=0.0.0.0");
        assertThat(result.properties().get(2).translated()).isEqualTo("quarkus.management.port=9000");
    }

    @Test
    void translatesQuarkusToMain() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "quarkus.http.port=8080\nquarkus.management.root-path=/admin",
                "quarkus", "main");

        assertThat(result.properties().get(0).translated()).isEqualTo("camel.server.port=8080");
        assertThat(result.properties().get(1).translated()).isEqualTo("camel.management.path=/admin");
    }

    @Test
    void agnosticCamelPropertiesPassThroughUnchanged() {
        String input = "camel.main.streamCachingEnabled=true\n"
                       + "camel.component.kafka.brokers=localhost:9092\n"
                       + "camel.dataformat.json-jackson.prettyPrint=true\n"
                       + "camel.health.enabled=true\n"
                       + "camel.routecontroller.enabled=true";

        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                input, "spring-boot", "quarkus");

        assertThat(result.summary().total()).isEqualTo(5);
        assertThat(result.summary().unchanged()).isEqualTo(5);
        assertThat(result.summary().translated()).isZero();
        assertThat(result.properties()).allSatisfy(p -> {
            assertThat(p.status()).isEqualTo("unchanged");
            assertThat(p.translated()).isEqualTo(p.original());
        });
    }

    @Test
    void legacySpringbootPrefixIsRewrittenToMain() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.springboot.name=Foo\ncamel.springboot.streamCachingEnabled=true",
                "spring-boot", "spring-boot");

        assertThat(result.summary().translated()).isEqualTo(2);
        assertThat(result.properties().get(0).translated()).isEqualTo("camel.main.name=Foo");
        assertThat(result.properties().get(0).note()).contains("4.13");
        assertThat(result.properties().get(1).translated()).isEqualTo("camel.main.streamCachingEnabled=true");
    }

    @Test
    void legacySpringbootPrefixIsRewrittenEvenWhenTargetIsQuarkus() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.springboot.name=Foo", "spring-boot", "quarkus");

        assertThat(result.properties().get(0).translated()).isEqualTo("camel.main.name=Foo");
        assertThat(result.properties().get(0).status()).isEqualTo("translated");
    }

    @Test
    void managementEnabledHasNoSpringBootEquivalent() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.management.enabled=true", "main", "spring-boot");

        assertThat(result.properties().get(0).status()).isEqualTo("unknown");
        assertThat(result.properties().get(0).note())
                .contains("Spring Boot Actuator");
    }

    @Test
    void unknownKeysArePassedThroughWithNote() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "spring.jpa.hibernate.ddl-auto=update\nlogging.level.root=INFO",
                "spring-boot", "quarkus");

        assertThat(result.summary().unknown()).isEqualTo(2);
        assertThat(result.properties()).allSatisfy(p -> {
            assertThat(p.status()).isEqualTo("unknown");
            assertThat(p.translated()).isEqualTo(p.original());
            assertThat(p.note()).contains("No translation rule");
        });
    }

    @Test
    void sameRuntimeReturnsKnownKeysUnchanged() {
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.server.port=8080\ncamel.main.streamCachingEnabled=true",
                "main", "main");

        assertThat(result.summary().unchanged()).isEqualTo(2);
        assertThat(result.summary().translated()).isZero();
        assertThat(result.properties().get(0).translated()).isEqualTo("camel.server.port=8080");
        assertThat(result.properties().get(1).translated()).isEqualTo("camel.main.streamCachingEnabled=true");
    }

    @Test
    void commentsAndBlankLinesArePreserved() {
        String input = """
                # this is a comment
                ! shell comment

                camel.server.port=8080
                """;

        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                input, "main", "quarkus");

        assertThat(result.summary().comments()).isEqualTo(3);
        assertThat(result.summary().translated()).isEqualTo(1);
        assertThat(result.properties().get(0).status()).isEqualTo("comment");
        assertThat(result.properties().get(1).status()).isEqualTo("comment");
        assertThat(result.properties().get(2).status()).isEqualTo("comment");
        assertThat(result.properties().get(3).status()).isEqualTo("translated");
        assertThat(result.properties().get(3).translated()).isEqualTo("quarkus.http.port=8080");
    }

    @Test
    void preservesPropertyValuesContainingEqualsSign() {
        // Values can legally contain '=' (e.g. JDBC URLs); only the first '=' is the delimiter.
        PropertiesTranslateTools.PropertiesTranslateResult result = tools.camel_properties_translate(
                "camel.main.name=foo=bar=baz", "main", "quarkus");

        assertThat(result.properties().get(0).status()).isEqualTo("unchanged");
        assertThat(result.properties().get(0).translated()).isEqualTo("camel.main.name=foo=bar=baz");
    }

    @Test
    void blankPropertiesArgumentThrows() {
        assertThatThrownBy(() -> tools.camel_properties_translate("   ", "main", "quarkus"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("properties");
    }

    @Test
    void invalidFromRuntimeThrows() {
        assertThatThrownBy(() -> tools.camel_properties_translate("camel.main.x=1", "bogus", "quarkus"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("fromRuntime");
    }

    @Test
    void invalidToRuntimeThrows() {
        assertThatThrownBy(() -> tools.camel_properties_translate("camel.main.x=1", "main", "bogus"))
                .isInstanceOf(ToolCallException.class)
                .hasMessageContaining("toRuntime");
    }
}
