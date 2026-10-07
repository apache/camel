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

import java.io.IOException;

import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts the converted content from a docling-serve {@link ConvertDocumentResponse} in the requested output format.
 * <p>
 * Shared by {@link DoclingProducer} (synchronous {@code CONVERT_*} operations and {@code CHECK_CONVERSION_STATUS}) and
 * {@link DoclingConsumer} (async task completion), so both yield the same body shape for a given output format.
 */
final class DoclingContentExtractor {

    private static final Logger LOG = LoggerFactory.getLogger(DoclingContentExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DoclingContentExtractor() {
    }

    static String extract(ConvertDocumentResponse response, String outputFormat) throws IOException {
        try {
            if (response instanceof InBodyConvertDocumentResponse inBodyResponse) {
                DocumentResponse document = inBodyResponse.getDocument();

                if (document == null) {
                    throw new IOException("No document in response");
                }

                String format = mapOutputFormat(outputFormat);

                switch (format) {
                    case "md":
                        String markdown = document.getMarkdownContent();
                        return markdown != null ? markdown : "";
                    case "html":
                        String html = document.getHtmlContent();
                        return html != null ? html : "";
                    case "text":
                        String text = document.getTextContent();
                        return text != null ? text : "";
                    case "json":
                        // Return the document JSON content
                        var jsonDoc = document.getJsonContent();
                        if (jsonDoc != null) {
                            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(jsonDoc);
                        }
                        return "{}";
                    default:
                        // Default to markdown
                        String defaultMarkdown = document.getMarkdownContent();
                        return defaultMarkdown != null ? defaultMarkdown : "";
                }
            } else {
                throw new IOException("Unsupported response type: cannot extract converted content");
            }
        } catch (Exception e) {
            LOG.warn("Failed to extract content from response: {}", e.getMessage());
            throw new IOException("Failed to extract content from response", e);
        }
    }

    private static String mapOutputFormat(String outputFormat) {
        if (outputFormat == null) {
            return "md";
        }

        switch (outputFormat.toLowerCase()) {
            case "markdown":
            case "md":
                return "md";
            case "html":
                return "html";
            case "json":
                return "json";
            case "text":
            case "txt":
                return "text";
            default:
                return "md";
        }
    }
}
