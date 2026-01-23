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

import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationResponse;
import com.ibm.watsonx.ai.textprocessing.textclassification.TextClassificationService;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiConstants;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiEndpoint;
import org.apache.camel.component.ibm.watsonx.ai.WatsonxAiOperations;
import org.apache.camel.component.ibm.watsonx.ai.support.FileInput;

/**
 * Handler for text classification operations.
 */
public class TextClassificationHandler extends AbstractWatsonxAiHandler {

    public TextClassificationHandler(WatsonxAiEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public WatsonxAiOperationResponse handle(Exchange exchange, WatsonxAiOperations operation) throws Exception {
        switch (operation) {
            case textClassification:
                return processTextClassification(exchange);
            case textClassificationFetch:
                return processTextClassificationFetch(exchange);
            case textClassificationUpload:
                return processTextClassificationUpload(exchange);
            case textClassificationUploadAndFetch:
                return processTextClassificationUploadAndFetch(exchange);
            case textClassificationUploadFile:
                return processTextClassificationUploadFile(exchange);
            case textClassificationDeleteFile:
                return processTextClassificationDeleteFile(exchange);
            case textClassificationDeleteRequest:
                return processTextClassificationDeleteRequest(exchange);
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    @Override
    public WatsonxAiOperations[] getSupportedOperations() {
        return new WatsonxAiOperations[] {
                WatsonxAiOperations.textClassification,
                WatsonxAiOperations.textClassificationFetch,
                WatsonxAiOperations.textClassificationUpload,
                WatsonxAiOperations.textClassificationUploadAndFetch,
                WatsonxAiOperations.textClassificationUploadFile,
                WatsonxAiOperations.textClassificationDeleteFile,
                WatsonxAiOperations.textClassificationDeleteRequest
        };
    }

    private WatsonxAiOperationResponse processTextClassification(Exchange exchange) throws Exception {
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
        TextClassificationService service = endpoint.getTextClassificationService();
        TextClassificationResponse response = service.startClassification(filePath);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.CLASSIFICATION_ID, response.metadata().id());
        headers.put(WatsonxAiConstants.CLASSIFICATION_STATUS, response.entity().results().status());

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processTextClassificationFetch(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get classification ID from body or header
        String classificationId = in.getHeader(WatsonxAiConstants.CLASSIFICATION_ID, String.class);
        if (classificationId == null || classificationId.isEmpty()) {
            classificationId = in.getBody(String.class);
        }

        if (classificationId == null || classificationId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Classification ID must be provided via message body or header '" + WatsonxAiConstants.CLASSIFICATION_ID
                                               + "'");
        }

        // Call the service
        TextClassificationService service = endpoint.getTextClassificationService();
        TextClassificationResponse response = service.fetchClassificationRequest(classificationId);

        // Build response headers
        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.CLASSIFICATION_ID, response.metadata().id());
        headers.put(WatsonxAiConstants.CLASSIFICATION_STATUS, response.entity().results().status());
        if (response.entity().results().documentType() != null) {
            headers.put(WatsonxAiConstants.CLASSIFICATION_RESULT, response.entity().results().documentType());
        }
        if (response.entity().results().documentClassified() != null) {
            headers.put(WatsonxAiConstants.DOCUMENT_CLASSIFIED, response.entity().results().documentClassified());
        }
        // Set error headers if classification failed
        if (response.entity().results().error() != null) {
            headers.put(WatsonxAiConstants.ERROR_CODE, response.entity().results().error().code());
            headers.put(WatsonxAiConstants.ERROR_MESSAGE, response.entity().results().error().message());
        }

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processTextClassificationUpload(Exchange exchange) throws Exception {
        FileInput input = getFileInput(exchange);
        TextClassificationService service = endpoint.getTextClassificationService();

        TextClassificationResponse response;
        if (input.isFile()) {
            response = service.uploadAndStartClassification(input.file());
        } else {
            response = service.uploadAndStartClassification(input.inputStream(), input.fileName());
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.CLASSIFICATION_ID, response.metadata().id());
        headers.put(WatsonxAiConstants.CLASSIFICATION_STATUS, response.entity().results().status());

        return WatsonxAiOperationResponse.create(response, headers);
    }

    private WatsonxAiOperationResponse processTextClassificationUploadAndFetch(Exchange exchange) throws Exception {
        FileInput input = getFileInput(exchange);
        TextClassificationService service = endpoint.getTextClassificationService();

        TextClassificationResponse.ClassificationResult result;
        if (input.isFile()) {
            result = service.uploadClassifyAndFetch(input.file());
        } else {
            result = service.uploadClassifyAndFetch(input.inputStream(), input.fileName());
        }

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.CLASSIFICATION_STATUS, result.status());
        if (result.documentType() != null) {
            headers.put(WatsonxAiConstants.CLASSIFICATION_RESULT, result.documentType());
        }
        if (result.documentClassified() != null) {
            headers.put(WatsonxAiConstants.DOCUMENT_CLASSIFIED, result.documentClassified());
        }

        return WatsonxAiOperationResponse.create(result, headers);
    }

    private WatsonxAiOperationResponse processTextClassificationUploadFile(Exchange exchange) throws Exception {
        FileInput input = getFileInput(exchange);
        TextClassificationService service = endpoint.getTextClassificationService();

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

    private WatsonxAiOperationResponse processTextClassificationDeleteFile(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get bucket name from header or configuration
        String bucketName = in.getHeader(WatsonxAiConstants.BUCKET_NAME, String.class);
        if (bucketName == null || bucketName.isEmpty()) {
            bucketName = getConfiguration().getDocumentBucket();
        }
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException(
                    "Bucket name must be provided via header '" + WatsonxAiConstants.BUCKET_NAME
                                               + "' or configuration 'documentBucket'");
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

        TextClassificationService service = endpoint.getTextClassificationService();
        boolean success = service.deleteFile(bucketName, fileName);

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.DELETE_SUCCESS, success);

        return WatsonxAiOperationResponse.create(success, headers);
    }

    private WatsonxAiOperationResponse processTextClassificationDeleteRequest(Exchange exchange) throws Exception {
        Message in = exchange.getIn();

        // Get classification ID from body or header
        String classificationId = in.getHeader(WatsonxAiConstants.CLASSIFICATION_ID, String.class);
        if (classificationId == null || classificationId.isEmpty()) {
            classificationId = in.getBody(String.class);
        }
        if (classificationId == null || classificationId.isEmpty()) {
            throw new IllegalArgumentException(
                    "Classification ID must be provided via message body or header '" + WatsonxAiConstants.CLASSIFICATION_ID
                                               + "'");
        }

        TextClassificationService service = endpoint.getTextClassificationService();
        boolean success = service.deleteRequest(classificationId);

        Map<String, Object> headers = new HashMap<>();
        headers.put(WatsonxAiConstants.DELETE_SUCCESS, success);

        return WatsonxAiOperationResponse.create(success, headers);
    }
}
