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

import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that {@link DoclingContentExtractor} returns the content matching the requested output format.
 * <p>
 * {@code doctags} is one of the documented values of the {@code outputFormat} option, and docling-serve returns it in a
 * field of its own, so asking for it must not fall through to the markdown branch: that branch finds no markdown in a
 * response that only carries DocTags and returns an empty body.
 */
class DoclingContentExtractorTest {

    @Test
    void doctagsReturnsTheDoctagsContent() throws Exception {
        // docling-serve only fills the fields of the formats that were requested, so a request for doctags returns a
        // document with doctags content and no markdown at all
        ConvertDocumentResponse response = responseWith(document().doctagsContent("<doctag>text</doctag>").build());

        assertThat(DoclingContentExtractor.extract(response, "doctags")).isEqualTo("<doctag>text</doctag>");
    }

    @Test
    void doctagsIsRecognisedInUpperCase() throws Exception {
        ConvertDocumentResponse response = responseWith(document().doctagsContent("<doctag>text</doctag>").build());

        assertThat(DoclingContentExtractor.extract(response, "DOCTAGS")).isEqualTo("<doctag>text</doctag>");
    }

    @Test
    void missingDoctagsContentIsAnEmptyBody() throws Exception {
        ConvertDocumentResponse response = responseWith(document().markdownContent("# text").build());

        assertThat(DoclingContentExtractor.extract(response, "doctags")).isEmpty();
    }

    @Test
    void otherFormatsStillReturnTheirOwnContent() throws Exception {
        // guard: widening the doctags case must not redirect the formats that were already right
        ConvertDocumentResponse everything = responseWith(document()
                .markdownContent("# md")
                .htmlContent("<h1>html</h1>")
                .textContent("plain")
                .doctagsContent("<doctag>text</doctag>")
                .build());

        assertThat(DoclingContentExtractor.extract(everything, "markdown")).isEqualTo("# md");
        assertThat(DoclingContentExtractor.extract(everything, "html")).isEqualTo("<h1>html</h1>");
        assertThat(DoclingContentExtractor.extract(everything, "text")).isEqualTo("plain");
        assertThat(DoclingContentExtractor.extract(everything, "txt")).isEqualTo("plain");
    }

    private static DocumentResponse.Builder document() {
        return DocumentResponse.builder().filename("input.pdf");
    }

    private static ConvertDocumentResponse responseWith(DocumentResponse document) {
        return InBodyConvertDocumentResponse.builder().document(document).status("success").build();
    }
}
