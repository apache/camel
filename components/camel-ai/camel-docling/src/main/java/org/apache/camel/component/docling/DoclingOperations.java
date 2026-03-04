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

public enum DoclingOperations {

    /**
     * Convert document to markdown format
     */
    CONVERT_TO_MARKDOWN,

    /**
     * Convert document to HTML format
     */
    CONVERT_TO_HTML,

    /**
     * Convert document to JSON format with user-specified or default options.
     */
    CONVERT_TO_JSON,

    /**
     * Extract text content from document
     */
    EXTRACT_TEXT,

    /**
     * Extract structured data from a document with table structure recognition enabled by default. Unlike
     * {@link #CONVERT_TO_JSON} which performs a plain format conversion, this operation enables table structure
     * recognition by default. Additional enrichment features (code enrichment, formula enrichment, picture
     * classification) can be enabled via configuration properties.
     */
    EXTRACT_STRUCTURED_DATA,

    /**
     * Submit an async conversion and return task ID (docling-serve only)
     */
    SUBMIT_ASYNC_CONVERSION,

    /**
     * Check the status of an async conversion task (docling-serve only)
     */
    CHECK_CONVERSION_STATUS,

    /**
     * Convert multiple documents to markdown format in a batch (docling-serve only)
     */
    BATCH_CONVERT_TO_MARKDOWN,

    /**
     * Convert multiple documents to HTML format in a batch (docling-serve only)
     */
    BATCH_CONVERT_TO_HTML,

    /**
     * Convert multiple documents to JSON format in a batch (docling-serve only)
     */
    BATCH_CONVERT_TO_JSON,

    /**
     * Extract text content from multiple documents in a batch (docling-serve only)
     */
    BATCH_EXTRACT_TEXT,

    /**
     * Extract structured data from multiple documents in a batch with table structure recognition enabled
     * (docling-serve only).
     *
     * @see #EXTRACT_STRUCTURED_DATA
     */
    BATCH_EXTRACT_STRUCTURED_DATA,

    /**
     * Extract document metadata (author, title, page count, creation date, etc.)
     */
    EXTRACT_METADATA,

    /**
     * Chunk document using HybridChunker (token-aware, structure-aware) (docling-serve only)
     */
    CHUNK_HYBRID,

    /**
     * Chunk document using HierarchicalChunker (structure-aware) (docling-serve only)
     */
    CHUNK_HIERARCHICAL

}
