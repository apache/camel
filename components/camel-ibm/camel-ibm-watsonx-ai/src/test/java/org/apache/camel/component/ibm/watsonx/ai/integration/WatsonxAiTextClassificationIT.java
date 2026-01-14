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

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse;
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
 * Integration tests for watsonx.ai text classification operations. These tests require valid IBM Cloud credentials and
 * COS configuration to be provided as system properties:
 * <ul>
 * <li>camel.ibm.watsonx.ai.apiKey - IBM Cloud API key</li>
 * <li>camel.ibm.watsonx.ai.projectId - watsonx.ai project ID</li>
 * <li>camel.ibm.watsonx.ai.baseUrl - watsonx.ai base URL (optional, defaults to us-south)</li>
 * <li>camel.ibm.watsonx.ai.cosUrl - Cloud Object Storage URL</li>
 * <li>camel.ibm.watsonx.ai.documentConnectionId - COS connection ID for documents</li>
 * <li>camel.ibm.watsonx.ai.documentBucket - COS bucket for documents</li>
 * </ul>
 *
 * <p>
 * Supported foundation models for text classification:
 * <ul>
 * <li>mistral-small-3-1-24b-instruct-2503 (certified for key-value pair classification)</li>
 * <li>llama-4-maverick-17b-128e-instruct-fp8 (alternative)</li>
 * <li>mistral-medium-2505 (alternative)</li>
 * </ul>
 * </p>
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
                                 disabledReason = "IBM COS document bucket not provided")
})
public class WatsonxAiTextClassificationIT extends WatsonxAiTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(WatsonxAiTextClassificationIT.class);
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
        try (InputStream is = WatsonxAiTextClassificationIT.class.getResourceAsStream(TEST_PDF)) {
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
    public void testUploadAndStartClassification() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:uploadAndClassify", testFile);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");
        assertTrue(body instanceof TextClassificationResponse, "Response should be TextClassificationResponse");

        String classificationId = exchange.getIn().getHeader(WatsonxAiConstants.CLASSIFICATION_ID, String.class);
        String status = exchange.getIn().getHeader(WatsonxAiConstants.CLASSIFICATION_STATUS, String.class);

        assertNotNull(classificationId, "Classification ID should be set");
        assertNotNull(status, "Classification status should be set");

        LOG.info("Classification started - ID: {}, Status: {}", classificationId, status);
    }

    @Test
    public void testUploadAndStartClassificationWithInputStream() throws Exception {
        mockResult.expectedMessageCount(1);

        try (InputStream is = Files.newInputStream(testFile.toPath())) {
            template.send("direct:uploadAndClassify", exchange -> {
                exchange.getIn().setBody(is);
                exchange.getIn().setHeader(Exchange.FILE_NAME, testFile.getName());
            });
        }

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        String classificationId = exchange.getIn().getHeader(WatsonxAiConstants.CLASSIFICATION_ID, String.class);

        assertNotNull(classificationId, "Classification ID should be set for InputStream upload");
        LOG.info("InputStream classification started - ID: {}", classificationId);
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
        // First, create a classification request
        template.sendBody("direct:uploadAndClassify", testFile);
        mockResult.expectedMessageCount(1);
        mockResult.assertIsSatisfied();

        String classificationId = mockResult.getExchanges().get(0).getIn()
                .getHeader(WatsonxAiConstants.CLASSIFICATION_ID, String.class);
        assertNotNull(classificationId, "Need classification ID for delete test");

        // Reset and test delete
        mockResult.reset();
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:deleteRequest", classificationId);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Boolean success = exchange.getIn().getBody(Boolean.class);
        Boolean headerSuccess = exchange.getIn().getHeader(WatsonxAiConstants.DELETE_SUCCESS, Boolean.class);

        assertNotNull(success, "Delete success should not be null");
        assertEquals(success, headerSuccess, "Body and header should match");

        LOG.info("Classification request {} deleted: {}", classificationId, success);
    }

    @Test
    public void testUploadClassifyAndFetch() throws Exception {
        mockResult.expectedMessageCount(1);

        template.sendBody("direct:uploadClassifyAndFetch", testFile);

        mockResult.assertIsSatisfied();

        Exchange exchange = mockResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");

        Boolean documentClassified = exchange.getIn()
                .getHeader(WatsonxAiConstants.DOCUMENT_CLASSIFIED, Boolean.class);
        String documentType = exchange.getIn()
                .getHeader(WatsonxAiConstants.CLASSIFICATION_RESULT, String.class);

        LOG.info("Synchronous classification completed - Classified: {}, Type: {}", documentClassified, documentType);
    }

    @Test
    public void testClassifyFromFileComponent() throws Exception {
        // This test demonstrates the seamless integration between Camel's file component
        // and the watsonx.ai classification. The route reads from a directory and
        // automatically classifies the file.

        // Set up mock expectations with extended timeout for classification
        // Classification can take 30+ seconds including upload, processing, and polling
        mockFileResult.expectedMessageCount(1);
        mockFileResult.setResultWaitTime(120000); // 2 minute timeout for classification

        // Copy the test file to the input directory to trigger the file consumer
        Path inputFile = fileInputDir.resolve("classify-me.pdf");
        try (InputStream is = WatsonxAiTextClassificationIT.class.getResourceAsStream(TEST_PDF)) {
            Files.copy(is, inputFile);
        }

        LOG.info("Copied test file to: {}", inputFile);

        // Wait for the file to be picked up and classified
        mockFileResult.assertIsSatisfied();

        Exchange exchange = mockFileResult.getExchanges().get(0);
        Object body = exchange.getIn().getBody();

        assertNotNull(body, "Response body should not be null");

        Boolean documentClassified = exchange.getIn()
                .getHeader(WatsonxAiConstants.DOCUMENT_CLASSIFIED, Boolean.class);
        String documentType = exchange.getIn()
                .getHeader(WatsonxAiConstants.CLASSIFICATION_RESULT, String.class);
        String originalFileName = exchange.getIn()
                .getHeader(Exchange.FILE_NAME, String.class);

        LOG.info("File component classification - File: {}, Classified: {}, Type: {}",
                originalFileName, documentClassified, documentType);
    }

    @Test
    public void testUploadAndFetchClassification() throws Exception {
        // Step 1: Upload and start classification
        mockResult.expectedMessageCount(1);
        template.sendBody("direct:uploadAndClassify", testFile);
        mockResult.assertIsSatisfied();

        Exchange startExchange = mockResult.getExchanges().get(0);
        String classificationId = startExchange.getIn().getHeader(WatsonxAiConstants.CLASSIFICATION_ID, String.class);
        assertNotNull(classificationId, "Classification ID should be set");
        LOG.info("Classification started - ID: {}", classificationId);

        // Step 2: Poll for completion
        String status = startExchange.getIn().getHeader(WatsonxAiConstants.CLASSIFICATION_STATUS, String.class);
        int maxAttempts = 30;
        int attempt = 0;

        while (!"completed".equals(status) && !"failed".equals(status) && attempt < maxAttempts) {
            Thread.sleep(2000); // Wait 2 seconds between polls
            attempt++;

            mockResult.reset();
            mockResult.expectedMessageCount(1);

            template.sendBodyAndHeader("direct:fetchClassification", null,
                    WatsonxAiConstants.CLASSIFICATION_ID, classificationId);

            mockResult.assertIsSatisfied();

            Exchange fetchExchange = mockResult.getExchanges().get(0);
            status = fetchExchange.getIn().getHeader(WatsonxAiConstants.CLASSIFICATION_STATUS, String.class);

            LOG.info("Poll attempt {} - Status: {}", attempt, status);

            if ("completed".equals(status)) {
                Boolean documentClassified = fetchExchange.getIn()
                        .getHeader(WatsonxAiConstants.DOCUMENT_CLASSIFIED, Boolean.class);
                String documentType = fetchExchange.getIn()
                        .getHeader(WatsonxAiConstants.CLASSIFICATION_RESULT, String.class);

                LOG.info("Classification completed - Classified: {}, Type: {}", documentClassified, documentType);
            } else if ("failed".equals(status)) {
                String errorCode = fetchExchange.getIn()
                        .getHeader(WatsonxAiConstants.ERROR_CODE, String.class);
                String errorMessage = fetchExchange.getIn()
                        .getHeader(WatsonxAiConstants.ERROR_MESSAGE, String.class);

                LOG.error("Classification failed - Code: {}, Message: {}", errorCode, errorMessage);
            }
        }

        assertTrue("completed".equals(status) || "failed".equals(status),
                "Classification should complete within timeout");

        // Log final result
        if ("failed".equals(status)) {
            LOG.warn("Classification ended with status 'failed'. Check error details above.");
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:uploadAndClassify")
                        .to(buildTextClassificationEndpointUri("textClassificationUpload"))
                        .to("mock:result");

                from("direct:uploadClassifyAndFetch")
                        .to(buildTextClassificationEndpointUri("textClassificationUploadAndFetch"))
                        .to("mock:result");

                from("direct:fetchClassification")
                        .to(buildTextClassificationEndpointUri("textClassificationFetch"))
                        .to("mock:result");

                from("direct:uploadFile")
                        .to(buildTextClassificationEndpointUri("textClassificationUploadFile"))
                        .to("mock:result");

                from("direct:deleteRequest")
                        .to(buildTextClassificationEndpointUri("textClassificationDeleteRequest"))
                        .to("mock:result");

                // File component integration route
                // Reads PDF files from the input directory and classifies them synchronously
                fromF("file:%s?noop=true&include=.*\\.pdf", fileInputDir.toAbsolutePath())
                        .log("Processing file: ${header.CamelFileName}")
                        .to(buildTextClassificationEndpointUri("textClassificationUploadAndFetch"))
                        .log("Classification result: ${header.CamelIBMWatsonxAiClassificationResult}")
                        .to("mock:fileResult");
            }
        };
    }
}
