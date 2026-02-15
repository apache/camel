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

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DoclingComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpoint() throws Exception {
        Endpoint endpoint = context.getEndpoint("docling:convert");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof DoclingEndpoint);

        DoclingEndpoint doclingEndpoint = (DoclingEndpoint) endpoint;
        assertEquals("convert", doclingEndpoint.getOperationId());
        assertEquals(DoclingOperations.CONVERT_TO_MARKDOWN, doclingEndpoint.getConfiguration().getOperation());
        assertTrue(doclingEndpoint.getConfiguration().isEnableOCR()); // OCR is enabled by default
    }

    @Test
    public void testCreateEndpointWithParameters() throws Exception {
        Endpoint endpoint = context.getEndpoint("docling:process?operation=CONVERT_TO_HTML&enableOCR=false&ocrLanguage=es");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof DoclingEndpoint);

        DoclingEndpoint doclingEndpoint = (DoclingEndpoint) endpoint;
        assertEquals("process", doclingEndpoint.getOperationId());
        assertEquals(DoclingOperations.CONVERT_TO_HTML, doclingEndpoint.getConfiguration().getOperation());
        assertFalse(doclingEndpoint.getConfiguration().isEnableOCR());
        assertEquals("es", doclingEndpoint.getConfiguration().getOcrLanguage());
    }

    @Test
    public void testProducerCreation() throws Exception {
        DoclingEndpoint endpoint = (DoclingEndpoint) context.getEndpoint("docling:convert");
        assertNotNull(endpoint.createProducer());
    }

    @Test
    public void testNewConfigPropertiesDefaultToNull() throws Exception {
        DoclingEndpoint endpoint = (DoclingEndpoint) context.getEndpoint("docling:convert");
        DoclingConfiguration config = endpoint.getConfiguration();

        assertNull(config.getDoOcr());
        assertNull(config.getForceOcr());
        assertNull(config.getOcrEngine());
        assertNull(config.getPdfBackend());
        assertNull(config.getTableMode());
        assertNull(config.getTableCellMatching());
        assertNull(config.getDoTableStructure());
        assertNull(config.getPipeline());
        assertNull(config.getDoCodeEnrichment());
        assertNull(config.getDoFormulaEnrichment());
        assertNull(config.getDoPictureClassification());
        assertNull(config.getDoPictureDescription());
        assertNull(config.getIncludeImages());
        assertNull(config.getImageExportMode());
        assertNull(config.getAbortOnError());
        assertNull(config.getDocumentTimeout());
        assertNull(config.getImagesScale());
        assertNull(config.getMdPageBreakPlaceholder());
    }

    @Test
    public void testNewConfigPropertiesParsedFromUri() throws Exception {
        Endpoint endpoint = context.getEndpoint(
                "docling:convert?doOcr=true&forceOcr=false&ocrEngine=TESSERACT"
                                                + "&pdfBackend=DLPARSE_V4&tableMode=ACCURATE&tableCellMatching=true"
                                                + "&doTableStructure=true&pipeline=STANDARD"
                                                + "&doCodeEnrichment=true&doFormulaEnrichment=true"
                                                + "&doPictureClassification=false&doPictureDescription=false"
                                                + "&includeImages=true&imageExportMode=EMBEDDED"
                                                + "&abortOnError=true&documentTimeout=120&imagesScale=2.0"
                                                + "&mdPageBreakPlaceholder=---");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof DoclingEndpoint);

        DoclingConfiguration config = ((DoclingEndpoint) endpoint).getConfiguration();
        assertEquals(Boolean.TRUE, config.getDoOcr());
        assertEquals(Boolean.FALSE, config.getForceOcr());
        assertEquals("TESSERACT", config.getOcrEngine());
        assertEquals("DLPARSE_V4", config.getPdfBackend());
        assertEquals("ACCURATE", config.getTableMode());
        assertEquals(Boolean.TRUE, config.getTableCellMatching());
        assertEquals(Boolean.TRUE, config.getDoTableStructure());
        assertEquals("STANDARD", config.getPipeline());
        assertEquals(Boolean.TRUE, config.getDoCodeEnrichment());
        assertEquals(Boolean.TRUE, config.getDoFormulaEnrichment());
        assertEquals(Boolean.FALSE, config.getDoPictureClassification());
        assertEquals(Boolean.FALSE, config.getDoPictureDescription());
        assertEquals(Boolean.TRUE, config.getIncludeImages());
        assertEquals("EMBEDDED", config.getImageExportMode());
        assertEquals(Boolean.TRUE, config.getAbortOnError());
        assertEquals(Long.valueOf(120), config.getDocumentTimeout());
        assertEquals(Double.valueOf(2.0), config.getImagesScale());
        assertEquals("---", config.getMdPageBreakPlaceholder());
    }

}
