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
package org.apache.camel.component.docling.integration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import ai.docling.core.DoclingDocument;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.ConversionStatus;
import org.apache.camel.component.docling.ConversionStatus.Status;
import org.apache.camel.component.docling.DoclingHeaders;
import org.apache.camel.component.docling.DoclingOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for Docling-Serve producer operations using test-infra for container management.
 *
 * This test demonstrates how to use the camel-test-infra-docling module to automatically spin up a Docling-Serve
 * container for testing without manual setup.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Too much resources on GitHub Actions")
class DoclingServeProducerIT extends DoclingITestSupport {

    @TempDir
    Path outputDir;

    @Test
    void testMarkdownConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-markdown-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertThat(result).containsIgnoringCase("Test Document");

        LOG.info("Successfully converted document to Markdown");
    }

    @Test
    void testHtmlConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-html-serve",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.CONVERT_TO_HTML, String.class);

        assertThat(result).containsIgnoringCase("<h1>Test Document</h1>");

        LOG.info("Successfully converted document to HTML");
    }

    @Test
    void testUrlConversionWithDoclingServe() throws Exception {
        // Test converting a document from a URL
        String url = "https://arxiv.org/pdf/2501.17887";

        String result = template.requestBody("direct:convert-url-serve", url, String.class);

        assertThat(result).containsIgnoringCase("An Efficient Open-Source Toolkit");

        LOG.info("Successfully converted document from URL");
    }

    @Test
    void testJsonConversionWithDoclingServe() throws Exception {
        Path testFile = createTestFile();

        DoclingDocument doclingDocument = template.requestBodyAndHeader("direct:convert-json-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), DoclingDocument.class);

        assertThat(doclingDocument).isNotNull();
        assertThat(doclingDocument.getSchemaName()).isEqualTo("DoclingDocument");

        LOG.info("Successfully converted document to JSON (DoclingDocument)");
    }

    @Test
    void testJsonConversionOfInvoice() throws Exception {
        Path testFile = createInvoicePdfFile();

        DoclingDocument doclingDocument = template.requestBodyAndHeader("direct:convert-json-serve",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), DoclingDocument.class);

        assertThat(doclingDocument).isNotNull();
        assertThat(doclingDocument.getSchemaName()).isEqualTo("DoclingDocument");
        assertThat(doclingDocument.getTables()).isNotEmpty();

        LOG.info("Successfully converted invoice PDF to DoclingDocument");
    }

    @Test
    void testConvertAndWriteToFile() throws Exception {
        Path testFile = createTestFile();

        // Send the file path to the route that converts and writes to file
        template.sendBodyAndHeader("direct:convert-and-write",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString());

        File outputFile = new File(outputDir.toFile(), "converted-output.md");
        assertThat(outputFile.toPath())
                .exists()
                .content().containsIgnoringCase("Test Document");

        LOG.info("Successfully converted document and wrote to file: {}", outputFile.getAbsolutePath());
        LOG.info("Output file size: {} bytes", outputFile.length());
    }

    @Test
    void testAsyncMarkdownConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-markdown",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertThat(result).containsIgnoringCase("Test Document");

        LOG.info("Successfully converted document to Markdown using async mode");
    }

    @Test
    void testAsyncHtmlConversion() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-html",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.CONVERT_TO_HTML, String.class);

        assertThat(result).containsIgnoringCase("<h1>Test Document</h1>");

        LOG.info("Successfully converted document to HTML using async mode");
    }

    @Test
    void testAsyncJsonConversion() throws Exception {
        Path testFile = createTestFile();

        DoclingDocument doclingDocument = template.requestBodyAndHeader("direct:convert-async-json",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), DoclingDocument.class);

        assertThat(doclingDocument).isNotNull();
        assertThat(doclingDocument.getSchemaName()).isEqualTo("DoclingDocument");

        LOG.info("Successfully converted document to JSON using async mode");
    }

    @Test
    void testAsyncUrlConversion() throws Exception {
        String url = "https://arxiv.org/pdf/2501.17887";

        String result = template.requestBody("direct:convert-async-url", url, String.class);

        assertThat(result).containsIgnoringCase("An Efficient Open-Source Toolkit");

        LOG.info("Successfully converted document from URL using async mode");
    }

    @Test
    void testAsyncConversionWithCustomTimeout() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:convert-async-custom-timeout",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertThat(result).contains("This is a test document for Docling-Serve processing.");

        LOG.info("Successfully converted document using async mode with custom timeout");
    }

    @Test
    void testAsyncConversionWithHeaderOverride() throws Exception {
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

        assertThat(result).contains("This is a test document for Docling-Serve processing.");

        LOG.info("Successfully converted document using async mode via header override");
    }

    @Test
    void testSubmitAsyncConversion() throws Exception {
        Path testFile = createTestFile();

        // Submit async conversion and get task ID
        String taskId = template.requestBodyAndHeader("direct:submit-async",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertThat(taskId).startsWith("task-");

        LOG.info("Successfully submitted async conversion with task ID: {}", taskId);
    }

    @Test
    void testCheckConversionStatus() throws Exception {
        Path testFile = createTestFile();

        // First, submit async conversion
        String taskId = template.requestBodyAndHeader("direct:submit-async",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(taskId, "Task ID should not be null");

        // Wait a bit for processing
        Thread.sleep(1000);

        // Check status
        ConversionStatus status = template.requestBody("direct:check-status", taskId, ConversionStatus.class);

        assertNotNull(status, "Status should not be null");
        assertNotNull(status.getTaskId(), "Status task ID should not be null");
        assertThat(status.getStatus()).isEqualTo(Status.COMPLETED);

        LOG.info("Successfully checked status for task {}: {}", taskId, status.getStatus());
    }

    @Test
    void testCustomAsyncWorkflow() throws Exception {
        Path testFile = createTestFile();

        // Custom workflow: submit, poll until complete, get result
        String taskId = template.requestBodyAndHeader("direct:submit-async",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertNotNull(taskId, "Task ID should not be null");
        LOG.info("Submitted conversion with task ID: {}", taskId);

        // Poll for completion
        ConversionStatus status = null;
        int maxAttempts = 120; // 120 seconds max (increased from 60)
        int attempts = 0;

        while (attempts < maxAttempts) {
            status = template.requestBody("direct:check-status", taskId, ConversionStatus.class);
            LOG.info("Attempt {}: Task {} status is {}", attempts + 1, taskId, status.getStatus());

            if (status.isCompleted()) {
                LOG.info("Task completed successfully after {} attempts", attempts + 1);
                break;
            } else if (status.isFailed()) {
                throw new RuntimeException("Task failed: " + status.getErrorMessage());
            }

            Thread.sleep(1000);
            attempts++;
        }

        assertNotNull(status, "Final status should not be null");
        if (!status.isCompleted()) {
            fail(String.format("Task did not complete within %d seconds. Last status: %s",
                    maxAttempts, status.getStatus()));
        }

        if (status.getResult() != null) {
            // TODO: this check can never happen as there is an if condition before. The status.getResult() is actually null, is it expected or a bug?
            assertTrue(status.getResult().length() > 0, "Result should not be empty");
            LOG.info("Successfully retrieved result: {} characters", status.getResult().length());
        }
    }

    @Test
    void testCustomPollingWorkflowWithRoute() throws Exception {
        Path testFile = createTestFile();

        // This test demonstrates using the built-in async mode with a route
        // The built-in mode handles polling automatically, which is the recommended approach
        String result = template.requestBodyAndHeader("direct:custom-polling-workflow",
                testFile.toString(),
                DoclingHeaders.INPUT_FILE_PATH, testFile.toString(), String.class);

        assertThat(result).contains("This is a test document for Docling-Serve processing.");

        LOG.info("Custom polling workflow (using built-in async mode) completed successfully with {} characters",
                result.length());
    }

    private Path createInvoicePdfFile() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("sample_invoice.pdf")) {
            Path tempFile = Files.createTempFile("docling-test-invoice", ".pdf");
            Files.copy(is, tempFile.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        }
    }

    private Path createTestFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-serve-test", ".md");
        Files.writeString(tempFile,
                """
                        # Test Document

                        This is a test document for Docling-Serve processing.

                        ## Section 1

                        Some content here.

                        - List item 1
                        - List item 2
                        """);
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

                // Custom async workflow routes
                from("direct:submit-async")
                        .to("docling:convert?operation=SUBMIT_ASYNC_CONVERSION");

                from("direct:check-status")
                        .to("docling:convert?operation=CHECK_CONVERSION_STATUS");

                // Custom polling workflow - demonstrates submit and poll pattern
                // This uses built-in async mode instead of manual polling to avoid complexity
                from("direct:custom-polling-workflow")
                        .log("Starting custom polling workflow for file: ${header.CamelDoclingInputFilePath}")
                        .to("docling:convert?operation=CONVERT_TO_MARKDOWN&contentInBody=true&" +
                            "useAsyncMode=true&asyncPollInterval=1000&asyncTimeout=120000");

            }
        };
    }

}
