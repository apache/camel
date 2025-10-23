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
import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.docling.services.DoclingService;
import org.apache.camel.test.infra.docling.services.DoclingServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for batch processing operations using test-infra for container management.
 */
public class BatchProcessingIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BatchProcessingIT.class);

    @RegisterExtension
    static DoclingService doclingService = DoclingServiceFactory.createService();

    @Test
    public void testBatchConvertToMarkdown() throws Exception {
        // Create test files
        List<String> filePaths = createTestFiles();
        LOG.info("Testing batch conversion of {} files to Markdown", filePaths.size());

        // Send batch for conversion
        BatchProcessingResults results = template.requestBody(
                "direct:batch-markdown",
                filePaths,
                BatchProcessingResults.class);

        assertNotNull(results);
        assertEquals(filePaths.size(), results.getTotalDocuments());
        assertTrue(results.getSuccessCount() > 0);

        LOG.info("Batch conversion completed: {}/{} successful, total time: {}ms",
                results.getSuccessCount(), results.getTotalDocuments(), results.getTotalProcessingTimeMs());

        // Verify individual results
        for (BatchConversionResult result : results.getResults()) {
            if (result.isSuccess()) {
                assertNotNull(result.getResult());
                assertTrue(result.getProcessingTimeMs() > 0);
                LOG.debug("Document {} processed in {}ms", result.getDocumentId(), result.getProcessingTimeMs());
            }
        }
    }

    @Test
    public void testBatchConvertWithParallelism() throws Exception {
        List<String> filePaths = createTestFiles();
        LOG.info("Testing batch conversion with custom parallelism (2 threads)");

        // Test with custom parallelism
        BatchProcessingResults results = template.requestBodyAndHeader(
                "direct:batch-parallel",
                filePaths,
                DoclingHeaders.BATCH_PARALLELISM, 2,
                BatchProcessingResults.class);

        assertNotNull(results);
        assertEquals(filePaths.size(), results.getTotalDocuments());

        LOG.info("Batch with parallelism=2 completed: {}/{} successful",
                results.getSuccessCount(), results.getTotalDocuments());
    }

    @Test
    public void testBatchConvertWithFailOnFirstError() throws Exception {
        List<String> filePaths = Arrays.asList(
                createTestFile("test1.md").toString(),
                "/nonexistent/file.pdf", // This will fail
                createTestFile("test2.md").toString());

        LOG.info("Testing batch conversion with failOnFirstError=true (expecting failure)");

        boolean exceptionThrown = false;
        try {
            template.requestBodyAndHeader(
                    "direct:batch-fail-on-error",
                    filePaths,
                    DoclingHeaders.BATCH_FAIL_ON_FIRST_ERROR, true,
                    BatchProcessingResults.class);
            fail("Expected an exception to be thrown when failOnFirstError=true and a document fails");
        } catch (Exception e) {
            // Expected to fail due to failOnFirstError=true
            exceptionThrown = true;
            LOG.info("Batch correctly failed on first error with exception: {}", e.getClass().getName());
            LOG.info("Exception message: {}", e.getMessage());

            // Check if the exception message contains the expected text
            // The message might be wrapped in a CamelExecutionException, so check the full message or cause
            String fullMessage = e.getMessage();
            if (e.getCause() != null && e.getCause().getMessage() != null) {
                fullMessage = fullMessage + " " + e.getCause().getMessage();
            }

            assertTrue(fullMessage.contains("Batch processing failed") || fullMessage.contains("nonexistent"),
                    "Expected exception message to contain 'Batch processing failed' or 'nonexistent', but was: "
                                                                                                               + fullMessage);
        }

        assertTrue(exceptionThrown, "Expected an exception to be thrown");
    }

    @Test
    public void testBatchConvertContinueOnError() throws Exception {
        List<String> filePaths = Arrays.asList(
                createTestFile("test1.md").toString(),
                "/nonexistent/file.pdf", // This will fail
                createTestFile("test2.md").toString());

        LOG.info("Testing batch conversion with failOnFirstError=false (continue on error)");

        BatchProcessingResults results = template.requestBodyAndHeader(
                "direct:batch-continue-on-error",
                filePaths,
                DoclingHeaders.BATCH_FAIL_ON_FIRST_ERROR, false,
                BatchProcessingResults.class);

        assertNotNull(results);
        assertEquals(3, results.getTotalDocuments());
        assertTrue(results.hasAnySuccessful());
        assertTrue(results.hasAnyFailures());
        assertEquals(1, results.getFailureCount());

        LOG.info("Batch completed with partial success: {}/{} successful, {}/{} failed",
                results.getSuccessCount(), results.getTotalDocuments(),
                results.getFailureCount(), results.getTotalDocuments());
    }

    @Test
    public void testBatchExtractText() throws Exception {
        List<String> filePaths = createTestFiles();
        LOG.info("Testing batch text extraction from {} files", filePaths.size());

        BatchProcessingResults results = template.requestBody(
                "direct:batch-text",
                filePaths,
                BatchProcessingResults.class);

        assertNotNull(results);
        assertEquals(filePaths.size(), results.getTotalDocuments());

        LOG.info("Batch text extraction completed: {}/{} successful",
                results.getSuccessCount(), results.getTotalDocuments());

        // Verify text extraction
        for (BatchConversionResult result : results.getSuccessful()) {
            assertNotNull(result.getResult());
            LOG.debug("Extracted text from {}: {} characters",
                    result.getOriginalPath(), result.getResult().length());
        }
    }

    @Test
    public void testBatchSplitResults() throws Exception {
        // Create test files
        List<String> filePaths = createTestFiles();
        LOG.info("Testing batch conversion with split results for {} files", filePaths.size());

        // Process batch with splitBatchResults=true
        // This will return a List<BatchConversionResult> instead of BatchProcessingResults
        @SuppressWarnings("unchecked")
        List<BatchConversionResult> results = template.requestBody(
                "direct:batch-split",
                filePaths,
                List.class);

        assertNotNull(results);
        assertEquals(3, results.size(), "Should have 3 individual results");

        LOG.info("Batch processing returned {} individual results", results.size());

        // Verify each individual result
        for (int i = 0; i < results.size(); i++) {
            BatchConversionResult result = results.get(i);
            assertNotNull(result, "Result " + i + " should not be null");
            assertTrue(result.isSuccess(), "Result " + i + " should be successful");
            assertNotNull(result.getResult(), "Result " + i + " should have content");
            assertEquals(i, result.getBatchIndex(), "Result should have correct batch index");

            LOG.info("Individual result {}: documentId={}, success={}, contentLength={}",
                    i, result.getDocumentId(), result.isSuccess(), result.getResult().length());
        }
    }

    @Test
    public void testBatchSplitWithSplitter() throws Exception {
        // Create test files
        Path file1 = createTestFileWithContent("doc-A.md", "Document A", "Content A");
        Path file2 = createTestFileWithContent("doc-B.md", "Document B", "Content B");
        Path file3 = createTestFileWithContent("doc-C.md", "Document C", "Content C");

        List<String> filePaths = Arrays.asList(file1.toString(), file2.toString(), file3.toString());

        LOG.info("Testing batch conversion with split and individual processing");

        // Send to route that splits and processes individually
        template.sendBody("direct:batch-split-and-process", filePaths);

        // Wait a bit for async processing
        Thread.sleep(1000);

        // Verify that the mock endpoint received 3 individual exchanges
        getMockEndpoint("mock:individual-result").expectedMessageCount(3);
        getMockEndpoint("mock:individual-result").assertIsSatisfied();

        // Verify each exchange contains a BatchConversionResult
        List<org.apache.camel.Exchange> exchanges = getMockEndpoint("mock:individual-result").getReceivedExchanges();
        assertEquals(3, exchanges.size());

        for (int i = 0; i < exchanges.size(); i++) {
            org.apache.camel.Exchange exchange = exchanges.get(i);
            BatchConversionResult result = exchange.getIn().getBody(BatchConversionResult.class);

            assertNotNull(result, "Exchange " + i + " should contain a BatchConversionResult");
            assertTrue(result.isSuccess(), "Document should be successfully converted");
            assertNotNull(result.getResult(), "Should have converted content");

            // Verify content contains expected text
            String content = result.getResult();
            assertTrue(content.contains("Document") || content.contains("Content"),
                    "Content should contain document text");

            LOG.info("Processed individual exchange {}: documentId={}, batchIndex={}, contentLength={}",
                    i, result.getDocumentId(), result.getBatchIndex(), content.length());
        }

        LOG.info("✓ All individual exchanges processed successfully!");
    }

    @Test
    public void testBatchConversionWithContentVerification() throws Exception {
        // Create test files with specific content
        Path file1 = createTestFileWithContent("invoice-001.md", "Invoice #001", "Total: $1,500.00");
        Path file2 = createTestFileWithContent("report-Q1.md", "Q1 Financial Report", "Revenue: $50,000");
        Path file3 = createTestFileWithContent("meeting-notes.md", "Team Meeting Notes", "Action Items:");

        List<String> filePaths = Arrays.asList(
                file1.toString(),
                file2.toString(),
                file3.toString());

        LOG.info("Testing batch conversion with content verification for {} files", filePaths.size());

        // Process batch
        BatchProcessingResults results = template.requestBody(
                "direct:batch-markdown-with-content",
                filePaths,
                BatchProcessingResults.class);

        assertNotNull(results);
        assertEquals(3, results.getTotalDocuments());
        assertTrue(results.isAllSuccessful(), "All conversions should succeed");

        LOG.info("Batch conversion completed: {}/{} successful in {}ms",
                results.getSuccessCount(), results.getTotalDocuments(), results.getTotalProcessingTimeMs());

        // Verify content for each file
        int verifiedCount = 0;
        for (BatchConversionResult result : results.getResults()) {
            assertTrue(result.isSuccess(), "Document " + result.getDocumentId() + " should be successful");
            assertNotNull(result.getResult(), "Result should not be null");
            assertTrue(result.getProcessingTimeMs() > 0, "Processing time should be greater than 0");

            String convertedContent = result.getResult();
            String originalPath = result.getOriginalPath();

            LOG.info("Verifying content for document {}: {} ({} characters)",
                    result.getDocumentId(), originalPath, convertedContent.length());

            // Verify content based on which file it is
            if (originalPath.contains("invoice-001")) {
                assertTrue(convertedContent.contains("Invoice #001") || convertedContent.contains("Invoice"),
                        "Invoice content should contain invoice reference");
                assertTrue(convertedContent.contains("1,500") || convertedContent.contains("1500"),
                        "Invoice should contain the amount");
                LOG.debug("✓ Invoice file content verified");
                verifiedCount++;
            } else if (originalPath.contains("report-Q1")) {
                assertTrue(convertedContent.contains("Q1") || convertedContent.contains("Financial"),
                        "Report should contain Q1 or Financial reference");
                assertTrue(convertedContent.contains("50,000") || convertedContent.contains("50000")
                        || convertedContent.contains("Revenue"),
                        "Report should contain revenue information");
                LOG.debug("✓ Report file content verified");
                verifiedCount++;
            } else if (originalPath.contains("meeting-notes")) {
                assertTrue(convertedContent.contains("Meeting") || convertedContent.contains("Team"),
                        "Meeting notes should contain meeting reference");
                assertTrue(convertedContent.contains("Action"),
                        "Meeting notes should contain action items section");
                LOG.debug("✓ Meeting notes file content verified");
                verifiedCount++;
            }

            // Verify batch metadata
            assertTrue(result.getBatchIndex() >= 0 && result.getBatchIndex() < 3,
                    "Batch index should be between 0 and 2");
            assertNotNull(result.getDocumentId(), "Document ID should not be null");
        }

        assertEquals(3, verifiedCount, "All 3 files should have been verified");

        // Verify aggregate statistics
        assertEquals(3, results.getSuccessCount());
        assertEquals(0, results.getFailureCount());
        assertEquals(100.0, results.getSuccessRate(), 0.01);
        assertTrue(results.getTotalProcessingTimeMs() > 0);

        LOG.info("✓ All batch conversion content verification passed successfully!");
    }

    private Path createTestFileWithContent(String filename, String title, String content) throws Exception {
        Path tempFile = Files.createTempFile("docling-batch-verify-", "-" + filename);
        String fileContent = String.format("# %s\n\n%s\n\n## Details\n\nContent: %s\n\n- Item 1\n- Item 2\n",
                title, content, filename);
        Files.write(tempFile, fileContent.getBytes());
        LOG.debug("Created test file: {} with title '{}' and content '{}'", filename, title, content);
        return tempFile;
    }

    private List<String> createTestFiles() throws Exception {
        Path file1 = createTestFile("doc1.md");
        Path file2 = createTestFile("doc2.md");
        Path file3 = createTestFile("doc3.md");

        return Arrays.asList(
                file1.toString(),
                file2.toString(),
                file3.toString());
    }

    private Path createTestFile(String filename) throws Exception {
        Path tempFile = Files.createTempFile("docling-batch-test-", "-" + filename);
        String content = "# Test Document: " + filename + "\n\n"
                         + "This is a test document for batch processing.\n\n"
                         + "## Section 1\n\n"
                         + "Some content here.\n\n"
                         + "- List item 1\n"
                         + "- List item 2\n";
        Files.write(tempFile, content.getBytes());
        return tempFile;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DoclingComponent docling = context.getComponent("docling", DoclingComponent.class);
        DoclingConfiguration conf = new DoclingConfiguration();
        conf.setUseDoclingServe(true);
        conf.setDoclingServeUrl(doclingService.doclingServerUrl());
        docling.setConfiguration(conf);

        LOG.info("Testing Docling batch processing at: {}", doclingService.doclingServerUrl());

        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:batch-markdown")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchSize=10&batchParallelism=4&contentInBody=true");

                from("direct:batch-parallel")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchSize=10&contentInBody=true");

                from("direct:batch-fail-on-error")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchFailOnFirstError=true&contentInBody=true");

                from("direct:batch-continue-on-error")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchFailOnFirstError=false&contentInBody=true");

                from("direct:batch-text")
                        .to("docling:convert?operation=BATCH_EXTRACT_TEXT&batchSize=10&batchParallelism=4&contentInBody=true");

                from("direct:batch-markdown-with-content")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchSize=10&batchParallelism=4&contentInBody=true");

                // Route with splitBatchResults=true
                from("direct:batch-split")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchSize=10&batchParallelism=4&contentInBody=true&splitBatchResults=true");

                // Route that splits and processes each document individually
                from("direct:batch-split-and-process")
                        .to("docling:convert?operation=BATCH_CONVERT_TO_MARKDOWN&batchSize=10&batchParallelism=4&contentInBody=true&splitBatchResults=true")
                        .split(body())
                        .log("Processing individual document: ${body.documentId}")
                        .to("mock:individual-result");
            }
        };
    }

}
