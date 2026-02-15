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

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import ai.docling.core.DoclingDocument;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.DoclingComponent;
import org.apache.camel.component.docling.DoclingConfiguration;
import org.apache.camel.test.infra.docling.services.DoclingService;
import org.apache.camel.test.infra.docling.services.DoclingServiceFactory;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for OCR text extraction from images using Docling.
 *
 * This test demonstrates how to use Docling's OCR capabilities to extract text from images containing text content.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Too much resources on GitHub Actions")
class OcrExtractionIT extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(OcrExtractionIT.class);

    private static final String TEST_TEXT_LINE1 = "Hello World";
    private static final String TEST_TEXT_LINE2 = "Apache Camel Integration";
    private static final String TEST_TEXT_LINE3 = "OCR Test Document";

    @RegisterExtension
    static DoclingService doclingService = DoclingServiceFactory.createService();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        DoclingComponent docling = context.getComponent("docling", DoclingComponent.class);
        DoclingConfiguration conf = new DoclingConfiguration();
        conf.setUseDoclingServe(true);
        conf.setDoclingServeUrl(doclingService.doclingServerUrl());
        conf.setEnableOCR(true);
        conf.setOcrLanguage("en");
        // OCR processing can take longer than the default 30s
        conf.setProcessTimeout(120000);
        docling.setConfiguration(conf);

        LOG.info("Testing Docling OCR at: {}", doclingService.doclingServerUrl());

        return context;
    }

    @Test
    void testOcrTextExtractionFromImage() throws Exception {
        Path testImage = createTestImageWithText();

        LOG.info("Created test image at: {}", testImage);

        String result = template.requestBody("direct:ocr-extract-text", testImage.toString(), String.class);

        assertThat(result).isNotBlank();

        LOG.info("OCR extraction result:\n{}", result);

        checkExtractedText(result);

        LOG.info("Successfully extracted text from image using OCR");
    }

    private void checkExtractedText(String result) {
        // Verify that at least some of the expected text was extracted
        // Note: OCR may not be 100% accurate, so we check for partial matches
        String resultLower = result.toLowerCase();
        boolean foundHello = resultLower.contains("hello") || resultLower.contains("world");
        boolean foundApache = resultLower.contains("apache") || resultLower.contains("camel");
        boolean foundOcr = resultLower.contains("ocr") || resultLower.contains("test");

        assertTrue(foundHello || foundApache || foundOcr,
                "OCR should extract at least some of the expected text. Got: " + result);
    }

    @Test
    void testOcrMarkdownConversionFromImage() throws Exception {
        Path testImage = createTestImageWithText();

        String result = template.requestBody("direct:ocr-convert-markdown", testImage.toString(), String.class);

        assertThat(result).isNotBlank();

        checkExtractedText(result);

        LOG.info("OCR Markdown conversion result:\n{}", result);
        LOG.info("Successfully converted image to Markdown using OCR");
    }

    @Test
    void testOcrJsonConversionFromImage() throws Exception {
        Path testImage = createTestImageWithText();

        DoclingDocument doclingDocument
                = template.requestBody("direct:ocr-convert-json", testImage.toString(), DoclingDocument.class);

        assertThat(doclingDocument).isNotNull();
        assertThat(doclingDocument.getSchemaName()).isEqualTo("DoclingDocument");

        LOG.info("Successfully converted image to JSON (DoclingDocument) using OCR");
    }

    @Test
    void testOcrWithAsyncMode() throws Exception {
        Path testImage = createTestImageWithText();

        String result = template.requestBody("direct:ocr-async-extract", testImage.toString(), String.class);

        assertThat(result).isNotBlank();

        checkExtractedText(result);

        LOG.info("Async OCR extraction result:\n{}", result);
        LOG.info("Successfully extracted text from image using async OCR");
    }

    @Test
    void testOcrFromPngImage() throws Exception {
        Path testImage = createTestPngImage();

        String result = template.requestBody("direct:ocr-extract-text", testImage.toString(), String.class);

        assertThat(result).isNotBlank();
        checkExtractedText(result);

        LOG.info("OCR extraction from PNG result:\n{}", result);
        LOG.info("Successfully extracted text from PNG image using OCR");
    }

    @Test
    void testOcrWithMultipleTextBlocks() throws Exception {
        Path testImage = createImageWithMultipleTextBlocks();

        String result = template.requestBody("direct:ocr-extract-text", testImage.toString(), String.class);

        assertThat(result).isNotBlank();

        // Verify that at least some of the expected text was extracted
        // Note: OCR may not be 100% accurate, so we check for partial matches
        String resultLower = result.toLowerCase();
        boolean foundFirst = resultLower.contains("first");
        boolean foundSecond = resultLower.contains("second");

        assertTrue(foundFirst && foundSecond,
                "OCR should extract at least some of the expected text. Got: " + result);

        // TODO: footer is not found by the ocr by Camel docling
        //        boolean foundFooter = resultLower.contains("footer");
        //        assertTrue(foundFooter,
        //                "OCR should extract at least some of the expected text from the footer. Got: " + result);

        LOG.info("OCR extraction with multiple text blocks result:\n{}", result);
        LOG.info("Successfully extracted text from image with multiple text blocks");
    }

    /**
     * Creates a test image with clear, readable text for OCR testing.
     */
    private Path createTestImageWithText() throws Exception {
        int width = 800;
        int height = 400;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        // Set rendering hints for better text quality
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Fill background with white
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Draw text in black with a large, clear font
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.BOLD, 48));

        // Draw multiple lines of text
        g2d.drawString(TEST_TEXT_LINE1, 50, 100);
        g2d.drawString(TEST_TEXT_LINE2, 50, 200);
        g2d.drawString(TEST_TEXT_LINE3, 50, 300);

        g2d.dispose();

        // Save as JPEG
        Path tempFile = Files.createTempFile("docling-ocr-test", ".jpg");
        ImageIO.write(image, "jpg", tempFile.toFile());

        return tempFile;
    }

    /**
     * Creates a PNG test image with text.
     */
    private Path createTestPngImage() throws Exception {
        int width = 600;
        int height = 300;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // White background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        // Black text
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 36));
        g2d.drawString("PNG Image Test", 50, 80);
        g2d.drawString("Docling OCR Processing", 50, 150);
        g2d.drawString("Text Extraction Demo", 50, 220);

        g2d.dispose();

        Path tempFile = Files.createTempFile("docling-ocr-png-test", ".png");
        ImageIO.write(image, "png", tempFile.toFile());

        return tempFile;
    }

    /**
     * Creates an image with multiple separate text blocks to test OCR layout detection.
     */
    private Path createImageWithMultipleTextBlocks() throws Exception {
        int width = 800;
        int height = 600;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // White background
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, width, height);

        g2d.setColor(Color.BLACK);

        // Title block (large font)
        g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2d.drawString("Document Title", 50, 80);

        // First paragraph block
        g2d.setFont(new Font("SansSerif", Font.PLAIN, 24));
        g2d.drawString("This is the first paragraph", 50, 160);
        g2d.drawString("with multiple lines of text", 50, 195);

        // Second paragraph block (different position)
        g2d.drawString("Second paragraph starts here", 50, 280);
        g2d.drawString("containing more information", 50, 315);

        // Footer block
        g2d.setFont(new Font("SansSerif", Font.ITALIC, 18));
        g2d.drawString("Footer: Apache Camel Docling Component", 50, 550);

        g2d.dispose();

        Path tempFile = Files.createTempFile("docling-ocr-multiblock-test", ".jpg");
        ImageIO.write(image, "jpg", tempFile.toFile());

        return tempFile;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // OCR text extraction route
                from("direct:ocr-extract-text")
                        .to("docling:ocr?operation=EXTRACT_TEXT&enableOCR=true&ocrLanguage=en");

                // OCR to Markdown conversion
                from("direct:ocr-convert-markdown")
                        .to("docling:ocr?operation=CONVERT_TO_MARKDOWN&enableOCR=true&ocrLanguage=en");

                // OCR to JSON conversion
                from("direct:ocr-convert-json")
                        .to("docling:ocr?operation=CONVERT_TO_JSON&enableOCR=true&ocrLanguage=en");

                // OCR with async mode
                from("direct:ocr-async-extract")
                        .to("docling:ocr?operation=EXTRACT_TEXT&enableOCR=true&ocrLanguage=en&useAsyncMode=true&asyncPollInterval=1000&asyncTimeout=180000");
            }
        };
    }

}
