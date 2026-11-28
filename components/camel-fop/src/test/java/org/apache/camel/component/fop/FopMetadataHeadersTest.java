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
package org.apache.camel.component.fop;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FopMetadataHeadersTest extends CamelTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("team", "Riders");
    }

    @Test
    public void metadataHeadersAreWrittenToThePdf() throws Exception {
        Date creationDate = new Date(1700000000000L);
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFop.Render.producer", "Report Service");
        headers.put("CamelFop.Render.creator", "Test User");
        headers.put("CamelFop.Render.author", "Jane Doe");
        headers.put("CamelFop.Render.title", "Monthly Report");
        headers.put("CamelFop.Render.subject", "Sales");
        headers.put("CamelFop.Render.keywords", "sales, monthly");
        headers.put("CamelFop.Render.creationDate", creationDate);

        try (PDDocument document = render(headers)) {
            PDDocumentInformation info = document.getDocumentInformation();
            assertEquals("Report Service", info.getProducer());
            assertEquals("Test User", info.getCreator());
            assertEquals("Jane Doe", info.getAuthor());
            assertEquals("Monthly Report", info.getTitle());
            assertEquals("Sales", info.getSubject());
            assertEquals("sales, monthly", info.getKeywords());
            Calendar created = info.getCreationDate();
            assertEquals(creationDate.getTime() / 1000, created.getTimeInMillis() / 1000);
        }
    }

    @Test
    public void metadataHeaderValueIsWrittenAsGiven() throws Exception {
        context.getPropertiesComponent().addInitialProperty("name", "Camel");

        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFop.Render.title", "Template {{name}}");
        headers.put("CamelFop.Render.keywords", "#team");
        headers.put("CamelFop.Render.author", "#1 Team");

        try (PDDocument document = render(headers)) {
            PDDocumentInformation info = document.getDocumentInformation();
            assertEquals("Template {{name}}", info.getTitle());
            assertEquals("#team", info.getKeywords());
            assertEquals("#1 Team", info.getAuthor());
        }
    }

    @Test
    public void metadataHeaderNameIsCaseInsensitive() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFop.Render.Creator", "Test User");
        headers.put("CamelFop.Render.TITLE", "Upper Case");

        try (PDDocument document = render(headers)) {
            PDDocumentInformation info = document.getDocumentInformation();
            assertEquals("Test User", info.getCreator());
            assertEquals("Upper Case", info.getTitle());
        }
    }

    @Test
    public void unsupportedRenderHeaderIsIgnored() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFop.Render.unknownOption", "some value");
        headers.put("CamelFop.Render.title", "Monthly Report");

        try (PDDocument document = render(headers)) {
            assertEquals("Monthly Report", document.getDocumentInformation().getTitle());
            assertTrue(FopHelper.extractTextFrom(document).contains("Test Content"));
        }
    }

    private PDDocument render(Map<String, Object> headers) throws Exception {
        byte[] pdf = template.requestBodyAndHeaders("fop:pdf", FopHelper.decorateTextWithXSLFO("Test Content"), headers,
                byte[].class);
        return Loader.loadPDF(pdf);
    }
}
