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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import ai.docling.core.DoclingDocument;
import ai.docling.core.DoclingDocument.PictureItem;
import ai.docling.core.DoclingDocument.TableData;
import ai.docling.core.DoclingDocument.TableItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.docling.DoclingHeaders;
import org.apache.camel.component.docling.DoclingOperations;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;

@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Too much resources on GitHub Actions")
class ExtractStructuredDataIT extends DoclingITestSupport {

    @Test
    void extractTableFromMarkdown() throws Exception {
        Path testFile = createTestFile();

        String result = template.requestBodyAndHeader("direct:extract-structured-data",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.EXTRACT_STRUCTURED_DATA, String.class);
        ObjectMapper mapper = new ObjectMapper();
        DoclingDocument doclingDocument = mapper.readValue(result, DoclingDocument.class);

        List<TableItem> tables = doclingDocument.getTables();
        assertThat(tables).hasSize(1);
        TableData table = tables.get(0).getData();
        assertThat(table.getNumCols()).isEqualTo(3);
        assertThat(table.getNumRows()).isEqualTo(4);
        assertThat(table.getGrid().get(1).get(2).getText()).isEqualTo("C1");
    }

    @Test
    void extractImageFromPDF() throws Exception {
        Path testFile = createTestPdfFile();

        String result = template.requestBodyAndHeader("direct:extract-structured-data",
                testFile.toString(),
                DoclingHeaders.OPERATION, DoclingOperations.EXTRACT_STRUCTURED_DATA, String.class);
        ObjectMapper mapper = new ObjectMapper();
        DoclingDocument doclingDocument = mapper.readValue(result, DoclingDocument.class);

        List<PictureItem> pictures = doclingDocument.getPictures();
        assertThat(pictures).hasSize(2);
    }

    private Path createTestFile() throws Exception {
        Path tempFile = Files.createTempFile("docling-extract-structureddata-test-", ".md");
        String content = """
                # Test Document

                This is a test document for structured data

                ## Section 1

                Some content here.

                - List item 1
                - List item 2

                ## Section 2

                |  A |  B |  C |
                |---|---|---|
                | A1  | B1  | C1  |
                |   A2|  B2 | C2  |
                |  A3 |  B3 | C3  |
                """;
        Files.write(tempFile, content.getBytes());
        return tempFile;
    }

    private Path createTestPdfFile() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("picture_classification.pdf")) {
            java.nio.file.Path tempFile = Files.createTempFile("docling-test-picture_classification", ".pdf");
            Files.copy(is, tempFile.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile;
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:extract-structured-data")
                        .to("docling:convert?operation=EXTRACT_STRUCTURED_DATA&contentInBody=true");
            }
        };
    }
}
