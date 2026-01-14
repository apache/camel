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
package org.apache.camel.component.ibm.watsonx.ai.handler;

import java.util.HashMap;
import java.util.Map;

import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionResponse;
import com.ibm.watsonx.ai.textprocessing.textextraction.TextExtractionService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;
import org.apache.camel.component.ibm.watsonx.ai.support.FileInput;

/**
 * Handler for text extraction operations.
 */
public class TextExtractionHandler extends AbstractWatsonxAiHandler {

    public TextExtractionHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case textExtraction:
                return processTextExtraction(exchange);
            case textExtractionFetch:
                return processTextExtractionFetch(exchange);
            case textExtractionUpload:
                return processTextExtractionUpload(exchange);
            case textExtractionUploadAndFetch:
                return processTextExtractionUploadAndFetch(exchange);
            case textExtractionUploadFile:
                return processTextExtractionUploadFile(exchange);
            case textExtractionReadFile:
                return processTextExtractionReadFile(exchange);
            case textExtractionDeleteFile:
                return processTextExtractionDeleteFile(exchange);
            case textExtractionDeleteRequest:
                return processTextExtractionDeleteRequest(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.textExtraction,
                WatsonxAiOperations.textExtractionFetch,
                WatsonxAiOperations.textExtractionUpload,
                WatsonxAiOperations.textExtractionUploadAndFetch,
                WatsonxAiOperations.textExtractionUploadFile,
                WatsonxAiOperations.textExtractionReadFile,
                WatsonxAiOperations.textExtractionDeleteFile,
                WatsonxAiOperations.textExtractionDeleteRequest
        };
    }

    private WatsonxAiOperationResponse processTextExtraction(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get file path from body or header
        String filePath = in.getHeader(WatsonxAiConstants.FILE_PATH, String.class);
        if (filePath == null || filePath.isEmpty()) {
            filePath = in.getBody(String.class);
        }

        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException(
                    "File path must be provided via message body or header '" + WatsonxAiConstants.FILE_PATH + "'");
        }

        // Call the service
        TextExtractionService service = endpoint.getTextExtractionService();
        TextExtractionResponse response = service.startExtraction(filePath);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.EXTRACTION_ID, response.metadata().id());
        headers.put(WatsonxAiConstants.EXTRACTION_STATUS, response.entity().results().status());

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processTextExtractionFetch(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get extraction ID from body or header
        String extractionId = in.getHeader(WatsonxAiConstants.EXTRACTION_ID, String.class);
        if (extractionId == null || extractionId.isEmpty()) {
            extractionId = in.getBody(String.class);
        }

        if (extractionId == null || extractionId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Extraction ID must be provided via message body or header '" + WatsonxAiConstants.EXTRACTION_ID + "'");
        }

        // Call the service
        TextExtractionService service = endpoint.getTextExtractionService();
        TextExtractionResponse response = service.fetchExtractionRequest(extractionId);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.EXTRACTION_ID, response.metadata().id());
        headers.put(WatsonxAiConstants.EXTRACTION_STATUS, response.entity().results().status());
        // Set error headers if extraction failed
        if (response.entity().results().error() != null) {
            headers.put(WatsonxAiConstants.ERROR_CODE, response.entity().results().error().code());
            headers.put(WatsonxAiConstants.ERROR_MESSAGE, response.entity().results().error().message());
        }

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processTextExtractionUpload(Exchange exchange) throws Exception {
        FileInput input = getFileInput(exchange);
        TextExtractionService service = endpoint.getTextExtractionService();

        TextExtractionResponse response;
        if (input.isFile()) {
            response = service.uploadAndStartExtraction(input.file());
        } else {
            response = service.uploadAndStartExtraction(input.inputStream(), input.fileName());
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.EXTRACTION_ID, response.metadata().id());
        headers.put(WatsonxAiConstants.EXTRACTION_STATUS, response.entity().results().status());

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processTextExtractionUploadAndFetch(Exchange exchange) throws Exception {
        FileInput input = getFileInput(exchange);
        TextExtractionService service = endpoint.getTextExtractionService();

        String extractedText;
        if (input.isFile()) {
            extractedText = service.uploadExtractAndFetch(input.file());
        } else {
            extractedText = service.uploadExtractAndFetch(input.inputStream(), input.fileName());
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.EXTRACTED_TEXT, extractedText);

        return WatsonxAiOperationResponse.create(extractedText, headers);
    }

    private WatsonxAiOperationResponse processTextExtractionUploadFile(Exchange exchange) throws Exception {
        FileInput input = getFileInput(exchange);
        TextExtractionService service = endpoint.getTextExtractionService();

        boolean success;
        if (input.isFile()) {
            success = service.uploadFile(input.file());
        } else {
            success = service.uploadFile(input.inputStream(), input.fileName());
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.UPLOAD_SUCCESS, success);

        return WatsonxAiOperationResponse.create(success, headers);
    }

    private WatsonxAiOperationResponse processTextExtractionReadFile(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get bucket name from header or configuration
        String bucketName = in.getHeader(WatsonxAiConstants.BUCKET_NAME, String.class);
        if (bucketName == null || bucketName.isEmpty()) {
            bucketName = getConfiguration().getResultBucket();
        }
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bucket name must be provided via header '" + WatsonxAiConstants.BUCKET_NAME
                                               + "' or configuration 'resultBucket'");
        }

        // Get file name from body or header
        String fileName = in.getHeader(WatsonxAiConstants.FILE_NAME, String.class);
        if (fileName == null || fileName.isEmpty()) {
            fileName = in.getBody(String.class);
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException(
                    "File name must be provided via message body or header '" + WatsonxAiConstants.FILE_NAME + "'");
        }

        TextExtractionService service = endpoint.getTextExtractionService();
        String content = service.readFile(bucketName, fileName);

        return WatsonxAiOperationResponse.create(content);
    }

    private WatsonxAiOperationResponse processTextExtractionDeleteFile(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get bucket name from header or configuration
        String bucketName = in.getHeader(WatsonxAiConstants.BUCKET_NAME, String.class);
        if (bucketName == null || bucketName.isEmpty()) {
            bucketName = getConfiguration().getResultBucket();
        }
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bucket name must be provided via header '" + WatsonxAiConstants.BUCKET_NAME
                                               + "' or configuration 'resultBucket'");
        }

        // Get file name from body or header
        String fileName = in.getHeader(WatsonxAiConstants.FILE_NAME, String.class);
        if (fileName == null || fileName.isEmpty()) {
            fileName = in.getBody(String.class);
        }
        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException(
                    "File name must be provided via message body or header '" + WatsonxAiConstants.FILE_NAME + "'");
        }

        TextExtractionService service = endpoint.getTextExtractionService();
        boolean success = service.deleteFile(bucketName, fileName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.DELETE_SUCCESS, success);

        return WatsonxAiOperationResponse.create(success, headers);
    }

    private WatsonxAiOperationResponse processTextExtractionDeleteRequest(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get extraction ID from body or header
        String extractionId = in.getHeader(WatsonxAiConstants.EXTRACTION_ID, String.class);
        if (extractionId == null || extractionId.isEmpty()) {
            extractionId = in.getBody(String.class);
        }
        if (extractionId == null || extractionId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Extraction ID must be provided via message body or header '" + WatsonxAiConstants.EXTRACTION_ID + "'");
        }

        TextExtractionService service = endpoint.getTextExtractionService();
        boolean success = service.deleteRequest(extractionId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.DELETE_SUCCESS, success);

        return WatsonxAiOperationResponse.create(success, headers);
    }
}
