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
package org.apache.camel.maven.packaging;

import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.maven.packaging.endpoint.SomeEndpoint;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithBadHeaders;
import org.apache.camel.maven.packaging.endpoint.SomeEndpointWithoutHeaders;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointHeaderModel;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The unit test of {@link EndpointSchemaGeneratorMojo}.
 */
class EndpointSchemaGeneratorMojoTest {

    private final EndpointSchemaGeneratorMojo mojo = new EndpointSchemaGeneratorMojo();
    private final ComponentModel model = new ComponentModel();

    @BeforeEach
    void init() throws Exception {
        mojo.sourceRoots = List.of(
                Paths.get(EndpointSchemaGeneratorMojoTest.class.getResource("/").toURI())
                        .resolve("../../src/test/java/"));
        mojo.project = new MavenProject();
    }

    @Test
    void testCanRetrieveMetadataOfHeaders() {
        mojo.addEndpointHeaders(model, SomeEndpoint.class);
        List<EndpointHeaderModel> endpointHeaders = model.getEndpointHeaders();
        assertEquals(18, endpointHeaders.size());
        for (int i = 1; i < endpointHeaders.size(); i++) {
            EndpointHeaderModel header = endpointHeaders.get(i);
            assertEquals("header", header.getKind());
            assertEquals(String.format("name-%d", i + 1), header.getName());
            assertEquals(String.format("key%d desc", i + 1), header.getDescription());
            assertTrue(header.getDisplayName().isEmpty());
            assertTrue(header.getJavaType().isEmpty());
            assertFalse(header.isRequired());
            assertInstanceOf(String.class, header.getDefaultValue());
            assertTrue(((String) header.getDefaultValue()).isEmpty());
            assertFalse(header.isDeprecated());
            assertTrue(header.getDeprecationNote().isEmpty());
            assertFalse(header.isSecret());
            assertTrue(header.getLabel().isEmpty());
            assertNull(header.getEnums());
            assertEquals("common", header.getGroup());
        }
        EndpointHeaderModel header = endpointHeaders.get(0);
        assertEquals("header", header.getKind());
        assertEquals("name-1", header.getName());
        assertEquals("key1 desc", header.getDescription());
        assertEquals("my display name", header.getDisplayName());
        assertEquals("org.apache.camel.maven.packaging.endpoint.SomeEndpoint$MyEnum", header.getJavaType());
        assertTrue(header.isRequired());
        assertEquals("VAL1", header.getDefaultValue());
        assertTrue(header.isDeprecated());
        assertEquals("my deprecated note", header.getDeprecationNote());
        assertTrue(header.isSecret());
        assertEquals("my label", header.getLabel());
        assertEquals(3, header.getEnums().size());
        assertEquals("my label", header.getGroup());
    }

    @Test
    void testHeadersNotProperlyDefinedAreIgnored() {
        mojo.addEndpointHeaders(model, SomeEndpointWithBadHeaders.class);
        assertEquals(0, model.getEndpointHeaders().size());
    }

    @Test
    void testEndpointWithoutHeadersAreIgnored() {
        mojo.addEndpointHeaders(model, SomeEndpointWithoutHeaders.class);
        assertEquals(0, model.getEndpointHeaders().size());
    }
}
