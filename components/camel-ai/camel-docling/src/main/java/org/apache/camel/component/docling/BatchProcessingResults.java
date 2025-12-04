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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Contains the results of a batch document processing operation.
 */
public class BatchProcessingResults {

    private List<BatchConversionResult> results;
    private int totalDocuments;
    private int successCount;
    private int failureCount;
    private long totalProcessingTimeMs;
    private long startTimeMs;
    private long endTimeMs;

    public BatchProcessingResults() {
        this.results = new ArrayList<>();
    }

    public BatchProcessingResults(List<BatchConversionResult> results) {
        this.results = results != null ? results : new ArrayList<>();
        updateCounts();
    }

    /**
     * Gets the list of all conversion results.
     *
     * @return list of batch conversion results
     */
    public List<BatchConversionResult> getResults() {
        return results;
    }

    public void setResults(List<BatchConversionResult> results) {
        this.results = results;
        updateCounts();
    }

    /**
     * Adds a conversion result to the batch.
     *
     * @param result the result to add
     */
    public void addResult(BatchConversionResult result) {
        this.results.add(result);
        updateCounts();
    }

    /**
     * Gets only the successful conversion results.
     *
     * @return list of successful results
     */
    public List<BatchConversionResult> getSuccessful() {
        return results.stream().filter(BatchConversionResult::isSuccess).collect(Collectors.toList());
    }

    /**
     * Gets only the failed conversion results.
     *
     * @return list of failed results
     */
    public List<BatchConversionResult> getFailed() {
        return results.stream().filter(r -> !r.isSuccess()).collect(Collectors.toList());
    }

    /**
     * Gets the total number of documents in the batch.
     *
     * @return total document count
     */
    public int getTotalDocuments() {
        return totalDocuments;
    }

    public void setTotalDocuments(int totalDocuments) {
        this.totalDocuments = totalDocuments;
    }

    /**
     * Gets the number of successfully converted documents.
     *
     * @return success count
     */
    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    /**
     * Gets the number of failed document conversions.
     *
     * @return failure count
     */
    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    /**
     * Gets the total processing time for the entire batch in milliseconds.
     *
     * @return total processing time
     */
    public long getTotalProcessingTimeMs() {
        return totalProcessingTimeMs;
    }

    public void setTotalProcessingTimeMs(long totalProcessingTimeMs) {
        this.totalProcessingTimeMs = totalProcessingTimeMs;
    }

    /**
     * Gets the start time of batch processing in milliseconds since epoch.
     *
     * @return start time
     */
    public long getStartTimeMs() {
        return startTimeMs;
    }

    public void setStartTimeMs(long startTimeMs) {
        this.startTimeMs = startTimeMs;
    }

    /**
     * Gets the end time of batch processing in milliseconds since epoch.
     *
     * @return end time
     */
    public long getEndTimeMs() {
        return endTimeMs;
    }

    public void setEndTimeMs(long endTimeMs) {
        this.endTimeMs = endTimeMs;
        if (startTimeMs > 0) {
            this.totalProcessingTimeMs = endTimeMs - startTimeMs;
        }
    }

    /**
     * Indicates whether all documents were processed successfully.
     *
     * @return true if all successful, false otherwise
     */
    public boolean isAllSuccessful() {
        return failureCount == 0 && totalDocuments > 0;
    }

    /**
     * Indicates whether any documents were processed successfully.
     *
     * @return true if at least one success, false otherwise
     */
    public boolean hasAnySuccessful() {
        return successCount > 0;
    }

    /**
     * Indicates whether any documents failed to process.
     *
     * @return true if at least one failure, false otherwise
     */
    public boolean hasAnyFailures() {
        return failureCount > 0;
    }

    /**
     * Gets the success rate as a percentage.
     *
     * @return success rate (0.0 to 100.0)
     */
    public double getSuccessRate() {
        if (totalDocuments == 0) {
            return 0.0;
        }
        return (successCount * 100.0) / totalDocuments;
    }

    /**
     * Updates the success and failure counts based on current results.
     */
    private void updateCounts() {
        this.totalDocuments = results.size();
        this.successCount =
                (int) results.stream().filter(BatchConversionResult::isSuccess).count();
        this.failureCount = totalDocuments - successCount;
    }

    @Override
    public String toString() {
        return "BatchProcessingResults{" + "totalDocuments=" + totalDocuments + ", successCount=" + successCount
                + ", failureCount=" + failureCount + ", totalProcessingTimeMs=" + totalProcessingTimeMs
                + ", successRate="
                + String.format("%.2f%%", getSuccessRate()) + '}';
    }
}
