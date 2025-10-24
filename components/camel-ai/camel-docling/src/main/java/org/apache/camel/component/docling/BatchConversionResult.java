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

/**
 * Represents the result of a single document conversion in a batch operation.
 */
public class BatchConversionResult {

    private String documentId;
    private String originalPath;
    private String result;
    private boolean success;
    private String errorMessage;
    private long processingTimeMs;
    private int batchIndex;

    public BatchConversionResult() {
    }

    public BatchConversionResult(String documentId, String originalPath) {
        this.documentId = documentId;
        this.originalPath = originalPath;
    }

    /**
     * Gets the unique identifier for this document in the batch.
     *
     * @return the document ID
     */
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    /**
     * Gets the original file path or URL of the document.
     *
     * @return the original path
     */
    public String getOriginalPath() {
        return originalPath;
    }

    public void setOriginalPath(String originalPath) {
        this.originalPath = originalPath;
    }

    /**
     * Gets the conversion result (content or file path depending on contentInBody configuration).
     *
     * @return the conversion result
     */
    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    /**
     * Indicates whether the conversion was successful.
     *
     * @return true if successful, false otherwise
     */
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    /**
     * Gets the error message if the conversion failed.
     *
     * @return the error message, or null if successful
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * Gets the processing time for this document in milliseconds.
     *
     * @return the processing time in milliseconds
     */
    public long getProcessingTimeMs() {
        return processingTimeMs;
    }

    public void setProcessingTimeMs(long processingTimeMs) {
        this.processingTimeMs = processingTimeMs;
    }

    /**
     * Gets the index of this document in the batch (0-based).
     *
     * @return the batch index
     */
    public int getBatchIndex() {
        return batchIndex;
    }

    public void setBatchIndex(int batchIndex) {
        this.batchIndex = batchIndex;
    }

    @Override
    public String toString() {
        return "BatchConversionResult{" + "documentId='" + documentId + '\'' + ", originalPath='" + originalPath + '\''
               + ", success=" + success + ", processingTimeMs=" + processingTimeMs + ", batchIndex=" + batchIndex + '}';
    }

}
