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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DoclingServeProducerTest extends CamelTestSupport {

    @Test
    @EnabledIfSystemProperty(named = "docling.serve.test.enabled", matches = "true")
    public void testMarkdownConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-markdown-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    @EnabledIfSystemProperty(named = "docling.serve.test.enabled", matches = "true")
    public void testHtmlConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-html-serve",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.CONVERT_TO_HTML, String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    @EnabledIfSystemProperty(named = "docling.serve.test.enabled", matches = "true")
    public void testUrlConversionWithDoclingServe() throws Exception {
        // Test converting a document from a URL
        String url = "https://arxiv.org/pdf/2501.17887";

        String result = template.requestBody("direct:convert-url-serve", url, String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
    }

    @Test
    @EnabledIfSystemProperty(named = "docling.serve.test.enabled", matches = "true")
    public void testJsonConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-json-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
        // JSON response should contain some structure
        assertTrue(result.contains("{") || result.contains("["));
    }

    private Path createTestFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-serve-test", ".md");
        Files.write(tempFile,
                "# Test Document\n\nThis is a test document for Docling-Serve processing.\n\n## Section 1\n\nSome content here.\n\n- List item 1\n- List item 2\n"
                        .getBytes());
        return tempFile;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Get the docling-serve URL from system property, default to localhost:5001
                String doclingServeUrl = System.getProperty("docling.serve.url", "http://localhost:5001");

                from("direct:convert-markdown-serve")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&useDoclingServe=true&doclingServeUrl="
                            + doclingServeUrl + "&contentInBody=true");

                from("direct:convert-html-serve")
                        .to("docling:convert?operation=CONVERT_TO_HTML&useDoclingServe=true&doclingServeUrl="
                            + doclingServeUrl + "&contentInBody=true");

                from("direct:convert-json-serve")
                        .to("docling:convert?operation=CONVERT_TO_JSON&useDoclingServe=true&doclingServeUrl="
                            + doclingServeUrl + "&contentInBody=true");

                from("direct:convert-url-serve")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&useDoclingServe=true&doclingServeUrl="
                            + doclingServeUrl + "&contentInBody=true");
            }
        };
    }

}
