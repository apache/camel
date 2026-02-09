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
package org.apache.camel.component.ibm.watsonx.ai.integration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for watsonx.ai text extraction operations. These tests require valid IBM Cloud credentials and COS
 * configuration to be provided as system properties:
 * <ul>
 * <li>camel.ibm.watsonx.ai.apiKey - IBM Cloud API key</li>
 * <li>camel.ibm.watsonx.ai.projectId - watsonx.ai project ID</li>
 * <li>camel.ibm.watsonx.ai.baseUrl - watsonx.ai base URL (optional, defaults to us-south)</li>
 * <li>camel.ibm.watsonx.ai.cosUrl - Cloud Object Storage URL</li>
 * <li>camel.ibm.watsonx.ai.documentConnectionId - COS connection ID for documents</li>
 * <li>camel.ibm.watsonx.ai.documentBucket - COS bucket for documents</li>
 * <li>camel.ibm.watsonx.ai.resultConnectionId - COS connection ID for results</li>
 * <li>camel.ibm.watsonx.ai.resultBucket - COS bucket for results</li>
 * </ul>
 *
 * <p>
 * Supported input file types: BMP, DOC, DOCX, GIF, HTML, JFIF, JPG, Markdown, PDF, PNG, PPT, PPTX, TIFF, XLSX
 * </p>
 */
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.apiKey", matches = ".+",
                                 disabledReason = "IBM watsonx.ai API Key not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.projectId", matches = ".+",
                                 disabledReason = "IBM watsonx.ai Project ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.cosUrl", matches = ".+",
                                 disabledReason = "IBM COS URL not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.documentConnectionId", matches = ".+",
                                 disabledReason = "IBM COS document connection ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.documentBucket", matches = ".+",
                                 disabledReason = "IBM COS document bucket not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.resultConnectionId", matches = ".+",
                                 disabledReason = "IBM COS result connection ID not provided"),
        @EnabledIfSystemProperty(named = "camel.ibm.watsonx.ai.resultBucket", matches = ".+",
                                 disabledReason = "IBM COS result bucket not provided")
})
public class WatsonxAiTextExtractionIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiTextExtractionIT.class);
    private static final String TEST_PDF = "/test-document.pdf";

    @TempDir
    static Path tempDir;

    @TempDir
    static Path fileInputDir;

    private static File testFile;

    @EndpointInject
    private ProducerTemplate template;

    @EndpointInject("mock:result")
    private MockEndpoint mockResult;

    @EndpointInject("mock:fileResult")
    private MockEndpoint mockFileResult;

    @BeforeAll
    static void createTestFile() throws IOException {
        // Copy the PDF from resources to a temp file for testing
        testFile = tempDir.resolve("test-document.pdf").toFile();
        try (InputStream is = WatsonxAiTextExtractionIT.class.getResourceAsStream(TEST_PDF)) {
            if (is == null) {
                throw new IOException("Test PDF not found in resources: " + TEST_PDF);
            }
            Files.copy(is, testFile.toPath());
        }
    }

    @BeforeEach
    public void resetMocks() {
        mockResult.reset();
        mockFileResult.reset();
    }

    @Test
    public void testUploadAndStartExtraction() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:uploadAndExtract", testFile);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof TextExtractionResponse, "Response should be TextExtractionResponse");

        TextExtractionResponse response = (TextExtractionResponse) body;
        String extractionId = exchange.getIn().getHeader(WatsonxAiConstants.EXTRACTION_ID, String.class);
        String status = exchange.getIn().getHeader(WatsonxAiConstants.EXTRACTION_STATUS, String.class);

        assertNotNull(extractionId, "Extraction ID should be set");
        assertNotNull(status, "Extraction status should be set");

        LOG.info("Extraction started - ID: {}, Status: {}", extractionId, status);
    }

    @Test
    public void testUploadAndStartExtractionWithInputStream() throws Exception {
        mockResult.expectedMessageCount(1);

        try (InputStream is = Files.newInputStream(testFile.toPath())) {
            template.send("direct:uploadAndExtract", exchange -> {
                exchange.getIn().setBody(is);
                exchange.getIn().setHeader(Exchange.FILE_NAME, testFile.getName());
            });
        }

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String extractionId = exchange.getIn().getHeader(WatsonxAiConstants.EXTRACTION_ID, String.class);

        assertNotNull(extractionId, "Extraction ID should be set for InputStream upload");
        LOG.info("InputStream extraction started - ID: {}", extractionId);
    }

    @Test
    public void testUploadExtractAndFetch() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:uploadExtractAndFetch", testFile);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof String, "Response should be extracted text (String)");

        String extractedText = (String) body;
        assertFalse(extractedText.isEmpty(), "Extracted text should not be empty");

        String headerText = exchange.getIn().getHeader(WatsonxAiConstants.EXTRACTED_TEXT, String.class);
        assertEquals(extractedText, headerText, "Body and header should contain same extracted text");

        LOG.info("Extracted text (first 200 chars): {}",
                extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);
    }

    @Test
    public void testUploadFile() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:uploadFile", testFile);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Boolean success = exchange.getIn().getBody(Boolean.class);
        Boolean headerSuccess = exchange.getIn().getHeader(WatsonxAiConstants.UPLOAD_SUCCESS, Boolean.class);

        assertNotNull(success, "Upload success should not be null");
        assertTrue(success, "Upload should be successful");
        assertEquals(success, headerSuccess, "Body and header should match");

        LOG.info("File upload successful: {}", success);
    }

    @Test
    public void testDeleteRequest() throws Exception {
        // First, create an extraction request
        template.sendBody("direct:uploadAndExtract", testFile);
        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied();

        String extractionId = mockResult.getExchanges().get(0).getIn()
                .getHeader(WatsonxAiConstants.EXTRACTION_ID, String.class);
        assertNotNull(extractionId, "Need extraction ID for delete test");

        // Reset and test delete
        mockResult.reset();
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:deleteRequest", extractionId);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Boolean success = exchange.getIn().getBody(Boolean.class);
        Boolean headerSuccess = exchange.getIn().getHeader(WatsonxAiConstants.DELETE_SUCCESS, Boolean.class);

        assertNotNull(success, "Delete success should not be null");
        assertEquals(success, headerSuccess, "Body and header should match");

        LOG.info("Extraction request {} deleted: {}", extractionId, success);
    }

    @Test
    public void testExtractFromFileComponent() throws Exception {
        // This test demonstrates the seamless integration between Camel's file component
        // and the watsonx.ai extraction. The route reads from a directory and
        // automatically extracts text from the file.

        // Set up mock expectations with extended timeout for extraction
        // Extraction can take 30+ seconds including upload, processing, and polling
        mockFileResult.expectedMessageCount(1);
        mockFileResult.setResultWaitTime(120000); // 2 minute timeout for extraction

        // Copy the test file to the input directory to trigger the file consumer
        Path inputFile = fileInputDir.resolve("extract-me.pdf");
        try (InputStream is = WatsonxAiTextExtractionIT.class.getResourceAsStream(TEST_PDF)) {
            Files.copy(is, inputFile);
        }

        LOG.info("Copied test file to: {}", inputFile);

        // Wait for the file to be picked up and extracted
        mockFileResult.assertIsSatisfied();

        Exchange exchange = mockFileResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof String, "Response should be extracted text (String)");

        String extractedText = (String) body;
        assertFalse(extractedText.isEmpty(), "Extracted text should not be empty");

        String originalFileName = exchange.getIn()
                .getHeader(Exchange.FILE_NAME, String.class);

        LOG.info("File component extraction - File: {}, Extracted text (first 200 chars): {}",
                originalFileName,
                extractedText.length() > 200 ? extractedText.substring(0, 200) + "..." : extractedText);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:uploadAndExtract")
                        .to(buildTextExtractionEndpointUri("textExtractionUpload"))
                        .to("mock:result");

                from("direct:uploadExtractAndFetch")
                        .to(buildTextExtractionEndpointUri("textExtractionUploadAndFetch"))
                        .to("mock:result");

                from("direct:uploadFile")
                        .to(buildTextExtractionEndpointUri("textExtractionUploadFile"))
                        .to("mock:result");

                from("direct:deleteRequest")
                        .to(buildTextExtractionEndpointUri("textExtractionDeleteRequest"))
                        .to("mock:result");

                // File component integration route
                // Reads PDF files from the input directory and extracts text from them synchronously
                fromF("file:%s?noop=true&include=.*\\.pdf", fileInputDir.toAbsolutePath())
                        .log("Processing file: ${header.CamelFileName}")
                        .to(buildTextExtractionEndpointUri("textExtractionUploadAndFetch"))
                        .log("Extraction completed for file: ${header.CamelFileName}")
                        .log("${body}")
                        .log("${headers}")
                        .to("mock:fileResult");

            }
        };
    }
}
