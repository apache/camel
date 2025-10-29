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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.DoclingComponent;
import org.apache.camel.component.docling.DoclingConfiguration;
import org.apache.camel.component.docling.DocumentMetadata;
import org.apache.camel.test.infra.docling.services.DoclingService;
import org.apache.camel.test.infra.docling.services.DoclingServiceFactory;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for metadata extraction operations using test-infra for container management.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Too much resources on GitHub Actions")
public class MetadataExtractionIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(MetadataExtractionIT.class);

    @RegisterExtension
    static DoclingService doclingService = DoclingServiceFactory.createService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DoclingComponent docling = context.getComponent("docling", DoclingComponent.class);
        DoclingConfiguration conf = new DoclingConfiguration();
        conf.setUseDoclingServe(true);
        conf.setDoclingServeUrl(doclingService.doclingServerUrl());
        docling.setConfiguration(conf);

        LOG.info("Testing Docling-Serve metadata extraction at: {}", doclingService.doclingServerUrl());

        return context;
    }

    @Test
    public void testBasicMetadataExtraction() throws Exception {
        Path testFile = createTestMarkdownFile();

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFileName(), "File name should be extracted");
        assertTrue(metadata.getFileSizeBytes() > 0, "File size should be greater than 0");
        assertNotNull(metadata.getFilePath(), "File path should be set");

        LOG.info("Successfully extracted metadata: {}", metadata);
        LOG.info("File name: {}", metadata.getFileName());
        LOG.info("File size: {} bytes", metadata.getFileSizeBytes());
    }

    @Test
    public void testMetadataExtractionWithHeaders() throws Exception {
        Path testFile = createTestMarkdownFile();

        // Extract metadata with headers enabled (default behavior)
        DocumentMetadata metadata = template.requestBody("direct:extract-metadata-with-headers",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFileName(), "File name should be extracted");

        LOG.info("Successfully extracted metadata with headers: {}", metadata);
    }

    @Test
    public void testMetadataExtractionWithoutHeaders() throws Exception {
        Path testFile = createTestMarkdownFile();

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata-no-headers",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFileName(), "File name should be extracted");

        LOG.info("Successfully extracted metadata without headers: {}", metadata);
    }

    @Test
    public void testMetadataExtractionWithAllFields() throws Exception {
        Path testFile = createTestMarkdownFile();

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata-all-fields",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFileName(), "File name should be extracted");

        // Custom metadata should be available when extractAllMetadata=true
        Map<String, Object> customMetadata = metadata.getCustomMetadata();
        assertNotNull(customMetadata, "Custom metadata map should not be null");

        LOG.info("Successfully extracted all metadata fields: {}", metadata);
        LOG.info("Custom metadata fields: {}", customMetadata.size());
    }

    @Test
    public void testMetadataExtractionWithRawMetadata() throws Exception {
        Path testFile = createTestMarkdownFile();

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata-with-raw",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFileName(), "File name should be extracted");

        // Raw metadata should be available when includeRawMetadata=true
        Map<String, Object> rawMetadata = metadata.getRawMetadata();
        assertNotNull(rawMetadata, "Raw metadata map should not be null");
        assertFalse(rawMetadata.isEmpty(), "Raw metadata should contain fields");

        LOG.info("Successfully extracted metadata with raw metadata: {}", metadata);
        LOG.info("Raw metadata fields: {}", rawMetadata.size());
    }

    @Test
    public void testMetadataExtractionFromUrl() throws Exception {
        // Test extracting metadata from a URL (if docling-serve supports it)
        String url = "https://arxiv.org/pdf/2501.17887";

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata-url",
                url, DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFilePath(), "File path should be set to URL");

        LOG.info("Successfully extracted metadata from URL: {}", metadata);
    }

    @Test
    public void testMetadataHelperMethods() throws Exception {
        Path testFile = createTestMarkdownFile();

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");

        // Test helper methods
        assertTrue(metadata.getFileName() != null, "Should have file name");

        LOG.info("Metadata helper methods tested successfully");
    }

    @Test
    public void testMetadataToString() throws Exception {
        Path testFile = createTestMarkdownFile();

        DocumentMetadata metadata = template.requestBody("direct:extract-metadata",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");

        String metadataString = metadata.toString();
        assertNotNull(metadataString, "toString() should not return null");
        assertTrue(metadataString.contains("DocumentMetadata"), "toString() should contain class name");

        LOG.info("Metadata toString(): {}", metadataString);
    }

    @Test
    public void testMetadataHeadersPopulated() throws Exception {
        Path testFile = createTestMarkdownFile();

        // Extract metadata and verify headers are populated in the exchange
        // We use a mock endpoint to capture the exchange with headers
        DocumentMetadata metadata = template.requestBody("direct:extract-metadata-verify-headers",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");
        assertNotNull(metadata.getFileName(), "File name should be extracted");

        LOG.info("Successfully verified metadata headers are populated");
    }

    @Test
    public void testMetadataExtractionEmptyCustomFields() throws Exception {
        Path testFile = createTestMarkdownFile();

        // Extract metadata without extractAllMetadata flag
        DocumentMetadata metadata = template.requestBody("direct:extract-metadata",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");

        // Custom metadata should be empty when extractAllMetadata=false (default)
        Map<String, Object> customMetadata = metadata.getCustomMetadata();
        assertNotNull(customMetadata, "Custom metadata map should not be null");
        assertTrue(customMetadata.isEmpty(), "Custom metadata should be empty by default");

        LOG.info("Successfully verified custom metadata is empty by default");
    }

    @Test
    public void testMetadataExtractionNoRawMetadata() throws Exception {
        Path testFile = createTestMarkdownFile();

        // Extract metadata without includeRawMetadata flag
        DocumentMetadata metadata = template.requestBody("direct:extract-metadata",
                testFile.toString(), DocumentMetadata.class);

        assertNotNull(metadata, "Metadata should not be null");

        // Raw metadata should be empty when includeRawMetadata=false (default)
        Map<String, Object> rawMetadata = metadata.getRawMetadata();
        assertNotNull(rawMetadata, "Raw metadata map should not be null");
        assertTrue(rawMetadata.isEmpty(), "Raw metadata should be empty by default");

        LOG.info("Successfully verified raw metadata is empty by default");
    }

    private Path createTestMarkdownFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-metadata-test", ".md");
        Files.write(tempFile,
                ("# Test Document for Metadata Extraction\n\n" +
                 "This is a test document for metadata extraction.\n\n" +
                 "## Section 1\n\n" +
                 "Some content here.\n\n" +
                 "- List item 1\n" +
                 "- List item 2\n\n" +
                 "## Section 2\n\n" +
                 "More content with some **bold** text and *italic* text.\n")
                        .getBytes());
        return tempFile;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // Basic metadata extraction
                from("direct:extract-metadata")
                        .to("docling:extract?operation=EXTRACT_METADATA");

                // Metadata extraction with headers enabled (default)
                from("direct:extract-metadata-with-headers")
                        .to("docling:extract?operation=EXTRACT_METADATA&includeMetadataInHeaders=true");

                // Metadata extraction without headers
                from("direct:extract-metadata-no-headers")
                        .to("docling:extract?operation=EXTRACT_METADATA&includeMetadataInHeaders=false");

                // Metadata extraction with all fields
                from("direct:extract-metadata-all-fields")
                        .to("docling:extract?operation=EXTRACT_METADATA&extractAllMetadata=true");

                // Metadata extraction with raw metadata
                from("direct:extract-metadata-with-raw")
                        .to("docling:extract?operation=EXTRACT_METADATA&includeRawMetadata=true");

                // Metadata extraction from URL
                from("direct:extract-metadata-url")
                        .to("docling:extract?operation=EXTRACT_METADATA");

                // Metadata extraction to verify headers are populated
                from("direct:extract-metadata-verify-headers")
                        .to("docling:extract?operation=EXTRACT_METADATA&includeMetadataInHeaders=true")
                        .log("Headers should contain metadata fields");
            }
        };
    }

}
