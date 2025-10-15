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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.docling.services.DoclingService;
import org.apache.camel.test.infra.docling.services.DoclingServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for Docling-Serve producer operations using test-infra for container management.
 *
 * This test demonstrates how to use the camel-test-infra-docling module to automatically spin up a Docling-Serve
 * container for testing without manual setup.
 */
public class DoclingServeProducerIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingServeProducerIT.class);

    @RegisterExtension
    static DoclingService doclingService = DoclingServiceFactory.createService();

    @TempDir
    Path outputDir;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DoclingComponent docling = context.getComponent("docling", DoclingComponent.class);
        DoclingConfiguration conf = new DoclingConfiguration();
        conf.setUseDoclingServe(true);
        conf.setDoclingServeUrl(doclingService.doclingServerUrl());
        docling.setConfiguration(conf);

        LOG.info("Testing Docling-Serve at: {}", doclingService.doclingServerUrl());

        return context;
    }

    @Test
    public void testMarkdownConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-markdown-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);

        LOG.info("Successfully converted document to Markdown");
    }

    @Test
    public void testHtmlConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-html-serve",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.CONVERT_TO_HTML, String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);

        LOG.info("Successfully converted document to HTML");
    }

    @Test
    public void testUrlConversionWithDoclingServe() throws Exception {
        // Test converting a document from a URL
        String url = "https://arxiv.org/pdf/2501.17887";

        String result = template.requestBody("direct:convert-url-serve", url, String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);

        LOG.info("Successfully converted document from URL");
    }

    @Test
    public void testJsonConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-json-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result);
        assertTrue(result.length() > 0);
        // JSON response should contain some structure
        assertTrue(result.contains("{") || result.contains("["));

        LOG.info("Successfully converted document to JSON");
    }

    @Test
    public void testConvertAndWriteToFile() throws Exception {
        Path testFile = createTestFile();

        // Send the file path to the route that converts and writes to file
        template.sendBodyAndHeader("direct:convert-and-write",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString());

        // Verify the output file was created
        File outputFile = new File(outputDir.toFile(), "converted-output.md");
        assertTrue(outputFile.exists(), "Output file should exist");
        assertTrue(outputFile.length() > 0, "Output file should not be empty");

        // Read and verify content
        String content = Files.readString(outputFile.toPath());
        assertNotNull(content);
        assertTrue(content.length() > 0);

        LOG.info("Successfully converted document and wrote to file: {}", outputFile.getAbsolutePath());
        LOG.info("Output file size: {} bytes", outputFile.length());
    }

    @Test
    public void testAsyncMarkdownConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-markdown",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result, "Async conversion result should not be null");
        assertTrue(result.length() > 0, "Async conversion result should not be empty");

        LOG.info("Successfully converted document to Markdown using async mode");
    }

    @Test
    public void testAsyncHtmlConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-html",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.CONVERT_TO_HTML, String.class);

        assertNotNull(result, "Async HTML conversion result should not be null");
        assertTrue(result.length() > 0, "Async HTML conversion result should not be empty");

        LOG.info("Successfully converted document to HTML using async mode");
    }

    @Test
    public void testAsyncJsonConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-json",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result, "Async JSON conversion result should not be null");
        assertTrue(result.length() > 0, "Async JSON conversion result should not be empty");
        assertTrue(result.contains("{") || result.contains("["), "JSON result should contain JSON structure");

        LOG.info("Successfully converted document to JSON using async mode");
    }

    @Test
    public void testAsyncUrlConversion() throws Exception {
        String url = "https://arxiv.org/pdf/2501.17887";

        String result = template.requestBody("direct:convert-async-url", url, String.class);

        assertNotNull(result, "Async URL conversion result should not be null");
        assertTrue(result.length() > 0, "Async URL conversion result should not be empty");

        LOG.info("Successfully converted document from URL using async mode");
    }

    @Test
    public void testAsyncConversionWithCustomTimeout() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-custom-timeout",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(result, "Async conversion with custom timeout should not be null");
        assertTrue(result.length() > 0, "Async conversion with custom timeout should not be empty");

        LOG.info("Successfully converted document using async mode with custom timeout");
    }

    @Test
    public void testAsyncConversionWithHeaderOverride() throws Exception {
        Path testFile = createTestFile();

        // Use sync endpoint but override with async header
        String result = template.requestBodyAndHeaders("direct:convert-markdown-serve",
                testFile.toString(),
                new java.util.HashMap<String, Object>() {
                    {
                        put(DoclingHeaders.INPUT_FILE_PATH, testFile.toString());
                        put(DoclingHeaders.USE_ASYNC_MODE, true);
                        put(DoclingHeaders.ASYNC_POLL_INTERVAL, 1000L);
                        put(DoclingHeaders.ASYNC_TIMEOUT, 120000L);
                    }
                }, String.class);

        assertNotNull(result, "Async conversion via header override should not be null");
        assertTrue(result.length() > 0, "Async conversion via header override should not be empty");

        LOG.info("Successfully converted document using async mode via header override");
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
                // Synchronous conversion routes
                from("direct:convert-markdown-serve")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true");

                from("direct:convert-html-serve")
                        .to("docling:convert?operation=CONVERT_TO_HTML&contentInBody=true");

                from("direct:convert-json-serve")
                        .to("docling:convert?operation=CONVERT_TO_JSON&contentInBody=true");

                from("direct:convert-url-serve")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true");

                from("direct:convert-and-write")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true")
                        .to("file:" + outputDir.toString() + "?fileName=converted-output.md");

                // Asynchronous conversion routes
                from("direct:convert-async-markdown")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&useAsyncMode=true&asyncPollInterval=1000&asyncTimeout=120000");

                from("direct:convert-async-html")
                        .to("docling:convert?operation=CONVERT_TO_HTML&contentInBody=true&useAsyncMode=true&asyncPollInterval=1000&asyncTimeout=120000");

                from("direct:convert-async-json")
                        .to("docling:convert?operation=CONVERT_TO_JSON&contentInBody=true&useAsyncMode=true&asyncPollInterval=1000&asyncTimeout=120000");

                from("direct:convert-async-url")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&useAsyncMode=true&asyncPollInterval=1000&asyncTimeout=120000");

                from("direct:convert-async-custom-timeout")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&useAsyncMode=true&asyncPollInterval=500&asyncTimeout=300000");
            }
        };
    }

}
