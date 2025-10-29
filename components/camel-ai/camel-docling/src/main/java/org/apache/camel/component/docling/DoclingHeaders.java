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

import org.apache.camel.spi.Metadata;

public final class DoclingHeaders {

    @Metadata(description = "The operation to perform", javaType = "DoclingOperations")
    public static final String OPERATION = "CamelDoclingOperation";

    @Metadata(description = "The output format for conversion", javaType = "String")
    public static final String OUTPUT_FORMAT = "CamelDoclingOutputFormat";

    @Metadata(description = "The input file path or content", javaType = "String")
    public static final String INPUT_FILE_PATH = "CamelDoclingInputFilePath";

    @Metadata(description = "The output file path for saving result", javaType = "String")
    public static final String OUTPUT_FILE_PATH = "CamelDoclingOutputFilePath";

    @Metadata(description = "Additional processing options", javaType = "Map<String, Object>")
    public static final String PROCESSING_OPTIONS = "CamelDoclingProcessingOptions";

    @Metadata(description = "Whether to include OCR processing", javaType = "Boolean")
    public static final String ENABLE_OCR = "CamelDoclingEnableOCR";

    @Metadata(description = "Language for OCR processing", javaType = "String")
    public static final String OCR_LANGUAGE = "CamelDoclingOCRLanguage";

    @Metadata(description = "Custom command line arguments to pass to Docling", javaType = "List<String>")
    public static final String CUSTOM_ARGUMENTS = "CamelDoclingCustomArguments";

    @Metadata(description = "Use asynchronous conversion mode (overrides endpoint configuration)", javaType = "Boolean")
    public static final String USE_ASYNC_MODE = "CamelDoclingUseAsyncMode";

    @Metadata(description = "Polling interval for async conversion status in milliseconds", javaType = "Long")
    public static final String ASYNC_POLL_INTERVAL = "CamelDoclingAsyncPollInterval";

    @Metadata(description = "Maximum time to wait for async conversion completion in milliseconds", javaType = "Long")
    public static final String ASYNC_TIMEOUT = "CamelDoclingAsyncTimeout";

    @Metadata(description = "Task ID for checking async conversion status", javaType = "String")
    public static final String TASK_ID = "CamelDoclingTaskId";

    @Metadata(description = "Override batch size for this operation", javaType = "Integer")
    public static final String BATCH_SIZE = "CamelDoclingBatchSize";

    @Metadata(description = "Override batch parallelism for this operation", javaType = "Integer")
    public static final String BATCH_PARALLELISM = "CamelDoclingBatchParallelism";

    @Metadata(description = "Override batch fail on first error setting for this operation", javaType = "Boolean")
    public static final String BATCH_FAIL_ON_FIRST_ERROR = "CamelDoclingBatchFailOnFirstError";

    @Metadata(description = "Override batch timeout for this operation in milliseconds", javaType = "Long")
    public static final String BATCH_TIMEOUT = "CamelDoclingBatchTimeout";

    @Metadata(description = "Total number of documents in the batch", javaType = "Integer")
    public static final String BATCH_TOTAL_DOCUMENTS = "CamelDoclingBatchTotalDocuments";

    @Metadata(description = "Number of successfully processed documents in the batch", javaType = "Integer")
    public static final String BATCH_SUCCESS_COUNT = "CamelDoclingBatchSuccessCount";

    @Metadata(description = "Number of failed documents in the batch", javaType = "Integer")
    public static final String BATCH_FAILURE_COUNT = "CamelDoclingBatchFailureCount";

    @Metadata(description = "Total processing time for the batch in milliseconds", javaType = "Long")
    public static final String BATCH_PROCESSING_TIME = "CamelDoclingBatchProcessingTime";

    @Metadata(description = "Split batch results into individual exchanges instead of single BatchProcessingResults",
              javaType = "Boolean")
    public static final String BATCH_SPLIT_RESULTS = "CamelDoclingBatchSplitResults";

    @Metadata(description = "Document title extracted from metadata", javaType = "String")
    public static final String METADATA_TITLE = "CamelDoclingMetadataTitle";

    @Metadata(description = "Document author extracted from metadata", javaType = "String")
    public static final String METADATA_AUTHOR = "CamelDoclingMetadataAuthor";

    @Metadata(description = "Document creator application", javaType = "String")
    public static final String METADATA_CREATOR = "CamelDoclingMetadataCreator";

    @Metadata(description = "Document producer application", javaType = "String")
    public static final String METADATA_PRODUCER = "CamelDoclingMetadataProducer";

    @Metadata(description = "Document subject", javaType = "String")
    public static final String METADATA_SUBJECT = "CamelDoclingMetadataSubject";

    @Metadata(description = "Document keywords", javaType = "String")
    public static final String METADATA_KEYWORDS = "CamelDoclingMetadataKeywords";

    @Metadata(description = "Document creation date", javaType = "java.time.Instant")
    public static final String METADATA_CREATION_DATE = "CamelDoclingMetadataCreationDate";

    @Metadata(description = "Document modification date", javaType = "java.time.Instant")
    public static final String METADATA_MODIFICATION_DATE = "CamelDoclingMetadataModificationDate";

    @Metadata(description = "Number of pages in the document", javaType = "Integer")
    public static final String METADATA_PAGE_COUNT = "CamelDoclingMetadataPageCount";

    @Metadata(description = "Document language code", javaType = "String")
    public static final String METADATA_LANGUAGE = "CamelDoclingMetadataLanguage";

    @Metadata(description = "Document type/format", javaType = "String")
    public static final String METADATA_DOCUMENT_TYPE = "CamelDoclingMetadataDocumentType";

    @Metadata(description = "Document format (MIME type)", javaType = "String")
    public static final String METADATA_FORMAT = "CamelDoclingMetadataFormat";

    @Metadata(description = "File size in bytes", javaType = "Long")
    public static final String METADATA_FILE_SIZE = "CamelDoclingMetadataFileSize";

    @Metadata(description = "File name", javaType = "String")
    public static final String METADATA_FILE_NAME = "CamelDoclingMetadataFileName";

    @Metadata(description = "Custom metadata fields as a Map", javaType = "Map<String, Object>")
    public static final String METADATA_CUSTOM = "CamelDoclingMetadataCustom";

    @Metadata(description = "Raw metadata fields as a Map", javaType = "Map<String, Object>")
    public static final String METADATA_RAW = "CamelDoclingMetadataRaw";

    private DoclingHeaders() {
    }

}
