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

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the MCP server is configured to strip {@code null} fields from JSON responses. The Quarkus MCP framework
 * serializes {@code @Tool} results through the default {@link ObjectMapper} produced by quarkus-jackson, which honors
 * the {@code quarkus.jackson.serialization-inclusion} build-time property. CAMEL-23476.
 */
class McpJsonSerializationTest {

    @Test
    void applicationPropertiesEnforcesNonNullSerialization() throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            assertThat(in).as("application.properties on classpath").isNotNull();
            props.load(in);
        }
        assertThat(props.getProperty("quarkus.jackson.serialization-inclusion"))
                .as("quarkus.jackson.serialization-inclusion must be 'non-null' to drop null fields from MCP responses")
                .isEqualToIgnoringCase("non-null");
    }

    @Test
    void componentInfoNullFieldsAreOmittedFromJson() throws Exception {
        ObjectMapper mapper = newConfiguredObjectMapper();

        CatalogTools.ComponentInfo info = new CatalogTools.ComponentInfo(
                "timer", "Timer", null, null, false, null);

        String json = mapper.writeValueAsString(info);

        assertThat(json).contains("\"name\":\"timer\"");
        assertThat(json).contains("\"title\":\"Timer\"");
        assertThat(json).contains("\"deprecated\":false");
        assertThat(json).doesNotContain("\"description\"");
        assertThat(json).doesNotContain("\"label\"");
        assertThat(json).doesNotContain("\"supportLevel\"");
    }

    @Test
    void optionInfoNullGroupIsOmittedFromJson() throws Exception {
        ObjectMapper mapper = newConfiguredObjectMapper();

        // Matches the bug from CAMEL-23476: component options pass null for group while endpoint options populate it.
        CatalogTools.OptionInfo opt = new CatalogTools.OptionInfo(
                "bridgeErrorHandler", "Allows bridging the consumer", "boolean", false, "false", null);

        String json = mapper.writeValueAsString(opt);

        assertThat(json).contains("\"name\":\"bridgeErrorHandler\"");
        assertThat(json).contains("\"required\":false");
        assertThat(json).doesNotContain("\"group\"");
    }

    @Test
    void componentDetailNullCollectionsAreOmittedFromJson() throws Exception {
        ObjectMapper mapper = newConfiguredObjectMapper();

        CatalogTools.ComponentDetailResult detail = new CatalogTools.ComponentDetailResult(
                "timer", "Timer", null, null, false, null, null, null, null, null,
                false, false, false, null, null);

        String json = mapper.writeValueAsString(detail);

        assertThat(json).contains("\"name\":\"timer\"");
        assertThat(json).contains("\"async\":false");
        assertThat(json).doesNotContain("\"description\"");
        assertThat(json).doesNotContain("\"supportLevel\"");
        assertThat(json).doesNotContain("\"groupId\"");
        assertThat(json).doesNotContain("\"componentOptions\"");
        assertThat(json).doesNotContain("\"endpointOptions\"");
    }

    private static ObjectMapper newConfiguredObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Mirrors the configuration applied by Quarkus' ConfigurationCustomizer when
        // quarkus.jackson.serialization-inclusion=non-null is set in application.properties.
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return mapper;
    }
}
