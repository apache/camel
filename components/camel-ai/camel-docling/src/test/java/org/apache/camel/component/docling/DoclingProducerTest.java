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

package org.apache.camel.component.docling;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

public class DoclingProducerTest extends CamelTestSupport {

    @Test
    @EnabledIfSystemProperty(named = "docling.test.enabled", matches = "true")
    public void testMarkdownConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader(
                "direct:convert-markdown",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH,
                testFile.toString(),
                String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    @EnabledIfSystemProperty(named = "docling.test.enabled", matches = "true")
    public void testHtmlConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader(
                "direct:convert-html",
                testFile.toString(),
                DoclingHeaders.OPERATION,
                DoclingOperations.CONVERT_TO_HTML,
                String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    @EnabledIfSystemProperty(named = "docling.test.enabled", matches = "true")
    public void testContentInBodyEnabled() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader(
                "direct:convert-content-in-body",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH,
                testFile.toString(),
                String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
        // When contentInBody is true, result should contain the actual content, not a file path
        assertTrue(result.contains("Test Document") || result.contains("test document"));
    }

    @Test
    @EnabledIfSystemProperty(named = "docling.test.enabled", matches = "true")
    public void testContentInBodyDisabled() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader(
                "direct:convert-file-path",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH,
                testFile.toString(),
                String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
        // When contentInBody is false, result should be a file path
        assertTrue(result.endsWith(".md") || result.endsWith(".html"));
        assertTrue(Files.exists(Path.of(result)));
    }

    private Path createTestFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-test", ".md");
        Files.write(
                tempFile,
                "# Test Document\n\nThis is a test document for Docling processing.\n\n## Section 1\n\nSome content here.\n\n- List item 1\n- List item 2\n"
                        .getBytes());
        return tempFile;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:convert-markdown").to("docling:convert?operation=CONVERT_TO_MARKDOWN");

                from("direct:convert-html").to("docling:convert?operation=CONVERT_TO_HTML");

                from("direct:convert-content-in-body")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true");

                from("direct:convert-file-path")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=false");
            }
        };
    }
}
