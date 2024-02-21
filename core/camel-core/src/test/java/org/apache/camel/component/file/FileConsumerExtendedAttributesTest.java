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
package org.apache.camel.component.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for consuming a batch of files (multiple files in one consume)
 */
public class FileConsumerExtendedAttributesTest extends ContextTestSupport {
    private static final String FILE = "attributes.txt";

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        Files.createFile(testDirectory().resolve(Path.of("basic", FILE)));
        Files.createFile(testDirectory().resolve(Path.of("basic-as-default", FILE)));
        Files.createFile(testDirectory().resolve(Path.of("basic-as-default-with-filter", FILE)));
        Files.createFile(testDirectory().resolve(Path.of("posix", FILE)));
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                fromF("file://%s/basic?initialDelay=0&delay=10&extendedAttributes=basic:*", testDirectory())
                        .convertBodyTo(String.class)
                        .to("mock:basic");
                fromF("file://%s/basic-as-default?initialDelay=0&delay=10&extendedAttributes=*", testDirectory())
                        .convertBodyTo(String.class).to("mock:basic-as-default");
                fromF("file://%s/basic-as-default-with-filter?initialDelay=0&delay=10&extendedAttributes=size,lastModifiedTime,lastAccessTime",
                        testDirectory()).convertBodyTo(String.class)
                        .to("mock:basic-as-default-with-filter");
                fromF("file://%s/posix?initialDelay=0&delay=10&extendedAttributes=posix:*", testDirectory())
                        .convertBodyTo(String.class)
                        .to("mock:posix");
            }
        };
    }

    @Test
    public void testBasicAttributes() throws Exception {
        testAttributes("mock:basic", "basic:");
    }

    @Test
    public void testBasicAttributesAsDefault() throws Exception {
        testAttributes("mock:basic-as-default", "basic:");
    }

    @Test
    public void testBasicAttributesAsDefaultWithFilter() throws Exception {
        testAttributes("mock:basic-as-default", "basic:");
    }

    @Test
    public void testPosixAttributes() throws Exception {
        if (FileUtil.isWindows()) {
            return;
        }
        testAttributes("mock:posix", "posix:");
    }

    private void testAttributes(String mockEndpoint, String prefix) throws Exception {
        MockEndpoint mock = getMockEndpoint(mockEndpoint);
        mock.expectedMessageCount(1);
        mock.message(0).header("CamelFileExtendedAttributes").isNotNull();
        mock.message(0).header("CamelFileExtendedAttributes").convertTo(Map.class);
        assertMockEndpointsSatisfied();

        Map<String, Object> attributes = mock.getExchanges().get(0).getIn().getHeader("CamelFileExtendedAttributes", Map.class);
        assertNotNull(attributes);
        assertFalse(attributes.isEmpty());
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            assertTrue(entry.getKey().startsWith(prefix));
        }
    }
}
